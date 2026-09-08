package cn.fj.roadagent.adapters.event.mysql;

import cn.fj.roadagent.application.port.AbnormalEventPort;
import cn.fj.roadagent.domain.dispatch.EmergencyEvent;
import cn.fj.roadagent.domain.dispatch.WorkflowStage;
import cn.fj.roadagent.domain.dispatch.UnclassifiedEmergencyEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * w_lw_incident贴源事件适配器；业务标识始终使用c_no。
 * 展示与模型事实字段止于route_name，post及人员/处置单位字段不进入项目；
 * status/completed/deleted仅作为系统待办过滤和完成回写字段使用。
 */
@Repository
public class AbnormalEventRepository implements AbnormalEventPort {
    private static final Pattern CONTACT_CLAUSE = Pattern.compile(
            "(?:[，,；;\\s]*)?(?:现场)?(?:联系人(?:及电话)?|联系电话|联系号码|联络人|联络电话)"
                    + "\\s*[:：]?\\s*[^。；;\\n]*(?:[。；;]|$)"
    );
    private static final String ELIGIBLE = """
            e.c_type = '4' AND e.status = '1' AND e.completed = 0
            AND e.deleted = 0 AND e.event_type IS NOT NULL AND e.event_type <> ''
            """;
    private static final String BASE_COLUMNS = """
            SELECT e.c_no, e.guard_time, e.event_type, e.content,
                   e.source_name, e.source_org_name, e.place, e.route_no, e.route_name,
                   e.lon, e.lat
            FROM w_lw_incident e
            """;

    private final JdbcTemplate jdbcTemplate;
    private final IncidentCityResolver cityResolver = new IncidentCityResolver();

    public AbnormalEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<EmergencyEvent> findById(String eventId) {
        return first(jdbcTemplate.query(BASE_COLUMNS + """
                WHERE e.c_no = ? AND e.c_type = '4' AND e.deleted = 0
                  AND e.event_type IS NOT NULL AND e.event_type <> ''
                LIMIT 1
                """, this::mapEvent, eventId));
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
        return first(jdbcTemplate.query(BASE_COLUMNS + """
                LEFT JOIN w_emergency_dispatch_workflow w ON w.event_id = e.c_no
                WHERE %s AND %s
                ORDER BY e.guard_time IS NULL, e.guard_time ASC,
                         w.stage_entered_at ASC, e.c_no ASC
                LIMIT 1
                """.formatted(ELIGIBLE, stagePredicate(stage)), this::mapEvent));
    }

    @Override
    public long countPending() {
        return countPendingForStage(WorkflowStage.LEVEL_1);
    }

    @Override
    public long countPendingForStage(WorkflowStage stage) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM w_lw_incident e
                LEFT JOIN w_emergency_dispatch_workflow w ON w.event_id = e.c_no
                WHERE %s AND %s
                """.formatted(ELIGIBLE, stagePredicate(stage)), Long.class);
        return count == null ? 0 : count;
    }

    @Override
    public boolean markDispatchApproved(String eventId, Instant updateTime) {
        return markCompleted(eventId);
    }

    @Override
    public boolean markNoDispatch(String eventId, String reason, Instant updateTime) {
        return markCompleted(eventId);
    }

    @Override
    public Optional<UnclassifiedEmergencyEvent> findNextUnclassified(Instant retryBefore) {
        return first(jdbcTemplate.query("""
                SELECT e.c_no, e.guard_time, e.content, e.source_name, e.source_org_name,
                       e.place, e.route_no, e.route_name
                FROM w_lw_incident e
                WHERE e.c_type = '4' AND e.status = '1' AND e.completed = 0
                  AND e.deleted = 0 AND (e.event_type IS NULL OR e.event_type = '')
                  AND NOT EXISTS (
                    SELECT 1 FROM w_emergency_event_classification c
                    WHERE c.event_id = e.c_no AND c.classification_status = 1
                      AND c.create_time > ?
                      AND c.attempt_no = (
                        SELECT MAX(c2.attempt_no) FROM w_emergency_event_classification c2
                        WHERE c2.event_id = e.c_no
                      )
                  )
                ORDER BY e.guard_time IS NULL, e.guard_time, e.c_no
                LIMIT 1
                """, this::mapUnclassified, Timestamp.from(retryBefore)));
    }

    @Override
    public Optional<UnclassifiedEmergencyEvent> findUnclassifiedById(String eventId) {
        return first(jdbcTemplate.query("""
                SELECT e.c_no, e.guard_time, e.content, e.source_name, e.source_org_name,
                       e.place, e.route_no, e.route_name
                FROM w_lw_incident e
                WHERE e.c_no = ? AND e.c_type = '4' AND e.status = '1'
                  AND e.completed = 0 AND e.deleted = 0
                  AND (e.event_type IS NULL OR e.event_type = '')
                LIMIT 1
                """, this::mapUnclassified, eventId));
    }

    @Override
    public long countUnclassified() {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM w_lw_incident
                WHERE c_type = '4' AND status = '1' AND completed = 0 AND deleted = 0
                  AND (event_type IS NULL OR event_type = '')
                """, Long.class);
        return count == null ? 0 : count;
    }

    @Override
    public long countClassificationFailures() {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM w_lw_incident e
                WHERE e.c_type = '4' AND e.status = '1' AND e.completed = 0 AND e.deleted = 0
                  AND (e.event_type IS NULL OR e.event_type = '')
                  AND EXISTS (
                    SELECT 1 FROM w_emergency_event_classification c
                    WHERE c.event_id = e.c_no AND c.classification_status = 1
                      AND c.attempt_no = (
                        SELECT MAX(c2.attempt_no) FROM w_emergency_event_classification c2
                        WHERE c2.event_id = e.c_no
                      )
                  )
                """, Long.class);
        return count == null ? 0 : count;
    }

    @Override
    public boolean assignEventTypeIfAbsent(String eventId, String eventType) {
        return jdbcTemplate.update("""
                UPDATE w_lw_incident SET event_type = ?
                WHERE c_no = ? AND c_type = '4' AND status = '1' AND completed = 0
                  AND deleted = 0 AND (event_type IS NULL OR event_type = '')
                """, eventType, eventId) == 1;
    }

    @Override
    public boolean correctEventType(String eventId, String expectedType, String nextType) {
        return jdbcTemplate.update("""
                UPDATE w_lw_incident SET event_type = ?
                WHERE c_no = ? AND c_type = '4' AND status = '1' AND completed = 0
                  AND deleted = 0 AND event_type = ?
                """, nextType, eventId, expectedType) == 1;
    }

    /** 保留给数据库连通性测试使用。 */
    public int countEvents() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM w_lw_incident", Integer.class);
        return count == null ? 0 : count;
    }

    private boolean markCompleted(String eventId) {
        return jdbcTemplate.update("""
                UPDATE w_lw_incident SET status = '2', completed = 1
                WHERE c_no = ? AND c_type = '4' AND status = '1'
                  AND completed = 0 AND deleted = 0
                """, eventId) == 1;
    }

    private Optional<EmergencyEvent> queryPendingById(String eventId, boolean forUpdate) {
        String lock = forUpdate ? " FOR UPDATE" : "";
        return first(jdbcTemplate.query(BASE_COLUMNS + " WHERE e.c_no = ? AND " + ELIGIBLE
                + " LIMIT 1" + lock, this::mapEvent, eventId));
    }

    private EmergencyEvent mapEvent(ResultSet rs, int row) throws SQLException {
        Double lon = decimal(rs, "lon");
        Double lat = decimal(rs, "lat");
        String description = sanitizeDescription(rs.getString("content"));
        IncidentCityResolver.ResolvedCity city = cityResolver.resolve(
                rs.getString("source_name"), rs.getString("source_org_name"),
                rs.getString("place"), description, lon, lat).orElse(null);
        String cNo = rs.getString("c_no");
        return new EmergencyEvent(
                cNo, cNo, toInstant(rs.getTimestamp("guard_time")),
                rs.getString("event_type"), description,
                city == null ? null : city.code(), city == null ? null : city.name(),
                rs.getString("source_name"), rs.getString("source_org_name"),
                rs.getString("place"), rs.getString("route_no"), rs.getString("route_name"),
                lon, lat
        );
    }

    private UnclassifiedEmergencyEvent mapUnclassified(ResultSet rs, int row) throws SQLException {
        return new UnclassifiedEmergencyEvent(
                rs.getString("c_no"), toInstant(rs.getTimestamp("guard_time")),
                sanitizeDescription(rs.getString("content")), rs.getString("source_name"),
                rs.getString("source_org_name"), rs.getString("place"),
                rs.getString("route_no"), rs.getString("route_name")
        );
    }

    private String stagePredicate(WorkflowStage stage) {
        if (stage == null) throw new IllegalArgumentException("工作流阶段不能为空");
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

    private static <T> Optional<T> first(java.util.List<T> values) {
        return values.stream().findFirst();
    }

    private static Instant toInstant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private static Double decimal(ResultSet rs, String column) throws SQLException {
        BigDecimal value = rs.getBigDecimal(column);
        return value == null ? null : value.doubleValue();
    }

    static String sanitizeDescription(String content) {
        if (content == null || content.isBlank()) {
            return "事件详情待核实";
        }
        String sanitized = CONTACT_CLAUSE.matcher(content.trim()).replaceAll("")
                .replaceAll("[，,；;\\s]+$", "")
                .trim();
        return sanitized.isBlank() ? "事件详情待核实" : sanitized;
    }
}
