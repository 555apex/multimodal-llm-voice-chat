package cn.fj.roadagent.core.traffic;

import cn.fj.roadagent.application.traffic.Freshness;
import cn.fj.roadagent.domain.traffic.AreaTrafficSnapshot;
import cn.fj.roadagent.domain.traffic.CongestionLevel;
import cn.fj.roadagent.domain.traffic.RoadSegmentStatus;
import cn.fj.roadagent.domain.traffic.TrafficSnapshot;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 仅依据结构化路况事实生成回答，不允许模型补充道路或路段信息。 */
final class TrafficAnswerComposer {
    private static final int MAX_FOCUS_SEGMENTS = 5;
    private static final int MAX_SPEECH_FOCUS_SEGMENTS = 3;
    private static final Comparator<RoadSegmentStatus> FOCUS_ORDER =
            Comparator.<RoadSegmentStatus>comparingInt(
                            segment -> segment.congestionLevel().severity()
                    ).reversed()
                    .thenComparing(
                            RoadSegmentStatus::averageSpeedKmh,
                            Comparator.nullsLast(Comparator.naturalOrder())
                    )
                    .thenComparing(RoadSegmentStatus::roadName)
                    .thenComparing(RoadSegmentStatus::direction);

    String compose(TrafficSnapshot snapshot, Freshness freshness) {
        String roadName = snapshot.query().roadName();
        if (snapshot.segments().isEmpty()) {
            return "暂未获取到%s的有效路况信息，当前无法判断通行状态。".formatted(roadName);
        }

        TrafficFacts facts = facts(snapshot.segments());
        StringBuilder answer = new StringBuilder(roadConclusion(roadName, facts, freshness));
        appendFocusSegments(answer, facts.focusSegments());
        appendAdvice(answer, facts, freshness);
        appendAllSegments(answer, facts.allSegments());
        return answer.toString();
    }

    String compose(AreaTrafficSnapshot snapshot, Freshness freshness) {
        String areaName = snapshot.query().area().name();
        if (snapshot.segments().isEmpty()) {
            return "暂未获取到%s的有效路况信息，当前无法判断整体通行情况。".formatted(areaName);
        }

        TrafficFacts facts = facts(snapshot.segments());
        boolean qualifiedScope = !snapshot.coverage().complete() || freshness != Freshness.FRESH;
        StringBuilder answer = new StringBuilder(
                areaConclusion(areaName, facts, qualifiedScope)
        );
        appendFocusSegments(answer, facts.focusSegments());
        appendAdvice(answer, facts, freshness);
        appendAllSegments(answer, facts.allSegments());
        return answer.toString();
    }

    String composeSpeech(TrafficSnapshot snapshot, Freshness freshness) {
        String roadName = snapshot.query().roadName();
        if (snapshot.segments().isEmpty()) {
            return "暂未获取到%s的有效路况信息，当前无法判断通行状态。".formatted(roadName);
        }
        TrafficFacts facts = facts(snapshot.segments());
        StringBuilder speech = new StringBuilder(roadConclusion(roadName, facts, freshness));
        appendFocusSegments(
                speech,
                facts.focusSegments().stream().limit(MAX_SPEECH_FOCUS_SEGMENTS).toList()
        );
        appendAdvice(speech, facts, freshness);
        speech.append(" 详细数据请查看页面。");
        return speech.toString();
    }

    String composeSpeech(AreaTrafficSnapshot snapshot, Freshness freshness) {
        String areaName = snapshot.query().area().name();
        if (snapshot.segments().isEmpty()) {
            return "暂未获取到%s的有效路况信息，当前无法判断整体通行情况。".formatted(areaName);
        }
        TrafficFacts facts = facts(snapshot.segments());
        boolean qualifiedScope = !snapshot.coverage().complete() || freshness != Freshness.FRESH;
        StringBuilder speech = new StringBuilder(areaConclusion(areaName, facts, qualifiedScope));
        appendFocusSegments(
                speech,
                facts.focusSegments().stream().limit(MAX_SPEECH_FOCUS_SEGMENTS).toList()
        );
        appendAdvice(speech, facts, freshness);
        speech.append(" 详细数据请查看页面。");
        return speech.toString();
    }

    private String roadConclusion(String roadName, TrafficFacts facts, Freshness freshness) {
        String scope = freshness == Freshness.FRESH
                ? roadName + "当前"
                : roadName + "现有路况中";
        if (facts.hasCongested()) {
            return scope + (facts.hasSlow()
                    ? "存在拥堵路段，并伴有部分路段缓行。"
                    : "存在拥堵路段。");
        }
        if (facts.hasSlow()) {
            return scope + "未发现拥堵，但部分路段通行缓慢。";
        }
        if (facts.hasSmooth() && facts.hasUnknown()) {
            return scope + "未发现明确的拥堵或缓行，但部分路段状态尚不明确。";
        }
        if (facts.hasSmooth()) {
            return scope + "通行总体正常，未发现拥堵或缓行路段。";
        }
        return scope + "返回的路段状态均不明确，暂无法判断通行情况。";
    }

    private String areaConclusion(String areaName, TrafficFacts facts, boolean qualifiedScope) {
        String scope = qualifiedScope
                ? areaName + "当前已获取的路况中"
                : areaName + "当前";
        if (facts.hasCongested()) {
            return scope + (facts.hasSlow()
                    ? "存在拥堵路段，并伴有局部缓行。"
                    : "存在拥堵路段。");
        }
        if (facts.hasSlow()) {
            return scope + "未发现拥堵，但部分路段通行缓慢。";
        }
        if (facts.hasSmooth() && facts.hasUnknown()) {
            return scope + "未发现明确的拥堵或缓行，但部分路段状态尚不明确。";
        }
        if (facts.hasSmooth()) {
            return scope + "未发现拥堵或缓行路段，整体通行平稳。";
        }
        return scope + "路段状态均不明确，暂无法判断整体通行情况。";
    }

    private void appendFocusSegments(
            StringBuilder answer,
            List<RoadSegmentStatus> focusSegments
    ) {
        if (focusSegments.isEmpty()) {
            return;
        }
        answer.append(" 重点路段：");
        for (int index = 0; index < focusSegments.size(); index++) {
            if (index > 0) {
                answer.append("；");
            }
            answer.append(formatSegment(focusSegments.get(index)));
        }
        answer.append("。");
    }

    private void appendAdvice(StringBuilder answer, TrafficFacts facts, Freshness freshness) {
        if (freshness != Freshness.FRESH) {
            answer.append(" 实时状态暂无法确认，建议重新查询后再安排出行。");
            return;
        }
        if (facts.hasCongested()) {
            answer.append(" 建议途经上述路段前再次确认实时路况，并预留充足通行时间。");
        } else if (facts.hasSlow()) {
            answer.append(" 建议途经上述缓行路段时适当预留通行时间。");
        } else {
            answer.append(" 路况可能动态变化，出行前可再次确认。");
        }
    }

    /** 完整保留本次查询实际返回的每一条路段，不用重点摘要替代明细。 */
    private void appendAllSegments(
            StringBuilder answer,
            List<RoadSegmentStatus> allSegments
    ) {
        answer.append("\n本次返回的全部路段：");
        for (RoadSegmentStatus segment : allSegments) {
            answer.append("\n- ").append(formatSegment(segment));
        }
    }

    private TrafficFacts facts(List<RoadSegmentStatus> segments) {
        boolean hasCongested = false;
        boolean hasSlow = false;
        boolean hasSmooth = false;
        boolean hasUnknown = false;

        List<RoadSegmentStatus> uniqueSegments = deduplicate(segments);
        for (RoadSegmentStatus segment : uniqueSegments) {
            switch (segment.congestionLevel()) {
                case CONGESTED -> hasCongested = true;
                case SLOW -> hasSlow = true;
                case SMOOTH -> hasSmooth = true;
                case UNKNOWN -> hasUnknown = true;
            }
        }
        return new TrafficFacts(
                hasCongested,
                hasSlow,
                hasSmooth,
                hasUnknown,
                uniqueSegments.stream()
                        .filter(segment -> segment.congestionLevel() == CongestionLevel.CONGESTED
                                || segment.congestionLevel() == CongestionLevel.SLOW)
                        .limit(MAX_FOCUS_SEGMENTS)
                        .toList(),
                uniqueSegments
        );
    }

    /**
     * 同一路名和方向只展示一次；排序后首先出现的是拥堵程度最高、同等级速度最低的记录。
     */
    List<RoadSegmentStatus> deduplicate(List<RoadSegmentStatus> segments) {
        Map<String, RoadSegmentStatus> uniqueSegments = new LinkedHashMap<>();
        segments.stream()
                .sorted(FOCUS_ORDER)
                .forEach(segment -> uniqueSegments.putIfAbsent(segmentKey(segment), segment));
        return List.copyOf(uniqueSegments.values());
    }

    private String formatSegment(RoadSegmentStatus segment) {
        StringBuilder value = new StringBuilder(segment.roadName()).append("（");
        boolean needsSeparator = false;
        if (!"方向未知".equals(segment.direction())) {
            value.append(segment.direction());
            needsSeparator = true;
        }
        if (needsSeparator) {
            value.append("，");
        }
        value.append(levelText(segment.congestionLevel()));
        if (segment.averageSpeedKmh() != null) {
            value.append("，约")
                    .append(formatSpeed(segment.averageSpeedKmh()))
                    .append(" km/h");
        }
        return value.append("）").toString();
    }

    private String levelText(CongestionLevel level) {
        return switch (level) {
            case CONGESTED -> "拥堵";
            case SLOW -> "缓行";
            case SMOOTH -> "畅通";
            case UNKNOWN -> "状态未知";
        };
    }

    private String formatSpeed(double speed) {
        if (Math.rint(speed) == speed) {
            return Long.toString((long) speed);
        }
        return String.format(Locale.ROOT, "%.1f", speed);
    }

    private String segmentKey(RoadSegmentStatus segment) {
        return segment.roadName().toLowerCase(Locale.ROOT) + "|"
                + segment.direction().toLowerCase(Locale.ROOT);
    }

    private record TrafficFacts(
            boolean hasCongested,
            boolean hasSlow,
            boolean hasSmooth,
            boolean hasUnknown,
            List<RoadSegmentStatus> focusSegments,
            List<RoadSegmentStatus> allSegments
    ) {
    }
}
