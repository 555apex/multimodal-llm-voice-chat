package cn.fj.roadagent.application.traffic;

public record RegionalChannelResultItem(
        String cityARegionCode, String cityAName,
        String cityBRegionCode, String cityBName,
        String routeCode, String routeName,
        int checkpointCount, long weeklyTotalFlow,
        long dailyAverageFlow, double averageSpeedKmh
) { }
