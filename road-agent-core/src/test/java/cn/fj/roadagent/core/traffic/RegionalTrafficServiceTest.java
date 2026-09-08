package cn.fj.roadagent.core.traffic;

import cn.fj.roadagent.application.exception.BusinessRuleException;
import cn.fj.roadagent.application.model.ModelRequest;
import cn.fj.roadagent.application.model.ModelResponse;
import cn.fj.roadagent.application.model.ModelStreamListener;
import cn.fj.roadagent.application.port.ChatModelPort;
import cn.fj.roadagent.application.traffic.HighwayTrafficQuery;
import cn.fj.roadagent.application.traffic.RegionalTrafficSummaryResponse;
import cn.fj.roadagent.domain.traffic.RegionalConnectionHub;
import cn.fj.roadagent.domain.traffic.RegionalTrafficSnapshot;
import cn.fj.roadagent.domain.traffic.TrafficQueryType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionalTrafficServiceTest {
    @Test
    void aggregatesSelectedThreeCityNetworkByPairAndRoute() {
        RegionalTrafficService service = service(List.of(
                hub("N1", "G104", "北京-平潭", "350100", "福州市", "350900", "宁德市", 700, 100),
                hub("N2", "G104", "北京-平潭", "350100", "福州市", "350900", "宁德市", 350, 50),
                hub("F1", "G316", "长乐-同仁", "350100", "福州市", "350700", "南平市", 1400, 200),
                hub("X1", "G324", "福州-昆明", "350100", "福州市", "350200", "厦门市", 9999, 999)
        ));

        var facts = service.collectFacts(query(TrafficQueryType.REGIONAL_TRAFFIC_OVERVIEW,
                List.of("福州", "宁德", "南平")));

        assertEquals(2, facts.totalPairCount());
        assertEquals(2, facts.totalChannelCount());
        assertEquals("福州市", facts.pairRows().get(0).cityAName());
        assertEquals("南平市", facts.pairRows().get(0).cityBName());
        assertEquals(1400, facts.pairRows().get(0).weeklyTotalFlow());
        assertEquals(1050, facts.pairRows().get(1).weeklyTotalFlow());
        assertTrue(facts.warnings().isEmpty(), "仓储跳过明细只用于内部诊断，不应暴露给用户");
    }

    @Test
    void individualQueryOnlyReturnsRequestedDimension() {
        RegionalTrafficService service = service(List.of(
                hub("N1", "G104", "北京-平潭", "350100", "福州市", "350900", "宁德市", 700, 100),
                hub("F1", "G316", "长乐-同仁", "350100", "福州市", "350700", "南平市", 1400, 200)
        ));
        var facts = service.collectFacts(query(TrafficQueryType.REGIONAL_KEY_CHANNELS,
                List.of("福州", "宁德", "南平")));
        assertTrue(facts.pairRows().isEmpty());
        assertEquals(2, facts.channelRows().size());
    }

    @Test
    void oneOrTwoCitiesRequireClarificationAndNoConnectionFailsClearly() {
        RegionalTrafficService service = service(List.of(
                hub("N1", "G104", "北京-平潭", "350100", "福州市", "350900", "宁德市", 700, 100)
        ));
        BusinessRuleException count = assertThrows(BusinessRuleException.class,
                () -> service.collectFacts(query(TrafficQueryType.REGIONAL_TRAFFIC_OVERVIEW, List.of("福州", "宁德"))));
        assertEquals("REGIONAL_TRAFFIC_CITY_COUNT_REQUIRED", count.errorCode());
        BusinessRuleException absent = assertThrows(BusinessRuleException.class,
                () -> service.collectFacts(query(TrafficQueryType.REGIONAL_TRAFFIC_OVERVIEW,
                        List.of("厦门", "泉州", "漳州"))));
        assertEquals("REGIONAL_CONNECTION_NOT_FOUND", absent.errorCode());
    }

    @Test
    void modelFailureUsesFactSafeSummaryAndDoesNotLoseTables() {
        ChatModelPort failing = new ChatModelPort() {
            public ModelResponse generate(ModelRequest request) { throw new UnsupportedOperationException(); }
            public <T> T generateStructured(ModelRequest request, Class<T> type) { throw new IllegalStateException("bad model"); }
            public void stream(ModelRequest request, ModelStreamListener listener) { throw new UnsupportedOperationException(); }
        };
        RegionalTrafficService service = new RegionalTrafficService(() -> snapshot(List.of(
                hub("N1", "G104", "北京-平潭", "350100", "福州市", "350900", "宁德市", 700, 100),
                hub("F1", "G316", "长乐-同仁", "350100", "福州市", "350700", "南平市", 1400, 200)
        )), failing);
        var result = service.query(query(TrafficQueryType.REGIONAL_TRAFFIC_OVERVIEW,
                List.of("福州", "宁德", "南平")));
        assertEquals(2, result.regionalPairRows().size());
        assertTrue(result.summary().contains("无方向统计"));
    }

    private RegionalTrafficService service(List<RegionalConnectionHub> hubs) {
        return new RegionalTrafficService(() -> snapshot(hubs), new FixedModel());
    }
    private RegionalTrafficSnapshot snapshot(List<RegionalConnectionHub> hubs) {
        return new RegionalTrafficSnapshot(hubs, Instant.parse("2026-08-27T08:00:00Z"), List.of(
                "已跳过2条起终点或名称不合法的路网记录。",
                "已跳过125条不属于有效跨市路线或数值不合法的卡口记录。"
        ));
    }
    private HighwayTrafficQuery query(TrafficQueryType type, List<String> cities) {
        return new HighwayTrafficQuery(type, null, null, null, null, cities, null, "trace");
    }
    private RegionalConnectionHub hub(String checkpoint, String route, String routeName,
            String aCode, String aName, String bCode, String bName, long weekly, long daily) {
        return new RegionalConnectionHub(checkpoint, checkpoint + "卡口", route, routeName,
                aCode, aName, bCode, bName, 10d, 40d, weekly, daily);
    }

    private static final class FixedModel implements ChatModelPort {
        public ModelResponse generate(ModelRequest request) { throw new UnsupportedOperationException(); }
        public <T> T generateStructured(ModelRequest request, Class<T> type) {
            return type.cast(new RegionalTrafficSummaryResponse(
                    "本次已完成所选城市范围内跨市交通联系统计。城市对排名反映了无方向联系压力。重要跨市路线可结合表格查看。排名以七日总流量为主要依据。建议持续关注高流量城市对与路线的后续变化。"));
        }
        public void stream(ModelRequest request, ModelStreamListener listener) { throw new UnsupportedOperationException(); }
    }
}
