package cn.fj.roadagent.domain.traffic;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 区域查询获得的结果顶层汇总
 * @param query AreaTrafficQuery.java 区域查询条件
 * @param segments
 * @param evaluation    TrafficEvaluation.java 区域拥堵情况统计
 * @param coverage      TrafficCoverage.java 区域查询覆盖率计算
 * @param source
 * @param acquiredAt
 * @param description
 * @param warnings
 */
public record AreaTrafficSnapshot(
        AreaTrafficQuery query,
        List<RoadSegmentStatus> segments,
        TrafficEvaluation evaluation,
        TrafficCoverage coverage,
        String source,
        Instant acquiredAt,
        String description,
        List<String> warnings
) {
    public AreaTrafficSnapshot {
        query = Objects.requireNonNull(query, "query不能为空");
        segments = segments == null ? List.of() : List.copyOf(segments);
        evaluation = Objects.requireNonNull(evaluation, "evaluation不能为空");
        coverage = Objects.requireNonNull(coverage, "coverage不能为空");
        source = source == null || source.isBlank() ? "UNKNOWN" : source.trim();
        acquiredAt = Objects.requireNonNull(acquiredAt, "acquiredAt不能为空");
        description = description == null ? "" : description.trim();
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public boolean isEmpty() {
        return segments.isEmpty();
    }
}
