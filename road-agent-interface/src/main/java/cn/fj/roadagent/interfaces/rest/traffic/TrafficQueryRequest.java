package cn.fj.roadagent.interfaces.rest.traffic;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TrafficQueryRequest(
        @NotBlank(message = "行政区划代码不能为空")
        @Pattern(regexp = "\\d{6}", message = "行政区划代码必须是6位数字")
        String areaCode,

        @NotBlank(message = "道路名称不能为空")
        @Size(max = 100, message = "道路名称不能超过100个字符")
        String roadName,

        @Size(max = 50, message = "方向描述不能超过50个字符")
        String direction
) {
}
