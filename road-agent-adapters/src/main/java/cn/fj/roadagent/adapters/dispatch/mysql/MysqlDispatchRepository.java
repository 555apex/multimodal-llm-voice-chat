package cn.fj.roadagent.adapters.dispatch.mysql;

import cn.fj.roadagent.application.port.DispatchRepository;
import cn.fj.roadagent.domain.dispatch.DispatchPlan;
import cn.fj.roadagent.domain.dispatch.DispatchStatus;
import cn.fj.roadagent.domain.dispatch.EmergencyEvent;
import cn.fj.roadagent.domain.dispatch.SuggestedResource;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class MysqlDispatchRepository implements DispatchRepository {
    private static final int GENERATING = 0;
    private static final int WAITING_APPROVAL = 1;
    private static final int REJECTED = 2;
    private static final int APPROVED = 3;
    private static final int FAILED = 4;

    private static final String SELECT_JOINED = """
            SELECT d.plan_id, d.version, d.resource_list, d.rescue_plan,
                   d.order_status, d.rejection_reason, d.error_message,
                   d.create_time AS dispatch_create_time,
                   d.update_time AS dispatch_update_time,
                   e.id AS event_id, e.custom_id, e.occurrence_time,
                   e.event_type, e.description
            FROM w_emergency_dispatch_order d
            JOIN w_abnormal_event e ON e.id = d.event_id
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RowMapper<DispatchPlan> rowMapper = this::mapPlan;

    public MysqlDispatchRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean insert(DispatchPlan plan) {
        try {
            return jdbcTemplate.update(
                    """
                    INSERT INTO w_emergency_dispatch_order (
                        plan_id, event_id, version, resource_list, rescue_plan,
                        order_status, rejection_reason, error_message,
                        create_time, update_time
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    plan.planId(),
                    parseEventId(plan.event().eventId()),
                    plan.version(),
                    writeResources(plan.suggestedResources()),
                    emptyToNull(plan.rescuePlan()),
                    toDatabaseStatus(plan.status()),
                    plan.rejectionReason(),
                    plan.errorMessage(),
                    Timestamp.from(plan.createdAt()),
                    Timestamp.from(plan.updatedAt())
            ) == 1;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    @Override
    public Optional<DispatchPlan> findLatestByPlanId(String planId) {
        return jdbcTemplate.query(
                SELECT_JOINED + """
                        WHERE d.plan_id = ?
                        ORDER BY d.version DESC
                        LIMIT 1
                        """,
                rowMapper,
                planId
        ).stream().findFirst();
    }

    @Override
    public Optional<DispatchPlan> findLatestByEventId(String eventId) {
        return jdbcTemplate.query(
                SELECT_JOINED + """
                        WHERE d.event_id = ?
                        ORDER BY d.version DESC
                        LIMIT 1
                        """,
                rowMapper,
                parseEventId(eventId)
        ).stream().findFirst();
    }

    @Override
    public Optional<DispatchPlan> findVersion(String planId, long version) {
        return jdbcTemplate.query(
                SELECT_JOINED + """
                        WHERE d.plan_id = ? AND d.version = ?
                        LIMIT 1
                        """,
                rowMapper,
                planId,
                version
        ).stream().findFirst();
    }

    @Override
    public boolean restartGeneration(DispatchPlan plan, Instant staleBefore) {
        return jdbcTemplate.update(
                """
                UPDATE w_emergency_dispatch_order
                SET resource_list = JSON_ARRAY(), rescue_plan = NULL,
                    order_status = ?, error_message = NULL, update_time = ?
                WHERE plan_id = ? AND version = ?
                  AND (
                    order_status = ?
                    OR (order_status = ? AND update_time <= ?)
                  )
                """,
                GENERATING,
                Timestamp.from(plan.updatedAt()),
                plan.planId(),
                plan.version(),
                FAILED,
                GENERATING,
                Timestamp.from(staleBefore)
        ) == 1;
    }

    @Override
    public boolean updateGenerated(DispatchPlan plan) {
        return jdbcTemplate.update(
                """
                UPDATE w_emergency_dispatch_order
                SET resource_list = ?, rescue_plan = ?, order_status = ?,
                    error_message = NULL, update_time = ?
                WHERE plan_id = ? AND version = ? AND order_status = ?
                """,
                writeResources(plan.suggestedResources()),
                plan.rescuePlan(),
                WAITING_APPROVAL,
                Timestamp.from(plan.updatedAt()),
                plan.planId(),
                plan.version(),
                GENERATING
        ) == 1;
    }

    @Override
    public boolean updateRejected(DispatchPlan plan) {
        return updateTerminal(
                plan, REJECTED, "rejection_reason", plan.rejectionReason(), WAITING_APPROVAL
        );
    }

    @Override
    public boolean updateApproved(DispatchPlan plan) {
        return jdbcTemplate.update(
                """
                UPDATE w_emergency_dispatch_order
                SET order_status = ?, approved_time = ?, update_time = ?
                WHERE plan_id = ? AND version = ? AND order_status = ?
                """,
                APPROVED,
                Timestamp.from(plan.updatedAt()),
                Timestamp.from(plan.updatedAt()),
                plan.planId(),
                plan.version(),
                WAITING_APPROVAL
        ) == 1;
    }

    @Override
    public boolean updateFailed(DispatchPlan plan) {
        return updateTerminal(plan, FAILED, "error_message", plan.errorMessage(), GENERATING);
    }

    private boolean updateTerminal(
            DispatchPlan plan,
            int nextStatus,
            String valueColumn,
            String value,
            int expectedStatus
    ) {
        String sql = """
                UPDATE w_emergency_dispatch_order
                SET order_status = ?, %s = ?, update_time = ?
                WHERE plan_id = ? AND version = ? AND order_status = ?
                """.formatted(valueColumn);
        return jdbcTemplate.update(
                sql,
                nextStatus,
                value,
                Timestamp.from(plan.updatedAt()),
                plan.planId(),
                plan.version(),
                expectedStatus
        ) == 1;
    }

    private DispatchPlan mapPlan(java.sql.ResultSet resultSet, int rowNumber)
            throws java.sql.SQLException {
        EmergencyEvent event = new EmergencyEvent(
                Long.toString(resultSet.getLong("event_id")),
                resultSet.getString("custom_id"),
                toInstant(resultSet.getTimestamp("occurrence_time")),
                resultSet.getString("event_type"),
                resultSet.getString("description")
        );
        return new DispatchPlan(
                resultSet.getString("plan_id"),
                event,
                readResources(resultSet.getString("resource_list")),
                resultSet.getString("rescue_plan"),
                fromDatabaseStatus(resultSet.getInt("order_status")),
                resultSet.getLong("version"),
                resultSet.getTimestamp("dispatch_create_time").toInstant(),
                resultSet.getTimestamp("dispatch_update_time").toInstant(),
                resultSet.getString("rejection_reason"),
                resultSet.getString("error_message")
        );
    }

    private String writeResources(List<SuggestedResource> resources) {
        try {
            return objectMapper.writeValueAsString(resources == null ? List.of() : resources);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("无法序列化建议资源清单", exception);
        }
    }

    private List<SuggestedResource> readResources(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("数据库中的建议资源清单不是有效JSON", exception);
        }
    }

    private int toDatabaseStatus(DispatchStatus status) {
        return switch (status) {
            case GENERATING -> GENERATING;
            case WAITING_APPROVAL -> WAITING_APPROVAL;
            case REJECTED -> REJECTED;
            case APPROVED -> APPROVED;
            case FAILED -> FAILED;
            default -> throw new IllegalArgumentException("状态不能持久化到应急调度工单：" + status);
        };
    }

    private DispatchStatus fromDatabaseStatus(int status) {
        return switch (status) {
            case GENERATING -> DispatchStatus.GENERATING;
            case WAITING_APPROVAL -> DispatchStatus.WAITING_APPROVAL;
            case REJECTED -> DispatchStatus.REJECTED;
            case APPROVED -> DispatchStatus.APPROVED;
            case FAILED -> DispatchStatus.FAILED;
            default -> throw new IllegalStateException("数据库中的工单状态无效：" + status);
        };
    }

    private long parseEventId(String eventId) {
        try {
            return Long.parseLong(eventId);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("事件ID格式不正确");
        }
    }

    private Instant toInstant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
