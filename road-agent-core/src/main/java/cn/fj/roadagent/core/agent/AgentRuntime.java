package cn.fj.roadagent.core.agent;

import cn.fj.roadagent.application.agent.AgentDecision;
import cn.fj.roadagent.application.agent.AgentEvent;
import cn.fj.roadagent.application.agent.AgentEventSink;
import cn.fj.roadagent.application.agent.AgentIntent;
import cn.fj.roadagent.application.agent.AgentMessageCommand;
import cn.fj.roadagent.application.agent.ConversationMessage;
import cn.fj.roadagent.application.agent.ConverseWithAgentUseCase;
import cn.fj.roadagent.application.exception.BusinessRuleException;
import cn.fj.roadagent.application.exception.ExternalServiceException;
import cn.fj.roadagent.application.port.ConversationMemoryPort;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Agent总入口：规划、选择Skill、执行并记录会话。 */
/* (agent-core层)AgentRuntime 实现 (agent-application层)的接口ConverseWithAgentUseCase
 包含需要实现的方法：void handle(AgentMessageCommand command, AgentEventSink sink)
 */
public final class AgentRuntime implements ConverseWithAgentUseCase {
    private final IntentPlanner intentPlanner;  // 意图规划器（分析用户文本意图，提取对应参数）
    private final SkillRegistry skillRegistry;  // 技能注册表（明确系统存在的skill目录，找到相应的java代码）
    private final ConversationMemoryPort memoryPort;    // 会话记忆（存储上下文）
    private final Clock clock;  // 时钟

    // 构造函数
    public AgentRuntime(
            IntentPlanner intentPlanner,
            SkillRegistry skillRegistry,
            ConversationMemoryPort memoryPort,
            Clock clock
    ) {
        this.intentPlanner = intentPlanner;
        this.skillRegistry = skillRegistry;
        this.memoryPort = memoryPort;
        this.clock = clock;
    }

    @Override
    public void handle(AgentMessageCommand command, AgentEventSink sink) {
        String runId = UUID.randomUUID().toString();    // 生成运行id，用于日志追踪
        emit(sink, "run.started", Map.of("runId", runId, "traceId", command.traceId()));
        try {
            List<ConversationMessage> history = memoryPort.load(command.conversationId()); // 加载历史记录
            emitStage(sink, "UNDERSTANDING", "正在理解问题"); // 前端显示：正在理解问题
            // （历史信息+用户信息）传递给大模型
            AgentDecision decision = intentPlanner.plan(command.message(), history);
            AgentIntent intent = decision.parsedIntent();   // 模型结果转化为AgentIntent包含的枚举
            var recognized = new java.util.LinkedHashMap<String, Object>();
            recognized.put("intent", intent.name());
            decision.parsedTrafficQueryType().ifPresent(type -> recognized.put("trafficScope", type.name()));
            emit(sink, "intent.recognized", Map.copyOf(recognized));

            // 存储本轮对话进入上下文记忆
            memoryPort.append(command.conversationId(),
                    new ConversationMessage("user", command.message(), clock.instant()));

            // 无关问题，限制Agent不回答
            if (intent == AgentIntent.UNSUPPORTED) {
                completeWithoutSkill(command, sink, runId,
                        "目前我支持福建普通国省干线交通态势与短时趋势研判、拥堵异常路段、指定路线状态、道路通行能力和瓶颈路线评估，也可以分析区域卡口、城市与路线交通压力，以及福州、厦门的车型出行特征、多城市OD七日统计、区域流量差异和关键通道分车型流量，并提供应急调度辅助。 ");
                return;
            }

            // 缺少参数，限制Agent不回答
            List<String> missing = intentPlanner.missingFields(decision);
            if (!missing.isEmpty()) {
                String clarification = decision.clarification();
                if (clarification == null || clarification.isBlank()) {
                    clarification = decision.parsedTrafficQueryType().map(type -> type.odQuery()).orElse(false)
                            ? "请选择福建九个地级市范围内的城市，可一次分析一个或多个城市。"
                            : "请补充城市、道路或事件位置等必要信息。";
                }
                completeWithoutSkill(command, sink, runId, clarification);
                return;
            }

            // 执行Skill
            AgentSkill skill = skillRegistry.require(intent);   // 通过intent寻找对应的skill
            emit(sink, "skill.selected", Map.of(
                    "skill", skill.getClass().getSimpleName(), "intent", intent.name()
            ));
            AgentSkillResult result = skill.execute(
                    new AgentExecutionContext(command, decision, history), sink
            );
            // 记录Agent的回答内容，作为上下文记忆
            memoryPort.append(command.conversationId(),
                    new ConversationMessage("assistant", result.assistantMessage(), clock.instant()));
            emit(sink, "answer.speech", Map.of("content", result.speechText()));
            emit(sink, "run.completed", Map.of("runId", runId));    // 推送Agent执行结束信息
        } catch (Exception exception) {
            emit(sink, "run.failed", Map.of(
                    "runId", runId,
                    "traceId", command.traceId(),
                    "code", errorCode(exception),
                    "message", safeMessage(exception)
            ));
        }
    }

    // 方法：直接推送answer，用于Agent无法回答的情况
    private void completeWithoutSkill(
            AgentMessageCommand command,
            AgentEventSink sink,
            String runId,
            String answer
    ) {
        emit(sink, "answer.delta", Map.of("content", answer));
        emit(sink, "answer.speech", Map.of(
                "content", SpeechTextSanitizer.toSpeakableText(answer)
        ));
        memoryPort.append(command.conversationId(),
                new ConversationMessage("assistant", answer, clock.instant()));
        emit(sink, "run.completed", Map.of("runId", runId));
    }

    // 方法：事件发送
    private void emitStage(AgentEventSink sink, String stage, String label) {
        emit(sink, "stage.changed", Map.of("stage", stage, "label", label));
    }

    // 方法：回调事件经过Controller变成SSE格式推给浏览器
    private void emit(AgentEventSink sink, String name, Object data) {
        sink.emit(new AgentEvent(name, data));
    }


    // 方法：错误分类
    private String errorCode(Exception exception) {
        if (exception instanceof ExternalServiceException external) {
            return external.errorCode();
        }
        if (exception instanceof BusinessRuleException business) {
            return business.errorCode();
        }
        return "AGENT_EXECUTION_FAILED";
    }

    private String safeMessage(Exception exception) {
        if (exception instanceof ExternalServiceException || exception instanceof BusinessRuleException
                || exception instanceof IllegalArgumentException) {
            return exception.getMessage();
        }
        return "Agent处理请求时发生异常";
    }
}
