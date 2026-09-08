package cn.fj.roadagent.adapters.dispatch.mysql;

import cn.fj.roadagent.application.port.EmergencyWorkflowRepository;
import cn.fj.roadagent.domain.dispatch.CommandDecision;
import cn.fj.roadagent.domain.dispatch.CommandDecisionStatus;
import cn.fj.roadagent.domain.dispatch.EmergencyWorkflow;
import cn.fj.roadagent.domain.dispatch.NoticeSnapshot;
import cn.fj.roadagent.domain.dispatch.ProfessionalReview;
import cn.fj.roadagent.domain.dispatch.ReviewStatus;
import cn.fj.roadagent.domain.dispatch.WorkflowAction;
import cn.fj.roadagent.domain.dispatch.WorkflowActionType;
import cn.fj.roadagent.domain.dispatch.WorkflowStage;
import cn.fj.roadagent.domain.dispatch.WorkflowStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class MysqlEmergencyWorkflowRepository implements EmergencyWorkflowRepository {
    private static final String WORKFLOW_COLUMNS = """
            SELECT workflow_id, event_id, current_stage, workflow_status,
                   plan_id, plan_version, terminal_reason, lock_version, stage_entered_at,
                   create_time, update_time
            FROM w_emergency_dispatch_workflow
            """;
    private static final String REVIEW_COLUMNS = """
            SELECT review_id, workflow_id, plan_id, plan_version, review_status,
                   event_severity, resource_feasibility, impact_assessment,
                   coordination_requirements, review_opinion, create_time, update_time
            FROM w_emergency_professional_review
            """;
    private static final String DECISION_COLUMNS = """
            SELECT decision_id, workflow_id, review_id, plan_id, plan_version,
                   decision_status, decision_opinion, notice_snapshot,
                   create_time, update_time
            FROM w_emergency_command_decision
            """;
    private static final String ACTION_COLUMNS = """
            SELECT action_id, workflow_id, action_type, from_stage, to_stage,
                   from_status, to_status, plan_id, plan_version, action_comment,
                   detail_json, idempotency_key, create_time
            FROM w_emergency_dispatch_action_log
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RowMapper<EmergencyWorkflow> workflowMapper = this::mapWorkflow;
    private final RowMapper<ProfessionalReview> reviewMapper = this::mapReview;
    private final RowMapper<CommandDecision> decisionMapper = this::mapDecision;
    private final RowMapper<WorkflowAction> actionMapper = this::mapAction;

    public MysqlEmergencyWorkflowRepository(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean insertWorkflow(EmergencyWorkflow workflow) {
        try {
            return jdbcTemplate.update(
                    """
                    INSERT INTO w_emergency_dispatch_workflow (
                        workflow_id, event_id, current_stage, workflow_status,
                        plan_id, plan_version, terminal_reason, lock_version, stage_entered_at,
                        create_time, update_time
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    workflow.workflowId(), workflow.eventId(),
                    stageValue(workflow.currentStage()), statusValue(workflow.status()),
                    workflow.planId(), workflow.planVersion(), workflow.terminalReason(), workflow.lockVersion(),
                    Timestamp.from(workflow.stageEnteredAt()),
                    Timestamp.from(workflow.createdAt()), Timestamp.from(workflow.updatedAt())
            ) == 1;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    @Override
    public Optional<EmergencyWorkflow> findWorkflow(String workflowId) {
        return first(jdbcTemplate.query(
                WORKFLOW_COLUMNS + " WHERE workflow_id = ? LIMIT 1",
                workflowMapper, workflowId
        ));
    }

    @Override
    public Optional<EmergencyWorkflow> findWorkflowByEventId(String eventId) {
        return first(jdbcTemplate.query(
                WORKFLOW_COLUMNS + " WHERE event_id = ? LIMIT 1",
                workflowMapper, eventId
        ));
    }

    @Override
    public Optional<EmergencyWorkflow> findWorkflowByPlanId(String planId) {
        return first(jdbcTemplate.query(
                WORKFLOW_COLUMNS + " WHERE plan_id = ? LIMIT 1",
                workflowMapper, planId
        ));
    }

    @Override
    public Optional<EmergencyWorkflow> lockWorkflow(String workflowId) {
        return first(jdbcTemplate.query(
                WORKFLOW_COLUMNS + " WHERE workflow_id = ? LIMIT 1 FOR UPDATE",
                workflowMapper, workflowId
        ));
    }

    @Override
    public boolean updateWorkflow(EmergencyWorkflow workflow, long expectedLockVersion) {
        return jdbcTemplate.update(
                """
                UPDATE w_emergency_dispatch_workflow
                SET current_stage = ?, workflow_status = ?, plan_id = ?,
                    plan_version = ?, terminal_reason = ?, lock_version = ?, stage_entered_at = ?,
                    update_time = ?
                WHERE workflow_id = ? AND lock_version = ?
                """,
                stageValue(workflow.currentStage()), statusValue(workflow.status()),
                workflow.planId(), workflow.planVersion(), workflow.terminalReason(), workflow.lockVersion(),
                Timestamp.from(workflow.stageEnteredAt()), Timestamp.from(workflow.updatedAt()),
                workflow.workflowId(), expectedLockVersion
        ) == 1;
    }

    @Override
    public boolean insertReview(ProfessionalReview review) {
        try {
            return jdbcTemplate.update(
                    """
                    INSERT INTO w_emergency_professional_review (
                        review_id, workflow_id, plan_id, plan_version, review_status,
                        event_severity, resource_feasibility, impact_assessment,
                        coordination_requirements, review_opinion, create_time, update_time
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    review.reviewId(), review.workflowId(), review.planId(), review.planVersion(),
                    reviewStatusValue(review.status()), enumName(review.eventSeverity()),
                    enumName(review.resourceFeasibility()), review.impactAssessment(),
                    review.coordinationRequirements(), review.reviewOpinion(),
                    Timestamp.from(review.createdAt()), Timestamp.from(review.updatedAt())
            ) == 1;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    @Override
    public Optional<ProfessionalReview> findLatestReview(String workflowId) {
        return first(jdbcTemplate.query(
                REVIEW_COLUMNS + " WHERE workflow_id = ? ORDER BY id DESC LIMIT 1",
                reviewMapper, workflowId
        ));
    }

    @Override
    public Optional<ProfessionalReview> findPendingReview(String workflowId) {
        return first(jdbcTemplate.query(
                REVIEW_COLUMNS + " WHERE workflow_id = ? AND review_status = 0 ORDER BY id DESC LIMIT 1",
                reviewMapper, workflowId
        ));
    }

    @Override
    public boolean updateReview(ProfessionalReview review) {
        return jdbcTemplate.update(
                """
                UPDATE w_emergency_professional_review
                SET review_status = ?, event_severity = ?, resource_feasibility = ?,
                    impact_assessment = ?, coordination_requirements = ?,
                    review_opinion = ?, update_time = ?
                WHERE review_id = ? AND review_status = 0
                """,
                reviewStatusValue(review.status()), enumName(review.eventSeverity()),
                enumName(review.resourceFeasibility()), review.impactAssessment(),
                review.coordinationRequirements(), review.reviewOpinion(),
                Timestamp.from(review.updatedAt()), review.reviewId()
        ) == 1;
    }

    @Override
    public boolean insertDecision(CommandDecision decision) {
        try {
            return jdbcTemplate.update(
                    """
                    INSERT INTO w_emergency_command_decision (
                        decision_id, workflow_id, review_id, plan_id, plan_version,
                        decision_status, decision_opinion, notice_snapshot,
                        create_time, update_time
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    decision.decisionId(), decision.workflowId(), decision.reviewId(),
                    decision.planId(), decision.planVersion(),
                    decisionStatusValue(decision.status()), decision.decisionOpinion(),
                    writeJson(decision.noticeSnapshot()), Timestamp.from(decision.createdAt()),
                    Timestamp.from(decision.updatedAt())
            ) == 1;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    @Override
    public Optional<CommandDecision> findLatestDecision(String workflowId) {
        return first(jdbcTemplate.query(
                DECISION_COLUMNS + " WHERE workflow_id = ? ORDER BY id DESC LIMIT 1",
                decisionMapper, workflowId
        ));
    }

    @Override
    public Optional<CommandDecision> findPendingDecision(String workflowId) {
        return first(jdbcTemplate.query(
                DECISION_COLUMNS + " WHERE workflow_id = ? AND decision_status = 0 ORDER BY id DESC LIMIT 1",
                decisionMapper, workflowId
        ));
    }

    @Override
    public boolean updateDecision(CommandDecision decision) {
        return jdbcTemplate.update(
                """
                UPDATE w_emergency_command_decision
                SET decision_status = ?, decision_opinion = ?, notice_snapshot = ?,
                    published_time = ?, update_time = ?
                WHERE decision_id = ? AND decision_status = 0
                """,
                decisionStatusValue(decision.status()), decision.decisionOpinion(),
                writeJson(decision.noticeSnapshot()),
                decision.noticeSnapshot() == null
                        ? null : Timestamp.from(decision.noticeSnapshot().publishedAt()),
                Timestamp.from(decision.updatedAt()), decision.decisionId()
        ) == 1;
    }

    @Override
    public boolean insertAction(WorkflowAction action) {
        try {
            return jdbcTemplate.update(
                    """
                    INSERT INTO w_emergency_dispatch_action_log (
                        action_id, workflow_id, action_type, from_stage, to_stage,
                        from_status, to_status, plan_id, plan_version,
                        action_comment, detail_json, idempotency_key, actor_stage, create_time
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    action.actionId(), action.workflowId(), action.actionType().name(),
                    stageValue(action.fromStage()), stageValue(action.toStage()),
                    nullableStatusValue(action.fromStatus()), statusValue(action.toStatus()),
                    action.planId(), action.planVersion(), action.comment(),
                    action.detailJson(), action.idempotencyKey(), actorStageValue(action),
                    Timestamp.from(action.createdAt())
            ) == 1;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    @Override
    public Optional<WorkflowAction> findActionByIdempotencyKey(
            String workflowId,
            String idempotencyKey
    ) {
        return first(jdbcTemplate.query(
                ACTION_COLUMNS + " WHERE workflow_id = ? AND idempotency_key = ? LIMIT 1",
                actionMapper, workflowId, idempotencyKey
        ));
    }

    @Override
    public List<WorkflowAction> findActions(String workflowId) {
        return jdbcTemplate.query(
                ACTION_COLUMNS + " WHERE workflow_id = ? ORDER BY create_time ASC, id ASC",
                actionMapper, workflowId
        );
    }

    @Override
    public List<EmergencyWorkflow> findHistory(int offset, int limit) {
        return jdbcTemplate.query(
                WORKFLOW_COLUMNS + """
                        WHERE workflow_status IN (7, 8)
                        ORDER BY update_time DESC, id DESC
                        LIMIT ? OFFSET ?
                        """,
                workflowMapper, limit, offset
        );
    }

    @Override
    public long countHistory() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM w_emergency_dispatch_workflow WHERE workflow_status IN (7, 8)",
                Long.class
        );
        return count == null ? 0 : count;
    }

    private EmergencyWorkflow mapWorkflow(ResultSet rs, int row) throws SQLException {
        return new EmergencyWorkflow(
                rs.getString("workflow_id"), rs.getString("event_id"),
                stage(rs, "current_stage"), workflowStatus(rs.getInt("workflow_status")),
                rs.getString("plan_id"), rs.getLong("plan_version"), rs.getString("terminal_reason"),
                rs.getLong("lock_version"), instant(rs, "stage_entered_at"),
                instant(rs, "create_time"), instant(rs, "update_time")
        );
    }

    private ProfessionalReview mapReview(ResultSet rs, int row) throws SQLException {
        return new ProfessionalReview(
                rs.getString("review_id"), rs.getString("workflow_id"),
                rs.getString("plan_id"), rs.getLong("plan_version"),
                reviewStatus(rs.getInt("review_status")),
                enumValue(cn.fj.roadagent.domain.dispatch.EventSeverity.class, rs.getString("event_severity")),
                enumValue(cn.fj.roadagent.domain.dispatch.ResourceFeasibility.class, rs.getString("resource_feasibility")),
                rs.getString("impact_assessment"), rs.getString("coordination_requirements"),
                rs.getString("review_opinion"), instant(rs, "create_time"),
                instant(rs, "update_time")
        );
    }

    private CommandDecision mapDecision(ResultSet rs, int row) throws SQLException {
        return new CommandDecision(
                rs.getString("decision_id"), rs.getString("workflow_id"),
                rs.getString("review_id"), rs.getString("plan_id"),
                rs.getLong("plan_version"), decisionStatus(rs.getInt("decision_status")),
                rs.getString("decision_opinion"),
                readJson(rs.getString("notice_snapshot"), NoticeSnapshot.class),
                instant(rs, "create_time"), instant(rs, "update_time")
        );
    }

    private WorkflowAction mapAction(ResultSet rs, int row) throws SQLException {
        return new WorkflowAction(
                rs.getString("action_id"), rs.getString("workflow_id"),
                WorkflowActionType.valueOf(rs.getString("action_type")),
                stage(rs, "from_stage"), stage(rs, "to_stage"),
                nullableWorkflowStatus(rs, "from_status"),
                workflowStatus(rs.getInt("to_status")), rs.getString("plan_id"),
                rs.getLong("plan_version"), rs.getString("action_comment"),
                rs.getString("detail_json"), rs.getString("idempotency_key"),
                instant(rs, "create_time")
        );
    }

    private WorkflowStage stage(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : switch (value) {
            case 1 -> WorkflowStage.LEVEL_1;
            case 2 -> WorkflowStage.LEVEL_2;
            case 3 -> WorkflowStage.LEVEL_3;
            default -> throw new IllegalStateException("数据库中的工作流阶段无效：" + value);
        };
    }

    private Integer stageValue(WorkflowStage stage) {
        if (stage == null) return null;
        return switch (stage) {
            case LEVEL_1 -> 1;
            case LEVEL_2 -> 2;
            case LEVEL_3 -> 3;
        };
    }

    private int actorStageValue(WorkflowAction action) {
        return switch (action.actionType()) {
            case GENERATION_STARTED, GENERATION_COMPLETED, GENERATION_FAILED,
                    GENERATION_RETRIED, EVENT_TYPE_CORRECTED, LEVEL_1_SUBMITTED, LEVEL_1_RETURNED,
                    NO_DISPATCH -> 1;
            case LEVEL_2_PASSED, LEVEL_2_RETURNED -> 2;
            case LEVEL_3_RETURNED, LEVEL_3_PUBLISHED, RESOURCES_RELEASED -> 3;
        };
    }

    private int statusValue(WorkflowStatus status) {
        return switch (status) {
            case WAITING_GENERATION -> 0;
            case GENERATING -> 1;
            case WAITING_LEVEL_1_SUBMISSION -> 2;
            case WAITING_LEVEL_2_REVIEW -> 3;
            case WAITING_LEVEL_3_DECISION -> 4;
            case REVISING -> 5;
            case GENERATION_FAILED -> 6;
            case PUBLISHED -> 7;
            case NO_DISPATCH -> 8;
        };
    }

    private Integer nullableStatusValue(WorkflowStatus status) {
        return status == null ? null : statusValue(status);
    }

    private WorkflowStatus workflowStatus(int value) {
        return switch (value) {
            case 0 -> WorkflowStatus.WAITING_GENERATION;
            case 1 -> WorkflowStatus.GENERATING;
            case 2 -> WorkflowStatus.WAITING_LEVEL_1_SUBMISSION;
            case 3 -> WorkflowStatus.WAITING_LEVEL_2_REVIEW;
            case 4 -> WorkflowStatus.WAITING_LEVEL_3_DECISION;
            case 5 -> WorkflowStatus.REVISING;
            case 6 -> WorkflowStatus.GENERATION_FAILED;
            case 7 -> WorkflowStatus.PUBLISHED;
            case 8 -> WorkflowStatus.NO_DISPATCH;
            default -> throw new IllegalStateException("数据库中的工作流状态无效：" + value);
        };
    }

    private WorkflowStatus nullableWorkflowStatus(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : workflowStatus(value);
    }

    private int reviewStatusValue(ReviewStatus status) {
        return switch (status) {
            case PENDING -> 0;
            case PASSED -> 1;
            case RETURNED -> 2;
        };
    }

    private ReviewStatus reviewStatus(int value) {
        return switch (value) {
            case 0 -> ReviewStatus.PENDING;
            case 1 -> ReviewStatus.PASSED;
            case 2 -> ReviewStatus.RETURNED;
            default -> throw new IllegalStateException("数据库中的专业复核状态无效：" + value);
        };
    }

    private int decisionStatusValue(CommandDecisionStatus status) {
        return switch (status) {
            case PENDING -> 0;
            case RETURNED -> 1;
            case PUBLISHED -> 2;
        };
    }

    private CommandDecisionStatus decisionStatus(int value) {
        return switch (value) {
            case 0 -> CommandDecisionStatus.PENDING;
            case 1 -> CommandDecisionStatus.RETURNED;
            case 2 -> CommandDecisionStatus.PUBLISHED;
            default -> throw new IllegalStateException("数据库中的省级决策状态无效：" + value);
        };
    }

    private String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private <T extends Enum<T>> T enumValue(Class<T> type, String value) {
        return value == null || value.isBlank() ? null : Enum.valueOf(type, value);
    }

    private String writeJson(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("无法序列化工作流快照", exception);
        }
    }

    private <T> T readJson(String value, Class<T> type) {
        if (value == null || value.isBlank()) return null;
        try {
            return objectMapper.readerFor(type)
                    .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("数据库中的工作流快照无效", exception);
        }
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private <T> Optional<T> first(List<T> values) {
        return values.stream().findFirst();
    }
}
