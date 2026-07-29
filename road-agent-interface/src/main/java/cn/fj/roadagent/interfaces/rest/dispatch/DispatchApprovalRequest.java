package cn.fj.roadagent.interfaces.rest.dispatch;

import cn.fj.roadagent.domain.dispatch.ApprovalDecision;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DispatchApprovalRequest(
        @NotNull(message = "审批决定不能为空") ApprovalDecision decision,
        @Size(max = 500, message = "审批意见不能超过500个字符") String comment,
        @Min(value = 1, message = "方案版本必须大于0") long expectedVersion,
        @NotBlank(message = "幂等键不能为空") String idempotencyKey
) {
}
