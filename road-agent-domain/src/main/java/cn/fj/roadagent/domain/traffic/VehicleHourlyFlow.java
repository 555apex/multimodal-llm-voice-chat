package cn.fj.roadagent.domain.traffic;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;

/** 数据库result2中的一个小时分车型通行量。 */
public record VehicleHourlyFlow(LocalDateTime hour, Map<VehicleType, Long> volumes) {
    public VehicleHourlyFlow {
        if (hour == null) {
            throw new IllegalArgumentException("小时不能为空");
        }
        EnumMap<VehicleType, Long> copy = new EnumMap<>(VehicleType.class);
        for (VehicleType type : VehicleType.values()) {
            long value = volumes == null ? 0L : volumes.getOrDefault(type, 0L);
            if (value < 0) {
                throw new IllegalArgumentException("小时车型通行量不能为负数");
            }
            copy.put(type, value);
        }
        volumes = Map.copyOf(copy);
    }

    public long volume(VehicleType type) {
        return volumes.getOrDefault(type, 0L);
    }
}
