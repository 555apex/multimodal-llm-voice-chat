package cn.fj.roadagent.interfaces.rest.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AgentMessageRequest(
        // 仅有文本信息
        @NotBlank(message = "消息不能为空")
        @Size(max = 1000, message = "消息不能超过1000个字符")
        String message
) {
}
