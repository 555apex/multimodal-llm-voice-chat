package cn.fj.roadagent.core.traffic;

import cn.fj.roadagent.application.model.ModelRequest;
import cn.fj.roadagent.domain.traffic.RoadSegmentStatus;
import cn.fj.roadagent.domain.traffic.TrafficSnapshot;

import java.util.stream.Collectors;

final class TrafficSummaryPromptFactory {

    ModelRequest create(TrafficSnapshot snapshot) {
        String segments = snapshot.segments().stream()
                .map(this::formatSegment)
                .collect(Collectors.joining("\n"));

        String systemPrompt = """
                你是应急交通系统的路况摘要助手。
                只能依据输入数据生成简洁中文摘要，不得猜测、补充或修改路况事实。
                不要给出调度指令，不要输出Markdown，控制在120字以内。
                """.strip();

        String userPrompt = """
                查询道路：%s
                查询方向：%s
                数据来源：%s
                获取时间：%s
                道路分段：
                %s
                """.formatted(
                snapshot.query().roadName(),
                snapshot.query().direction() == null ? "未指定" : snapshot.query().direction(),
                snapshot.source(),
                snapshot.acquiredAt(),
                segments
        ).strip();

        return new ModelRequest(systemPrompt, userPrompt, 0.2);
    }

    private String formatSegment(RoadSegmentStatus segment) {
        String speed = segment.averageSpeedKmh() == null
                ? "未提供"
                : "%.1fkm/h".formatted(segment.averageSpeedKmh());
        return "- 道路=%s，方向=%s，状态=%s，平均速度=%s"
                .formatted(segment.roadName(), segment.direction(), segment.congestionLevel(), speed);
    }
}
