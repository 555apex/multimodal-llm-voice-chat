package cn.fj.roadagent.core.agent;

import cn.fj.roadagent.application.agent.AgentDecision;
import cn.fj.roadagent.application.agent.ConversationMessage;
import cn.fj.roadagent.application.model.ModelRequest;
import cn.fj.roadagent.application.model.ModelResponse;
import cn.fj.roadagent.application.model.ModelStreamListener;
import cn.fj.roadagent.application.port.ChatModelPort;
import cn.fj.roadagent.domain.traffic.HighwayRoute;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

        assertTrue(model.lastRequest.systemPrompt().contains("PROVINCE_OVERVIEW、ROUTE_CATALOG、PROVINCE_ABNORMAL、CITY_PAIR、ROUTE_DETAIL"));
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
    void vehiclePatternAcceptsOneOrBothSupportedCities() {
        AgentDecision oneCity = decisionWithCities(
                "VEHICLE_PATTERN_OVERVIEW", List.of("福州"), null
        );
        assertTrue(new IntentPlanner(new FixedModel(oneCity)).missingFields(oneCity).isEmpty());

        AgentDecision twoCities = decisionWithCities(
                "VEHICLE_PATTERN_OVERVIEW", List.of("福州", "厦门"), null
        );
        assertTrue(new IntentPlanner(new FixedModel(twoCities)).missingFields(twoCities).isEmpty());
    }

    @Test
    void promptDefinesRegionalAndVehicleScopesWithoutTreatingTrafficContactAsOd() {
        FixedModel model = new FixedModel(decisionWithCities(
                "REGIONAL_TRAFFIC_OVERVIEW", List.of("福州", "厦门"), null
        ));

        new IntentPlanner(model).plan("请规划这条测试请求", List.of());

        assertTrue(model.lastRequest.systemPrompt().contains("REGIONAL_PAIR_PRESSURE、REGIONAL_KEY_CHANNELS"));
        assertTrue(model.lastRequest.systemPrompt().contains("不输出卡口排名"));
        assertTrue(model.lastRequest.systemPrompt().contains("VEHICLE_PATTERN_OVERVIEW、VEHICLE_STRUCTURE"));
        assertTrue(model.lastRequest.systemPrompt().contains("不表示真实OD"));
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
        assertEquals("REGIONAL_KEY_CHANNELS",
                planner.plan("福建省哪些卡口承担较大的交通压力？", List.of()).trafficScope());
        assertEquals("REGIONAL_PAIR_PRESSURE",
                planner.plan("福建省哪些城市日均流量较大？", List.of()).trafficScope());
        assertEquals("REGIONAL_PAIR_PRESSURE",
                planner.plan("福建哪些城市对的日总流量较高？", List.of()).trafficScope());
        assertEquals("REGIONAL_KEY_CHANNELS",
                planner.plan("福建省哪些国省道日均流量较大？", List.of()).trafficScope());
        assertEquals("REGIONAL_KEY_CHANNELS",
                planner.plan("福建路线日总流量排行如何？", List.of()).trafficScope());
        assertEquals("REGIONAL_KEY_CHANNELS",
                planner.plan("全省卡口枢纽Top20有哪些？", List.of()).trafficScope());
        assertEquals("REGIONAL_TRAFFIC_OVERVIEW",
                planner.plan("福建省哪些城市和国省道日均流量较大？", List.of()).trafficScope());
    }

    @Test
    void vehicleQuestionAllowsTwoSupportedCitiesButStillRequiresAtLeastOne() {
        IntentPlanner planner = new IntentPlanner(new ThrowingModel());

        AgentDecision twoCities = planner.plan("福州和厦门的交通运输特征如何？", List.of());
        assertNull(twoCities.analysisCity());
        assertEquals(List.of("福州", "厦门"), twoCities.selectedCities());
        assertTrue(planner.missingFields(twoCities).isEmpty());
        assertNull(twoCities.clarification());

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
        assertEquals("REGIONAL_PAIR_PRESSURE",
                planner.plan("福建各地市交通负荷排名", List.of()).trafficScope());
        assertEquals("REGIONAL_KEY_CHANNELS",
                planner.plan("福建哪些交调站流量最大？", List.of()).trafficScope());
    }

    @Test
    void controlledExplanationsAndUnsupportedMetadataUseDirectAnswers() {
        IntentPlanner planner = new IntentPlanner(new ThrowingModel());

        AgentDecision threshold = planner.plan("通行能力利用率等于80%时是什么等级？", List.of());
        assertEquals("DIRECT_ANSWER", threshold.intent());
        assertTrue(threshold.clarification().contains("正常"));

        AgentDecision exactActive = planner.plan("日均流量刚好为100算活跃卡口吗？", List.of());
        assertEquals("DIRECT_ANSWER", exactActive.intent());
        assertTrue(exactActive.clarification().contains("不算"));

        AgentDecision metadata = planner.plan("最新一批交通数据的数据快照时间是什么？", List.of());
        assertEquals("DIRECT_ANSWER", metadata.intent());
        assertTrue(metadata.clarification().contains("暂不支持"));
    }

    @Test
    void routeCatalogRouteNamesTrendAndNegativePresentationAreDeterministic() {
        assertEquals("ROUTE_CATALOG", KnownTrafficQuestionClassifier
                .classify("列出当前知识库中有数据的国省道")
                .orElseThrow().trafficScope());

        List<HighwayRoute> routes = List.of(new HighwayRoute("G104", "北京-平潭", "国道", "宁德", "福州"));
        AgentDecision byName = KnownTrafficQuestionClassifier
                .classify("北京—平潭当前通行情况如何？", routes).orElseThrow();
        assertEquals("ROUTE_DETAIL", byName.trafficScope());
        assertEquals("北京-平潭", byName.routeName());
        assertFalse(Boolean.TRUE.equals(byName.includeTrend()));

        AgentDecision congestion = KnownTrafficQuestionClassifier
                .classify("北京-平潭哪些路段拥堵？", routes).orElseThrow();
        assertTrue(Boolean.TRUE.equals(congestion.includeTrend()));

        AgentDecision cause = KnownTrafficQuestionClassifier
                .classify("G104为什么会堵车，是否受节假日影响？", routes).orElseThrow();
        assertEquals("ROUTE_DETAIL", cause.trafficScope());
        assertEquals("G104", cause.routeCode());
        assertTrue(Boolean.TRUE.equals(cause.includeTrend()));

        assertEquals("VEHICLE_HOURLY_PATTERN", KnownTrafficQuestionClassifier
                .classify("厦门只分析24小时车型流量，不要展示周末对比")
                .orElseThrow().trafficScope());
    }

    @Test
    void regionalQueryAcceptsThreeCitiesInOneResult() {
        IntentPlanner planner = new IntentPlanner(new ThrowingModel());
        AgentDecision decision = planner.plan("福州、厦门和泉州的城市交通压力如何？", List.of());

        assertEquals("REGIONAL_PAIR_PRESSURE", decision.trafficScope());
        assertEquals(List.of("福州", "厦门", "泉州"), decision.selectedCities());
        assertTrue(planner.missingFields(decision).isEmpty());
    }

    @Test
    void regionalClarificationCanMergeAThirdCityFromFollowUp() {
        IntentPlanner planner = new IntentPlanner(new ThrowingModel());
        AgentDecision pending = planner.plan("福州和宁德的跨区域交通联系如何？", List.of());
        assertEquals(List.of("selectedCities"), planner.missingFields(pending));

        AgentDecision completed = planner.plan("再加南平", List.of(
                new ConversationMessage("user", "福州和宁德的跨区域交通联系如何？", Instant.EPOCH)
        ), pending);

        assertEquals(List.of("福州", "宁德", "南平"), completed.selectedCities());
        assertTrue(planner.missingFields(completed).isEmpty());
    }

    @Test
    void capacityBetweenTwoCitiesAndStandaloneConstraintsUseTheIntendedBoundary() {
        IntentPlanner planner = new IntentPlanner(new ThrowingModel());

        AgentDecision capacity = planner.plan("福州到厦门之间哪些路线属于能力瓶颈？", List.of());
        assertEquals("CAPACITY_BOTTLENECKS", capacity.trafficScope());
        assertEquals("福州", capacity.originCity());
        assertEquals("厦门", capacity.destinationCity());

        for (String instruction : List.of(
                "请把当前态势总结和未来1至2小时定性趋势放在同一段回答里。",
                "请只给出定性趋势，不要编造未来具体车速、流量或解除时间。",
                "当前只有部分活动路线有容量数据时，请有多少展示多少。",
                "两市查询请合并两市卡口统计，但不要把结果描述成真实OD流量。")) {
            AgentDecision answer = planner.plan(instruction, List.of());
            assertEquals("DIRECT_ANSWER", answer.intent(), instruction);
            assertTrue(answer.clarification().contains("可以"), instruction);
        }
    }

    @Test
    void structuredContextInheritsRouteAndReversesCityPair() {
        IntentPlanner planner = new IntentPlanner(new ThrowingModel());
        AgentDecision route = planner.plan("G104当前通行情况如何？", List.of());
        AgentDecision inherited = planner.plan("那它最拥堵的路段呢？",
                List.of(new cn.fj.roadagent.application.agent.ConversationMessage(
                        "user", "G104当前通行情况如何？", java.time.Instant.EPOCH)), route);
        assertEquals("G104", inherited.routeCode());
        assertEquals("ROUTE_DETAIL", inherited.trafficScope());
        assertTrue(Boolean.TRUE.equals(inherited.includeTrend()));

        AgentDecision pair = new AgentDecision(
                "TRAFFIC_QUERY", "CITY_PAIR", "宁德", "福州", null, null,
                List.of(), null, null, null, null, null, null, null, null, null,
                List.of(), null, false);
        AgentDecision reversed = planner.plan("那反方向呢？",
                List.of(new cn.fj.roadagent.application.agent.ConversationMessage(
                        "user", "宁德到福州路况", java.time.Instant.EPOCH)), pair);
        assertEquals("福州", reversed.originCity());
        assertEquals("宁德", reversed.destinationCity());
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
