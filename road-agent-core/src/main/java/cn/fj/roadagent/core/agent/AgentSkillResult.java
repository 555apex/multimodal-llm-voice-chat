package cn.fj.roadagent.core.agent;

public record AgentSkillResult(String assistantMessage, String speechText) {
    public AgentSkillResult {
        if (assistantMessage == null || assistantMessage.isBlank()) {
            throw new IllegalArgumentException("Agent回答不能为空");
        }
        assistantMessage = assistantMessage.trim();
        speechText = speechText == null || speechText.isBlank()
                ? SpeechTextSanitizer.toSpeakableText(assistantMessage)
                : speechText.trim();
    }

    public AgentSkillResult(String assistantMessage) {
        this(assistantMessage, SpeechTextSanitizer.toSpeakableText(assistantMessage));
    }
}
