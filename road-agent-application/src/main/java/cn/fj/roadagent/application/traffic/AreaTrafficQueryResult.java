package cn.fj.roadagent.application.traffic;

import cn.fj.roadagent.domain.traffic.AreaTrafficQuery;
import cn.fj.roadagent.domain.traffic.RoadSegmentStatus;
import cn.fj.roadagent.domain.traffic.TrafficCoverage;
import cn.fj.roadagent.domain.traffic.TrafficEvaluation;

import java.time.Instant;
import java.util.List;

public record AreaTrafficQueryResult(
        AreaTrafficQuery query,
        String summary,
        SummarySource summarySource,
        List<RoadSegmentStatus> segments,
        TrafficEvaluation evaluation,
        TrafficCoverage coverage,
        String source,
        Instant acquiredAt,
        Freshness freshness,
        List<String> warnings,
        String traceId
) {
    public AreaTrafficQueryResult {
        segments = segments == null ? List.of() : List.copyOf(segments);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
