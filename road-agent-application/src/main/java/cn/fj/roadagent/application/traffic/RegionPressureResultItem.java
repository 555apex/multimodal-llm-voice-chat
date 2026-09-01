package cn.fj.roadagent.application.traffic;

public record RegionPressureResultItem(
        String regionCode,
        String regionName,
        int activeHubCount,
        long totalDailyFlow,
        double hubShareRatio,
        String interpretation
) {
    public RegionPressureResultItem withInterpretation(String value) {
        return new RegionPressureResultItem(
                regionCode, regionName, activeHubCount, totalDailyFlow, hubShareRatio, value
        );
    }
}
