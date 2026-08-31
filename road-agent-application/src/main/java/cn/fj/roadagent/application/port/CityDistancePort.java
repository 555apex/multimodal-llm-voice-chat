package cn.fj.roadagent.application.port;

/** 城市级调度距离，仅用于资源来源排序。 */
public interface CityDistancePort {
    double estimatedDistanceKm(String originCityCode, String destinationCityCode);
}
