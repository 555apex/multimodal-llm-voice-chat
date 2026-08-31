package cn.fj.roadagent.domain.traffic;

import java.util.Objects;

/** w_congestion_detection_result 中按交调站划分的路段状态。 */
public record HighwayTrafficSegment(
        String routeCode,
        String routeName,
        String routeSection,
        Double distanceKm,
        Double averageSpeedKmh,
        TrafficStatus status,
        Double severity
) {
    public HighwayTrafficSegment {
        routeCode = requireText(routeCode, "路段路线编号不能为空").toUpperCase();
        routeName = requireText(routeName, "路段路线名称不能为空");
        routeSection = requireText(routeSection, "路段定位不能为空");
        status = Objects.requireNonNull(status, "路段状态不能为空");
        requireOptionalNonNegative(distanceKm, "路段距离");
        requireNonNegative(averageSpeedKmh, "路段均速");
        if (severity == null || !Double.isFinite(severity) || severity < 0 || severity > 1) {
            throw new IllegalArgumentException("拥堵指数必须在0到1之间");
        }
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static void requireNonNegative(Double value, String label) {
        if (value == null || !Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException(label + "必须是非负数");
        }
    }

    private static void requireOptionalNonNegative(Double value, String label) {
        if (value != null && (!Double.isFinite(value) || value < 0)) {
            throw new IllegalArgumentException(label + "有值时必须是非负数");
        }
    }
}
