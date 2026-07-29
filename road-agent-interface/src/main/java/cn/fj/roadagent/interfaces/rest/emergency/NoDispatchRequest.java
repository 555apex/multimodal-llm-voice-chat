package cn.fj.roadagent.interfaces.rest.emergency;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NoDispatchRequest(
        @NotBlank(message = "无需调度原因不能为空")
        @Size(max = 500, message = "无需调度原因不能超过500个字符")
        String reason,
        @AssertTrue(message = "必须二次确认无需生成调度工单")
        boolean confirmed
) {
}
