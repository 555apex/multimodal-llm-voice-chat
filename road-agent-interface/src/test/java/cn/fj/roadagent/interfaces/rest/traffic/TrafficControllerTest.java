package cn.fj.roadagent.interfaces.rest.traffic;

import cn.fj.roadagent.application.traffic.Freshness;
import cn.fj.roadagent.application.traffic.QueryRealtimeTrafficUseCase;
import cn.fj.roadagent.application.traffic.SummarySource;
import cn.fj.roadagent.application.traffic.TrafficQueryResult;
import cn.fj.roadagent.domain.traffic.CongestionLevel;
import cn.fj.roadagent.domain.traffic.RoadSegmentStatus;
import cn.fj.roadagent.domain.traffic.TrafficQuery;
import cn.fj.roadagent.interfaces.rest.common.GlobalExceptionHandler;
import cn.fj.roadagent.interfaces.rest.common.TraceIdFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TrafficControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        QueryRealtimeTrafficUseCase useCase = command -> new TrafficQueryResult(
                new TrafficQuery(command.areaCode(), command.roadName(), command.direction()),
                "五四路当前缓行。",
                SummarySource.MODEL,
                List.of(new RoadSegmentStatus("五四路", "南向北", CongestionLevel.SLOW, 25.0, null)),
                "AMAP",
                Instant.parse("2026-07-17T08:00:00Z"),
                Freshness.FRESH,
                false,
                List.of(),
                command.traceId()
        );
        mockMvc = MockMvcBuilders.standaloneSetup(new TrafficController(useCase))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new TraceIdFilter())
                .build();
    }

    @Test
    void shouldReturnTrafficResult() throws Exception {
        mockMvc.perform(post("/api/v1/traffic/queries")
                        .header("X-Trace-Id", "trace-controller-test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"areaCode":"350100","roadName":"五四路","direction":"南向北"}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", "trace-controller-test"))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.summary").value("五四路当前缓行。"))
                .andExpect(jsonPath("$.data.segments[0].congestionLevel").value("SLOW"));
    }

    @Test
    void shouldRejectInvalidRequest() throws Exception {
        mockMvc.perform(post("/api/v1/traffic/queries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"areaCode":"福州","roadName":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}
