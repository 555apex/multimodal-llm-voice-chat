package cn.fj.roadagent.interfaces.rest.traffic;

import cn.fj.roadagent.domain.traffic.CongestionLevel;

public record TrafficSegmentResponse(
        String roadName,
        String direction,
        CongestionLevel congestionLevel,
        Double averageSpeedKmh,
        String polyline
) {
}
