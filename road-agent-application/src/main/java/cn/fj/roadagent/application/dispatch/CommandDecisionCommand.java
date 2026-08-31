package cn.fj.roadagent.application.dispatch;

import cn.fj.roadagent.domain.dispatch.ApprovalDecision;

public record CommandDecisionCommand(
        String workflowId,
        ApprovalDecision decision,
        String comment,
        long expectedWorkflowVersion,
        String idempotencyKey
) {
}
