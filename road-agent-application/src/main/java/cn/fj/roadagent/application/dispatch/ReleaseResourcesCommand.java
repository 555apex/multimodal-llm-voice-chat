package cn.fj.roadagent.application.dispatch;

public record ReleaseResourcesCommand(
        String workflowId,
        String reason,
        long expectedWorkflowVersion,
        String idempotencyKey
) {
}
