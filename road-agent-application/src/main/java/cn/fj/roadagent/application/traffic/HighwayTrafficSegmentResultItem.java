package cn.fj.roadagent.application.traffic;

import cn.fj.roadagent.domain.traffic.HighwayTrafficSegment;

/** 对外保留数据库原生状态编码的路段项。 */
public record HighwayTrafficSegmentResultItem(
        String routeCode,
        String routeName,
        String routeSection,
        Double distanceKm,
        Double averageSpeedKmh,
        int status,
        String statusName,
        Double severity
) {
    public static HighwayTrafficSegmentResultItem from(HighwayTrafficSegment source) {
        return new HighwayTrafficSegmentResultItem(
                source.routeCode(), source.routeName(), source.routeSection(), source.distanceKm(),
                source.averageSpeedKmh(), source.status().code(), source.status().displayName(), source.severity()
        );
    }
}
