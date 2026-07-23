package cn.fj.roadagent.interfaces.rest.traffic;

import cn.fj.roadagent.domain.traffic.TrafficQueryScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AreaTrafficQueryRequest(
        @NotBlank(message = "城市不能为空")
        @Size(max = 30, message = "城市名称不能超过30个字符")
        String city,
        @NotBlank(message = "行政区名称不能为空")
        @Size(max = 50, message = "行政区名称不能超过50个字符")
        String areaName,
        @NotNull(message = "区域查询范围不能为空")
        TrafficQueryScope scope
) {
}
