package cn.fj.roadagent.core.agent;

import cn.fj.roadagent.application.agent.*;
import cn.fj.roadagent.application.model.*;
import cn.fj.roadagent.application.port.ChatModelPort;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class OdIntentPlannerTest {
    @Test void distinguishesNewOdQueriesFromAllExistingBusinessTypes() {
        var planner = new IntentPlanner(new Model());
        Map<String, String> cases = new LinkedHashMap<>();
        cases.put("福州的出行主要联系哪些城市？", "OD_DESTINATION_TENDENCY");
        cases.put("福州车辆主要去哪些城市？", "OD_DESTINATION_TENDENCY");
        cases.put("福州的主要出行去向是哪里？", "OD_DESTINATION_TENDENCY");
        cases.put("福州的目的地分布如何？", "OD_DESTINATION_TENDENCY");
        cases.put("福州与哪些城市的出行需求强度较高？", "OD_DESTINATION_TENDENCY");
        cases.put("分析福州的目的地联系倾向", "OD_DESTINATION_TENDENCY");
        cases.put("福州和厦门的OD情况如何？", "OD_CONNECTION_MATRIX");
        cases.put("请给我一份福州与厦门的OD综合分析", "OD_CONNECTION_MATRIX");
        cases.put("分析福州、厦门、泉州的OD情况。", "OD_CONNECTION_MATRIX");
        cases.put("福建省城市联系矩阵", "OD_CONNECTION_MATRIX");
        cases.put("福建省哪些卡口承担较大的交通压力？", "REGIONAL_KEY_CHANNELS");
        cases.put("福州、厦门、泉州的区域交通压力分布如何？", "REGIONAL_TRAFFIC_OVERVIEW");
        cases.put("福州到厦门目前拥堵吗？", "CITY_PAIR");
        cases.put("福州市车型占比如何？", "VEHICLE_STRUCTURE");
        cases.put("福建省各国省道通行能力利用率如何？", "CAPACITY_OVERVIEW");
        cases.forEach((question, type) -> {
            var decision = planner.plan(question, List.of());
            assertEquals(type, decision.trafficScope(), question);
            assertTrue(planner.missingFields(decision).isEmpty(), question + ": " + decision.clarification());
        });
        var multiple = planner.plan("分析福州、厦门、泉州的OD情况。", List.of());
        assertEquals(List.of("福州", "厦门", "泉州"), multiple.selectedCities());
    }

    @Test void unresolvedScopeHistoryDatesAndOutsideCitiesAskRatherThanDroppingCities() {
        var planner = new IntentPlanner(new Model());
        for (String question : List.of("这两个城市的OD分析", "福州上个月OD分析", "福州和南京的OD分析",
                "北京的OD分析", "福州和上海的OD分析", "福州实际OD流向", "福州2025年8月OD分析",
                "福州OD未来趋势", "福州厦门OD分析以及通行能力利用率")) {
            var decision = planner.plan(question, List.of());
            assertFalse(planner.missingFields(decision).isEmpty(), question);
            assertNotNull(decision.clarification(), question);
        }
    }

    @Test void contextAndNegationGoToPlannerWithHistoryAndPreserveSelectedCities() {
        var model = new Model();
        model.response = od(List.of("福州", "厦门", "泉州"));
        var planner = new IntentPlanner(model);
        var history = List.of(new ConversationMessage("user", "福州和厦门的OD情况如何", Instant.now()));
        for (String question : List.of("再加上泉州", "只看第二张表", "去掉厦门", "不是路况，我要OD分析", "不要OD，只看交通压力")) {
            assertNotNull(planner.plan(question, history));
            assertNotNull(model.last, question);
            assertTrue(model.last.systemPrompt().contains("再加上泉州"));
            assertFalse(model.last.history().isEmpty());
        }
        assertTrue(planner.missingFields(od(List.of("福州", "厦门", "泉州"))).isEmpty());
        assertFalse(planner.missingFields(od(List.of("福州", "南京"))).isEmpty());
    }

    static AgentDecision od(List<String> cities) {
        return new AgentDecision("TRAFFIC_QUERY", cities.size() == 1 ? "OD_DESTINATION_TENDENCY" : "OD_CONNECTION_MATRIX", null, null, null, null,
                cities, null, null, null, null, null, null, null, null, null, List.of(), null);
    }
    static class Model implements ChatModelPort {
        ModelRequest last;
        AgentDecision response;
        public ModelResponse generate(ModelRequest r) { throw new UnsupportedOperationException(); }
        public <T> T generateStructured(ModelRequest r, Class<T> type) {
            last = r;
            if (response == null) throw new AssertionError("内置问题不应依赖模型: " + r.userPrompt());
            return type.cast(response);
        }
        public void stream(ModelRequest r, ModelStreamListener l) { throw new UnsupportedOperationException(); }
    }
}
