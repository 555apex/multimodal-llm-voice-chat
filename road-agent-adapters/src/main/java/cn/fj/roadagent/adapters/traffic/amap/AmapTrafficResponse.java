package cn.fj.roadagent.adapters.traffic.amap;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record AmapTrafficResponse(
        String status,  // 状态位，"1"为成功
        String info,
        String infocode,
        @JsonProperty("trafficinfo")
        TrafficInfo trafficInfo // JSON字段名和java字段名不能同时使用这个映射，即JSON中的trafficinfo和java的trafficInfo是一个东西
) {
    //高德返回的JSON如果有我们不关心的字段（比如count、suggestion），Jackson会自动忽略，不报错
    @JsonIgnoreProperties(ignoreUnknown = true)

    record TrafficInfo(String description, List<Road> roads) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Road(
            String name,
            String status,
            String direction,
            String speed,
            String polyline
    ) {
    }
}
