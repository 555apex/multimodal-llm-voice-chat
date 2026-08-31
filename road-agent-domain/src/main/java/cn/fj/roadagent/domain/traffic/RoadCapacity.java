package cn.fj.roadagent.domain.traffic;

/** w_road_capacity 中由协作者计算并维护的一条路线最新通行能力结果。 */
public record RoadCapacity(
        String routeCode,
        String routeName,
        double actualCapacityVph,
        double designCapacityVph,
        double utilizationRatio
) {
    public RoadCapacity {
        routeCode = requireText(routeCode, "路线编号不能为空").toUpperCase();
        routeName = requireText(routeName, "路线名称不能为空");
        requireNonNegative(actualCapacityVph, "实际通行能力");
        requireNonNegative(designCapacityVph, "设计通行能力");
        CapacityLevel.fromUtilization(utilizationRatio);
    }

    public CapacityLevel level() {
        return CapacityLevel.fromUtilization(utilizationRatio);
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static void requireNonNegative(double value, String label) {
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException(label + "必须是非负数");
        }
    }
}
