package cn.fj.roadagent.interfaces.rest.dispatch;

import cn.fj.roadagent.application.dispatch.DispatchApprovalUseCase;
import cn.fj.roadagent.application.dispatch.DispatchQueryUseCase;
import cn.fj.roadagent.domain.dispatch.DispatchPlan;
import cn.fj.roadagent.domain.dispatch.DispatchStatus;
import cn.fj.roadagent.domain.dispatch.DispatchTask;
import cn.fj.roadagent.domain.dispatch.EmergencyEvent;
import cn.fj.roadagent.interfaces.rest.common.TraceIdFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DispatchControllerTest {
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        DispatchPlan submitted = plan();
        DispatchQueryUseCase query = ignored -> submitted;
        DispatchApprovalUseCase approval = ignored -> submitted;
        mockMvc = MockMvcBuilders.standaloneSetup(new DispatchController(query, approval))
                .addFilter(new TraceIdFilter())
                .build();
    }

    @Test
    void shouldApproveDispatchWithVersionAndIdempotencyKey() throws Exception {
        mockMvc.perform(post("/api/v1/dispatches/DP-1/approvals")
                        .contentType("application/json")
                        .content("""
                                {"decision":"APPROVE","expectedVersion":1,"idempotencyKey":"idem-1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.planId").value("DP-1"))
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"));
    }

    private DispatchPlan plan() {
        return new DispatchPlan(
                "DP-1", new EmergencyEvent("塌方", "福州", "五四路", "HIGH", "道路塌方"),
                "建议封控", List.of(new DispatchTask(1, "警戒", "属地", null)),
                List.of(), List.of(), DispatchStatus.SUBMITTED, 3L, Instant.EPOCH, null
        );
    }
}
