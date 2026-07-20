package cn.fj.roadagent.domain.traffic;

import java.util.Objects;

/**
 * 一段道路的标准路况。平均速度允许为空，因为部分数据源不提供该字段。
 */
public record RoadSegmentStatus(
        String roadName,
        String direction,
        CongestionLevel congestionLevel,
        Double averageSpeedKmh,
        String polyline
) {
    public RoadSegmentStatus {
        roadName = roadName == null ? "未知道路" : roadName.trim();
        direction = direction == null || direction.isBlank() ? "方向未知" : direction.trim();
        congestionLevel = Objects.requireNonNullElse(congestionLevel, CongestionLevel.UNKNOWN);
        polyline = polyline == null || polyline.isBlank() ? null : polyline.trim();

        if (averageSpeedKmh != null && averageSpeedKmh < 0) {
            throw new IllegalArgumentException("平均速度不能为负数");
        }
    }
}
