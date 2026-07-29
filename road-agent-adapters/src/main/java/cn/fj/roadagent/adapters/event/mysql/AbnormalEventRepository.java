package cn.fj.roadagent.adapters.event.mysql;

import cn.fj.roadagent.application.port.AbnormalEventPort;
import cn.fj.roadagent.domain.dispatch.AbnormalEventStatus;
import cn.fj.roadagent.domain.dispatch.EmergencyEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Repository
public class AbnormalEventRepository implements AbnormalEventPort {

    private static final String BASE_COLUMNS = """
            SELECT id, custom_id, occurrence_time, event_type, description, create_time
            FROM w_abnormal_event
            """;

    private static final RowMapper<EmergencyEvent> EVENT_MAPPER = (resultSet, rowNumber) ->
            new EmergencyEvent(
                    Long.toString(resultSet.getLong("id")),
                    resultSet.getString("custom_id"),
                    toInstant(resultSet.getTimestamp("occurrence_time")),
                    resultSet.getString("event_type"),
                    resultSet.getString("description")
            );

    private final JdbcTemplate jdbcTemplate;

    public AbnormalEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
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
        return jdbcTemplate.query(
                BASE_COLUMNS + """
                        WHERE event_status = ?
                          AND COALESCE(del_flag, '0') = '0'
                        ORDER BY occurrence_time IS NULL, occurrence_time ASC, id ASC
                        LIMIT 1
                        """,
                EVENT_MAPPER,
                AbnormalEventStatus.PENDING.databaseValue()
        ).stream().findFirst();
    }

    @Override
    public long countPending() {
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM w_abnormal_event
                WHERE event_status = ? AND COALESCE(del_flag, '0') = '0'
                """,
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
                  AND COALESCE(del_flag, '0') = '0'
                """,
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
                  AND COALESCE(del_flag, '0') = '0'
                """,
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
                          AND COALESCE(del_flag, '0') = '0'
                        LIMIT 1
                        """ + lockClause,
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

    private static Instant toInstant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
