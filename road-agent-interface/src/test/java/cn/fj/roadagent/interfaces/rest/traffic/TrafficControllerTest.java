package cn.fj.roadagent.interfaces.rest.traffic;

import cn.fj.roadagent.application.traffic.HighwayTrafficResult;
import cn.fj.roadagent.application.traffic.QueryHighwayTrafficUseCase;
import cn.fj.roadagent.application.traffic.RouteTrafficResultItem;
import cn.fj.roadagent.application.traffic.RoadCapacityResultItem;
import cn.fj.roadagent.domain.traffic.TrafficQueryType;
import cn.fj.roadagent.interfaces.rest.common.GlobalExceptionHandler;
import cn.fj.roadagent.interfaces.rest.common.TraceIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TrafficControllerTest {

    @Test
    void acceptsThreeCityOdRequestAndSerializesTendencyMatrix() throws Exception {
        QueryHighwayTrafficUseCase useCase = query -> {
            org.junit.jupiter.api.Assertions.assertEquals(3, query.selectedCities().size());
            var facts = new cn.fj.roadagent.application.traffic.OdTrafficFacts(
                    query.queryType(), "城市目的地联系倾向矩阵",
                    List.of(new cn.fj.roadagent.application.traffic.SelectedRegionResultItem("350100", "福州市"),
                            new cn.fj.roadagent.application.traffic.SelectedRegionResultItem("350200", "厦门市"),
                            new cn.fj.roadagent.application.traffic.SelectedRegionResultItem("350500", "泉州市")),
                    List.of(), List.of(new cn.fj.roadagent.application.traffic.OdMatrixRowResultItem(
                            "350100", "福州市", List.of(
                            new cn.fj.roadagent.application.traffic.OdMatrixCellResultItem("350100", "福州市", null, null),
                            new cn.fj.roadagent.application.traffic.OdMatrixCellResultItem("350200", "厦门市", 1200.5, 0.3),
                            new cn.fj.roadagent.application.traffic.OdMatrixCellResultItem("350500", "泉州市", null, null)))),
                    Instant.parse("2026-09-03T04:00:00Z"), List.of());
            return HighwayTrafficResult.fromOdFacts(facts, "已完成所选城市的目的地联系倾向分析。", query.traceId());
        };
        var mvc = MockMvcBuilders.standaloneSetup(new TrafficController(useCase))
                .setControllerAdvice(new GlobalExceptionHandler()).addFilter(new TraceIdFilter()).build();
        mvc.perform(post("/api/v1/traffic/queries").contentType("application/json")
                .content("{\"queryType\":\"OD_CONNECTION_MATRIX\",\"selectedCities\":[\"福州\",\"厦门\",\"泉州\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.odMatrixRows[0].analysisCityName").value("福州市"))
                .andExpect(jsonPath("$.data.odMatrixRows[0].cells[1].weeklyConnectionStrength").value(1200.5))
                .andExpect(jsonPath("$.data.odMatrixRows[0].cells[1].tendencyRatio").value(0.3))
                .andExpect(jsonPath("$.data.segments").isEmpty());
    }

    @Test
    void acceptsTrafficContractAndReturnsUnifiedMysqlResult() throws Exception {
        QueryHighwayTrafficUseCase useCase = query -> new HighwayTrafficResult(
                TrafficQueryType.PROVINCE_OVERVIEW, "福建省国省道整体交通态势",
                "当前国省道整体运行平稳。请按实时状态合理安排出行。",
                List.of(new RouteTrafficResultItem("G104", "北京-平潭", 89.34, 10, "畅通")),
                List.of(), List.of(), 0, 0, false, "MYSQL", Instant.parse("2026-08-13T01:00:00Z"),
                List.of(), query.traceId()
        );
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new TrafficController(useCase))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilter(new TraceIdFilter())
                .build();

        mockMvc.perform(post("/api/v1/traffic/queries")
                        .contentType("application/json")
                        .content("{\"queryType\":\"PROVINCE_OVERVIEW\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("MYSQL"))
                .andExpect(jsonPath("$.data.queryType").value("PROVINCE_OVERVIEW"))
                .andExpect(jsonPath("$.data.routeSummaries[0].status").value(10))
                .andExpect(jsonPath("$.data.routeSummaries[0].statusName").value("畅通"));
    }

    @Test
    void acceptsCapacityQueryAndReturnsCapacityRows() throws Exception {
        QueryHighwayTrafficUseCase useCase = query -> new HighwayTrafficResult(
                query.queryType(), "G104 北京-平潭通行能力",
                "当前路线通行能力评估结果已完成汇总，实际与设计通行能力均按最新数据展示。"
                        + "该路线利用率对应的三级评估结果已明确，可结合页面详细数值进行研判。"
                        + "建议运行监测人员持续关注该路线，并根据评估等级安排后续巡查工作。",
                List.of(), List.of(), List.of(new RoadCapacityResultItem(
                "G104", "北京-平潭", 0, 1920, 0,
                "SEVERE_BOTTLENECK", "严重瓶颈"
        )), 1, 1, false, "MYSQL", Instant.parse("2026-08-13T01:00:00Z"),
                List.of(), query.traceId()
        );
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new TrafficController(useCase))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilter(new TraceIdFilter())
                .build();

        mockMvc.perform(post("/api/v1/traffic/queries")
                        .contentType("application/json")
                        .content("{\"queryType\":\"CAPACITY_ROUTE_DETAIL\",\"routeCode\":\"G104\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.capacityRows[0].actualCapacityVph").value(0))
                .andExpect(jsonPath("$.data.capacityRows[0].utilizationRatio").value(0))
                .andExpect(jsonPath("$.data.capacityRows[0].capacityLevelName").value("严重瓶颈"));
    }

    @Test
    void rejectsMissingQueryType() throws Exception {
        QueryHighwayTrafficUseCase unused = query -> { throw new AssertionError("must not call"); };
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new TrafficController(unused))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilter(new TraceIdFilter())
                .build();

        mockMvc.perform(post("/api/v1/traffic/queries")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}
