package cn.fj.roadagent.core.traffic;

import cn.fj.roadagent.application.agent.AgentEvent;
import cn.fj.roadagent.application.model.*;
import cn.fj.roadagent.application.port.ChatModelPort;
import cn.fj.roadagent.application.traffic.HighwayTrafficResult;
import cn.fj.roadagent.domain.traffic.TrafficQueryType;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TrafficLiveResponderTest {
    private static HighwayTrafficResult facts() {
        return new HighwayTrafficResult(TrafficQueryType.PROVINCE_OVERVIEW, "路网", "已查询2条路线。",
                List.of(), List.of(), List.of(), 0, 0, false, "MYSQL", Instant.EPOCH, List.of(), "test");
    }

    @Test void repairsRejectedContentBeforePublishingAndMatchesSpeech() {
        List<AgentEvent> events = new ArrayList<>();
        TestModel model = new TestModel("请查看路段数据。当前有999辆事故车。", "当前2条路线均可查看详情。建议关注后续路况变化。");
        var result = TrafficLiveResponder.respond(facts(), Map.of("routes", 2), model, events::add, false, false, false);
        assertEquals(1, model.repairs);
        assertTrue(result.assistantMessage().contains("建议关注后续路况变化"));
        assertFalse(result.assistantMessage().contains("999"));
        assertFalse(result.assistantMessage().contains("补充解读暂未完成"));
        assertEquals(result.assistantMessage(), result.speechText());
        assertEquals("result.traffic", events.get(1).name());
        assertFalse(events.stream().filter(e -> e.name().equals("answer.delta"))
                .anyMatch(e -> e.data().toString().contains("999")));
    }

    @Test void retriesTransportFailureAndPublishesRecoveredExplanation() {
        TestModel model = new TestModel(null, "道路状态可结合数据表查看。建议合理安排出行。");
        var result = TrafficLiveResponder.respond(facts(), Map.of(), model, e -> {}, false, false, false);
        assertEquals(1, model.repairs);
        assertTrue(result.assistantMessage().contains("合理安排出行"));
        assertEquals(result.assistantMessage(), result.speechText());
    }

    @Test void retainsSafeSentencesOnlyAfterRepairAlsoFails() {
        TestModel model = new TestModel("建议关注路况变化。当前有999辆事故车。后续可查看路线详情。", null);
        var result = TrafficLiveResponder.respond(facts(), Map.of(), model, e -> {}, false, false, false);
        assertEquals(1, model.repairs);
        assertTrue(result.assistantMessage().contains("后续可查看路线详情"));
        assertFalse(result.assistantMessage().contains("999"));
        assertFalse(result.speechText().contains("暂未完成"));
        assertEquals(result.assistantMessage(), result.speechText());
    }

    @Test void acceptsDisplayConversionsWithoutRepair() {
        TestModel model = new TestModel("小型客车占比69.52%，联系流量为12.35万，均速38.51。建议关注重点通道。", null);
        var result = TrafficLiveResponder.respond(facts(), "shareRatio=0.6952;weeklyTotalFlow=123456;averageSpeedKmh=38.506",
                model, e -> {}, false, false, false);
        assertEquals(0, model.repairs);
        assertTrue(result.assistantMessage().contains("69.52%"));
    }

    @Test void completeFailureUsesNoNoticeInTextOrSpeech() {
        TestModel model = new TestModel(null, null);
        var result = TrafficLiveResponder.respond(facts(), Map.of(), model, e -> {}, false, false, false);
        assertEquals(result.assistantMessage(), result.speechText());
        assertFalse(result.assistantMessage().contains("补充解读"));
        assertTrue(result.assistantMessage().contains("持续关注"));
    }

    @Test void incompleteSentenceIsRepairedAndTrendOnlyNeverReturnsEmpty() {
        TestModel model = new TestModel("当前状况", "未来1至2小时，预计道路状态基本稳定。");
        var result = TrafficLiveResponder.respond(facts(), Map.of(), model, e -> {}, true, true, false);
        assertEquals(1, model.repairs);
        assertEquals("未来1至2小时，预计道路状态基本稳定。", result.assistantMessage());
        assertEquals(result.assistantMessage(), result.speechText());
    }

    private static class TestModel implements ChatModelPort {
        private final String streamed;
        private final String repaired;
        int repairs;
        TestModel(String streamed, String repaired) { this.streamed = streamed; this.repaired = repaired; }
        public ModelResponse generate(ModelRequest request) {
            repairs++;
            if (repaired == null) throw new IllegalStateException("测试修复失败");
            return new ModelResponse(repaired, "TEST", "TEST");
        }
        public <T> T generateStructured(ModelRequest request, Class<T> type) { throw new UnsupportedOperationException(); }
        public void stream(ModelRequest request, ModelStreamListener listener) {
            if (streamed == null) throw new IllegalStateException("测试连接失败");
            listener.onDelta(streamed);
        }
    }
}
