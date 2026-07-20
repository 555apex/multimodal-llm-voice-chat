package cn.fj.roadagent.application.traffic;

import cn.fj.roadagent.domain.traffic.RoadSegmentStatus;
import cn.fj.roadagent.domain.traffic.TrafficQuery;

import java.time.Instant;
import java.util.List;

public record TrafficQueryResult(
        TrafficQuery query,
        String summary,
        SummarySource summarySource,
        List<RoadSegmentStatus> segments,
        String source,
        Instant acquiredAt,
        Freshness freshness,
        boolean mock,
        List<String> warnings,
        String traceId
) {
    public TrafficQueryResult {
        segments = segments == null ? List.of() : List.copyOf(segments);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
