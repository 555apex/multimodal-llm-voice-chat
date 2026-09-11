package cn.fj.roadagent.interfaces.rest.workflow;

import cn.fj.roadagent.application.dispatch.EmergencyWorkflowView;
import cn.fj.roadagent.application.dispatch.QueryEmergencyWorkflowUseCase;
import cn.fj.roadagent.application.dispatch.WorkflowCounts;
import cn.fj.roadagent.application.dispatch.WorkflowHistoryPage;
import cn.fj.roadagent.application.dispatch.WorkflowInbox;
import cn.fj.roadagent.application.exception.BusinessRuleException;
import cn.fj.roadagent.domain.dispatch.EmergencyEvent;
import cn.fj.roadagent.domain.dispatch.EmergencyWorkflow;
import cn.fj.roadagent.interfaces.rest.common.GlobalExceptionHandler;
import cn.fj.roadagent.interfaces.rest.common.TraceIdFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EmergencyWorkflowControllerTest {
    private EmergencyWorkflowView view;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Instant now = Instant.parse("2026-08-19T00:00:00Z");
        EmergencyEvent event = new EmergencyEvent(
                "202607280000000001", "EVT-1", now.minusSeconds(60),
                "DT01", "边坡崩塌"
        );
        EmergencyWorkflow workflow = EmergencyWorkflow.generating(
                "WF-1", event.eventId(), "DP-1", 1L, now
        ).generated(now);
        view = new EmergencyWorkflowView(workflow, event, null, null, null, List.of());
        QueryEmergencyWorkflowUseCase query = new QueryEmergencyWorkflowUseCase() {
            @Override
            public EmergencyWorkflowView getWorkflow(String workflowId) {
                return view;
            }

            @Override
            public WorkflowHistoryPage history(int page, int size) {
                return new WorkflowHistoryPage(List.of(view), page, size, 1);
            }

            @Override
            public cn.fj.roadagent.application.dispatch.NoticePage notices(String completionStatus, int page, int size) {
                return new cn.fj.roadagent.application.dispatch.NoticePage(List.of(
                        new cn.fj.roadagent.application.dispatch.NoticePage.Item(
                                "WF-1", event.eventId(), event.eventType(), "福州", null,
                                "NT-1", now, "PUBLISHED", completionStatus)), page, size, 1, 1, 0);
            }
        };
        EmergencyWorkflowController controller = new EmergencyWorkflowController(
                stage -> new WorkflowInbox(view, new WorkflowCounts(3, 2, 1)),
                command -> {
                    if ("conflict".equals(command.idempotencyKey())) {
                        throw new BusinessRuleException(
                                "WORKFLOW_VERSION_CONFLICT", "工作流版本已变化"
                        );
                    }
                    return view;
                },
                command -> view,
                command -> view,
                query,
                command -> view
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilter(new TraceIdFilter())
                .build();
    }

    @Test
    void shouldReturnNoticeSummariesWithoutLoadingFullPlan() throws Exception {
        mockMvc.perform(get("/api/v1/emergency-workflows/notices").param("completionStatus", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pendingCount").value(1))
                .andExpect(jsonPath("$.data.items[0].eventId").value("202607280000000001"))
                .andExpect(jsonPath("$.data.items[0].completionStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.items[0].timeline").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].currentPlan").doesNotExist());
    }

    @Test
    void shouldReturnStageInboxCountsAndBigEventIdAsString() throws Exception {
        mockMvc.perform(get("/api/v1/emergency-workflows/inbox").param("stage", "LEVEL_2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.counts.level1").value(3))
                .andExpect(jsonPath("$.data.counts.level2").value(2))
                .andExpect(jsonPath("$.data.counts.level3").value(1))
                .andExpect(jsonPath("$.data.item.event.eventId").value("202607280000000001"));
    }

    @Test
    void shouldValidateStructuredProfessionalReview() throws Exception {
        mockMvc.perform(post("/api/v1/emergency-workflows/WF-1/professional-reviews")
                        .contentType("application/json")
                        .content("""
                                {
                                  "decision":"APPROVE",
                                  "resourceFeasibility":"NEEDS_ADJUSTMENT",
                                  "expectedWorkflowVersion":1,
                                  "idempotencyKey":"review-invalid"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void shouldMapOptimisticLockConflictTo409() throws Exception {
        mockMvc.perform(post("/api/v1/emergency-workflows/WF-1/level-1-decisions")
                        .contentType("application/json")
                        .content("""
                                {
                                  "decision":"SUBMIT",
                                  "comment":"上报",
                                  "expectedWorkflowVersion":0,
                                  "idempotencyKey":"conflict"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WORKFLOW_VERSION_CONFLICT"));
    }

    @Test
    void shouldReturnHistoryAndTimelineContainer() throws Exception {
        mockMvc.perform(get("/api/v1/emergency-workflows/history")
                        .param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].workflowId").value("WF-1"))
                .andExpect(jsonPath("$.data.items[0].timeline").isArray());
    }

    @Test
    void shouldAcceptAuditedFullResourceRelease() throws Exception {
        mockMvc.perform(post("/api/v1/emergency-workflows/WF-1/resource-releases")
                        .contentType("application/json")
                        .content("""
                                {
                                  "reason":"演练完成后全部归队",
                                  "expectedWorkflowVersion":1,
                                  "idempotencyKey":"release-1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workflowId").value("WF-1"));
    }
}
