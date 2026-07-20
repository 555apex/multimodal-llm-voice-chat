package cn.fj.roadagent.core.traffic;

import cn.fj.roadagent.domain.traffic.CongestionLevel;
import cn.fj.roadagent.domain.traffic.RoadSegmentStatus;
import cn.fj.roadagent.domain.traffic.TrafficSnapshot;

import java.util.Comparator;

public final class RuleBasedTrafficSummaryGenerator {

    public String generate(TrafficSnapshot snapshot) {
        if (snapshot.isEmpty()) {
            return "暂未查询到“%s”的实时路况数据，请核对行政区划代码和道路名称。"
                    .formatted(snapshot.query().roadName());
        }

        CongestionLevel worst = snapshot.worstCongestionLevel();
        RoadSegmentStatus slowest = snapshot.segments().stream()
                .filter(segment -> segment.averageSpeedKmh() != null)
                .min(Comparator.comparing(RoadSegmentStatus::averageSpeedKmh))
                .orElse(null);

        String speedText = slowest == null
                ? "数据源未提供平均速度"
                : "最低平均速度约%.0f公里/小时".formatted(slowest.averageSpeedKmh());

        return "“%s”共返回%d段路况，整体最严重状态为%s，%s。"
                .formatted(snapshot.query().roadName(), snapshot.segments().size(),
                        toChinese(worst), speedText);
    }

    private String toChinese(CongestionLevel level) {
        return switch (level) {
            case SMOOTH -> "畅通";
            case SLOW -> "缓行";
            case CONGESTED -> "拥堵";
            case UNKNOWN -> "未知";
        };
    }
}
