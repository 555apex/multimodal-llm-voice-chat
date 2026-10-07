package cn.fj.roadagent.core.agent;

import cn.fj.roadagent.application.agent.AgentDecision;
import cn.fj.roadagent.application.agent.AgentEvent;
import cn.fj.roadagent.application.agent.AgentMessageCommand;
import cn.fj.roadagent.application.model.ModelRequest;
import cn.fj.roadagent.application.model.ModelResponse;
import cn.fj.roadagent.application.model.ModelStreamListener;
import cn.fj.roadagent.application.port.ChatModelPort;
import cn.fj.roadagent.application.rag.RagSearchResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeQaSkillTest {
    @Test
    void returnsEvidenceBoundAnswerAndSources() {
        List<AgentEvent> events = new ArrayList<>();
        ChatModelPort model = new FixedModel("应提交车辆、货物和路线材料。", new AtomicInteger());
        KnowledgeQaSkill skill = new KnowledgeQaSkill((query, topK) -> List.of(
                new RagSearchResult("1", "应提交车辆、货物和路线材料。", "超限运输办法", "", 0.9)
        ), model, 5);

        AgentSkillResult result = skill.execute(context("需要哪些材料？"), events::add);

        assertTrue(result.assistantMessage().contains("车辆"));
        assertTrue(events.stream().anyMatch(event -> "result.rag".equals(event.name())
                && event.data().toString().contains("超限运输办法")));
    }

    @Test
    void usesModelKnowledgeWhenRetrievalHasNoEvidence() {
        AtomicInteger modelCalls = new AtomicInteger();
        KnowledgeQaSkill skill = new KnowledgeQaSkill((query, topK) -> List.of(),
                new FixedModel("经常检查侧重日常发现异常，定期检查侧重系统评定技术状况。", modelCalls), 5);
        List<AgentEvent> events = new ArrayList<>();

        AgentSkillResult result = skill.execute(context("桥梁经常检查与定期检查有什么区别？"), events::add);

        assertEquals(1, modelCalls.get());
        assertTrue(result.assistantMessage().contains("系统评定"));
        assertTrue(events.stream().anyMatch(event -> "result.rag".equals(event.name())
                && event.data().toString().contains("hitCount=0")));
    }

    @Test
    void retriesWithoutRagLanguageWhenFirstAnswerRefuses() {
        AtomicInteger modelCalls = new AtomicInteger();
        ChatModelPort model = new ChatModelPort() {
            @Override public ModelResponse generate(ModelRequest request) {
                int call = modelCalls.incrementAndGet();
                String content = call == 1
                        ? "依据提供的知识库片段，无法回答，证据不足。"
                        : "经常检查侧重高频巡视和明显异常，定期检查侧重全面检查与技术状况评定。";
                return new ModelResponse(content, "TEST", "test");
            }
            @Override public <T> T generateStructured(ModelRequest request, Class<T> type) {
                throw new UnsupportedOperationException();
            }
            @Override public void stream(ModelRequest request, ModelStreamListener listener) {
                throw new UnsupportedOperationException();
            }
        };
        KnowledgeQaSkill skill = new KnowledgeQaSkill((query, topK) -> List.of(
                new RagSearchResult("1", "桥面系风险排查要点", "桥梁风险指南", "", 0.8)
        ), model, 5);

        AgentSkillResult result = skill.execute(context("桥梁经常检查与定期检查有什么区别？"), event -> {});

        assertEquals(2, modelCalls.get());
        assertTrue(result.assistantMessage().contains("技术状况评定"));
        assertTrue(INTERNAL_REFUSAL_TERMS.stream().noneMatch(result.assistantMessage()::contains));
    }

    private static final List<String> INTERNAL_REFUSAL_TERMS = List.of("无法回答", "证据不足", "知识库片段");

    @Test
    void plannerOnlyRoutesKnowledgeQuestionsWhenEnabled() {
        FixedModel model = new FixedModel("unused", new AtomicInteger()) {
            @Override public <T> T generateStructured(ModelRequest request, Class<T> type) {
                return type.cast(new AgentDecision("UNSUPPORTED", null, null, null, null, null,
                        null, null, null, null, List.of(), null));
            }
        };
        assertEquals("KNOWLEDGE_QA", new IntentPlanner(model, null, true)
                .plan("公路养护验收有哪些规范？", List.of()).intent());
        assertEquals("UNSUPPORTED", new IntentPlanner(model, null, false)
                .plan("公路养护验收有哪些规范？", List.of()).intent());
    }

    private AgentExecutionContext context(String message) {
        return new AgentExecutionContext(new AgentMessageCommand("conversation-1", message, "trace-1"),
                new AgentDecision("KNOWLEDGE_QA", null, null, null, null, null,
                        null, null, null, null, List.of(), null), List.of());
    }

    private static class FixedModel implements ChatModelPort {
        private final String answer;
        private final AtomicInteger calls;
        FixedModel(String answer, AtomicInteger calls) { this.answer = answer; this.calls = calls; }
        @Override public ModelResponse generate(ModelRequest request) {
            calls.incrementAndGet();
            return new ModelResponse(answer, "TEST", "test");
        }
        @Override public <T> T generateStructured(ModelRequest request, Class<T> type) {
            throw new UnsupportedOperationException();
        }
        @Override public void stream(ModelRequest request, ModelStreamListener listener) {
            throw new UnsupportedOperationException();
        }
    }
}
