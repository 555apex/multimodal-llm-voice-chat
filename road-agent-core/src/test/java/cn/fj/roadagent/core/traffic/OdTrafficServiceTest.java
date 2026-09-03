package cn.fj.roadagent.core.traffic;

import cn.fj.roadagent.application.agent.*;
import cn.fj.roadagent.application.exception.BusinessRuleException;
import cn.fj.roadagent.application.model.*;
import cn.fj.roadagent.application.port.*;
import cn.fj.roadagent.application.traffic.*;
import cn.fj.roadagent.core.agent.AgentExecutionContext;
import cn.fj.roadagent.domain.traffic.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class OdTrafficServiceTest {
    static final Instant TIME = Instant.parse("2026-09-03T04:00:00Z");

    @Test void unionAggregationKeepsRoutesPresentInOnlyOneCityAndSeparatesDailyFromWeekly() {
        var facts = service(data(), new Model()).collectFacts(query(TrafficQueryType.OD_OVERVIEW, "福州", "厦门"));
        assertEquals(3, facts.checkpointCount());
        assertEquals(600, facts.weeklyTotalFlow());
        assertEquals(60, facts.dailyAverageFlow());
        assertEquals(2, facts.cityRows().size());
        assertEquals(2, facts.channelRows().size());
        var fz = facts.cityRows().stream().filter(r -> r.regionCode().equals("350100")).findFirst().orElseThrow();
        assertEquals(2, fz.checkpointCount());
        assertEquals(300, fz.weeklyTotalFlow());
        assertEquals(20, fz.averageSpeedKmh());
        assertEquals("G104", facts.channelRows().get(0).routeCode());
        assertEquals(400, facts.channelRows().get(0).weeklyTotalFlow());
        assertEquals(300, facts.channelRows().get(0).carWeeklyFlow());
        assertTrue(facts.channelRows().stream().anyMatch(r -> r.routeCode().equals("S201")));
    }

    @Test void allCitiesAndMultiCityScopesWorkWithoutFixedRouteCount() {
        var service = service(data(), new Model());
        var province = service.collectFacts(query(TrafficQueryType.OD_OVERVIEW));
        assertEquals(9, province.selectedRegions().size());
        assertEquals(6, province.missingRegions().size());
        assertFalse(province.warnings().isEmpty());
        var selected = service.collectFacts(query(TrafficQueryType.OD_OVERVIEW, "福州", "厦门", "泉州", "福州市"));
        assertEquals(3, selected.selectedRegions().size());
        assertEquals(4, selected.checkpointCount());
        assertEquals(3, selected.cityRows().size());
        assertEquals(selected.weeklyTotalFlow(), selected.cityRows().stream().mapToLong(OdCityFlowResultItem::weeklyTotalFlow).sum());
        assertEquals(selected.weeklyTotalFlow(), selected.channelRows().stream().mapToLong(OdChannelResultItem::weeklyTotalFlow).sum());
    }

    @Test void tableSelectionDoesNotLoadUnneededVehiclesAndApiKeepsAllRoutes() {
        boolean[] loadVehicles = { true };
        var svc = new OdTrafficService((codes, vehicles) -> {
            loadVehicles[0] = vehicles;
            return new OdTrafficSnapshot(data(), TIME, List.of());
        }, new Model());
        var city = svc.query(query(TrafficQueryType.OD_CITY_FLOW, "福州"));
        assertFalse(loadVehicles[0]);
        assertEquals(1, city.odCityFlowRows().size());
        assertTrue(city.odChannelRows().isEmpty());
        assertEquals(7, city.periodDays());
        assertTrue(city.segments().isEmpty());
        var channels = svc.query(query(TrafficQueryType.OD_KEY_CHANNELS, "福州"));
        assertTrue(loadVehicles[0]);
        assertTrue(channels.odCityFlowRows().isEmpty());
        assertEquals(2, channels.odChannelRows().size());
        var many = java.util.stream.IntStream.range(0, 21)
                .mapToObj(i -> hub("C" + i, "350100", "G" + (100 + i), 100, 10, 30)).toList();
        var result = service(many, new Model()).query(query(TrafficQueryType.OD_KEY_CHANNELS, "福州"));
        assertEquals(21, result.odChannelRows().size());
        assertFalse(result.truncated());
        assertEquals("G100", result.odChannelRows().get(0).routeCode());
    }

    @Test void emptyInvalidAndOverflowHaveExplicitErrorsAndZeroStaysValid() {
        var svc = service(data(), new Model());
        assertEquals("OD_ANALYSIS_NOT_FOUND", assertThrows(BusinessRuleException.class,
                () -> svc.collectFacts(query(TrafficQueryType.OD_OVERVIEW, "龙岩"))).errorCode());
        assertEquals("OD_CITY_INVALID", assertThrows(BusinessRuleException.class,
                () -> svc.collectFacts(query(TrafficQueryType.OD_OVERVIEW, "南京"))).errorCode());
        var zero = service(List.of(hub("Z", "350100", "G104", 0, 0, 0)), new Model())
                .collectFacts(query(TrafficQueryType.OD_OVERVIEW));
        assertEquals(0, zero.weeklyTotalFlow());
        assertEquals(0, zero.cityRows().get(0).averageSpeedKmh());
        var large = service(List.of(hub("1", "350100", "G104", Long.MAX_VALUE, 0, 0),
                hub("2", "350100", "G104", 1, 0, 0)), new Model());
        assertEquals("OD_ANALYSIS_DATA_INVALID", assertThrows(BusinessRuleException.class,
                () -> large.collectFacts(query(TrafficQueryType.OD_CITY_FLOW))).errorCode());
    }

    @Test void relaxedSummaryAcceptsWordingButFalseFactsAndDirectionsUseSafeSummary() {
        var model = new Model();
        var svc = service(data(), model);
        assertEquals(model.summary, svc.query(query(TrafficQueryType.OD_OVERVIEW, "福州", "厦门")).summary());
        assertTrue(model.last.systemPrompt().contains("不生成历史日期区间"));
        assertTrue(model.last.userPrompt().contains("7"));
        for (String unsafe : List.of("福州的流量为999999999辆。", "福州流向厦门的车辆很多。",
                "未来1至2小时预计拥堵加剧。", "这些路线直接连接所选城市。", "事故造成流量差异。", "G999流量最高。")) {
            model.summary = unsafe;
            String answer = svc.query(query(TrafficQueryType.OD_OVERVIEW, "福州", "厦门")).summary();
            assertNotEquals(unsafe, answer);
            assertTrue(answer.contains("按所选城市卡口汇总"));
        }
    }

    @Test void restRoutingAndAgentRoutingBothUseOdAndPublishSummaryBeforeData() {
        var svc = service(data(), new Model());
        var result = new UnifiedTrafficQueryService(null, null, null, null, svc)
                .query(query(TrafficQueryType.OD_OVERVIEW, "福州", "厦门"));
        assertEquals(2, result.odCityFlowRows().size());
        var events = new ArrayList<AgentEvent>();
        var skill = new HighwayTrafficSkill(null, null, null, null, svc);
        var response = skill.execute(context(), events::add);
        var names = events.stream().map(AgentEvent::name).toList();
        assertTrue(names.indexOf("answer.delta") < names.indexOf("result.traffic"));
        assertEquals(response.assistantMessage(), response.speechText());
        assertFalse(response.speechText().contains("小型客车总流量"));
        var payload = (TrafficAgentResult) events.stream().filter(e -> e.name().equals("result.traffic")).findFirst().orElseThrow().data();
        assertEquals(7, payload.periodDays());
        assertEquals(2, payload.odChannelRows().size());
    }

    @Test void modelFailureAndInvalidJsonProduceNoPartialAnswerDataOrSpeech() {
        var model = new Model();
        model.fail = true;
        var events = new ArrayList<AgentEvent>();
        var skill = new HighwayTrafficSkill(null, null, null, null, service(data(), model));
        assertThrows(IllegalStateException.class, () -> skill.execute(context(), events::add));
        assertTrue(events.stream().noneMatch(e -> List.of("answer.delta", "result.traffic", "answer.speech").contains(e.name())));
        assertThrows(IllegalArgumentException.class, () -> new OdTrafficSummaryResponse(""));
    }

    private AgentExecutionContext context() {
        return new AgentExecutionContext(new AgentMessageCommand("od-test", "福州和厦门OD分析", "trace-od"),
                new AgentDecision("TRAFFIC_QUERY", "OD_OVERVIEW", null, null, null, null,
                        List.of("福州", "厦门"), null, null, null, null, null, null, null, null, null, List.of(), null),
                List.of());
    }

    static List<OdTransportHub> data() {
        return List.of(hub("F1", "350100", "G104", 100, 10, 0),
                hub("F2", "350100", "S201", 200, 20, 40),
                hub("X1", "350200", "G104", 300, 30, 60),
                hub("Q1", "350500", "G205", 800, 80, 70));
    }

    static OdTransportHub hub(String checkpoint, String code, String route, long weekly, long daily, double speed) {
        return new OdTransportHub(checkpoint, code, FujianCity.fromAdcode(code).orElseThrow().displayName() + "市",
                route, "测试路线", weekly, daily, speed, weekly / 4 * 3, weekly / 8, weekly / 8);
    }

    static HighwayTrafficQuery query(TrafficQueryType type, String... cities) {
        return new HighwayTrafficQuery(type, null, null, null, null, List.of(cities), null, "trace-od");
    }
    static OdTrafficService service(List<OdTransportHub> rows, ChatModelPort model) {
        return new OdTrafficService((codes, vehicles) -> new OdTrafficSnapshot(rows, TIME, List.of()), model);
    }
    static class Model implements ChatModelPort {
        String summary = "所选城市的卡口流量已经完成汇总，重点城市与路线可结合下方统计查看。";
        boolean fail;
        ModelRequest last;
        public ModelResponse generate(ModelRequest request) { throw new UnsupportedOperationException(); }
        public <T> T generateStructured(ModelRequest request, Class<T> type) {
            last = request;
            if (fail) throw new IllegalStateException("模型超时或JSON无效");
            return type.cast(new OdTrafficSummaryResponse(summary));
        }
        public void stream(ModelRequest request, ModelStreamListener listener) { throw new UnsupportedOperationException(); }
    }
}
