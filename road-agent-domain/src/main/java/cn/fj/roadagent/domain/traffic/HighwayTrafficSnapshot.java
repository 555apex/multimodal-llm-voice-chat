package cn.fj.roadagent.domain.traffic;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** 一次通过引用完整性和稳定性校验后原子发布的不可变交通快照。业务表允许只覆盖部分活动路线。 */
public record HighwayTrafficSnapshot(
        List<HighwayRoute> routes,
        List<RouteTrafficSummary> routeSummaries,
        List<HighwayTrafficSegment> segments,
        Instant acquiredAt,
        String fingerprint
) {
    public HighwayTrafficSnapshot {
        routes = routes == null ? List.of() : List.copyOf(routes);
        routeSummaries = routeSummaries == null ? List.of() : List.copyOf(routeSummaries);
        segments = segments == null ? List.of() : List.copyOf(segments);
        acquiredAt = Objects.requireNonNull(acquiredAt, "快照时间不能为空");
        fingerprint = fingerprint == null ? "" : fingerprint.trim();
        if (routes.isEmpty()) {
            throw new IllegalArgumentException("交通快照必须包含活动国省道路网");
        }
    }
}
