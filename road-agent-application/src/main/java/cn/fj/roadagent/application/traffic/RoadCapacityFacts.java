package cn.fj.roadagent.application.traffic;

import cn.fj.roadagent.domain.traffic.RoadCapacity;
import cn.fj.roadagent.domain.traffic.TrafficQueryType;

import java.time.Instant;
import java.util.List;

/** Java 已完成分类、统计、排序与截断的通行能力事实。 */
public record RoadCapacityFacts(
        TrafficQueryType queryType,
        String title,
        List<RoadCapacity> rows,
        int totalCount,
        int normalCount,
        int bottleneckCount,
        int severeBottleneckCount,
        boolean truncated,
        Instant acquiredAt,
        List<String> warnings
) {
    public RoadCapacityFacts {
        rows = rows == null ? List.of() : List.copyOf(rows);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
