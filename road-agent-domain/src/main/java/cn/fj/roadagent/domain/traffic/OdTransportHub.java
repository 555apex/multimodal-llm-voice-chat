package cn.fj.roadagent.domain.traffic;

/** 所选城市的卡口统计；车型字段仅在通道查询时加载。不是车辆轨迹或城市对流量。 */
public record OdTransportHub(
        String checkpointNo, String regionCode, String regionName,
        String routeCode, String routeName, long weeklyTotalFlow,
        long dailyAverageFlow, double averageSpeedKmh,
        Long carWeeklyFlow, Long busWeeklyFlow, Long truckWeeklyFlow
) { }
