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
    private static final List<String> INTERNAL_REFUSAL_MARKERS = List.of(
            "无法回答", "证据不足", "知识库中没有", "未检索到", "没有检索到",
            "缺少资料", "片段中未", "提供的片段", "检索结果不足"
    );
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
        String answeringLabel = results.isEmpty() ? "正在整理专业回答" : "正在结合知识库生成回答";
        sink.emit(new AgentEvent("stage.changed", Map.of("stage", "ANSWERING", "label", answeringLabel)));
        String answer = chatModelPort.generate(new ModelRequest(systemPrompt(),
                buildUserPrompt(context.command().message(), results), toModelMessages(context.history()), 0.2,
                1536)).content();
        if (containsInternalRefusal(answer)) {
            answer = chatModelPort.generate(new ModelRequest(recoverySystemPrompt(),
                    recoveryUserPrompt(context.command().message()), toModelMessages(context.history()), 0.1,
                    1536)).content();
        }
        answer = removeInternalRefusalLines(answer);
        sink.emit(new AgentEvent("answer.delta", Map.of("content", answer)));
        sink.emit(new AgentEvent("result.rag", Map.of("hitCount", results.size(), "sources", sources)));
        return new AgentSkillResult(answer);
    }

    private String systemPrompt() {
        return """
                你是福建公路法规和业务知识助手。知识库片段是优先参考资料，但不是回答范围的唯一限制。
                先使用与问题直接相关的片段；片段不完整、不直接相关或为空时，使用可靠的公路工程、桥隧养护和交通管理通用专业知识补充并直接回答问题。
                不得向用户提及检索过程、片段是否充分、知识库缺少资料，也不得输出“无法回答”“证据不足”等拒答措辞。
                不得把通用知识伪装成知识库原文，不得编造法规条款、文件名、文号、强制时限或办理要求；涉及精确法定要求时使用审慎表述。
                使用中文，风格专业、自然、简洁；比较类问题优先按维度对照，材料、条件和流程优先使用项目符号，并在结尾给出简短结论。
                """.strip();
    }

    private String recoverySystemPrompt() {
        return """
                你是公路工程与养护业务助手。请仅根据可靠的通用专业知识直接回答用户问题。
                不讨论知识库、检索结果、证据是否充分或自身能力，不使用“无法回答”“证据不足”等措辞。
                不编造具体法规条款、文件编号、强制周期或法定要求。使用中文，回答专业、简洁、结构清楚。
                """.strip();
    }

    private String buildUserPrompt(String question, List<RagSearchResult> results) {
        StringBuilder context = new StringBuilder();
        for (int index = 0; index < results.size(); index++) {
            RagSearchResult result = results.get(index);
            context.append('[').append(index + 1).append("] 来源：").append(sourceName(result)).append('\n')
                    .append(trimContent(result.content())).append("\n\n");
        }
        String references = context.isEmpty() ? "（本次没有直接相关片段）" : context.toString().trim();
        return "用户问题：\n" + question + "\n\n知识库参考片段：\n" + references
                + "\n\n请先判断片段与问题的相关性。相关内容优先采用；其余部分使用可靠的通用专业知识补充。"
                + "不要向用户说明资料缺失或检索情况，直接形成一份完整、自然且风格一致的回答。";
    }

    private String recoveryUserPrompt(String question) {
        return "用户问题：\n" + question + "\n\n请直接给出完整的专业回答。";
    }

    private boolean containsInternalRefusal(String answer) {
        if (answer == null || answer.isBlank()) return true;
        return INTERNAL_REFUSAL_MARKERS.stream().anyMatch(answer::contains);
    }

    private String removeInternalRefusalLines(String answer) {
        if (answer == null || answer.isBlank()) return "请换一种更具体的表述后重试。";
        String cleaned = answer.lines()
                .filter(line -> INTERNAL_REFUSAL_MARKERS.stream().noneMatch(line::contains))
                .collect(java.util.stream.Collectors.joining("\n")).trim();
        return cleaned.isBlank() ? "请换一种更具体的表述后重试。" : cleaned;
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
