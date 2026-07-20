package cn.fj.roadagent.core.traffic;

import cn.fj.roadagent.application.model.ModelResponse;
import cn.fj.roadagent.application.port.ChatModelPort;
import cn.fj.roadagent.application.port.TrafficQueryTool;
import cn.fj.roadagent.application.traffic.Freshness;
import cn.fj.roadagent.application.traffic.QueryRealtimeTrafficUseCase;
import cn.fj.roadagent.application.traffic.SummarySource;
import cn.fj.roadagent.application.traffic.TrafficQueryCommand;
import cn.fj.roadagent.application.traffic.TrafficQueryResult;
import cn.fj.roadagent.domain.traffic.TrafficQuery;
import cn.fj.roadagent.domain.traffic.TrafficSnapshot;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 交通路况查询Skill：以确定性Java工作流控制业务，大模型仅辅助生成摘要。（固定工作流编排）
 * （Tool、Adapter、PromptFactory等）按固定步骤编排起来，完成"查询路况→生成摘要"这个任务
 */
public final class RealtimeTrafficSkill implements QueryRealtimeTrafficUseCase {

    private static final List<TrafficWorkflowStep> WORKFLOW = List.of(
            TrafficWorkflowStep.VALIDATE_QUERY, // 用enum进行预先定义好的选项，流程不由AI动态决定，先跑通确定性流程
            TrafficWorkflowStep.QUERY_TRAFFIC,
            TrafficWorkflowStep.VALIDATE_FRESHNESS,
            TrafficWorkflowStep.SUMMARIZE,
            TrafficWorkflowStep.PUBLISH_RESULT
    );  // 定义工作流列表，static保证不管创建多少个对象，workflow只有一份

    private final TrafficQueryTool trafficQueryTool;    // 查交通数据的Tool
    private final Optional<ChatModelPort> chatModelPort;// 调用大模型端口（可选项）
    private final RuleBasedTrafficSummaryGenerator ruleSummaryGenerator;    // 规则生成器（若不使用大模型端口）
    private final TrafficSummaryPromptFactory promptFactory;    // Prompt工厂
    private final Clock clock;  // 时钟：用于判断数据是否过期
    private final Duration staleAfter;  // 定义过期时间

    public RealtimeTrafficSkill(    // 类的构造函数
            TrafficQueryTool trafficQueryTool,
            Optional<ChatModelPort> chatModelPort,
            Clock clock,
            Duration staleAfter
    ) {
        this.trafficQueryTool = trafficQueryTool;   // Spring注入构造方式：由配置决定
        this.chatModelPort = chatModelPort;
        this.clock = clock;
        this.staleAfter = staleAfter;
        this.ruleSummaryGenerator = new RuleBasedTrafficSummaryGenerator(); // new构造方法：Java逻辑
        this.promptFactory = new TrafficSummaryPromptFactory();
    }

    @Override   // 表示覆写接口或父类的方法，作为安全保障（以防不存在这个方法）
    // 此处，为覆写QueryRealtimeTrafficUseCase的query方法
    public TrafficQueryResult query(TrafficQueryCommand command) {
        // command - 领域对象
        TrafficQuery query = new TrafficQuery(command.areaCode(), command.roadName(), command.direction());
        TrafficSnapshot snapshot = trafficQueryTool.execute(query); // 执行tool查数据
        Freshness freshness = evaluateFreshness(snapshot.acquiredAt()); // 评估数据的实时程度（判断数据是否过期）
        List<String> warnings = collectDataWarnings(snapshot, freshness);   // 收集警告（返回前端）

        Summary summary = summarize(snapshot, warnings);    // Summary为record类型的数据
        // 确保traceId
        String traceId = command.traceId() == null || command.traceId().isBlank()
                ? UUID.randomUUID().toString()
                : command.traceId();

        return new TrafficQueryResult(
                query,  // 查询条件
                summary.content(),  // 摘要文字
                summary.source(),   // 摘要来源（MODEL/规则生成）
                snapshot.segments(),// 路段列表
                snapshot.source(),  // 数据来源（高德/MOCK）
                snapshot.acquiredAt(), // 获取时间
                freshness,  // 数据实时性
                snapshot.mock(), // MOCK（布尔位）
                warnings, // 警告列表
                traceId   // 追踪ID
        );  // 从而TrafficController拿到这个TrafficQueryResult 转为Response发给前端
    }

    // public方法：将workflow暴露出去，后续可能使用
    public List<TrafficWorkflowStep> workflow() {
        return WORKFLOW;
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
    private List<String> collectDataWarnings(TrafficSnapshot snapshot, Freshness freshness) {
        List<String> warnings = new ArrayList<>();
        if (snapshot.isEmpty()) {   // 表示查询的路的数据为空
            warnings.add("EMPTY_TRAFFIC_DATA");
        }
        if (freshness == Freshness.STALE) {
            warnings.add("TRAFFIC_DATA_STALE");
        }
        return warnings;
    }

    // 方法实现：摘要生成
    private Summary summarize(TrafficSnapshot snapshot, List<String> warnings) {
        if (!snapshot.isEmpty() && chatModelPort.isPresent()) { // 数据不为空且模型已配置
            try {
                ModelResponse response = chatModelPort.get().generate(promptFactory.create(snapshot));
                // TrafficSummaryPromptFactory类的实例对象promptFactory 把结构化的TrafficSnapshot转换成大模型能理解的 ModelRequest（systemPrompt + userPrompt + temperature）
                return new Summary(response.content(), SummarySource.MODEL);
            } catch (RuntimeException exception) {
                warnings.add("MODEL_SUMMARY_FALLBACK");
            }
        }
        // 存在异常时走规则（纯Java字符串拼接器，不依赖任何外部服务）
        return new Summary(ruleSummaryGenerator.generate(snapshot), SummarySource.RULE_FALLBACK);
    }
    // 私有内部record类（类包含两个字段：content和source），该类仅服务在RealtimeTrafficSkill类中，其他类不可见
    private record Summary(String content, SummarySource source) {
    }
}
