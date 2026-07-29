package cn.fj.roadagent.interfaces.rest.dispatch;

import cn.fj.roadagent.application.dispatch.DispatchApprovalUseCase;
import cn.fj.roadagent.application.dispatch.DispatchQueryUseCase;
import cn.fj.roadagent.domain.dispatch.DispatchPlan;
import cn.fj.roadagent.domain.dispatch.DispatchStatus;
import cn.fj.roadagent.domain.dispatch.EmergencyEvent;
import cn.fj.roadagent.domain.dispatch.SuggestedResource;
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
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.event.eventId").value("202607280000000001"));
    }

    private DispatchPlan plan() {
        return new DispatchPlan(
                "DP-1",
                new EmergencyEvent(
                        "202607280000000001", "EVT-1", Instant.EPOCH, "DT01", "道路塌方"
                ),
                List.of(new SuggestedResource("队伍", "抢险队", 1, "组", "警戒")),
                "建议封控",
                DispatchStatus.APPROVED,
                1L,
                Instant.EPOCH,
                Instant.EPOCH,
                null,
                null
        );
    }
}
