package cn.fj.roadagent.interfaces.rest.traffic;

import cn.fj.roadagent.domain.traffic.TrafficQueryType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record TrafficQueryRequest(
        @NotNull(message = "交通查询类型不能为空")
        TrafficQueryType queryType,

        @Size(max = 20, message = "出发城市不能超过20个字符")
        String originCity,

        @Size(max = 20, message = "到达城市不能超过20个字符")
        String destinationCity,

        @Size(max = 20, message = "路线编号不能超过20个字符")
        String routeCode,

        @Size(max = 100, message = "路线名称不能超过100个字符")
        String routeName,

        @Size(max = 9, message = "一次最多选择九个城市；区域交通压力仍限定两个城市")
        List<@NotBlank(message = "城市名称不能为空") @Size(max = 20, message = "城市名称不能超过20个字符") String> selectedCities,

        @Size(max = 20, message = "车型分析城市不能超过20个字符")
        String analysisCity
) {
}
