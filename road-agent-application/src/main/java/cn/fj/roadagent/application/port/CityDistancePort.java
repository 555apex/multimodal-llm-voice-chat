package cn.fj.roadagent.application.port;

import cn.fj.roadagent.domain.dispatch.GeoPoint;

/** 城市级调度距离，仅用于资源来源排序。 */
public interface CityDistancePort {
    double estimatedDistanceKm(GeoPoint origin, GeoPoint destination);
}
