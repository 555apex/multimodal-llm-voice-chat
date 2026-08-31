package cn.fj.roadagent.interfaces.rest.workflow;

import cn.fj.roadagent.application.dispatch.Level1Decision;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;

public record Level1DecisionRequest(
        @NotNull(message = "一级处理决定不能为空") Level1Decision decision,
        @Size(max = 500, message = "处理意见不能超过500个字符") String comment,
        @Min(value = 0, message = "工作流版本不能小于0") long expectedWorkflowVersion,
        @NotBlank(message = "幂等键不能为空")
        @Size(max = 100, message = "幂等键不能超过100个字符") String idempotencyKey
) {
    @AssertTrue(message = "一级退回时必须填写返工意见")
    public boolean isCommentValidForDecision() {
        return decision == null || decision != Level1Decision.REJECT
                || (comment != null && !comment.isBlank());
    }
}
