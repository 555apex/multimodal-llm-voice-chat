package cn.fj.roadagent.application.dispatch;

import cn.fj.roadagent.domain.dispatch.ApprovalDecision;
import cn.fj.roadagent.domain.dispatch.EventSeverity;
import cn.fj.roadagent.domain.dispatch.ResourceFeasibility;

public record ProfessionalReviewCommand(
        String workflowId,
        ApprovalDecision decision,
        EventSeverity eventSeverity,
        ResourceFeasibility resourceFeasibility,
        String impactAssessment,
        String coordinationRequirements,
        String comment,
        long expectedWorkflowVersion,
        String idempotencyKey
) {
}
