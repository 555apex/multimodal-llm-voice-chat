package cn.fj.roadagent.application.dispatch;

import cn.fj.roadagent.domain.dispatch.ApprovalDecision;

public record DispatchApprovalCommand(
        String planId,
        ApprovalDecision decision,
        String comment,
        long expectedVersion,
        String idempotencyKey
) {
}
