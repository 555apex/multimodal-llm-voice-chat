package cn.fj.roadagent.core.agent;

import cn.fj.roadagent.application.agent.AgentEvent;
import cn.fj.roadagent.application.agent.AgentEventSink;
import cn.fj.roadagent.application.agent.AgentIntent;
import cn.fj.roadagent.application.agent.ConversationMessage;
import cn.fj.roadagent.application.model.ModelMessage;
import cn.fj.roadagent.application.model.ModelRequest;
import cn.fj.roadagent.application.port.ChatModelPort;
import cn.fj.roadagent.application.port.RagSearchPort;
import cn.fj.roadagent.application.rag.RagSearchResult;

import java.util.List;
import java.util.Map;

public final class KnowledgeQaSkill implements AgentSkill {
    private static final String NO_EVIDENCE = "知识库中没有检索到足以支持结论的资料。请补充更具体的业务场景、法规名称或文件范围。";
    private final RagSearchPort ragSearchPort;
    private final ChatModelPort chatModelPort;
    private final int topK;

    public KnowledgeQaSkill(RagSearchPort ragSearchPort, ChatModelPort chatModelPort, int topK) {
        this.ragSearchPort = ragSearchPort;
        this.chatModelPort = chatModelPort;
        this.topK = topK <= 0 ? 5 : topK;
    }

    @Override public AgentIntent intent() { return AgentIntent.KNOWLEDGE_QA; }

    @Override
    public AgentSkillResult execute(AgentExecutionContext context, AgentEventSink sink) {
        sink.emit(new AgentEvent("stage.changed", Map.of("stage", "TOOL_CALLING", "label", "正在检索知识库")));
        sink.emit(new AgentEvent("tool.started", Map.of("tool", "weknora_knowledge_search")));
        List<RagSearchResult> results = ragSearchPort.search(context.command().message(), topK);
        sink.emit(new AgentEvent("tool.completed", Map.of("tool", "weknora_knowledge_search",
                "hitCount", results.size())));
        List<String> sources = results.stream().map(this::sourceName).distinct().toList();
        if (results.isEmpty()) {
            sink.emit(new AgentEvent("answer.delta", Map.of("content", NO_EVIDENCE)));
            sink.emit(new AgentEvent("result.rag", Map.of("hitCount", 0, "sources", List.of())));
            return new AgentSkillResult(NO_EVIDENCE);
        }
        sink.emit(new AgentEvent("stage.changed", Map.of("stage", "ANSWERING", "label", "正在依据知识库生成回答")));
        String answer = chatModelPort.generate(new ModelRequest(systemPrompt(),
                buildUserPrompt(context.command().message(), results), toModelMessages(context.history()), 0.2,
                1536)).content();
        sink.emit(new AgentEvent("answer.delta", Map.of("content", answer)));
        sink.emit(new AgentEvent("result.rag", Map.of("hitCount", results.size(), "sources", sources)));
        return new AgentSkillResult(answer);
    }

    private String systemPrompt() {
        return """
                你是福建公路法规和业务知识助手。只能依据用户问题后提供的知识库片段回答。
                如果片段不足以支持结论，必须明确说明证据不足。不得编造法规条款、文件名、时限或办理要求。
                使用中文，回答应简洁、可执行；材料、条件和流程优先使用项目符号。
                """.strip();
    }

    private String buildUserPrompt(String question, List<RagSearchResult> results) {
        StringBuilder context = new StringBuilder();
        for (int index = 0; index < results.size(); index++) {
            RagSearchResult result = results.get(index);
            context.append('[').append(index + 1).append("] 来源：").append(sourceName(result)).append('\n')
                    .append(trimContent(result.content())).append("\n\n");
        }
        return "用户问题：\n" + question + "\n\n知识库片段：\n" + context.toString().trim()
                + "\n\n只依据以上片段回答；证据不足时说明缺少什么。";
    }

    private String trimContent(String content) {
        return content.length() <= 1200 ? content : content.substring(0, 1200) + "...";
    }

    private String sourceName(RagSearchResult result) {
        if (!result.knowledgeTitle().isBlank()) return result.knowledgeTitle();
        if (!result.knowledgeFilename().isBlank()) return result.knowledgeFilename();
        return result.id().isBlank() ? "未知来源" : result.id();
    }

    private List<ModelMessage> toModelMessages(List<ConversationMessage> history) {
        return history.stream().map(message -> new ModelMessage(message.role(), message.content())).toList();
    }
}
