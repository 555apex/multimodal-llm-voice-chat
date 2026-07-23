package cn.fj.roadagent.interfaces.rest.traffic;

import cn.fj.roadagent.application.traffic.AreaTrafficQueryResult;
import cn.fj.roadagent.application.traffic.Freshness;
import cn.fj.roadagent.application.traffic.SummarySource;
import cn.fj.roadagent.domain.traffic.TrafficCoverage;
import cn.fj.roadagent.domain.traffic.TrafficEvaluation;
import cn.fj.roadagent.domain.traffic.TrafficQueryScope;

import java.time.Instant;
import java.util.List;

public record AreaTrafficResponse(
        TrafficQueryScope queryScope,
        String areaCode,
        String areaName,
        String city,
        String summary,
        SummarySource summarySource,
        TrafficEvaluation evaluation,
        TrafficCoverage coverage,
        List<TrafficSegmentResponse> segments,
        String source,
        Instant acquiredAt,
        Freshness freshness,
        List<String> warnings
) {
    public static AreaTrafficResponse from(AreaTrafficQueryResult result) {
        List<TrafficSegmentResponse> segments = result.segments().stream()
                .map(segment -> new TrafficSegmentResponse(
                        segment.roadName(), segment.direction(), segment.congestionLevel(),
                        segment.averageSpeedKmh(), segment.polyline()
                )).toList();
        return new AreaTrafficResponse(
                result.query().scope(), result.query().area().adcode(), result.query().area().name(),
                result.query().area().city(), result.summary(), result.summarySource(),
                result.evaluation(), result.coverage(), segments, result.source(), result.acquiredAt(),
                result.freshness(), result.warnings()
        );
    }
}
