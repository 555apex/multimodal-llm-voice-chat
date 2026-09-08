package cn.fj.roadagent.core.traffic;

import cn.fj.roadagent.application.exception.BusinessRuleException;
import cn.fj.roadagent.application.model.ModelRequest;
import cn.fj.roadagent.application.model.ModelResponse;
import cn.fj.roadagent.application.model.ModelStreamListener;
import cn.fj.roadagent.application.port.ChatModelPort;
import cn.fj.roadagent.application.traffic.HighwayTrafficQuery;
import cn.fj.roadagent.application.traffic.OdTrafficSummaryResponse;
import cn.fj.roadagent.domain.traffic.RegionalConnectionHub;
import cn.fj.roadagent.domain.traffic.RegionalTrafficSnapshot;
import cn.fj.roadagent.domain.traffic.TrafficQueryType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OdTrafficServiceTest {
    private static final Instant TIME = Instant.parse("2026-09-07T10:00:00Z");

    @Test
    void averagesCheckpointsPerRouteThenNormalizesAcrossTheFullCityNetwork() {
        var facts = service(network(), new FixedModel()).collectFacts(query(
                TrafficQueryType.OD_DESTINATION_TENDENCY, "福州"));

        assertEquals(2, facts.destinationRows().size());
        var ningde = facts.destinationRows().get(0);
        assertEquals("宁德市", ningde.destinationCityName());
        assertEquals(700.00, ningde.weeklyConnectionStrength(), 0.0001);
        assertEquals(0.70, ningde.tendencyRatio(), 0.0001);
        var xiamen = facts.destinationRows().get(1);
        assertEquals(2, xiamen.routeCount());
        assertEquals(300.00, xiamen.weeklyConnectionStrength(), 0.0001);
        assertEquals(0.30, xiamen.tendencyRatio(), 0.0001);
    }

    @Test
    void matrixUsesProvinceWideDenominatorAndKeepsMissingPairsBlank() {
        var facts = service(network(), new FixedModel()).collectFacts(query(
                TrafficQueryType.OD_CONNECTION_MATRIX, "福州", "厦门", "泉州"));

        assertTrue(facts.destinationRows().isEmpty());
        assertEquals(3, facts.matrixRows().size());
        var fuzhou = facts.matrixRows().get(0);
        assertNull(fuzhou.cells().get(0).tendencyRatio());
        assertEquals(0.30, fuzhou.cells().get(1).tendencyRatio(), 0.0001);
        assertNull(fuzhou.cells().get(2).tendencyRatio());
        var xiamen = facts.matrixRows().get(1);
        assertEquals(0.75, xiamen.cells().get(0).tendencyRatio(), 0.0001);
    }

    @Test
    void emptyMatrixSelectionDefaultsToAllNineCities() {
        var facts = service(network(), new FixedModel()).collectFacts(query(TrafficQueryType.OD_CONNECTION_MATRIX));
        assertEquals(9, facts.selectedRegions().size());
        assertEquals(9, facts.matrixRows().size());
        assertTrue(facts.matrixRows().stream().allMatch(row -> row.cells().size() == 9));
    }

    @Test
    void validatesQueryCityCountsAndNames() {
        OdTrafficService service = service(network(), new FixedModel());
        assertEquals("OD_SINGLE_CITY_REQUIRED", assertThrows(BusinessRuleException.class,
                () -> service.collectFacts(query(TrafficQueryType.OD_DESTINATION_TENDENCY, "福州", "厦门"))).errorCode());
        assertEquals("OD_MATRIX_CITY_COUNT_REQUIRED", assertThrows(BusinessRuleException.class,
                () -> service.collectFacts(query(TrafficQueryType.OD_CONNECTION_MATRIX, "福州"))).errorCode());
        assertEquals("OD_CITY_INVALID", assertThrows(BusinessRuleException.class,
                () -> service.collectFacts(query(TrafficQueryType.OD_DESTINATION_TENDENCY, "南京"))).errorCode());
    }

    @Test
    void rejectsDirectionalModelClaimAndUsesFactSafeSummary() {
        ChatModelPort unsafe = new FixedModel("70.00%的车辆实际驶往宁德市。");
        var result = service(network(), unsafe).query(query(TrafficQueryType.OD_DESTINATION_TENDENCY, "福州"));
        assertTrue(result.summary().contains("目的地联系倾向"));
        assertTrue(result.summary().contains("70.00%"));
        assertTrue(!result.summary().contains("驶往"));
        assertEquals(2, result.odDestinationRows().size());
        assertTrue(result.odMatrixRows().isEmpty());
    }

    private OdTrafficService service(List<RegionalConnectionHub> hubs, ChatModelPort model) {
        return new OdTrafficService(() -> new RegionalTrafficSnapshot(hubs, TIME, List.of()), model);
    }

    private List<RegionalConnectionHub> network() {
        return List.of(
                hub("F1", "G104", "350100", "福州市", "350200", "厦门市", 100),
                hub("F2", "G104", "350100", "福州市", "350200", "厦门市", 300),
                hub("F3", "S213", "350100", "福州市", "350200", "厦门市", 100),
                hub("N1", "G316", "350100", "福州市", "350900", "宁德市", 700),
                hub("X1", "S507", "350200", "厦门市", "350900", "宁德市", 100)
        );
    }

    private RegionalConnectionHub hub(String checkpoint, String route, String aCode, String aName,
            String bCode, String bName, long weekly) {
        return new RegionalConnectionHub(checkpoint, checkpoint + "卡口", route, route + "路线",
                aCode, aName, bCode, bName, 10d, 40d, weekly, weekly / 7);
    }

    private HighwayTrafficQuery query(TrafficQueryType type, String... cities) {
        return new HighwayTrafficQuery(type, null, null, null, null, List.of(cities), null, "trace");
    }

    private static class FixedModel implements ChatModelPort {
        private final String summary;
        FixedModel() {
            this("福州市的目的地联系倾向呈现层次差异。宁德市联系倾向为70.00%，在当前城市联系网络中较为突出。厦门市联系倾向为30.00%，同样构成重要联系方向。建议持续关注主要关联城市之间的交通运行变化。");
        }
        FixedModel(String summary) { this.summary = summary; }
        public ModelResponse generate(ModelRequest request) { throw new UnsupportedOperationException(); }
        public <T> T generateStructured(ModelRequest request, Class<T> type) {
            return type.cast(new OdTrafficSummaryResponse(summary));
        }
        public void stream(ModelRequest request, ModelStreamListener listener) { throw new UnsupportedOperationException(); }
    }
}
