package cn.fj.roadagent.application.agent;

import cn.fj.roadagent.application.traffic.Freshness;
import cn.fj.roadagent.application.traffic.AreaTrafficQueryResult;
import cn.fj.roadagent.application.traffic.SummarySource;
import cn.fj.roadagent.application.traffic.TrafficQueryResult;
import cn.fj.roadagent.domain.traffic.RoadSegmentStatus;
import cn.fj.roadagent.domain.traffic.TrafficCoverage;
import cn.fj.roadagent.domain.traffic.TrafficEvaluation;
import cn.fj.roadagent.domain.traffic.TrafficQueryScope;

import java.time.Instant;
import java.util.List;

/** 对话事件使用的扁平交通结果，避免前端了解内部TrafficQuery对象。 */
public record TrafficAgentResult(
        TrafficQueryScope queryScope,
        String areaCode,
        String areaName,
        String roadName,
        String direction,
        String summary,
        SummarySource summarySource,
        List<RoadSegmentStatus> segments,
        TrafficEvaluation evaluation,
        TrafficCoverage coverage,
        String source,
        Instant acquiredAt,
        Freshness freshness,
        boolean mock,
        List<String> warnings,
        String traceId
) {
    public static TrafficAgentResult from(TrafficQueryResult result) {
        return new TrafficAgentResult(
                TrafficQueryScope.ROAD, result.query().areaCode(), null,
                result.query().roadName(), result.query().direction(),
                result.summary(), result.summarySource(), result.segments(),
                TrafficEvaluation.from(result.segments()), TrafficCoverage.of(1, 1, 0), result.source(),
                result.acquiredAt(), result.freshness(), result.mock(), result.warnings(), result.traceId()
        );
    }

    public static TrafficAgentResult from(AreaTrafficQueryResult result) {
        return new TrafficAgentResult(
                result.query().scope(), result.query().area().adcode(), result.query().area().name(),
                null, null, result.summary(), result.summarySource(), result.segments(),
                result.evaluation(), result.coverage(), result.source(), result.acquiredAt(),
                result.freshness(), false, result.warnings(), result.traceId()
        );
    }
}
