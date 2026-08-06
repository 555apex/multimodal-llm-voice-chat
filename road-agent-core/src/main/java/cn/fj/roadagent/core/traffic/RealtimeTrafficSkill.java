package cn.fj.roadagent.core.traffic;

import cn.fj.roadagent.application.agent.AgentEvent;
import cn.fj.roadagent.application.agent.AgentEventSink;
import cn.fj.roadagent.application.agent.AgentIntent;
import cn.fj.roadagent.application.agent.TrafficAgentResult;
import cn.fj.roadagent.application.port.AreaTrafficQueryTool;
import cn.fj.roadagent.application.port.TrafficQueryTool;
import cn.fj.roadagent.application.traffic.Freshness;
import cn.fj.roadagent.application.traffic.AreaTrafficProgressListener;
import cn.fj.roadagent.application.traffic.AreaTrafficQueryCommand;
import cn.fj.roadagent.application.traffic.AreaTrafficQueryResult;
import cn.fj.roadagent.application.traffic.QueryAreaTrafficUseCase;
import cn.fj.roadagent.application.traffic.QueryRealtimeTrafficUseCase;
import cn.fj.roadagent.application.traffic.SummarySource;
import cn.fj.roadagent.application.traffic.TrafficQueryCommand;
import cn.fj.roadagent.application.traffic.TrafficQueryResult;
import cn.fj.roadagent.core.agent.AgentExecutionContext;
import cn.fj.roadagent.core.agent.AgentSkill;
import cn.fj.roadagent.core.agent.AgentSkillResult;
import cn.fj.roadagent.domain.traffic.FujianCity;
import cn.fj.roadagent.domain.traffic.AreaTrafficSnapshot;
import cn.fj.roadagent.domain.traffic.RoadSegmentStatus;
import cn.fj.roadagent.domain.traffic.TrafficEvaluation;
import cn.fj.roadagent.domain.traffic.TrafficQuery;
import cn.fj.roadagent.domain.traffic.TrafficSnapshot;
import cn.fj.roadagent.domain.traffic.TrafficQueryScope;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 交通路况查询Skill：以确定性Java工作流控制业务和回答事实。
 * Tool与回答生成器按固定步骤编排，完成“查询路况→生成专业结论”。
 */
// （Agent-core层）RealtimeTrafficSkill 实现 （Agent-application层）提供的interface
/*
    interface包含：
    1.单条路查询：QueryRealtimeTrafficUseCase
    2.区域查询：QueryAreaTrafficUseCase
    3.Agent统一接口：AgentSkill，包含intent(),excute()方法
 */
public final class RealtimeTrafficSkill implements QueryRealtimeTrafficUseCase, QueryAreaTrafficUseCase, AgentSkill {
    // 定义交通查询Skill的工作流
    private static final List<TrafficWorkflowStep> WORKFLOW = List.of(
            TrafficWorkflowStep.VALIDATE_QUERY, // 用enum进行预先定义好的选项，流程不由AI动态决定，先跑通确定性流程
            TrafficWorkflowStep.QUERY_TRAFFIC,
            TrafficWorkflowStep.VALIDATE_FRESHNESS,
            TrafficWorkflowStep.SUMMARIZE,
            TrafficWorkflowStep.PUBLISH_RESULT
    );  // 定义工作流列表，static保证不管创建多少个对象，workflow只有一份

    private final TrafficQueryTool trafficQueryTool;    // 查交通数据的Tool
    private final AreaTrafficQueryTool areaTrafficQueryTool; // 查行政区交通态势的Tool
    private final TrafficAnswerComposer answerComposer;
    private final Clock clock;  // 时钟：用于判断数据是否过期
    private final Duration staleAfter;  // 定义过期时间

    public RealtimeTrafficSkill(    // 类的构造函数
            TrafficQueryTool trafficQueryTool,
            AreaTrafficQueryTool areaTrafficQueryTool,
            Clock clock,
            Duration staleAfter
    ) {
        this.trafficQueryTool = trafficQueryTool;   // Spring注入构造方式：由配置决定
        this.areaTrafficQueryTool = areaTrafficQueryTool;
        this.clock = clock;
        this.staleAfter = staleAfter;
        this.answerComposer = new TrafficAnswerComposer();
    }

    @Override   // 表示覆写接口或父类的方法，作为安全保障（以防不存在这个方法）
    // 此处，为覆写QueryRealtimeTrafficUseCase的query方法
    // 函数功能：查询某条道路的交通数据信息
    public TrafficQueryResult query(TrafficQueryCommand command) {
        // command - 领域对象
        TrafficQuery query = new TrafficQuery(command.areaCode(), command.roadName(), command.direction());
        TrafficSnapshot snapshot = trafficQueryTool.execute(query); // 执行tool查数据
        Freshness freshness = evaluateFreshness(snapshot.acquiredAt()); // 评估数据的实时程度（判断数据是否过期）
        List<String> warnings = collectDataWarnings(query, snapshot, freshness);   // 收集警告（返回前端）
        List<RoadSegmentStatus> displaySegments = answerComposer.deduplicate(snapshot.segments());

        String summary = answerComposer.compose(snapshot, freshness);
        // 确保traceId
        String traceId = command.traceId() == null || command.traceId().isBlank()
                ? UUID.randomUUID().toString()
                : command.traceId();

        return new TrafficQueryResult(
                query,  // 查询条件
                summary,
                SummarySource.DETERMINISTIC,
                displaySegments,// 按道路名称+方向去重后的路段列表
                snapshot.source(),  // 数据来源（当前为高德，未来可切换甲方接口）
                snapshot.acquiredAt(), // 获取时间
                freshness,  // 数据实时性
                snapshot.mock(), // 兼容字段：当前交通查询固定为false
                warnings, // 警告列表
                traceId   // 追踪ID
        );  // 从而TrafficController拿到这个TrafficQueryResult 转为Response发给前端
    }

    // public方法：将workflow暴露出去，后续可能使用
    public List<TrafficWorkflowStep> workflow() {
        return WORKFLOW;
    }


    @Override   // 覆写区域查询interface方法：QueryAreaTrafficUseCase
    // 函数功能：查询某个区域的交通信息
    public AreaTrafficQueryResult queryArea(AreaTrafficQueryCommand command) {
        TrafficQueryScope scope = command.scope() == null ? TrafficQueryScope.AREA_ALL : command.scope();
        if (!scope.isArea()) {
            throw new IllegalArgumentException("区域查询范围必须是AREA_ALL或AREA_MAJOR");
        }
        AreaTrafficSnapshot snapshot = areaTrafficQueryTool.execute(
                command.city(), command.areaName(), scope, AreaTrafficProgressListener.noOp()
        );
        Freshness freshness = evaluateFreshness(snapshot.acquiredAt());
        List<String> warnings = collectAreaWarnings(snapshot, freshness);
        List<RoadSegmentStatus> displaySegments = answerComposer.deduplicate(snapshot.segments());
        String summary = answerComposer.compose(snapshot, freshness);
        String traceId = command.traceId() == null || command.traceId().isBlank()
                ? UUID.randomUUID().toString() : command.traceId();
        return new AreaTrafficQueryResult(
                snapshot.query(), summary, SummarySource.DETERMINISTIC, displaySegments,
                TrafficEvaluation.from(displaySegments), snapshot.coverage(), snapshot.source(), snapshot.acquiredAt(),
                freshness, warnings, traceId
        );
    }

    @Override   // 覆写Agent统一接口AgentSkill：intent()方法
    public AgentIntent intent() {
        return AgentIntent.TRAFFIC_QUERY;
    }
    // 向SkillRegistry注册自己的意图（表示调用这个Skill时是交通查询意图）

    @Override   // 覆写Agent统一接口AgentSkill：execute()方法
    public AgentSkillResult execute(AgentExecutionContext context, AgentEventSink sink) {
        TrafficQueryScope scope = context.decision().parsedTrafficScope()
                .orElseThrow(() -> new IllegalArgumentException("交通查询范围不正确"));
        return scope == TrafficQueryScope.ROAD
                ? executeRoad(context, sink)    // 查询具体的路
                : executeArea(context, sink, scope);    // 查询区域
    }

    // 方法：道路查询流程（核心方法），在execute()方法中使用
    private AgentSkillResult executeRoad(AgentExecutionContext context, AgentEventSink sink) {
        // 城市名转换为行政区划代码
        FujianCity city = FujianCity.fromName(context.decision().city())
                .orElseThrow(() -> new IllegalArgumentException("无法识别福建城市"));
        TrafficQuery query = new TrafficQuery(
                city.adcode(), context.decision().roadName(), context.decision().direction()
        );

        // 推事件显示，实时查看事件情况
        sink.emit(new AgentEvent("stage.changed", Map.of(
                "stage", "TOOL_CALLING", "label", "正在查询高德实时路况"
        )));
        sink.emit(new AgentEvent("tool.started", Map.of("tool", "query_realtime_traffic")));
        // Tool调用：负责取得事实数据
        TrafficSnapshot snapshot = trafficQueryTool.execute(query);
        sink.emit(new AgentEvent("tool.completed", Map.of(
                "tool", "query_realtime_traffic", "source", snapshot.source()
        )));

        Freshness freshness = evaluateFreshness(snapshot.acquiredAt());
        List<String> warnings = collectDataWarnings(query, snapshot, freshness);
        List<RoadSegmentStatus> displaySegments = answerComposer.deduplicate(snapshot.segments());
        sink.emit(new AgentEvent("stage.changed", Map.of(
                "stage", "ANSWERING", "label", "正在生成专业路况结论"
        )));
        String answer = answerComposer.compose(snapshot, freshness);
        sink.emit(new AgentEvent("answer.delta", Map.of("content", answer)));

        TrafficQueryResult result = new TrafficQueryResult(
                query, answer, SummarySource.DETERMINISTIC, displaySegments, snapshot.source(),
                snapshot.acquiredAt(), freshness, false, warnings, context.command().traceId()
        );
        sink.emit(new AgentEvent("result.traffic", TrafficAgentResult.from(result)));
        return new AgentSkillResult(answer);
    }

    // 方法：区域查询流程（核心方法），在execute()方法中使用
    private AgentSkillResult executeArea(
            AgentExecutionContext context,
            AgentEventSink sink,
            TrafficQueryScope scope
    ) {
        String areaName = context.decision().areaName();
        if (areaName == null || areaName.isBlank()) {
            areaName = context.decision().city();
        }
        sink.emit(new AgentEvent("stage.changed", Map.of(
                "stage", "TOOL_CALLING", "label", "正在采集行政区交通态势"
        )));
        sink.emit(new AgentEvent("tool.started", Map.of(
                "tool", "query_area_traffic", "scope", scope.name()
        )));
        AreaTrafficSnapshot snapshot = areaTrafficQueryTool.execute(
                context.decision().city(), areaName, scope,
                progress -> sink.emit(new AgentEvent("tool.progress", Map.of(
                        "tool", "query_area_traffic",
                        "totalTiles", progress.totalTiles(),
                        "completedTiles", progress.completedTiles(),
                        "failedTiles", progress.failedTiles()
                )))
        );
        sink.emit(new AgentEvent("tool.completed", Map.of(
                "tool", "query_area_traffic",
                "source", snapshot.source(),
                "segmentCount", snapshot.segments().size(),
                "coverageRatio", snapshot.coverage().coverageRatio()
        )));

        Freshness freshness = evaluateFreshness(snapshot.acquiredAt());
        List<String> warnings = collectAreaWarnings(snapshot, freshness);
        List<RoadSegmentStatus> displaySegments = answerComposer.deduplicate(snapshot.segments());
        sink.emit(new AgentEvent("stage.changed", Map.of(
                "stage", "ANSWERING", "label", "正在生成专业交通结论"
        )));
        String answer = answerComposer.compose(snapshot, freshness);
        sink.emit(new AgentEvent("answer.delta", Map.of("content", answer)));
        AreaTrafficQueryResult result = new AreaTrafficQueryResult(
                snapshot.query(), answer, SummarySource.DETERMINISTIC, displaySegments,
                TrafficEvaluation.from(displaySegments), snapshot.coverage(), snapshot.source(), snapshot.acquiredAt(),
                freshness, warnings, context.command().traceId()
        );
        sink.emit(new AgentEvent("result.traffic", TrafficAgentResult.from(result)));
        return new AgentSkillResult(answer);
    }

    // 方法实现：数据过期判断
    private Freshness evaluateFreshness(Instant acquiredAt) {
        Instant now = clock.instant();
        if (acquiredAt.isAfter(now)) {
            return Freshness.UNKNOWN;
        }
        return Duration.between(acquiredAt, now).compareTo(staleAfter) > 0
                ? Freshness.STALE
                : Freshness.FRESH;
    }

    // 方法实现：收集警告
    private List<String> collectDataWarnings(
            TrafficQuery query,
            TrafficSnapshot snapshot,
            Freshness freshness
    ) {
        List<String> warnings = new ArrayList<>();
        if (snapshot.isEmpty()) {   // 表示查询的路的数据为空
            warnings.add("EMPTY_TRAFFIC_DATA");
        }
        if (freshness == Freshness.STALE) {
            warnings.add("TRAFFIC_DATA_STALE");
        }
        // 方向检查匹配
        if (query.direction() != null && !query.direction().isBlank() && !snapshot.isEmpty()) {
            String expected = normalizeDirection(query.direction());
            // 用户说了"南向北" → 检查高德返回的数据里有没有这个方向的路段
            boolean directionMatched = snapshot.segments().stream()
                    .map(segment -> normalizeDirection(segment.direction()))
                    .anyMatch(actual -> !actual.isBlank()
                            && (actual.contains(expected) || expected.contains(actual)));
            if (!directionMatched) {
                warnings.add("REQUESTED_DIRECTION_NOT_COVERED");
            }
        }
        return warnings;
    }

    private List<String> collectAreaWarnings(AreaTrafficSnapshot snapshot, Freshness freshness) {
        List<String> warnings = new ArrayList<>(snapshot.warnings());
        if (freshness == Freshness.STALE && !warnings.contains("TRAFFIC_DATA_STALE")) {
            warnings.add("TRAFFIC_DATA_STALE");
        }
        return warnings;
    }

    // 只去掉“向、往”等连接字，用于判断高德结果是否覆盖用户指定方向。
    private String normalizeDirection(String direction) {
        if (direction == null) {
            return "";
        }
        return direction.replaceAll("[由往向至\\s]", "").trim();
    }

}
