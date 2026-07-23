package cn.fj.roadagent.interfaces.rest.traffic;

import cn.fj.roadagent.application.traffic.AreaTrafficQueryResult;
import cn.fj.roadagent.application.traffic.Freshness;
import cn.fj.roadagent.application.traffic.QueryAreaTrafficUseCase;
import cn.fj.roadagent.application.traffic.SummarySource;
import cn.fj.roadagent.domain.traffic.AdministrativeArea;
import cn.fj.roadagent.domain.traffic.AdministrativeAreaLevel;
import cn.fj.roadagent.domain.traffic.AreaTrafficQuery;
import cn.fj.roadagent.domain.traffic.CongestionLevel;
import cn.fj.roadagent.domain.traffic.GeoBoundary;
import cn.fj.roadagent.domain.traffic.GeoPoint;
import cn.fj.roadagent.domain.traffic.RoadSegmentStatus;
import cn.fj.roadagent.domain.traffic.TrafficCoverage;
import cn.fj.roadagent.domain.traffic.TrafficEvaluation;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AreaTrafficControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        QueryAreaTrafficUseCase useCase = command -> {
            AdministrativeArea area = new AdministrativeArea(
                    "思明区", "厦门", "350203", AdministrativeAreaLevel.DISTRICT,
                    new GeoBoundary(List.of(List.of(
                            new GeoPoint(118.08, 24.44), new GeoPoint(118.10, 24.44),
                            new GeoPoint(118.10, 24.46), new GeoPoint(118.08, 24.46)
                    )))
            );
            List<RoadSegmentStatus> segments = List.of(
                    new RoadSegmentStatus("成功大道", "北向南", CongestionLevel.CONGESTED, 12.0, null)
            );
            return new AreaTrafficQueryResult(
                    new AreaTrafficQuery(area, command.scope()), "思明区部分道路拥堵。", SummarySource.MODEL,
                    segments, TrafficEvaluation.from(segments), TrafficCoverage.of(2, 1, 1),
                    "AMAP", Instant.parse("2026-07-21T08:00:00Z"), Freshness.FRESH,
                    List.of("PARTIAL_AREA_COVERAGE"), command.traceId()
            );
        };
        mockMvc = MockMvcBuilders.standaloneSetup(new AreaTrafficController(useCase))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new TraceIdFilter())
                .build();
    }

    @Test
    void shouldReturnAreaMetricsCoverageAndSegments() throws Exception {
        mockMvc.perform(post("/api/v1/traffic/area-queries")
                        .header("X-Trace-Id", "trace-area-controller")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"city":"厦门","areaName":"思明区","scope":"AREA_MAJOR"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.queryScope").value("AREA_MAJOR"))
                .andExpect(jsonPath("$.data.areaCode").value("350203"))
                .andExpect(jsonPath("$.data.evaluation.congestedSegments").value(1))
                .andExpect(jsonPath("$.data.coverage.coverageRatio").value(0.5))
                .andExpect(jsonPath("$.data.segments[0].roadName").value("成功大道"));
    }

    @Test
    void shouldRejectRoadScopeOnAreaEndpoint() throws Exception {
        mockMvc.perform(post("/api/v1/traffic/area-queries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"city":"厦门","areaName":"思明区","scope":"ROAD"}
                                """))
                .andExpect(status().isBadRequest());
    }
}
