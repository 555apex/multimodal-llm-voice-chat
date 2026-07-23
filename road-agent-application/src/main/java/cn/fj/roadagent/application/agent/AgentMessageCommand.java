package cn.fj.roadagent.application.agent;

public record AgentMessageCommand(String conversationId, String message, String traceId) {
    public AgentMessageCommand {
        if (conversationId == null || !conversationId.matches("[A-Za-z0-9-]{1,64}")) {
            throw new IllegalArgumentException("conversationId格式不正确");
        }
        if (message == null || message.isBlank() || message.length() > 1000) {
            throw new IllegalArgumentException("消息长度必须为1至1000个字符");
        }
        message = message.trim();
        traceId = traceId == null ? "unknown" : traceId;
    }
}
