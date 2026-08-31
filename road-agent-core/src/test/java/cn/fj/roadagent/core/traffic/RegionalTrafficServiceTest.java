package cn.fj.roadagent.core.traffic;

import cn.fj.roadagent.application.exception.BusinessRuleException;
import cn.fj.roadagent.application.model.ModelRequest;
import cn.fj.roadagent.application.model.ModelResponse;
import cn.fj.roadagent.application.model.ModelStreamListener;
import cn.fj.roadagent.application.port.ChatModelPort;
import cn.fj.roadagent.application.traffic.HighwayTrafficQuery;
import cn.fj.roadagent.application.traffic.RegionInsight;
import cn.fj.roadagent.application.traffic.RegionalTrafficSummaryResponse;
import cn.fj.roadagent.domain.traffic.RegionalTrafficSnapshot;
import cn.fj.roadagent.domain.traffic.TrafficQueryType;
import cn.fj.roadagent.domain.traffic.TransportHub;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionalTrafficServiceTest {

    @Test
    void provinceOverviewProducesTop20Top5AndTop10WithStableRanking() {
        List<TransportHub> hubs = java.util.stream.IntStream.range(0, 30)
                .mapToObj(index -> hub(
                        "CP%02d".formatted(index), index % 2 == 0 ? "G104" : "S201",
                        index < 15 ? "350100" : "350200", index < 15 ? "福州市" : "厦门市",
                        index < 2 ? 500 : index, 20 + index
                )).toList();
        RegionalTrafficService service = service(hubs);

        var facts = service.collectFacts(query(TrafficQueryType.REGIONAL_TRAFFIC_OVERVIEW, List.of()));

        assertEquals(20, facts.hubRows().size());
        assertEquals(List.of("CP00", "CP01"), facts.hubRows().stream().limit(2)
                .map(row -> row.checkpointNo()).toList());
        assertEquals(2, facts.regionRows().size());
        assertEquals(2, facts.routeRows().size());
        assertEquals(30, facts.totalHubCount());
    }

    @Test
    void twoCitiesAreCombinedAndHubShareUsesAllCheckpointsInSelectedScope() {
        RegionalTrafficService service = service(List.of(
                hub("F1", "G104", "350100", "福州市", 101, 30),
                hub("F2", "G104", "350100", "福州市", 100, 20),
                hub("X1", "S201", "350200", "厦门市", 200, 40),
                hub("X2", "S203", "350200", "厦门市", 150, 50),
                hub("Q1", "G324", "350500", "泉州市", 900, 60)
        ));

        var facts = service.collectFacts(query(
                TrafficQueryType.REGIONAL_TRAFFIC_OVERVIEW, List.of("福州", "厦门")
        ));

        assertEquals(4, facts.totalHubCount());
        assertEquals(List.of("350100", "350200"), facts.selectedRegions().stream()
                .map(row -> row.regionCode()).toList());
        var fuzhou = facts.regionRows().stream().filter(row -> row.regionCode().equals("350100")).findFirst().orElseThrow();
        var xiamen = facts.regionRows().stream().filter(row -> row.regionCode().equals("350200")).findFirst().orElseThrow();
        assertEquals(1, fuzhou.activeHubCount());
        assertEquals(0.25, fuzhou.hubShareRatio());
        assertEquals(2, xiamen.activeHubCount());
        assertEquals(0.50, xiamen.hubShareRatio());
        assertTrue(facts.hubRows().stream().noneMatch(row -> row.checkpointNo().equals("Q1")));
    }

    @Test
    void specificQueryOnlyProducesRequestedTableAndRejectsMoreThanTwoCities() {
        RegionalTrafficService service = service(List.of(
                hub("F1", "G104", "350100", "福州市", 101, 30)
        ));

        var facts = service.collectFacts(query(TrafficQueryType.ROUTE_PRESSURE, List.of("福州")));
        assertTrue(facts.hubRows().isEmpty());
        assertTrue(facts.regionRows().isEmpty());
        assertEquals(1, facts.routeRows().size());

        BusinessRuleException exception = assertThrows(BusinessRuleException.class, () ->
                service.collectFacts(query(TrafficQueryType.CITY_PRESSURE, List.of("福州", "厦门", "泉州"))));
        assertEquals("REGIONAL_TRAFFIC_CITY_LIMIT", exception.errorCode());
    }

    @Test
    void missingOrMalformedCityInsightsAreSafelyCompletedWithoutFailing() {
        ChatModelPort incompleteModel = new ChatModelPort() {
            @Override public ModelResponse generate(ModelRequest request) { throw new UnsupportedOperationException(); }
            @Override public <T> T generateStructured(ModelRequest request, Class<T> type) {
                return type.cast(new RegionalTrafficSummaryResponse(
                        "当前查询范围内城市卡口压力已经完成汇总，重点区域的运行差异较为清晰。"
                                + "活跃卡口较集中的区域需要加强监测，并持续关注排名靠前的交通枢纽。"
                                + "建议结合城市压力表和后续批次变化，合理安排重点卡口巡查工作。",
                        List.of(new RegionInsight("999999", "无关区域的错误解读"))
                ));
            }
            @Override public void stream(ModelRequest request, ModelStreamListener listener) {
                throw new UnsupportedOperationException();
            }
        };
        RegionalTrafficService service = new RegionalTrafficService(
                () -> new RegionalTrafficSnapshot(List.of(
                        hub("F1", "G104", "350100", "福州市", 300, 40),
                        hub("X1", "S201", "350200", "厦门市", 260, 45)
                ), Instant.parse("2026-08-27T08:00:00Z")),
                incompleteModel
        );

        var result = service.query(query(TrafficQueryType.CITY_PRESSURE, List.of()));

        assertEquals(2, result.regionPressureRows().size());
        assertTrue(result.regionPressureRows().stream().allMatch(row -> !row.interpretation().isBlank()));
        assertFalse(result.regionPressureRows().stream().anyMatch(row -> row.regionCode().equals("999999")));
    }

    private RegionalTrafficService service(List<TransportHub> hubs) {
        return new RegionalTrafficService(
                () -> new RegionalTrafficSnapshot(hubs, Instant.parse("2026-08-27T08:00:00Z")),
                new FixedModel()
        );
    }

    private HighwayTrafficQuery query(TrafficQueryType type, List<String> cities) {
        return new HighwayTrafficQuery(type, null, null, null, null, cities, null, "trace");
    }

    private TransportHub hub(
            String checkpoint, String route, String regionCode, String regionName, long flow, double speed
    ) {
        return new TransportHub(
                checkpoint, route, route.equals("G104") ? "北京-平潭" : "测试路线",
                speed, regionCode, regionName, flow
        );
    }

    private static final class FixedModel implements ChatModelPort {
        @Override public ModelResponse generate(ModelRequest request) { throw new UnsupportedOperationException(); }
        @Override public <T> T generateStructured(ModelRequest request, Class<T> type) {
            if (type == RegionalTrafficSummaryResponse.class) {
                @SuppressWarnings("unchecked") T response = (T) new RegionalTrafficSummaryResponse(
                        "当前查询范围内卡口交通压力已经完成综合统计，重点卡口和路线分布较为清晰。日均流量较高的监测点需要优先关注，城市压力可结合活跃卡口规模综合研判。路线排名反映了当前范围内不同通道承担的交通负荷差异。建议持续关注排名靠前对象并合理安排监测资源。",
                        List.of(new RegionInsight("350100", "福州市当前活跃卡口和日总流量值得持续关注"),
                                new RegionInsight("350200", "厦门市当前卡口流量压力相对较为突出"))
                );
                return response;
            }
            throw new UnsupportedOperationException();
        }
        @Override public void stream(ModelRequest request, ModelStreamListener listener) { throw new UnsupportedOperationException(); }
    }
}
