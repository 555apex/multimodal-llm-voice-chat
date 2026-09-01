package cn.fj.roadagent.application.port;

import cn.fj.roadagent.domain.traffic.VehicleTravelPatternSnapshot;

import java.util.Optional;

/** 按城市读取最新车型出行特征记录。 */
public interface VehicleTravelPatternPort {
    Optional<VehicleTravelPatternSnapshot> latestForCity(String cityName);
}
