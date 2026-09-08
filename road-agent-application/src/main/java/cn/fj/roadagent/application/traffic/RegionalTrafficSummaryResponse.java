package cn.fj.roadagent.application.traffic;

/** 区域交通联系模型摘要。 */
public record RegionalTrafficSummaryResponse(String summary) {
    public RegionalTrafficSummaryResponse {
        summary = new TrafficSummaryResponse(summary).summary();
    }
}
