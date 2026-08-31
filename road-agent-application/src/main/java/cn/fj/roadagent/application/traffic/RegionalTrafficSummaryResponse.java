package cn.fj.roadagent.application.traffic;

import java.util.List;

/** 区域交通模型摘要和城市表逐行解读。 */
public record RegionalTrafficSummaryResponse(String summary, List<RegionInsight> regionInsights) {
    public RegionalTrafficSummaryResponse {
        summary = new TrafficSummaryResponse(summary).summary();
        regionInsights = regionInsights == null ? List.of() : List.copyOf(regionInsights);
    }
}
