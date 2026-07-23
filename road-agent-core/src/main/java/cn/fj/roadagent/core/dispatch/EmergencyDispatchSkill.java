package cn.fj.roadagent.core.dispatch;

import cn.fj.roadagent.application.agent.AgentEvent;
import cn.fj.roadagent.application.agent.AgentEventSink;
import cn.fj.roadagent.application.agent.AgentIntent;
import cn.fj.roadagent.application.dispatch.ResourceQuery;
import cn.fj.roadagent.application.model.ModelMessage;
import cn.fj.roadagent.application.model.ModelRequest;
import cn.fj.roadagent.application.port.ChatModelPort;
import cn.fj.roadagent.application.port.DispatchRepository;
import cn.fj.roadagent.application.port.ResourceQueryTool;
import cn.fj.roadagent.core.agent.AgentExecutionContext;
import cn.fj.roadagent.core.agent.AgentSkill;
import cn.fj.roadagent.core.agent.AgentSkillResult;
import cn.fj.roadagent.domain.dispatch.DispatchPlan;
import cn.fj.roadagent.domain.dispatch.DispatchStatus;
import cn.fj.roadagent.domain.dispatch.DispatchTask;
import cn.fj.roadagent.domain.dispatch.EmergencyEvent;
import cn.fj.roadagent.domain.dispatch.EmergencyResource;
import cn.fj.roadagent.domain.traffic.FujianCity;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** 应急事件调度Skill：生成调度草案并暂停在人工审批闸门。 */
public final class EmergencyDispatchSkill implements AgentSkill {
    private final ResourceQueryTool resourceQueryTool;  // 查询可用资源Tool
    private final ChatModelPort chatModelPort;  // 大模型生成调度方案
    private final DispatchRepository dispatchRepository;    // 保存调度计划（涉及写操作）
    private final Clock clock;  // 时钟

    // 构造函数
    public EmergencyDispatchSkill(
            ResourceQueryTool resourceQueryTool,
            ChatModelPort chatModelPort,
            DispatchRepository dispatchRepository,
            Clock clock
    ) {
        this.resourceQueryTool = resourceQueryTool;
        this.chatModelPort = chatModelPort;
        this.dispatchRepository = dispatchRepository;
        this.clock = clock;
    }

    // 实现Agent统一入口AgentSkill：intent()方法
    @Override
    public AgentIntent intent() {
        return AgentIntent.EMERGENCY_DISPATCH;
    }

    // 实现Agent统一入口AgentSkill：execute()方法
    @Override
    public AgentSkillResult execute(AgentExecutionContext context, AgentEventSink sink) {
        // 城市名转行政区划编码，并防止识别其他城市（抛出异常处理）
        FujianCity city = FujianCity.fromName(context.decision().city())
                .orElseThrow(() -> new IllegalArgumentException("无法识别福建城市"));

        // 构造事件对象。对象信息来源：AgentDecision(大模型从用户输入文本提取的结构化字段)
        EmergencyEvent event = new EmergencyEvent(
                defaultText(context.decision().eventType(), "道路应急事件"),  // 事件类型
                city.displayName(), // 城市
                context.decision().location(),  // 位置
                defaultText(context.decision().severity(), "UNKNOWN"),  // 严重程度
                context.decision().eventDescription()   // 事件描述
        );

        // 信息推送前端浏览器显示
        sink.emit(new AgentEvent("stage.changed", Map.of(
                "stage", "TOOL_CALLING", "label", "正在查询可用应急资源"
        )));
        sink.emit(new AgentEvent("tool.started", Map.of("tool", "query_emergency_resources")));

        // 查询应急调度可用资源：调用resourceQueryTool
        // 具体实现：根据事件类型和城市，查询匹配的应急资源（包括人员、车辆、物资）
        List<EmergencyResource> candidates = resourceQueryTool.execute(
                new ResourceQuery(city.displayName(), context.decision().resourceTypes())
        );
        sink.emit(new AgentEvent("tool.completed", Map.of(
                "tool", "query_emergency_resources", "count", candidates.size(), "source", "MOCK"
        )));

        sink.emit(new AgentEvent("stage.changed", Map.of(
                "stage", "PLANNING", "label", "正在生成受约束的调度方案"
        )));
        // 大模型生成调度方案，调用proposalRequest方法（函数实现在本文件下方）
        // 具体实现：通过System Prompt等手段，限定模型，使用输入中列出的可用资源前提下，生成结构化文本
        DispatchPlanProposal proposal = chatModelPort.generateStructured(
                proposalRequest(event, candidates, context),    // Prompt包含：事件信息+可用资源清单
                DispatchPlanProposal.class  // 返回DispatchPlanProposal格式的JSON
        );
        // Java白名单验证，validateAndBuild()函数实现在本文件下方
        DispatchPlan plan = validateAndBuild(event, candidates, proposal);

        StringBuilder answer = new StringBuilder();
        sink.emit(new AgentEvent("stage.changed", Map.of(
                "stage", "ANSWERING", "label", "正在说明调度建议"
        )));
        // 大模型生成调度方案的自然语言说明，调用explanationRequest（函数实现在本文件下方）
        chatModelPort.stream(explanationRequest(plan, context), delta -> {
            answer.append(delta);
            sink.emit(new AgentEvent("answer.delta", Map.of("content", delta)));
        });

        // 模型完整回答成功后再保存，避免失败草案进入审批队列。
        dispatchRepository.save(plan);  // 生成的应急调度计划保存，但等待人工审批
        sink.emit(new AgentEvent("result.dispatch", plan));
        sink.emit(new AgentEvent("approval.required", Map.of(
                "planId", plan.planId(), "version", plan.version()
        )));    // 告知前端：这个方案需要人工审批
        return new AgentSkillResult(answer.toString());
    }

    // 方法：调度方案生成函数（大模型使用）
    private ModelRequest proposalRequest(
            EmergencyEvent event,
            List<EmergencyResource> resources,
            AgentExecutionContext context
    ) {
        String resourceText = resources.stream()
                .map(resource -> "- %s | %s | %s | %s".formatted(
                        resource.resourceId(), resource.type(), resource.name(), resource.capability()))
                .collect(Collectors.joining("\n"));
        String system = """
                你是公路应急调度方案生成器。只能使用输入中列出的可用资源。
                必须输出json对象，包含summary、tasks、selectedResourceIds、warnings。
                tasks每项包含action、responsibleUnit、resourceId。
                不得声称已经下发工单，不得编造联系人、数量、距离和到达时间。
                至少给出一项可执行任务；没有资源时明确写入warnings。
                """.strip();
        String user = """
                事件类型：%s
                城市：%s
                位置：%s
                严重程度：%s
                事件描述：%s
                可用资源：
                %s
                """.formatted(event.eventType(), event.city(), event.locationDescription(),
                event.severity(), event.description(), resourceText.isBlank() ? "无" : resourceText);
        return new ModelRequest(system, user, toModelMessages(context), 0.1);
    }
    // 方法：调度方案解释说明函数（解释调度方案，给用户解释，面向用户使用）
    private ModelRequest explanationRequest(DispatchPlan plan, AgentExecutionContext context) {
        String system = """
                你是应急调度助手。请简洁说明已经生成的调度草案及风险。
                只能依据输入方案，不得补充不存在的资源，不得声称工单已下发。
                最后明确提示：必须在界面点击批准后才会创建Mock工单。
                不要输出Markdown表格。
                """.strip();
        return new ModelRequest(system, plan.toString(), toModelMessages(context), 0.2);
    }

    // 方法：Java白名单检查方案
    private DispatchPlan validateAndBuild(
            EmergencyEvent event,
            List<EmergencyResource> candidates,
            DispatchPlanProposal proposal
    ) {
        // 检查：摘要、任务不能为空
        if (proposal.summary() == null || proposal.summary().isBlank() || proposal.tasks().isEmpty()) {
            throw new IllegalArgumentException("模型生成的调度方案缺少摘要或任务");
        }

        // 检查：大模型选择的资源ID必须在候选列表（可用资源不可捏造）
        Set<String> allowedIds = candidates.stream()
                .map(EmergencyResource::resourceId).collect(Collectors.toSet());
        List<EmergencyResource> selected = candidates.stream()
                .filter(resource -> proposal.selectedResourceIds().contains(resource.resourceId()))
                .toList();
        List<DispatchTask> tasks = new ArrayList<>();
        for (int index = 0; index < proposal.tasks().size(); index++) {
            DispatchPlanProposal.ProposedTask item = proposal.tasks().get(index);
            if (item.action() == null || item.action().isBlank()) {
                throw new IllegalArgumentException("调度任务内容不能为空");
            }
            // 如果大模型编了一个不存在的资源ID → 被替换为null
            String resourceId = allowedIds.contains(item.resourceId()) ? item.resourceId() : null;
            tasks.add(new DispatchTask(index + 1, item.action(),
                    defaultText(item.responsibleUnit(), "待人工指定"), resourceId));
        }

        // 收集warning信息
        List<String> warnings = new ArrayList<>(proposal.warnings());
        if (candidates.isEmpty()) {
            warnings.add("MOCK_RESOURCE_NOT_FOUND");
        }
        return new DispatchPlan(
                "DP-" + UUID.randomUUID(), event, proposal.summary(), tasks, selected, warnings,
                DispatchStatus.WAITING_APPROVAL, 1L, clock.instant(), null
        );
    }

    private List<ModelMessage> toModelMessages(AgentExecutionContext context) {
        return context.history().stream()
                .map(message -> new ModelMessage(message.role(), message.content()))
                .toList();
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
