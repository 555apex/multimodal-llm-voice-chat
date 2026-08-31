package cn.fj.roadagent.application.traffic;

public record RoutePressureResultItem(
        String routeCode,
        String routeName,
        long totalDailyFlow,
        int checkpointCount,
        double averageSpeedKmh
) {
}
