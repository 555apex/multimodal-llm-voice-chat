package cn.fj.roadagent.adapters.dispatch.distance;

import cn.fj.roadagent.application.port.CityDistancePort;
import cn.fj.roadagent.domain.dispatch.GeoPoint;
import org.springframework.stereotype.Component;

/** 基于数据库坐标计算球面直线距离，仅用于调度来源排序。 */
@Component
public final class FujianCityDistanceAdapter implements CityDistancePort {
    private static final double EARTH_RADIUS_KM = 6371.0088;

    @Override
    public double estimatedDistanceKm(GeoPoint origin, GeoPoint destination) {
        if (origin == null || destination == null) {
            throw new IllegalArgumentException("距离计算坐标不能为空");
        }
        double latitudeDelta = Math.toRadians(destination.latitude() - origin.latitude());
        double longitudeDelta = Math.toRadians(destination.longitude() - origin.longitude());
        double a = Math.sin(latitudeDelta / 2) * Math.sin(latitudeDelta / 2)
                + Math.cos(Math.toRadians(origin.latitude()))
                * Math.cos(Math.toRadians(destination.latitude()))
                * Math.sin(longitudeDelta / 2) * Math.sin(longitudeDelta / 2);
        return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
