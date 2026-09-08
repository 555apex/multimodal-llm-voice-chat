package cn.fj.roadagent.domain.dispatch;

/** WGS84经纬度坐标；经度在前、纬度在后。 */
public record GeoPoint(double longitude, double latitude) {
    public GeoPoint {
        if (!Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("经度必须在-180到180之间");
        }
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("纬度必须在-90到90之间");
        }
    }
}
