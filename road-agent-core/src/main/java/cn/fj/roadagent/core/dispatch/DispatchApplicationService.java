package cn.fj.roadagent.core.dispatch;

import cn.fj.roadagent.application.dispatch.DispatchApprovalCommand;
import cn.fj.roadagent.application.dispatch.DispatchApprovalUseCase;
import cn.fj.roadagent.application.dispatch.DispatchQueryUseCase;
import cn.fj.roadagent.application.dispatch.EmergencyAlert;
import cn.fj.roadagent.application.dispatch.GenerateDispatchUseCase;
import cn.fj.roadagent.application.dispatch.NoDispatchCommand;
import cn.fj.roadagent.application.dispatch.NoDispatchUseCase;
import cn.fj.roadagent.application.dispatch.QueryPendingEmergencyUseCase;
import cn.fj.roadagent.application.exception.BusinessRuleException;
import cn.fj.roadagent.application.exception.ExternalServiceException;
import cn.fj.roadagent.application.model.ModelRequest;
import cn.fj.roadagent.application.port.AbnormalEventPort;
import cn.fj.roadagent.application.port.ChatModelPort;
import cn.fj.roadagent.application.port.DispatchRepository;
import cn.fj.roadagent.application.port.UnitOfWork;
import cn.fj.roadagent.domain.dispatch.ApprovalDecision;
import cn.fj.roadagent.domain.dispatch.DispatchPlan;
import cn.fj.roadagent.domain.dispatch.DispatchStatus;
import cn.fj.roadagent.domain.dispatch.EmergencyEvent;
import cn.fj.roadagent.domain.dispatch.SuggestedResource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 数据库应急事件的工单生成、审批和返工编排。 */
public final class DispatchApplicationService implements
        DispatchQueryUseCase,
        DispatchApprovalUseCase,
        QueryPendingEmergencyUseCase,
        GenerateDispatchUseCase,
        NoDispatchUseCase {

    private final AbnormalEventPort eventPort;
    private final DispatchRepository dispatchRepository;
    private final ChatModelPort chatModelPort;
    private final UnitOfWork unitOfWork;
    private final Clock clock;
    private final Duration staleGeneratingAfter;

    public DispatchApplicationService(
            AbnormalEventPort eventPort,
            DispatchRepository dispatchRepository,
            ChatModelPort chatModelPort,
            UnitOfWork unitOfWork,
            Clock clock,
            Duration staleGeneratingAfter
    ) {
        this.eventPort = eventPort;
        this.dispatchRepository = dispatchRepository;
        this.chatModelPort = chatModelPort;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
        this.staleGeneratingAfter = staleGeneratingAfter;
    }

    @Override
    public Optional<EmergencyAlert> nextPending() {
        return eventPort.findNextPending().map(event -> new EmergencyAlert(
                event,
                dispatchRepository.findLatestByEventId(event.eventId()).orElse(null),
                eventPort.countPending()
        ));
    }

    @Override
    public DispatchPlan get(String planId) {
        return dispatchRepository.findLatestByPlanId(planId)
                .orElseThrow(() -> notFound("调度工单不存在"));
    }

    @Override
    public DispatchPlan generate(String eventId) {
        requirePendingEvent(eventId);
        Optional<DispatchPlan> existing = dispatchRepository.findLatestByEventId(eventId);
        if (existing.isPresent()) {
            DispatchPlan current = existing.get();
            if (current.status() == DispatchStatus.WAITING_APPROVAL
                    || current.status() == DispatchStatus.APPROVED) {
                return current;
            }
            if (current.status() == DispatchStatus.GENERATING && !isStale(current)) {
                return current;
            }
            if (current.status() == DispatchStatus.FAILED
                    || current.status() == DispatchStatus.GENERATING) {
                DispatchPlan retry = current.retry(clock.instant());
                if (!dispatchRepository.restartGeneration(
                        retry,
                        clock.instant().minus(staleGeneratingAfter)
                )) {
                    return dispatchRepository.findLatestByEventId(eventId).orElseThrow(
                            () -> conflict("DISPATCH_STATE_CONFLICT", "工单状态已变化，请刷新后重试")
                    );
                }
                return generateContent(retry, previousVersion(retry), previousFeedback(retry));
            }
            throw conflict("DISPATCH_STATE_CONFLICT", "当前工单状态不能重新生成");
        }

        GenerationClaim claim = unitOfWork.required(() -> {
            EmergencyEvent lockedEvent = lockPendingEvent(eventId);
            Optional<DispatchPlan> concurrent = dispatchRepository.findLatestByEventId(eventId);
            if (concurrent.isPresent()) {
                return new GenerationClaim(concurrent.get(), false);
            }
            DispatchPlan created = DispatchPlan.generating(
                    "DP-" + UUID.randomUUID(), lockedEvent, 1L, clock.instant()
            );
            if (!dispatchRepository.insert(created)) {
                DispatchPlan alreadyCreated = dispatchRepository.findLatestByEventId(eventId)
                        .orElseThrow(
                                () -> conflict(
                                        "DISPATCH_CREATE_CONFLICT",
                                        "工单已由其他请求创建"
                                )
                        );
                return new GenerationClaim(alreadyCreated, false);
            }
            return new GenerationClaim(created, true);
        });
        if (!claim.claimed()) {
            return claim.plan();
        }
        return generateContent(claim.plan(), null, null);
    }

    @Override
    public DispatchPlan decide(DispatchApprovalCommand command) {
        if (command == null || command.decision() == null) {
            throw new IllegalArgumentException("审批决定不能为空");
        }
        if (command.decision() == ApprovalDecision.REJECT) {
            return rejectAndRevise(command);
        }
        return approve(command);
    }

    @Override
    public void markNoDispatch(NoDispatchCommand command) {
        if (!command.confirmed()) {
            throw new IllegalArgumentException("必须二次确认无需生成调度工单");
        }
        String reason = requireLength(command.reason(), "无需调度原因", 500);
        unitOfWork.required(() -> {
            lockPendingEvent(command.eventId());
            if (dispatchRepository.findLatestByEventId(command.eventId()).isPresent()) {
                throw conflict("DISPATCH_ALREADY_EXISTS", "该事件已经生成工单，不能标记为无需调度");
            }
            if (!eventPort.markNoDispatch(command.eventId(), reason, clock.instant())) {
                throw conflict("EVENT_STATE_CONFLICT", "事件状态已变化，请刷新后重试");
            }
        });
    }

    private DispatchPlan approve(DispatchApprovalCommand command) {
        return unitOfWork.required(() -> {
            DispatchPlan current = get(command.planId());
            if (current.status() == DispatchStatus.APPROVED) {
                return current;
            }
            requireExpectedVersion(current, command.expectedVersion());
            DispatchPlan approved = current.approve(clock.instant());
            if (!dispatchRepository.updateApproved(approved)) {
                throw conflict("DISPATCH_STATE_CONFLICT", "工单状态已变化，请刷新后重试");
            }
            if (!eventPort.markDispatchApproved(current.event().eventId(), clock.instant())) {
                throw conflict("EVENT_STATE_CONFLICT", "事件状态已变化，审批未提交");
            }
            return approved;
        });
    }

    private DispatchPlan rejectAndRevise(DispatchApprovalCommand command) {
        String comment = requireLength(command.comment(), "驳回意见", 500);
        DispatchPlan next = unitOfWork.required(() -> {
            DispatchPlan current = get(command.planId());
            requireExpectedVersion(current, command.expectedVersion());
            DispatchPlan rejected = current.reject(comment, clock.instant());
            if (!dispatchRepository.updateRejected(rejected)) {
                throw conflict("DISPATCH_STATE_CONFLICT", "工单状态已变化，请刷新后重试");
            }
            DispatchPlan revision = rejected.nextRevision(clock.instant());
            if (!dispatchRepository.insert(revision)) {
                throw conflict("DISPATCH_VERSION_CONFLICT", "返工版本已存在，请刷新后重试");
            }
            return revision;
        });
        DispatchPlan rejected = dispatchRepository.findVersion(next.planId(), next.version() - 1)
                .orElseThrow(() -> notFound("上一版调度工单不存在"));
        return generateContent(next, rejected, comment);
    }

    private DispatchPlan generateContent(
            DispatchPlan generating,
            DispatchPlan previous,
            String feedback
    ) {
        try {
            DispatchPlanProposal proposal = chatModelPort.generateStructured(
                    proposalRequest(generating.event(), previous, feedback),
                    DispatchPlanProposal.class
            );
            DispatchPlan completed;
            try {
                if (proposal == null) {
                    throw new IllegalArgumentException("模型返回的工单内容为空");
                }
                List<SuggestedResource> resources = proposal.suggestedResources().stream()
                        .map(item -> new SuggestedResource(
                                item.resourceType(),
                                item.resourceName(),
                                item.quantity(),
                                item.unit(),
                                item.purpose()
                        ))
                        .toList();
                completed = generating.generated(
                        resources, proposal.rescuePlan(), clock.instant()
                );
            } catch (IllegalArgumentException exception) {
                throw new ExternalServiceException(
                        "CHAT_MODEL",
                        "MODEL_INVALID_OUTPUT",
                        "模型返回的调度工单内容不符合要求：" + exception.getMessage(),
                        exception
                );
            }
            if (!dispatchRepository.updateGenerated(completed)) {
                throw conflict("DISPATCH_STATE_CONFLICT", "工单生成状态已变化，请刷新后重试");
            }
            return completed;
        } catch (RuntimeException exception) {
            DispatchPlan failed = generating.failed(safeError(exception), clock.instant());
            dispatchRepository.updateFailed(failed);
            throw exception;
        }
    }

    private ModelRequest proposalRequest(
            EmergencyEvent event,
            DispatchPlan previous,
            String feedback
    ) {
        String system = """
                你是福建公路应急调度工单生成器。请根据事件信息和公路应急通用知识提出建议。
                必须输出严格JSON对象，仅包含suggestedResources和rescuePlan。
                suggestedResources是数组，每项包含resourceType、resourceName、quantity、unit、purpose。
                quantity必须是大于0的整数。至少给出一项资源和一份可执行的救援方案。
                所有资源必须表述为建议，不得声称库存真实可用，不得编造联系人、仓库、距离或到达时间。
                rescuePlan应覆盖现场安全、交通组织、救援处置和信息报送，不得声称工单已经审批或下发。
                """.strip();
        StringBuilder user = new StringBuilder("""
                事件ID：%s
                事件类型：%s（%s）
                发生时间：%s
                事件描述：%s
                """.formatted(
                event.customId().isBlank() ? event.eventId() : event.customId(),
                eventTypeName(event.eventType()),
                event.eventType(),
                event.occurrenceTime() == null ? "未知" : event.occurrenceTime(),
                event.description()
        ));
        if (previous != null) {
            user.append("\n上一版资源建议：").append(previous.suggestedResources());
            user.append("\n上一版救援方案：").append(previous.rescuePlan());
            user.append("\n人工驳回意见：").append(feedback);
            user.append("\n请针对驳回意见返工，不能原样重复上一版。");
        }
        return new ModelRequest(system, user.toString(), List.of(), 0.1);
    }

    private DispatchPlan previousVersion(DispatchPlan current) {
        if (current.version() <= 1) {
            return null;
        }
        return dispatchRepository.findVersion(current.planId(), current.version() - 1).orElse(null);
    }

    private String previousFeedback(DispatchPlan current) {
        DispatchPlan previous = previousVersion(current);
        return previous == null ? null : previous.rejectionReason();
    }

    private EmergencyEvent requirePendingEvent(String eventId) {
        return eventPort.findPendingById(eventId)
                .orElseThrow(() -> eventNotFound("待处理异常事件不存在"));
    }

    private EmergencyEvent lockPendingEvent(String eventId) {
        return eventPort.lockPendingById(eventId)
                .orElseThrow(() -> eventNotFound("待处理异常事件不存在"));
    }

    private void requireExpectedVersion(DispatchPlan current, long expectedVersion) {
        if (current.version() != expectedVersion) {
            throw conflict("DISPATCH_VERSION_CONFLICT", "工单版本已变化，请刷新后重试");
        }
    }

    private boolean isStale(DispatchPlan plan) {
        return !plan.updatedAt().isAfter(clock.instant().minus(staleGeneratingAfter));
    }

    private String eventTypeName(String code) {
        return switch (code) {
            case "DT01" -> "崩塌";
            case "ET101" -> "拥堵";
            default -> "异常事件";
        };
    }

    private String safeError(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "模型生成失败";
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    private String requireLength(String value, String label, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(label + "不能超过" + maxLength + "个字符");
        }
        return normalized;
    }

    private BusinessRuleException notFound(String message) {
        return new BusinessRuleException("DISPATCH_NOT_FOUND", message);
    }

    private BusinessRuleException eventNotFound(String message) {
        return new BusinessRuleException("EVENT_NOT_FOUND", message);
    }

    private BusinessRuleException conflict(String code, String message) {
        return new BusinessRuleException(code, message);
    }

    private record GenerationClaim(DispatchPlan plan, boolean claimed) {
    }
}
