package cn.fj.roadagent.domain.traffic;

/** 区域交通压力分析使用的单个卡口事实。 */
public record TransportHub(
        String checkpointNo,
        String routeCode,
        String routeName,
        double averageSpeedKmh,
        String regionCode,
        String regionName,
        long dailyAverageFlow
) {
    public TransportHub {
        checkpointNo = required(checkpointNo, "卡口编号");
        routeCode = required(routeCode, "路线编号");
        routeName = required(routeName, "路线名称");
        regionCode = required(regionCode, "区域代码");
        regionName = required(regionName, "区域名称");
        if (!Double.isFinite(averageSpeedKmh) || averageSpeedKmh < 0) {
            throw new IllegalArgumentException("卡口均速必须是非负有限数值");
        }
        if (dailyAverageFlow < 0) {
            throw new IllegalArgumentException("卡口日均流量不能为负数");
        }
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        return value.trim();
    }
}
