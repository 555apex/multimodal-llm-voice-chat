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
    @Test void publishesFactsBeforeModelAndRejectsInventedNumbers() {
        List<AgentEvent> events = new ArrayList<>();
        ChatModelPort model = new ChatModelPort() {
            public ModelResponse generate(ModelRequest request) { throw new UnsupportedOperationException(); }
            public <T> T generateStructured(ModelRequest request, Class<T> type) { throw new UnsupportedOperationException(); }
            public void stream(ModelRequest request, ModelStreamListener listener) {
                assertTrue(events.stream().anyMatch(e -> e.name().equals("result.traffic")));
                assertTrue(events.stream().anyMatch(e -> e.name().equals("answer.speech")));
                listener.onDelta("请查看路段数据。");
                listener.onDelta("当前有999辆事故车。");
            }
        };
        var facts = new HighwayTrafficResult(TrafficQueryType.PROVINCE_OVERVIEW, "路网", "已查询2条路线。",
                List.of(), List.of(), List.of(), 0,0,false,"MYSQL",Instant.EPOCH,List.of(),"test");
        var result = TrafficLiveResponder.respond(facts, Map.of("routes", 2), model, events::add, false,false,false);
        assertTrue(result.assistantMessage().contains("请查看路段数据"));
        assertFalse(result.assistantMessage().contains("999"));
        assertTrue(result.assistantMessage().contains("已显示的数据仍可查看"));
    }
}
