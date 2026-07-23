package cn.fj.roadagent.domain.traffic;

/** 高德行政边界和交通数据均使用GCJ-02经纬度。 */
public record GeoPoint(double longitude, double latitude) {
    public GeoPoint {
        if (longitude < 117.0 || longitude > 121.0 || latitude < 23.0 || latitude > 29.0) {
            throw new IllegalArgumentException("坐标不在福建及邻近有效范围内");
        }
    }
}
