package cn.fj.roadagent.domain.traffic;

import java.util.List;

/** 由Java依据全部去重路段计算，模型只能解释这些指标。 */
public record TrafficEvaluation(
        int totalSegments,
        int smoothSegments,
        int slowSegments,
        int congestedSegments,
        int unknownSegments,
        double smoothRatio,
        double slowRatio,
        double congestedRatio,
        double unknownRatio,
        Double averageSpeedKmh
) {
    public static TrafficEvaluation from(List<RoadSegmentStatus> segments) {
        int total = segments.size();
        int smooth = count(segments, CongestionLevel.SMOOTH);
        int slow = count(segments, CongestionLevel.SLOW);
        int congested = count(segments, CongestionLevel.CONGESTED);
        int unknown = count(segments, CongestionLevel.UNKNOWN);
        Double averageSpeed = segments.stream()
                .map(RoadSegmentStatus::averageSpeedKmh)
                .filter(speed -> speed != null)
                .mapToDouble(Double::doubleValue)
                .average()
                .stream().boxed().findFirst().orElse(null);
        return new TrafficEvaluation(
                total, smooth, slow, congested, unknown,
                ratio(smooth, total), ratio(slow, total), ratio(congested, total), ratio(unknown, total),
                averageSpeed
        );
    }

    private static int count(List<RoadSegmentStatus> segments, CongestionLevel level) {
        return (int) segments.stream().filter(segment -> segment.congestionLevel() == level).count();
    }

    private static double ratio(int value, int total) {
        return total == 0 ? 0.0 : (double) value / total;
    }
}
