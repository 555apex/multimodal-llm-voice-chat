package cn.fj.roadagent.application.dispatch;

public record CorrectEventTypeCommand(
        String workflowId,
        String eventType,
        String reason,
        long expectedWorkflowVersion,
        String idempotencyKey
) { }
