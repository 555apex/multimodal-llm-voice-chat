package cn.fj.roadagent.domain.traffic;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** 与路况快照相互独立的通行能力快照；允许当前批次只包含有数据的活动路线。 */
public record RoadCapacitySnapshot(
        List<RoadCapacity> capacities,
        Instant acquiredAt,
        String fingerprint
) {
    public RoadCapacitySnapshot {
        capacities = capacities == null ? List.of() : List.copyOf(capacities);
        acquiredAt = Objects.requireNonNull(acquiredAt, "快照时间不能为空");
        fingerprint = fingerprint == null ? "" : fingerprint.trim();
    }
}
