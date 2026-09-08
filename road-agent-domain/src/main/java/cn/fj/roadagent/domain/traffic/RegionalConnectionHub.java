package cn.fj.roadagent.domain.traffic;

/** 由路网起终点城市和同路线卡口组合得到的跨城市交通联系事实。 */
public record RegionalConnectionHub(
        String checkpointNo,
        String checkpointName,
        String routeCode,
        String routeName,
        String cityARegionCode,
        String cityAName,
        String cityBRegionCode,
        String cityBName,
        Double stakeKm,
        double averageSpeedKmh,
        long weeklyTotalFlow,
        long dailyAverageFlow
) {
    public RegionalConnectionHub {
        checkpointNo = required(checkpointNo, "卡口编号");
        checkpointName = optional(checkpointName);
        routeCode = required(routeCode, "路线编号");
        routeName = required(routeName, "路线名称");
        cityARegionCode = required(cityARegionCode, "城市A代码");
        cityAName = required(cityAName, "城市A名称");
        cityBRegionCode = required(cityBRegionCode, "城市B代码");
        cityBName = required(cityBName, "城市B名称");
        if (cityARegionCode.equals(cityBRegionCode)) throw new IllegalArgumentException("跨市路线两端城市不能相同");
        if (stakeKm != null && (!Double.isFinite(stakeKm) || stakeKm < 0)) throw new IllegalArgumentException("桩号必须是非负有限数值");
        if (!Double.isFinite(averageSpeedKmh) || averageSpeedKmh < 0) throw new IllegalArgumentException("卡口均速必须是非负有限数值");
        if (weeklyTotalFlow < 0 || dailyAverageFlow < 0) throw new IllegalArgumentException("卡口流量不能为负数");
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + "不能为空");
        return value.trim();
    }

    private static String optional(String value) {
        return value == null ? "" : value.trim();
    }
}
