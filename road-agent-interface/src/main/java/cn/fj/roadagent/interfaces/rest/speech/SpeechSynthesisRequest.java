package cn.fj.roadagent.interfaces.rest.speech;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SpeechSynthesisRequest(
        @NotBlank(message = "朗读文本不能为空")
        @Size(max = 500, message = "单个朗读片段不能超过500个字符")
        String text
) {
}
