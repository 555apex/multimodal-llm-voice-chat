package cn.fj.roadagent.core.agent;

import cn.fj.roadagent.application.agent.AgentDecision;
import cn.fj.roadagent.application.model.ModelRequest;
import cn.fj.roadagent.application.model.ModelResponse;
import cn.fj.roadagent.application.model.ModelStreamListener;
import cn.fj.roadagent.application.port.ChatModelPort;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntentPlannerTest {

    @Test
    void shouldAllowProvinceOverviewWithoutExtraFields() {
        AgentDecision decision = new AgentDecision(
                "TRAFFIC_QUERY", "PROVINCE_OVERVIEW", null, null, null, null,
                null, null, null, null, List.of(), null
        );

        assertTrue(new IntentPlanner(new FixedModel(decision)).missingFields(decision).isEmpty());
    }

    @Test
    void shouldRequireTwoValidFujianCitiesForCityPair() {
        AgentDecision decision = new AgentDecision(
                "TRAFFIC_QUERY", "CITY_PAIR", "宁德", "北京", null, null,
                null, null, null, null, null, null, null, null, List.of(), null
        );

        assertEquals(List.of("destinationCity"),
                new IntentPlanner(new FixedModel(decision)).missingFields(decision));
    }

    @Test
    void promptShouldDefineTrafficAndCapacityScopesAndRejectCityRoads() {
        FixedModel model = new FixedModel(new AgentDecision(
                "TRAFFIC_QUERY", "PROVINCE_ABNORMAL", null, null, null, null,
                null, null, null, null, List.of(), null
        ));

        new IntentPlanner(model).plan("请根据上下文规划这条模糊请求", List.of());

        assertTrue(model.lastRequest.systemPrompt().contains("PROVINCE_OVERVIEW、PROVINCE_ABNORMAL、CITY_PAIR、ROUTE_DETAIL"));
        assertTrue(model.lastRequest.systemPrompt().contains("CAPACITY_OVERVIEW、CAPACITY_BOTTLENECKS、CAPACITY_ROUTE_DETAIL"));
        assertTrue(model.lastRequest.systemPrompt().contains("通行能力利用率"));
        assertTrue(model.lastRequest.systemPrompt().contains("不得把五四路、成功大道"));
    }

    @Test
    void capacityRouteDetailRequiresRouteCodeOrName() {
        AgentDecision decision = new AgentDecision(
                "TRAFFIC_QUERY", "CAPACITY_ROUTE_DETAIL", null, null, null, null,
                null, null, null, null, null, null, null, null, List.of(), null
        );

        assertEquals(List.of("routeCodeOrName"),
                new IntentPlanner(new FixedModel(decision)).missingFields(decision));
    }

    @Test
    void vehiclePatternAcceptsOneSelectedCityButRequiresChoiceForTwoCities() {
        AgentDecision oneCity = decisionWithCities(
                "VEHICLE_PATTERN_OVERVIEW", List.of("福州"), null
        );
        assertTrue(new IntentPlanner(new FixedModel(oneCity)).missingFields(oneCity).isEmpty());

        AgentDecision twoCities = decisionWithCities(
                "VEHICLE_PATTERN_OVERVIEW", List.of("福州", "厦门"), null
        );
        assertEquals(List.of("analysisCity"),
                new IntentPlanner(new FixedModel(twoCities)).missingFields(twoCities));
    }

    @Test
    void promptDefinesRegionalAndVehicleScopesWithoutTreatingTrafficContactAsOd() {
        FixedModel model = new FixedModel(decisionWithCities(
                "REGIONAL_TRAFFIC_OVERVIEW", List.of("福州", "厦门"), null
        ));

        new IntentPlanner(model).plan("请规划这条测试请求", List.of());

        assertTrue(model.lastRequest.systemPrompt().contains("CHECKPOINT_PRESSURE、CITY_PRESSURE、ROUTE_PRESSURE"));
        assertTrue(model.lastRequest.systemPrompt().contains("VEHICLE_PATTERN_OVERVIEW、VEHICLE_STRUCTURE"));
        assertTrue(model.lastRequest.systemPrompt().contains("不是OD查询"));
    }

    @Test
    void builtInVehicleQuestionsAreRecognizedWithoutDependingOnModelClassification() {
        IntentPlanner planner = new IntentPlanner(new ThrowingModel());

        AgentDecision overview = planner.plan("福州市的交通运输特征如何？", List.of());
        assertEquals("VEHICLE_PATTERN_OVERVIEW", overview.trafficScope());
        assertEquals("福州", overview.analysisCity());
        assertEquals(List.of("福州"), overview.selectedCities());

        assertEquals("VEHICLE_STRUCTURE",
                planner.plan("厦门市各类车型通行量占比是多少？", List.of()).trafficScope());
        assertEquals("VEHICLE_HOURLY_PATTERN",
                planner.plan("厦门市24小时分车型出行规律如何？", List.of()).trafficScope());
        assertEquals("VEHICLE_DAY_TYPE_COMPARISON",
                planner.plan("福州市工作日和周末的分车型通行量有什么差异？", List.of()).trafficScope());
    }

    @Test
    void builtInRegionalQuestionsChooseTheCorrectResultCombination() {
        IntentPlanner planner = new IntentPlanner(new ThrowingModel());

        AgentDecision overview = planner.plan("福州和厦门的区域交通压力分布如何？", List.of());
        assertEquals("REGIONAL_TRAFFIC_OVERVIEW", overview.trafficScope());
        assertEquals(List.of("福州", "厦门"), overview.selectedCities());
        assertEquals("CHECKPOINT_PRESSURE",
                planner.plan("福建省哪些卡口承担较大的交通压力？", List.of()).trafficScope());
        assertEquals("CITY_PRESSURE",
                planner.plan("福建省哪些城市日均流量较大？", List.of()).trafficScope());
        assertEquals("CITY_PRESSURE",
                planner.plan("福建各市活跃卡口数和城市日总流量如何？", List.of()).trafficScope());
        assertEquals("ROUTE_PRESSURE",
                planner.plan("福建省哪些国省道日均流量较大？", List.of()).trafficScope());
        assertEquals("ROUTE_PRESSURE",
                planner.plan("福建路线日总流量排行如何？", List.of()).trafficScope());
        assertEquals("CHECKPOINT_PRESSURE",
                planner.plan("全省卡口枢纽Top20有哪些？", List.of()).trafficScope());
        assertEquals("REGIONAL_TRAFFIC_OVERVIEW",
                planner.plan("福建省哪些城市和国省道日均流量较大？", List.of()).trafficScope());
    }

    @Test
    void vehicleQuestionWithTwoOrNoCitiesKeepsClarificationBoundary() {
        IntentPlanner planner = new IntentPlanner(new ThrowingModel());

        AgentDecision twoCities = planner.plan("福州和厦门的交通运输特征如何？", List.of());
        assertNull(twoCities.analysisCity());
        assertEquals(List.of("analysisCity"), planner.missingFields(twoCities));
        assertTrue(twoCities.clarification().contains("一个城市"));

        AgentDecision noCity = planner.plan("交通运输特征如何？", List.of());
        assertNull(noCity.analysisCity());
        assertEquals(List.of("analysisCity"), planner.missingFields(noCity));
    }

    @Test
    void commonRoadAndCapacityQuestionsUseDeterministicBusinessScopes() {
        IntentPlanner planner = new IntentPlanner(new ThrowingModel());

        assertEquals("PROVINCE_OVERVIEW",
                planner.plan("福建省目前整体交通态势如何？", List.of()).trafficScope());
        assertEquals("PROVINCE_ABNORMAL",
                planner.plan("福建省哪些国省道路段处于拥堵异常状态？", List.of()).trafficScope());

        AgentDecision cityPair = planner.plan("宁德市到福州市的交通情况如何？", List.of());
        assertEquals("CITY_PAIR", cityPair.trafficScope());
        assertEquals("宁德", cityPair.originCity());
        assertEquals("福州", cityPair.destinationCity());

        AgentDecision route = planner.plan("G104 北京—平潭当前通行情况如何？", List.of());
        assertEquals("ROUTE_DETAIL", route.trafficScope());
        assertEquals("G104", route.routeCode());

        assertEquals("CAPACITY_OVERVIEW",
                planner.plan("福建省各国省道通行能力利用率怎么样？", List.of()).trafficScope());
        assertEquals("CAPACITY_BOTTLENECKS",
                planner.plan("福建省有哪些严重瓶颈路线？", List.of()).trafficScope());
        AgentDecision capacityRoute = planner.plan("S 201当前实际通行能力如何？", List.of());
        assertEquals("CAPACITY_ROUTE_DETAIL", capacityRoute.trafficScope());
        assertEquals("S201", capacityRoute.routeCode());
        assertEquals("CAPACITY_BOTTLENECKS",
                planner.plan("哪些国省道利用率偏低？", List.of()).trafficScope());
    }

    @Test
    void regionalContactAndEmergencyWordingAreNotCapturedByRoadShortcut() {
        IntentPlanner planner = new IntentPlanner(new ThrowingModel());

        assertEquals("REGIONAL_TRAFFIC_OVERVIEW",
                planner.plan("福州和厦门的区域交通联系及交通压力如何？", List.of()).trafficScope());
        assertThrows(AssertionError.class,
                () -> planner.plan("福州市G104发生塌方，需要应急调度", List.of()));
    }

    @Test
    void commonNaturalVariantsStillReachTheNewBusinessFeatures() {
        IntentPlanner planner = new IntentPlanner(new ThrowingModel());

        assertEquals("VEHICLE_PATTERN_OVERVIEW",
                planner.plan("福州车型分析", List.of()).trafficScope());
        assertEquals("VEHICLE_HOURLY_PATTERN",
                planner.plan("厦门24小时交通量变化如何？", List.of()).trafficScope());
        assertEquals("CITY_PRESSURE",
                planner.plan("福建各地市交通负荷排名", List.of()).trafficScope());
        assertEquals("CHECKPOINT_PRESSURE",
                planner.plan("福建哪些交调站流量最大？", List.of()).trafficScope());
    }

    private AgentDecision decisionWithCities(String scope, List<String> selectedCities, String analysisCity) {
        return new AgentDecision(
                "TRAFFIC_QUERY", scope, null, null, null, null,
                selectedCities, analysisCity, null, null, null, null,
                null, null, null, null, List.of(), null
        );
    }

    private static final class FixedModel implements ChatModelPort {
        private final AgentDecision decision;
        private ModelRequest lastRequest;

        private FixedModel(AgentDecision decision) {
            this.decision = decision;
        }

        @Override
        public ModelResponse generate(ModelRequest request) {
            return new ModelResponse("", "TEST", "test");
        }

        @Override
        public <T> T generateStructured(ModelRequest request, Class<T> resultType) {
            lastRequest = request;
            return resultType.cast(decision);
        }

        @Override
        public void stream(ModelRequest request, ModelStreamListener listener) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class ThrowingModel implements ChatModelPort {
        @Override public ModelResponse generate(ModelRequest request) { throw new AssertionError("不应调用模型"); }
        @Override public <T> T generateStructured(ModelRequest request, Class<T> resultType) {
            throw new AssertionError("不应调用模型");
        }
        @Override public void stream(ModelRequest request, ModelStreamListener listener) {
            throw new AssertionError("不应调用模型");
        }
    }
}
