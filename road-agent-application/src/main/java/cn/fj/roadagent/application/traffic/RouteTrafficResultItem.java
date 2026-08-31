package cn.fj.roadagent.application.traffic;

import cn.fj.roadagent.domain.traffic.RouteTrafficSummary;

/** 对外保留数据库原生状态编码的路线总览项。 */
public record RouteTrafficResultItem(
        String routeCode,
        String routeName,
        Double averageSpeedKmh,
        int status,
        String statusName
) {
    public static RouteTrafficResultItem from(RouteTrafficSummary source) {
        return new RouteTrafficResultItem(
                source.routeCode(), source.routeName(), source.averageSpeedKmh(),
                source.status().code(), source.status().displayName()
        );
    }
}
