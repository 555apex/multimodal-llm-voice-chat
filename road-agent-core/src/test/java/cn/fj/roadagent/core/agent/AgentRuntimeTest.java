package cn.fj.roadagent.core.agent;

import cn.fj.roadagent.application.agent.AgentDecision;
import cn.fj.roadagent.application.agent.AgentEvent;
import cn.fj.roadagent.application.agent.AgentIntent;
import cn.fj.roadagent.application.agent.AgentMessageCommand;
import cn.fj.roadagent.application.agent.ConversationMessage;
import cn.fj.roadagent.application.model.ModelRequest;
import cn.fj.roadagent.application.model.ModelResponse;
import cn.fj.roadagent.application.model.ModelStreamListener;
import cn.fj.roadagent.application.port.ChatModelPort;
import cn.fj.roadagent.application.port.ConversationMemoryPort;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentRuntimeTest {

    @Test
    void shouldReuseConversationHistoryForFollowUp() {
        RecordingModel model = new RecordingModel(new AgentDecision(
                "TRAFFIC_QUERY", "ROUTE_DETAIL", null, null, "G104", null,
                null, null, null, null, null, null, null, null, List.of(), null
        ));
        TestMemory memory = new TestMemory();
        AgentRuntime runtime = runtime(model, memory, new SuccessfulTrafficSkill());

        runtime.handle(command("G104通行情况如何"), event -> { });
        runtime.handle(command("那拥堵路段呢"), event -> { });

        // 首轮G104常见问法由Java直接识别；第二轮模糊追问交给模型并携带完整上下文。
        assertEquals(List.of(2), model.historySizes);
        assertEquals(4, memory.load("conversation-1").size());
    }

    @Test
    void shouldAskForMissingRouteWithoutCallingSkill() {
        RecordingModel model = new RecordingModel(new AgentDecision(
                "TRAFFIC_QUERY", "ROUTE_DETAIL", null, null, null, null,
                null, null, null, null, null, null, null, null, List.of(), "请提供G/S路线编号或名称。"
        ));
        AtomicInteger calls = new AtomicInteger();
        AgentSkill skill = new SuccessfulTrafficSkill() {
            @Override
            public AgentSkillResult execute(AgentExecutionContext context, cn.fj.roadagent.application.agent.AgentEventSink sink) {
                calls.incrementAndGet();
                return super.execute(context, sink);
            }
        };
        List<AgentEvent> events = new ArrayList<>();

        runtime(model, new TestMemory(), skill).handle(command("查一下这条路"), events::add);

        assertEquals(0, calls.get());
        assertTrue(events.stream().anyMatch(event -> "answer.delta".equals(event.name())));
        assertTrue(events.stream().anyMatch(event -> "run.completed".equals(event.name())));
    }

    @Test
    void vehicleFeatureQuestionCannotFallThroughToUnsupportedAnswer() {
        RecordingModel model = new RecordingModel(new AgentDecision(
                "UNSUPPORTED", null, null, null, null, null,
                null, null, null, null, List.of(), null
        ));
        AtomicReference<AgentDecision> executed = new AtomicReference<>();
        AgentSkill skill = new SuccessfulTrafficSkill() {
            @Override
            public AgentSkillResult execute(
                    AgentExecutionContext context,
                    cn.fj.roadagent.application.agent.AgentEventSink sink
            ) {
                executed.set(context.decision());
                return super.execute(context, sink);
            }
        };
        List<AgentEvent> events = new ArrayList<>();

        runtime(model, new TestMemory(), skill)
                .handle(command("福州市的交通运输特征如何？"), events::add);

        assertEquals("VEHICLE_PATTERN_OVERVIEW", executed.get().trafficScope());
        assertEquals("福州", executed.get().analysisCity());
        assertTrue(model.historySizes.isEmpty());
        assertTrue(events.stream().anyMatch(event -> "skill.selected".equals(event.name())));
        assertTrue(events.stream().noneMatch(event -> "run.failed".equals(event.name())));
    }

    private AgentRuntime runtime(ChatModelPort model, ConversationMemoryPort memory, AgentSkill skill) {
        Clock clock = Clock.fixed(Instant.parse("2026-07-21T00:00:00Z"), ZoneOffset.UTC);
        return new AgentRuntime(
                new IntentPlanner(model), new SkillRegistry(List.of(skill)), memory, clock
        );
    }

    private AgentMessageCommand command(String message) {
        return new AgentMessageCommand("conversation-1", message, "trace-1");
    }

    private static class SuccessfulTrafficSkill implements AgentSkill {
        @Override
        public AgentIntent intent() {
            return AgentIntent.TRAFFIC_QUERY;
        }

        @Override
        public AgentSkillResult execute(AgentExecutionContext context, cn.fj.roadagent.application.agent.AgentEventSink sink) {
            return new AgentSkillResult("测试回答");
        }
    }

    private static final class RecordingModel implements ChatModelPort {
        private final AgentDecision decision;
        private final List<Integer> historySizes = new ArrayList<>();

        private RecordingModel(AgentDecision decision) {
            this.decision = decision;
        }

        @Override
        public ModelResponse generate(ModelRequest request) {
            return new ModelResponse("unused", "TEST", "test");
        }

        @Override
        public <T> T generateStructured(ModelRequest request, Class<T> resultType) {
            historySizes.add(request.history().size());
            return resultType.cast(decision);
        }

        @Override
        public void stream(ModelRequest request, ModelStreamListener listener) {
            listener.onDelta("unused");
        }
    }

    private static final class TestMemory implements ConversationMemoryPort {
        private final Map<String, List<ConversationMessage>> messages = new HashMap<>();

        @Override
        public List<ConversationMessage> load(String conversationId) {
            return List.copyOf(messages.getOrDefault(conversationId, List.of()));
        }

        @Override
        public void append(String conversationId, ConversationMessage message) {
            messages.computeIfAbsent(conversationId, ignored -> new ArrayList<>()).add(message);
        }
    }
}
