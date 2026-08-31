package cn.fj.roadagent.application.traffic;

import cn.fj.roadagent.domain.traffic.HighwayTrafficSegment;
import cn.fj.roadagent.domain.traffic.RouteTrafficSummary;
import cn.fj.roadagent.domain.traffic.TrafficQueryType;

import java.time.Instant;
import java.util.List;

/** 已由 Java 完成筛选、排序和截断，可安全交给模型总结的事实。 */
public record HighwayTrafficFacts(
        TrafficQueryType queryType,
        String title,
        List<RouteTrafficSummary> routeSummaries,
        List<HighwayTrafficSegment> segments,
        List<HighwayTrafficSegment> forecastSegments,
        int totalSegmentCount,
        boolean truncated,
        Instant acquiredAt,
        List<String> warnings
) {
    public HighwayTrafficFacts {
        routeSummaries = routeSummaries == null ? List.of() : List.copyOf(routeSummaries);
        segments = segments == null ? List.of() : List.copyOf(segments);
        forecastSegments = forecastSegments == null ? List.of() : List.copyOf(forecastSegments);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
