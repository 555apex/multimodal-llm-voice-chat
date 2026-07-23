package cn.fj.roadagent.domain.traffic;

import java.util.List;

/** 一个行政区可能由多个不连续多边形组成，例如岛屿区域。 */
public record GeoBoundary(List<List<GeoPoint>> polygons) {
    public GeoBoundary {
        if (polygons == null || polygons.isEmpty()) {
            throw new IllegalArgumentException("行政区边界不能为空");
        }
        polygons = polygons.stream().map(points -> {
            if (points == null || points.size() < 3) {
                throw new IllegalArgumentException("行政区多边形至少需要3个坐标点");
            }
            return List.copyOf(points);
        }).toList();
    }
}
