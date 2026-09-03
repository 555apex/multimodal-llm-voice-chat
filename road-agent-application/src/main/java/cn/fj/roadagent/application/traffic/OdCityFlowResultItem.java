package cn.fj.roadagent.application.traffic;

public record OdCityFlowResultItem(
        String regionCode, String regionName, int checkpointCount,
        long weeklyTotalFlow, long dailyAverageFlow, double averageSpeedKmh
) { }
