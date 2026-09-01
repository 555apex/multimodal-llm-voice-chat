package cn.fj.roadagent.adapters.event.mysql;

import cn.fj.roadagent.application.port.AbnormalEventPort;
import cn.fj.roadagent.domain.dispatch.AbnormalEventStatus;
import cn.fj.roadagent.domain.dispatch.EmergencyEvent;
import cn.fj.roadagent.domain.dispatch.WorkflowStage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Repository
public class AbnormalEventRepository implements AbnormalEventPort {

    /** 兼容当前N/Y与历史0/1两种逻辑删除编码。 */
    private static final String ACTIVE_EVENT_PREDICATE =
            "(del_flag IS NULL OR del_flag IN ('N', '0'))";

    private static final String BASE_COLUMNS = """
            SELECT id, custom_id, occurrence_time, event_type, description,
                   event_city_code, event_city_name, create_time
            FROM w_abnormal_event
            """;

    private static final RowMapper<EmergencyEvent> EVENT_MAPPER = (resultSet, rowNumber) ->
            new EmergencyEvent(
                    Long.toString(resultSet.getLong("id")),
                    resultSet.getString("custom_id"),
                    toInstant(resultSet.getTimestamp("occurrence_time")),
                    resultSet.getString("event_type"),
                    resultSet.getString("description"),
                    resultSet.getString("event_city_code"),
                    resultSet.getString("event_city_name")
            );

    private final JdbcTemplate jdbcTemplate;

    public AbnormalEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<EmergencyEvent> findById(String eventId) {
        return jdbcTemplate.query(
                BASE_COLUMNS + """
                        WHERE id = ? AND %s
                        LIMIT 1
                        """.formatted(ACTIVE_EVENT_PREDICATE),
                EVENT_MAPPER,
                parseEventId(eventId)
        ).stream().findFirst();
    }

    @Override
    public Optional<EmergencyEvent> findPendingById(String eventId) {
        return queryPendingById(eventId, false);
    }

    @Override
    public Optional<EmergencyEvent> lockPendingById(String eventId) {
        return queryPendingById(eventId, true);
    }

    @Override
    public Optional<EmergencyEvent> findNextPending() {
        return findNextPendingForStage(WorkflowStage.LEVEL_1);
    }

    @Override
    public Optional<EmergencyEvent> findNextPendingForStage(WorkflowStage stage) {
        String stagePredicate = stagePredicate(stage);
        return jdbcTemplate.query(
                """
                        SELECT e.id, e.custom_id, e.occurrence_time, e.event_type,
                               e.description, e.event_city_code, e.event_city_name, e.create_time
                        FROM w_abnormal_event e
                        LEFT JOIN w_emergency_dispatch_workflow w ON w.event_id = e.id
                        WHERE e.event_status = ?
                          AND (e.del_flag IS NULL OR e.del_flag IN ('N', '0'))
                          AND %s
                        ORDER BY e.occurrence_time IS NULL, e.occurrence_time ASC,
                                 w.stage_entered_at ASC, e.id ASC
                        LIMIT 1
                        """.formatted(stagePredicate),
                EVENT_MAPPER,
                AbnormalEventStatus.PENDING.databaseValue()
        ).stream().findFirst();
    }

    @Override
    public long countPending() {
        return countPendingForStage(WorkflowStage.LEVEL_1);
    }

    @Override
    public long countPendingForStage(WorkflowStage stage) {
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM w_abnormal_event e
                LEFT JOIN w_emergency_dispatch_workflow w ON w.event_id = e.id
                WHERE e.event_status = ?
                  AND (e.del_flag IS NULL OR e.del_flag IN ('N', '0'))
                  AND %s
                """.formatted(stagePredicate(stage)),
                Long.class,
                AbnormalEventStatus.PENDING.databaseValue()
        );
        return count == null ? 0 : count;
    }

    @Override
    public boolean markDispatchApproved(String eventId, Instant updateTime) {
        return jdbcTemplate.update(
                """
                UPDATE w_abnormal_event
                SET event_status = ?, no_dispatch_reason = NULL, update_time = ?
                WHERE id = ? AND event_status = ?
                  AND %s
                """.formatted(ACTIVE_EVENT_PREDICATE),
                AbnormalEventStatus.DISPATCH_APPROVED.databaseValue(),
                Timestamp.from(updateTime),
                parseEventId(eventId),
                AbnormalEventStatus.PENDING.databaseValue()
        ) == 1;
    }

    @Override
    public boolean markNoDispatch(String eventId, String reason, Instant updateTime) {
        return jdbcTemplate.update(
                """
                UPDATE w_abnormal_event
                SET event_status = ?, no_dispatch_reason = ?, update_time = ?
                WHERE id = ? AND event_status = ?
                  AND %s
                """.formatted(ACTIVE_EVENT_PREDICATE),
                AbnormalEventStatus.NO_DISPATCH_REQUIRED.databaseValue(),
                reason,
                Timestamp.from(updateTime),
                parseEventId(eventId),
                AbnormalEventStatus.PENDING.databaseValue()
        ) == 1;
    }

    /** 保留给数据库连通性测试使用。 */
    public int countEvents() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM w_abnormal_event",
                Integer.class
        );
        return count == null ? 0 : count;
    }

    private Optional<EmergencyEvent> queryPendingById(String eventId, boolean forUpdate) {
        String lockClause = forUpdate ? " FOR UPDATE" : "";
        return jdbcTemplate.query(
                BASE_COLUMNS + """
                        WHERE id = ?
                          AND event_status = ?
                          AND %s
                        LIMIT 1
                        """.formatted(ACTIVE_EVENT_PREDICATE) + lockClause,
                EVENT_MAPPER,
                parseEventId(eventId),
                AbnormalEventStatus.PENDING.databaseValue()
        ).stream().findFirst();
    }

    private long parseEventId(String eventId) {
        try {
            return Long.parseLong(eventId);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("事件ID格式不正确");
        }
    }

    private String stagePredicate(WorkflowStage stage) {
        if (stage == null) {
            throw new IllegalArgumentException("工作流阶段不能为空");
        }
        return switch (stage) {
            case LEVEL_1 -> """
                    (w.workflow_id IS NULL OR (
                        w.current_stage = 1 AND w.workflow_status IN (0, 1, 2, 5, 6)
                    ))
                    """;
            case LEVEL_2 -> "w.current_stage = 2 AND w.workflow_status = 3";
            case LEVEL_3 -> "w.current_stage = 3 AND w.workflow_status = 4";
        };
    }

    private static Instant toInstant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
