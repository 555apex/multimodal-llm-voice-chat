package cn.fj.roadagent.interfaces.rest.workflow;

import cn.fj.roadagent.domain.dispatch.ApprovalDecision;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;

public record CommandDecisionRequest(
        @NotNull(message = "三级决策不能为空") ApprovalDecision decision,
        @Size(max = 500, message = "省级批示不能超过500个字符") String comment,
        @Min(value = 0, message = "工作流版本不能小于0") long expectedWorkflowVersion,
        @NotBlank(message = "幂等键不能为空")
        @Size(max = 100, message = "幂等键不能超过100个字符") String idempotencyKey
) {
    @AssertTrue(message = "三级退回时必须填写退回意见")
    public boolean isCommentValidForDecision() {
        return decision == null || decision != ApprovalDecision.REJECT
                || (comment != null && !comment.isBlank());
    }
}
