package cn.fj.roadagent.application.traffic;

import cn.fj.roadagent.domain.traffic.TrafficQueryScope;

public record AreaTrafficQueryCommand(
        String city,
        String areaName,
        TrafficQueryScope scope,
        String traceId
) {
}
