package cn.fj.roadagent.interfaces.rest.emergency;

import cn.fj.roadagent.application.dispatch.EmergencyAlert;
import cn.fj.roadagent.application.dispatch.NoDispatchUseCase;
import cn.fj.roadagent.application.dispatch.QueryPendingEmergencyUseCase;
import cn.fj.roadagent.domain.dispatch.EmergencyEvent;
import cn.fj.roadagent.interfaces.rest.common.GlobalExceptionHandler;
import cn.fj.roadagent.interfaces.rest.common.TraceIdFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Optional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EmergencyEventControllerTest {
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        EmergencyEvent event = new EmergencyEvent(
                "202607280000000001",
                "AGT20260728EVT000000000000000001",
                Instant.parse("2026-07-28T00:00:00Z"),
                "DT01",
                "福州市某道路发生边坡崩塌"
        );
        QueryPendingEmergencyUseCase query = () ->
                Optional.of(new EmergencyAlert(event, null, 19));
        NoDispatchUseCase noDispatch = ignored -> {
        };
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new EmergencyEventController(
                                query,
                                ignored -> {
                                    throw new UnsupportedOperationException();
                                },
                                noDispatch
                        )
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilter(new TraceIdFilter())
                .build();
    }

    @Test
    void shouldReturnBigEventIdAsString() throws Exception {
        mockMvc.perform(get("/api/v1/emergency-events/pending/next"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.event.eventId").value("202607280000000001"))
                .andExpect(jsonPath("$.data.event.eventTypeName").value("崩塌（落石）"))
                .andExpect(jsonPath("$.data.pendingCount").value(19));
    }

    @Test
    void shouldRequireSecondConfirmationForNoDispatch() throws Exception {
        mockMvc.perform(post("/api/v1/emergency-events/202607280000000001/no-dispatch")
                        .contentType("application/json")
                        .content("""
                                {"reason":"无需救援","confirmed":false}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}
