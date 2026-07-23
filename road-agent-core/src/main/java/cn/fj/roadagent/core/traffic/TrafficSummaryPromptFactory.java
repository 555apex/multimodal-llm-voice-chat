package cn.fj.roadagent.core.traffic;

import cn.fj.roadagent.application.model.ModelRequest;
import cn.fj.roadagent.application.model.ModelMessage;
import cn.fj.roadagent.application.agent.ConversationMessage;
import cn.fj.roadagent.domain.traffic.RoadSegmentStatus;
import cn.fj.roadagent.domain.traffic.TrafficSnapshot;
import cn.fj.roadagent.domain.traffic.AreaTrafficSnapshot;

import java.util.stream.Collectors;
import java.util.List;

final class TrafficSummaryPromptFactory {

    ModelRequest create(TrafficSnapshot snapshot) {
        return create(snapshot, "请根据查询结果回答路况。", List.of());
    }

    ModelRequest create(
            TrafficSnapshot snapshot,
            String originalQuestion,
            List<ConversationMessage> history
    ) {
        String segments = snapshot.segments().stream()
                .map(this::formatSegment)
                .collect(Collectors.joining("\n"));

        String systemPrompt = """
                你是应急交通系统的路况摘要助手。
                只能依据输入数据生成简洁中文摘要，不得猜测、补充或修改路况事实。
                不要给出调度指令，不要输出Markdown，控制在120字以内。
                """.strip();

        String userPrompt = """
                用户问题：%s
                查询道路：%s
                查询方向：%s
                数据来源：%s
                获取时间：%s
                道路分段：
                %s
                """.formatted(
                originalQuestion,
                snapshot.query().roadName(),
                snapshot.query().direction() == null ? "未指定" : snapshot.query().direction(),
                snapshot.source(),
                snapshot.acquiredAt(),
                segments
        ).strip();

        List<ModelMessage> modelHistory = history.stream()
                .map(message -> new ModelMessage(message.role(), message.content()))
                .toList();
        return new ModelRequest(systemPrompt, userPrompt, modelHistory, 0.2);
    }

    ModelRequest create(
            AreaTrafficSnapshot snapshot,
            String originalQuestion,
            List<ConversationMessage> history
    ) {
        String importantSegments = snapshot.segments().stream()
                .limit(50)
                .map(this::formatSegment)
                .collect(Collectors.joining("\n"));
        var evaluation = snapshot.evaluation();
        var coverage = snapshot.coverage();
        String systemPrompt = """
                你是应急交通系统的区域路况汇报助手。
                只能依据Java计算的整体指标和输入路段生成中文摘要，不得凭自身知识补充交通要道。
                必须说明行政区、数据来源、采集时间和覆盖率。
                覆盖率不足100%时必须明确说明结果不代表全区完整态势。
                重点指出拥堵和缓行道路；不要逐条复述全部路段，不要输出Markdown表格。
                控制在260字以内。
                """.strip();
        String userPrompt = """
                用户问题：%s
                查询范围：%s
                行政区：%s（%s）
                数据来源：%s
                获取时间：%s
                切片覆盖：成功%d/%d，覆盖率=%.1f%%
                全部去重路段统计：总数=%d，畅通=%d，缓行=%d，拥堵=%d，未知=%d，平均速度=%s
                最严重的最多50条路段：
                %s
                """.formatted(
                originalQuestion,
                snapshot.query().scope(),
                snapshot.query().area().name(),
                snapshot.query().area().city(),
                snapshot.source(),
                snapshot.acquiredAt(),
                coverage.succeededTiles(), coverage.totalTiles(), coverage.coverageRatio() * 100,
                evaluation.totalSegments(), evaluation.smoothSegments(), evaluation.slowSegments(),
                evaluation.congestedSegments(), evaluation.unknownSegments(),
                evaluation.averageSpeedKmh() == null
                        ? "未提供" : "%.1fkm/h".formatted(evaluation.averageSpeedKmh()),
                importantSegments.isBlank() ? "无可用路段" : importantSegments
        ).strip();
        List<ModelMessage> modelHistory = history.stream()
                .map(message -> new ModelMessage(message.role(), message.content()))
                .toList();
        return new ModelRequest(systemPrompt, userPrompt, modelHistory, 0.1);
    }

    private String formatSegment(RoadSegmentStatus segment) {
        String speed = segment.averageSpeedKmh() == null
                ? "未提供"
                : "%.1fkm/h".formatted(segment.averageSpeedKmh());
        return "- 道路=%s，方向=%s，状态=%s，平均速度=%s"
                .formatted(segment.roadName(), segment.direction(), segment.congestionLevel(), speed);
    }
}
