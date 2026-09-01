package cn.fj.roadagent.interfaces.rest.workflow;

import cn.fj.roadagent.domain.dispatch.ApprovalDecision;
import cn.fj.roadagent.domain.dispatch.EventSeverity;
import cn.fj.roadagent.domain.dispatch.ResourceFeasibility;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;

public record ProfessionalReviewRequest(
        @NotNull(message = "二级复核决定不能为空") ApprovalDecision decision,
        EventSeverity eventSeverity,
        ResourceFeasibility resourceFeasibility,
        @Size(max = 1000, message = "影响研判不能超过1000个字符") String impactAssessment,
        @Size(max = 1000, message = "协同要求不能超过1000个字符") String coordinationRequirements,
        @Size(max = 500, message = "专业意见不能超过500个字符") String comment,
        @Min(value = 0, message = "工作流版本不能小于0") long expectedWorkflowVersion,
        @NotBlank(message = "幂等键不能为空")
        @Size(max = 100, message = "幂等键不能超过100个字符") String idempotencyKey
) {
    @AssertTrue(message = "复核通过时必须填写事件等级、资源可行性、影响研判和专业意见；有缺口时还必须填写协调要求；退回时必须填写意见")
    public boolean isContentValidForDecision() {
        if (decision == null) {
            return true;
        }
        if (decision == ApprovalDecision.REJECT) {
            return hasText(comment);
        }
        return eventSeverity != null
                && (resourceFeasibility == ResourceFeasibility.FEASIBLE
                    || resourceFeasibility == ResourceFeasibility.FEASIBLE_WITH_GAP)
                && hasText(impactAssessment)
                && (resourceFeasibility != ResourceFeasibility.FEASIBLE_WITH_GAP
                    || hasText(coordinationRequirements))
                && hasText(comment);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
