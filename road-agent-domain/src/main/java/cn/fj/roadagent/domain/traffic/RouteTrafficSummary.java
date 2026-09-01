package cn.fj.roadagent.domain.traffic;

import java.util.Objects;

/** w_road_network_status 提供的路线整体权威状态。 */
public record RouteTrafficSummary(
        String routeCode,
        String routeName,
        Double averageSpeedKmh,
        TrafficStatus status
) {
    public RouteTrafficSummary {
        routeCode = requireText(routeCode, "路线编号不能为空").toUpperCase();
        routeName = requireText(routeName, "路线名称不能为空");
        status = Objects.requireNonNull(status, "路线状态不能为空");
        if (averageSpeedKmh == null || !Double.isFinite(averageSpeedKmh) || averageSpeedKmh < 0) {
            throw new IllegalArgumentException("路线均速必须是非负数");
        }
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
