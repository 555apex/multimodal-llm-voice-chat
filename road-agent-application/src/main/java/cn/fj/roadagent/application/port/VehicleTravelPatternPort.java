package cn.fj.roadagent.application.port;

import cn.fj.roadagent.domain.traffic.VehicleTravelPatternSnapshot;

import java.time.LocalDate;
import java.util.Optional;

/** 按城市和可选记录日期读取车型出行特征。 */
public interface VehicleTravelPatternPort {
    Optional<VehicleTravelPatternSnapshot> latestForCity(String cityName);

    default Optional<VehicleTravelPatternSnapshot> forCityOnDate(String cityName, LocalDate date) {
        return latestForCity(cityName);
    }
}
