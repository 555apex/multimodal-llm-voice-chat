package cn.fj.roadagent.interfaces.rest.workflow;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResourceReleaseRequest(
        @NotBlank(message = "资源归还原因不能为空")
        @Size(max = 500, message = "资源归还原因不能超过500个字符") String reason,
        @Min(value = 0, message = "工作流版本不能小于0") long expectedWorkflowVersion,
        @NotBlank(message = "幂等键不能为空")
        @Size(max = 100, message = "幂等键不能超过100个字符") String idempotencyKey
) {
}
