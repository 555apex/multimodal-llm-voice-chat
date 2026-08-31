package cn.fj.roadagent.application.dispatch;

public record Level1DecisionCommand(
        String workflowId,
        Level1Decision decision,
        String comment,
        long expectedWorkflowVersion,
        String idempotencyKey
) {
}
