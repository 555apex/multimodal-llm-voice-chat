package cn.fj.roadagent.adapters.dispatch.mysql;

import cn.fj.roadagent.domain.dispatch.CommandDecision;
import cn.fj.roadagent.domain.dispatch.EmergencyEvent;
import cn.fj.roadagent.domain.dispatch.EmergencyWorkflow;
import cn.fj.roadagent.domain.dispatch.EventSeverity;
import cn.fj.roadagent.domain.dispatch.NoticeSnapshot;
import cn.fj.roadagent.domain.dispatch.ProfessionalReview;
import cn.fj.roadagent.domain.dispatch.ResourceFeasibility;
import cn.fj.roadagent.domain.dispatch.SuggestedResource;
import cn.fj.roadagent.domain.dispatch.WorkflowAction;
import cn.fj.roadagent.domain.dispatch.WorkflowActionType;
import cn.fj.roadagent.domain.dispatch.WorkflowStage;
import cn.fj.roadagent.domain.dispatch.WorkflowStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MysqlEmergencyWorkflowRepositoryTest {
    private static final Instant NOW = Instant.parse("2026-08-19T01:02:03Z");

    private JdbcTemplate jdbc;
    private MysqlEmergencyWorkflowRepository repository;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:workflow-repository;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""
        );
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP ALL OBJECTS");
        jdbc.execute("""
                CREATE TABLE w_emergency_dispatch_workflow (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    workflow_id VARCHAR(40) NOT NULL UNIQUE,
                    event_id VARCHAR(64) NOT NULL UNIQUE,
                    current_stage TINYINT,
                    workflow_status TINYINT NOT NULL,
                    plan_id VARCHAR(40) UNIQUE,
                    plan_version INT NOT NULL,
                    terminal_reason VARCHAR(500),
                    lock_version BIGINT NOT NULL,
                    stage_entered_at TIMESTAMP NOT NULL,
                    create_time TIMESTAMP NOT NULL,
                    update_time TIMESTAMP NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE w_emergency_professional_review (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    review_id VARCHAR(40) NOT NULL UNIQUE,
                    workflow_id VARCHAR(40) NOT NULL,
                    plan_id VARCHAR(40) NOT NULL,
                    plan_version INT NOT NULL,
                    review_status TINYINT NOT NULL,
                    event_severity VARCHAR(32),
                    resource_feasibility VARCHAR(32),
                    impact_assessment CLOB,
                    coordination_requirements CLOB,
                    review_opinion VARCHAR(500),
                    create_time TIMESTAMP NOT NULL,
                    update_time TIMESTAMP NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE w_emergency_command_decision (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    decision_id VARCHAR(40) NOT NULL UNIQUE,
                    workflow_id VARCHAR(40) NOT NULL,
                    review_id VARCHAR(40) NOT NULL,
                    plan_id VARCHAR(40) NOT NULL,
                    plan_version INT NOT NULL,
                    decision_status TINYINT NOT NULL,
                    decision_opinion VARCHAR(500),
                    notice_snapshot CLOB,
                    published_time TIMESTAMP,
                    create_time TIMESTAMP NOT NULL,
                    update_time TIMESTAMP NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE w_emergency_dispatch_action_log (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    action_id VARCHAR(40) NOT NULL UNIQUE,
                    workflow_id VARCHAR(40) NOT NULL,
                    action_type VARCHAR(40) NOT NULL,
                    from_stage TINYINT,
                    to_stage TINYINT,
                    from_status TINYINT,
                    to_status TINYINT NOT NULL,
                    plan_id VARCHAR(40),
                    plan_version INT NOT NULL,
                    action_comment VARCHAR(500),
                    detail_json CLOB,
                    idempotency_key VARCHAR(100) NOT NULL,
                    actor_stage TINYINT,
                    create_time TIMESTAMP NOT NULL,
                    UNIQUE (workflow_id, idempotency_key)
                )
                """);
        repository = new MysqlEmergencyWorkflowRepository(
                jdbc, new ObjectMapper().findAndRegisterModules()
        );
    }

    @Test
    void shouldPersistOptimisticTransitionsReviewsNoticeAndTimeline() {
        EmergencyWorkflow workflow = EmergencyWorkflow.generating(
                "WF-1", "9223372036854775000", "DP-1", 1L, NOW
        );
        assertTrue(repository.insertWorkflow(workflow));
        assertFalse(repository.insertWorkflow(workflow));
        assertEquals("9223372036854775000",
                repository.findWorkflowByEventId("9223372036854775000").orElseThrow().eventId());

        EmergencyWorkflow level1 = workflow.generated(NOW.plusSeconds(1));
        assertTrue(repository.updateWorkflow(level1, 0L));
        assertFalse(repository.updateWorkflow(level1, 0L));

        ProfessionalReview pendingReview = ProfessionalReview.pending(
                "PR-1", workflow.workflowId(), workflow.planId(), 1L, NOW
        );
        assertTrue(repository.insertReview(pendingReview));
        ProfessionalReview passedReview = pendingReview.pass(
                EventSeverity.LARGER, ResourceFeasibility.FEASIBLE,
                "影响主线交通", "协调交警", "方案可行", NOW.plusSeconds(2)
        );
        assertTrue(repository.updateReview(passedReview));
        assertEquals(ResourceFeasibility.FEASIBLE,
                repository.findLatestReview(workflow.workflowId()).orElseThrow().resourceFeasibility());

        CommandDecision pendingDecision = CommandDecision.pending(
                "CD-1", workflow.workflowId(), pendingReview.reviewId(), workflow.planId(), 1L, NOW
        );
        assertTrue(repository.insertDecision(pendingDecision));
        NoticeSnapshot snapshot = notice();
        assertTrue(repository.updateDecision(
                pendingDecision.publish("同意发布", snapshot, NOW.plusSeconds(3))
        ));
        NoticeSnapshot restored = repository.findLatestDecision(workflow.workflowId())
                .orElseThrow().noticeSnapshot();
        assertEquals(snapshot, restored);

        WorkflowAction action = new WorkflowAction(
                "AC-1", workflow.workflowId(), WorkflowActionType.LEVEL_1_SUBMITTED,
                WorkflowStage.LEVEL_1, WorkflowStage.LEVEL_2,
                WorkflowStatus.WAITING_LEVEL_1_SUBMISSION,
                WorkflowStatus.WAITING_LEVEL_2_REVIEW,
                workflow.planId(), 1L, "上报", null, "idem-1", NOW
        );
        assertTrue(repository.insertAction(action));
        assertFalse(repository.insertAction(new WorkflowAction(
                "AC-2", workflow.workflowId(), WorkflowActionType.LEVEL_1_SUBMITTED,
                WorkflowStage.LEVEL_1, WorkflowStage.LEVEL_2,
                WorkflowStatus.WAITING_LEVEL_1_SUBMISSION,
                WorkflowStatus.WAITING_LEVEL_2_REVIEW,
                workflow.planId(), 1L, "重复", null, "idem-1", NOW
        )));
        assertEquals(1, repository.findActions(workflow.workflowId()).size());
        assertEquals(1, jdbc.queryForObject(
                "SELECT actor_stage FROM w_emergency_dispatch_action_log WHERE action_id = 'AC-1'",
                Integer.class
        ));

        EmergencyWorkflow published = level1
                .submitLevel1(NOW.plusSeconds(4))
                .approveLevel2(NOW.plusSeconds(5))
                .publish(NOW.plusSeconds(6));
        assertTrue(repository.updateWorkflow(published, level1.lockVersion()));
        assertEquals(1, repository.countHistory());
        assertEquals(workflow.workflowId(), repository.findHistory(0, 10).get(0).workflowId());
    }

    @Test
    void shouldReadNoticeCreatedBeforeInventoryResourceFieldsExisted() {
        jdbc.update("""
                INSERT INTO w_emergency_command_decision (
                    decision_id, workflow_id, review_id, plan_id, plan_version,
                    decision_status, decision_opinion, notice_snapshot,
                    create_time, update_time
                ) VALUES ('CD-LEGACY', 'WF-LEGACY', 'PR-LEGACY', 'DP-LEGACY', 1,
                          2, '同意发布', ?, ?, ?)
                """, """
                {
                  "noticeNumber":"NT-LEGACY",
                  "title":"旧版应急通告",
                  "event":{"eventId":"1","customId":"EVT-1","occurrenceTime":"2026-08-19T01:01:03Z","eventType":"DT01","description":"边坡崩塌"},
                  "planId":"DP-LEGACY","planVersion":1,
                  "suggestedResources":[{"resourceType":"抢险队伍","resourceName":"旧资源建议","quantity":1,"unit":"组","purpose":"抢通"}],
                  "rescuePlan":"先警戒，再抢通。","eventSeverity":"LARGER",
                  "impactAssessment":"影响主线交通","professionalOpinion":"方案可行",
                  "commandOpinion":"同意发布","publishedAt":"2026-08-19T01:02:03Z"
                }
                """, java.sql.Timestamp.from(NOW), java.sql.Timestamp.from(NOW));

        NoticeSnapshot restored = repository.findLatestDecision("WF-LEGACY")
                .orElseThrow().noticeSnapshot();
        assertEquals("NT-LEGACY", restored.noticeNumber());
        assertTrue(restored.resourceRequirements().isEmpty());
        assertTrue(restored.allocatedResources().isEmpty());
        assertTrue(restored.resourceShortages().isEmpty());
    }

    private NoticeSnapshot notice() {
        EmergencyEvent event = new EmergencyEvent(
                "9223372036854775000", "EVT-1", NOW.minusSeconds(60),
                "DT01", "边坡崩塌"
        );
        return new NoticeSnapshot(
                "NT-1", "应急处置通告", event, "DP-1", 1L,
                List.of(new SuggestedResource(
                        "抢险队伍", "道路抢险人员", 1, "组", "现场抢通"
                )),
                "先警戒，再抢通。", EventSeverity.LARGER,
                "影响主线交通", "协调交警", "专业会商通过", "同意发布", NOW
        );
    }
}
