package cn.fj.roadagent.adapters.dispatch.distance;

import cn.fj.roadagent.application.port.CityDistancePort;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 福建九市城市中心直线估算距离，仅用于调度来源排序。 */
@Component
public final class FujianCityDistanceAdapter implements CityDistancePort {
    private static final double EARTH_RADIUS_KM = 6371.0088;
    private static final Map<String, Point> CITY_CENTERS = Map.of(
            "350100", new Point(26.0745, 119.2965),
            "350200", new Point(24.4798, 118.0894),
            "350300", new Point(25.4541, 119.0077),
            "350400", new Point(26.2634, 117.6389),
            "350500", new Point(24.8741, 118.6757),
            "350600", new Point(24.5130, 117.6471),
            "350700", new Point(26.6418, 118.1777),
            "350800", new Point(25.0751, 117.0175),
            "350900", new Point(26.6656, 119.5482)
    );

    @Override
    public double estimatedDistanceKm(String originCityCode, String destinationCityCode) {
        if (originCityCode != null && originCityCode.equals(destinationCityCode)) return 0;
        Point origin = requireCity(originCityCode);
        Point destination = requireCity(destinationCityCode);
        double latitudeDelta = Math.toRadians(destination.latitude - origin.latitude);
        double longitudeDelta = Math.toRadians(destination.longitude - origin.longitude);
        double a = Math.sin(latitudeDelta / 2) * Math.sin(latitudeDelta / 2)
                + Math.cos(Math.toRadians(origin.latitude))
                * Math.cos(Math.toRadians(destination.latitude))
                * Math.sin(longitudeDelta / 2) * Math.sin(longitudeDelta / 2);
        return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private Point requireCity(String cityCode) {
        Point point = CITY_CENTERS.get(cityCode);
        if (point == null) throw new IllegalArgumentException("不支持的福建城市编码：" + cityCode);
        return point;
    }

    private record Point(double latitude, double longitude) {
    }
}
