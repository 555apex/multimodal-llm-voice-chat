package cn.fj.roadagent.domain.traffic;

import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** 某城市按create_time选出的最新车型出行特征记录。 */
public record VehicleTravelPatternSnapshot(
        String cityName,
        Map<VehicleType, Long> weeklyVolumes,
        List<VehicleHourlyFlow> hourlyFlows,
        Map<VehicleType, Long> weekendVolumes,
        LocalDate dataDate,
        Instant acquiredAt
) {
    public VehicleTravelPatternSnapshot {
        if (cityName == null || cityName.isBlank() || dataDate == null || acquiredAt == null) {
            throw new IllegalArgumentException("车型分析记录的城市和时间不能为空");
        }
        cityName = cityName.trim();
        weeklyVolumes = immutableVolumes(weeklyVolumes, "周通行量");
        weekendVolumes = immutableVolumes(weekendVolumes, "周末通行量");
        for (VehicleType type : VehicleType.values()) {
            if (weekendVolumes.get(type) > weeklyVolumes.get(type)) {
                throw new IllegalArgumentException(type.displayName() + "周末通行量不能大于周通行量");
            }
        }
        hourlyFlows = hourlyFlows == null ? List.of() : List.copyOf(hourlyFlows);
    }

    public long weeklyVolume(VehicleType type) {
        return weeklyVolumes.get(type);
    }

    public long weekendVolume(VehicleType type) {
        return weekendVolumes.get(type);
    }

    private static Map<VehicleType, Long> immutableVolumes(Map<VehicleType, Long> source, String label) {
        EnumMap<VehicleType, Long> copy = new EnumMap<>(VehicleType.class);
        for (VehicleType type : VehicleType.values()) {
            if (source == null || !source.containsKey(type) || source.get(type) == null) {
                throw new IllegalArgumentException(label + "缺少" + type.displayName());
            }
            long value = source.get(type);
            if (value < 0) {
                throw new IllegalArgumentException(label + "不能为负数");
            }
            copy.put(type, value);
        }
        return Map.copyOf(copy);
    }
}
