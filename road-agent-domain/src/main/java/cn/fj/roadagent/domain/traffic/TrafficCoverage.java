package cn.fj.roadagent.domain.traffic;

public record TrafficCoverage(
        int totalTiles,
        int succeededTiles,
        int failedTiles,
        double coverageRatio,
        boolean complete
) {
    public TrafficCoverage {
        if (totalTiles < 1 || succeededTiles < 0 || failedTiles < 0
                || succeededTiles + failedTiles != totalTiles) {
            throw new IllegalArgumentException("区域覆盖统计不正确");
        }
        coverageRatio = Math.max(0.0, Math.min(1.0, coverageRatio));
        complete = failedTiles == 0;
    }

    public static TrafficCoverage of(int total, int succeeded, int failed) {
        return new TrafficCoverage(total, succeeded, failed,
                total == 0 ? 0.0 : (double) succeeded / total, failed == 0);
    }
}
