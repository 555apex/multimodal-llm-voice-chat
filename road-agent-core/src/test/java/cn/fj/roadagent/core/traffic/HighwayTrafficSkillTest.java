package cn.fj.roadagent.core.traffic;

import cn.fj.roadagent.application.agent.AgentDecision;
import cn.fj.roadagent.application.agent.AgentEvent;
import cn.fj.roadagent.application.agent.AgentMessageCommand;
import cn.fj.roadagent.application.model.ModelRequest;
import cn.fj.roadagent.application.model.ModelResponse;
import cn.fj.roadagent.application.model.ModelStreamListener;
import cn.fj.roadagent.application.port.ChatModelPort;
import cn.fj.roadagent.core.agent.AgentExecutionContext;
import cn.fj.roadagent.domain.traffic.HighwayRoute;
import cn.fj.roadagent.domain.traffic.HighwayTrafficSegment;
import cn.fj.roadagent.domain.traffic.HighwayTrafficSnapshot;
import cn.fj.roadagent.domain.traffic.RouteTrafficSummary;
import cn.fj.roadagent.domain.traffic.RoadCapacity;
import cn.fj.roadagent.domain.traffic.RoadCapacitySnapshot;
import cn.fj.roadagent.domain.traffic.TrafficStatus;
import cn.fj.roadagent.domain.traffic.RegionalTrafficSnapshot;
import cn.fj.roadagent.domain.traffic.RegionalConnectionHub;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HighwayTrafficSkillTest {

    @Test
    void modelFailureDoesNotEmitAnswerTrafficResultOrSpeech() {
        ChatModelPort failingModel = new ChatModelPort() {
            @Override public ModelResponse generate(ModelRequest request) { throw new UnsupportedOperationException(); }
            @Override public <T> T generateStructured(ModelRequest request, Class<T> type) {
                throw new IllegalStateException("模型超时");
            }
            @Override public void stream(ModelRequest request, ModelStreamListener listener) {
                throw new UnsupportedOperationException();
            }
        };
        HighwayTrafficService service = new HighwayTrafficService(this::snapshot, failingModel);
        HighwayTrafficSkill skill = new HighwayTrafficSkill(service);
        List<AgentEvent> events = new ArrayList<>();

        assertThrows(IllegalStateException.class, () -> skill.execute(context(), events::add));

        List<String> names = events.stream().map(AgentEvent::name).toList();
        assertFalse(names.contains("answer.delta"));
        assertFalse(names.contains("answer.speech"));
        assertFalse(names.contains("result.traffic"));
    }

    @Test
    void capacityModelFailureAlsoDoesNotEmitAnswerTrafficResultOrSpeech() {
        ChatModelPort failingModel = new ChatModelPort() {
            @Override public ModelResponse generate(ModelRequest request) { throw new UnsupportedOperationException(); }
            @Override public <T> T generateStructured(ModelRequest request, Class<T> type) {
                throw new IllegalStateException("模型超时");
            }
            @Override public void stream(ModelRequest request, ModelStreamListener listener) {
                throw new UnsupportedOperationException();
            }
        };
        HighwayTrafficService trafficService = new HighwayTrafficService(this::snapshot, failingModel);
        RoadCapacityService capacityService = new RoadCapacityService(() -> new RoadCapacitySnapshot(
                List.of(new RoadCapacity("G104", "北京-平潭", 0, 1920, 0)),
                Instant.parse("2026-08-13T01:00:00Z"), "capacity-fp"
        ), failingModel);
        HighwayTrafficSkill skill = new HighwayTrafficSkill(trafficService, capacityService);
        AgentDecision decision = new AgentDecision(
                "TRAFFIC_QUERY", "CAPACITY_BOTTLENECKS", null, null, null, null,
                null, null, null, null, null, null, null, null, List.of(), null
        );
        AgentExecutionContext context = new AgentExecutionContext(
                new AgentMessageCommand("conversation-1", "福建省有哪些瓶颈路线", "trace-1"),
                decision, List.of()
        );
        List<AgentEvent> events = new ArrayList<>();

        assertThrows(IllegalStateException.class, () -> skill.execute(context, events::add));

        List<String> names = events.stream().map(AgentEvent::name).toList();
        assertFalse(names.contains("answer.delta"));
        assertFalse(names.contains("answer.speech"));
        assertFalse(names.contains("result.traffic"));
    }

    @Test
    void regionalModelFailureUsesDeterministicSummaryAndPublishesCompleteResult() {
        ChatModelPort failingModel = new ChatModelPort() {
            @Override public ModelResponse generate(ModelRequest request) { throw new UnsupportedOperationException(); }
            @Override public <T> T generateStructured(ModelRequest request, Class<T> type) {
                throw new IllegalStateException("模型摘要无效");
            }
            @Override public void stream(ModelRequest request, ModelStreamListener listener) {
                throw new UnsupportedOperationException();
            }
        };
        HighwayTrafficService trafficService = new HighwayTrafficService(this::snapshot, failingModel);
        RegionalTrafficService regionalService = new RegionalTrafficService(
                () -> new RegionalTrafficSnapshot(List.of(new RegionalConnectionHub(
                        "FJ001", "宁德卡口", "G104", "北京-平潭", "350100", "福州市",
                        "350900", "宁德市", 10d, 30, 1400, 200
                )), Instant.parse("2026-08-13T01:00:00Z"), List.of()),
                failingModel
        );
        HighwayTrafficSkill skill = new HighwayTrafficSkill(trafficService, null, regionalService, null);
        AgentDecision decision = new AgentDecision(
                "TRAFFIC_QUERY", "REGIONAL_KEY_CHANNELS", null, null, null, null,
                List.of(), null, null, null, null, null, null, null, null, null, List.of(), null
        );
        AgentExecutionContext context = new AgentExecutionContext(
                new AgentMessageCommand("conversation-1", "福建省跨市通道关键卡口", "trace-1"),
                decision, List.of()
        );
        List<AgentEvent> events = new ArrayList<>();

        skill.execute(context, events::add);

        List<String> names = events.stream().map(AgentEvent::name).toList();
        assertTrue(names.contains("answer.delta"));
        assertTrue(names.contains("result.traffic"));
    }

    private AgentExecutionContext context() {
        AgentDecision decision = new AgentDecision(
                "TRAFFIC_QUERY", "PROVINCE_OVERVIEW", null, null, null, null,
                null, null, null, null, null, null, null, null, List.of(), null
        );
        return new AgentExecutionContext(
                new AgentMessageCommand("conversation-1", "福建省交通态势", "trace-1"), decision, List.of()
        );
    }

    private HighwayTrafficSnapshot snapshot() {
        return new HighwayTrafficSnapshot(
                List.of(new HighwayRoute("G104", "北京-平潭", "国道", "宁德市", "福州市")),
                List.of(new RouteTrafficSummary("G104", "北京-平潭", 80d, TrafficStatus.SMOOTH)),
                List.of(new HighwayTrafficSegment(
                        "G104", "北京-平潭", "FJ001→FJ002", 10d, 80d, TrafficStatus.SMOOTH, 0d
                )),
                Instant.parse("2026-08-13T01:00:00Z"), "fp"
        );
    }
}
