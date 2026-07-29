package cn.fj.roadagent.domain.traffic;

import java.util.Objects;

/**
 * 单条路段状态描述（查询后返回的结果输出）
 * 具体：描述查询所得到的一段道路的标准路况。平均速度允许为空，因为部分数据源不提供该字段。
 */
public record RoadSegmentStatus(
        String roadName,    // 道路名称
        String direction,   // 道路方向
        CongestionLevel congestionLevel,    // 拥堵等级
        Double averageSpeedKmh, // 平均车速
        String polyline // 路段坐标串（用于地图定位、渲染）
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
