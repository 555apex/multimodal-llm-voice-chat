package cn.fj.roadagent.interfaces.rest.traffic;

import cn.fj.roadagent.application.traffic.Freshness;
import cn.fj.roadagent.application.traffic.SummarySource;
import cn.fj.roadagent.application.traffic.TrafficQueryResult;

import java.time.Instant;
import java.util.List;

public record TrafficQueryResponse(
        String areaCode,
        String roadName,
        String direction,
        String summary,
        SummarySource summarySource,
        List<TrafficSegmentResponse> segments,
        String source,
        Instant acquiredAt,
        Freshness freshness,
        boolean mock,
        List<String> warnings
) {
    public static TrafficQueryResponse from(TrafficQueryResult result) {
        List<TrafficSegmentResponse> segments = result.segments().stream()
                .map(segment -> new TrafficSegmentResponse(
                        segment.roadName(),
                        segment.direction(),
                        segment.congestionLevel(),
                        segment.averageSpeedKmh(),
                        segment.polyline()
                ))
                .toList();

        return new TrafficQueryResponse(
                result.query().areaCode(),
                result.query().roadName(),
                result.query().direction(),
                result.summary(),
                result.summarySource(),
                segments,
                result.source(),
                result.acquiredAt(),
                result.freshness(),
                result.mock(),
                result.warnings()
        );
    }
}
