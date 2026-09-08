package cn.fj.roadagent.application.traffic;

public record RegionalPairResultItem(
        String cityARegionCode, String cityAName,
        String cityBRegionCode, String cityBName,
        int routeCount, int checkpointCount,
        long weeklyTotalFlow, long dailyAverageFlow,
        double averageSpeedKmh
) { }
