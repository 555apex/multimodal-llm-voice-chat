package cn.fj.roadagent.application.traffic;

public record TransportHubResultItem(
        String checkpointNo,
        String routeCode,
        String routeName,
        double averageSpeedKmh,
        long dailyAverageFlow
) {
}
