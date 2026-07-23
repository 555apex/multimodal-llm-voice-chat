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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentRuntimeTest {

    @Test
    void shouldReuseConversationHistoryForFollowUp() {
        RecordingModel model = new RecordingModel(new AgentDecision(
                "TRAFFIC_QUERY", "ROAD", "福州", null, "五四路", null,
                null, null, null, null, List.of(), null
        ));
        TestMemory memory = new TestMemory();
        AgentRuntime runtime = runtime(model, memory, new SuccessfulTrafficSkill());

        runtime.handle(command("五四路堵吗"), event -> { });
        runtime.handle(command("北向南呢"), event -> { });

        assertEquals(List.of(0, 2), model.historySizes);
        assertEquals(4, memory.load("conversation-1").size());
    }

    @Test
    void shouldAskForMissingCityWithoutCallingSkill() {
        RecordingModel model = new RecordingModel(new AgentDecision(
                "TRAFFIC_QUERY", "ROAD", null, null, "五四路", null,
                null, null, null, null, List.of(), "请问要查询哪个城市？"
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

        runtime(model, new TestMemory(), skill).handle(command("五四路堵吗"), events::add);

        assertEquals(0, calls.get());
        assertTrue(events.stream().anyMatch(event -> "answer.delta".equals(event.name())));
        assertTrue(events.stream().anyMatch(event -> "run.completed".equals(event.name())));
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
