package cn.fj.roadagent.core.dispatch;

import cn.fj.roadagent.application.dispatch.CommandDecisionCommand;
import cn.fj.roadagent.application.dispatch.CommandDecisionUseCase;
import cn.fj.roadagent.application.dispatch.DispatchApprovalCommand;
import cn.fj.roadagent.application.dispatch.DispatchApprovalUseCase;
import cn.fj.roadagent.application.dispatch.DispatchQueryUseCase;
import cn.fj.roadagent.application.dispatch.EmergencyAlert;
import cn.fj.roadagent.application.dispatch.EmergencyWorkflowView;
import cn.fj.roadagent.application.dispatch.GenerateDispatchUseCase;
import cn.fj.roadagent.application.dispatch.Level1Decision;
import cn.fj.roadagent.application.dispatch.Level1DecisionCommand;
import cn.fj.roadagent.application.dispatch.Level1DecisionUseCase;
import cn.fj.roadagent.application.dispatch.NoDispatchCommand;
import cn.fj.roadagent.application.dispatch.NoDispatchUseCase;
import cn.fj.roadagent.application.dispatch.ProfessionalReviewCommand;
import cn.fj.roadagent.application.dispatch.ProfessionalReviewUseCase;
import cn.fj.roadagent.application.dispatch.QueryEmergencyWorkflowUseCase;
import cn.fj.roadagent.application.dispatch.QueryPendingEmergencyUseCase;
import cn.fj.roadagent.application.dispatch.QueryWorkflowInboxUseCase;
import cn.fj.roadagent.application.dispatch.ReleaseResourcesCommand;
import cn.fj.roadagent.application.dispatch.ReleaseResourcesUseCase;
import cn.fj.roadagent.application.dispatch.WorkflowCounts;
import cn.fj.roadagent.application.dispatch.WorkflowHistoryPage;
import cn.fj.roadagent.application.dispatch.WorkflowInbox;
import cn.fj.roadagent.application.exception.BusinessRuleException;
import cn.fj.roadagent.application.exception.ExternalServiceException;
import cn.fj.roadagent.application.model.ModelRequest;
import cn.fj.roadagent.application.port.AbnormalEventPort;
import cn.fj.roadagent.application.port.ChatModelPort;
import cn.fj.roadagent.application.port.ResourceAllocationPort;
import cn.fj.roadagent.application.port.ResourceDataPort;
import cn.fj.roadagent.application.port.DispatchRepository;
import cn.fj.roadagent.application.port.EmergencyWorkflowRepository;
import cn.fj.roadagent.application.port.UnitOfWork;
import cn.fj.roadagent.domain.dispatch.ApprovalDecision;
import cn.fj.roadagent.domain.dispatch.CommandDecision;
import cn.fj.roadagent.domain.dispatch.AllocatedResource;
import cn.fj.roadagent.domain.dispatch.DispatchPlan;
import cn.fj.roadagent.domain.dispatch.DispatchStatus;
import cn.fj.roadagent.domain.dispatch.EmergencyEvent;
import cn.fj.roadagent.domain.dispatch.EmergencyWorkflow;
import cn.fj.roadagent.domain.dispatch.EmergencyResource;
import cn.fj.roadagent.domain.dispatch.NoticeSnapshot;
import cn.fj.roadagent.domain.dispatch.ProfessionalReview;
import cn.fj.roadagent.domain.dispatch.ReviewStatus;
import cn.fj.roadagent.domain.dispatch.ResourceAllocation;
import cn.fj.roadagent.domain.dispatch.ResourceAllocationStatus;
import cn.fj.roadagent.domain.dispatch.ResourceFeasibility;
import cn.fj.roadagent.domain.dispatch.ResourceRequirement;
import cn.fj.roadagent.domain.dispatch.WorkflowAction;
import cn.fj.roadagent.domain.dispatch.WorkflowActionType;
import cn.fj.roadagent.domain.dispatch.WorkflowStage;
import cn.fj.roadagent.domain.dispatch.WorkflowStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** 数据库应急事件的三级上报、返工、最终通告和历史追溯编排。 */
public final class DispatchApplicationService implements
        DispatchQueryUseCase,
        DispatchApprovalUseCase,
        QueryPendingEmergencyUseCase,
        GenerateDispatchUseCase,
        NoDispatchUseCase,
        QueryWorkflowInboxUseCase,
        Level1DecisionUseCase,
        ProfessionalReviewUseCase,
        CommandDecisionUseCase,
        QueryEmergencyWorkflowUseCase,
        ReleaseResourcesUseCase {

    private final AbnormalEventPort eventPort;
    private final DispatchRepository dispatchRepository;
    private final EmergencyWorkflowRepository workflowRepository;
    private final ChatModelPort chatModelPort;
    private final ResourceDataPort resourceDataPort;
    private final ResourceAllocationPort resourceAllocationPort;
    private final EmergencyResourceAllocator resourceAllocator;
    private final UnitOfWork unitOfWork;
    private final Clock clock;
    private final Duration staleGeneratingAfter;

    public DispatchApplicationService(
            AbnormalEventPort eventPort,
            DispatchRepository dispatchRepository,
            EmergencyWorkflowRepository workflowRepository,
            ChatModelPort chatModelPort,
            ResourceDataPort resourceDataPort,
            ResourceAllocationPort resourceAllocationPort,
            EmergencyResourceAllocator resourceAllocator,
            UnitOfWork unitOfWork,
            Clock clock,
            Duration staleGeneratingAfter
    ) {
        this.eventPort = eventPort;
        this.dispatchRepository = dispatchRepository;
        this.workflowRepository = workflowRepository;
        this.chatModelPort = chatModelPort;
        this.resourceDataPort = resourceDataPort;
        this.resourceAllocationPort = resourceAllocationPort;
        this.resourceAllocator = resourceAllocator;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
        this.staleGeneratingAfter = staleGeneratingAfter;
    }

    @Override
    public Optional<EmergencyAlert> nextPending() {
        WorkflowInbox inbox = inbox(WorkflowStage.LEVEL_1);
        if (inbox.item() == null) {
            return Optional.empty();
        }
        return Optional.of(new EmergencyAlert(
                inbox.item().event(),
                inbox.item().currentPlan(),
                inbox.counts().level1()
        ));
    }

    @Override
    public WorkflowInbox inbox(WorkflowStage stage) {
        if (stage == null) {
            throw new IllegalArgumentException("待办阶段不能为空");
        }
        WorkflowCounts counts = new WorkflowCounts(
                eventPort.countPendingForStage(WorkflowStage.LEVEL_1),
                eventPort.countPendingForStage(WorkflowStage.LEVEL_2),
                eventPort.countPendingForStage(WorkflowStage.LEVEL_3)
        );
        EmergencyWorkflowView item = eventPort.findNextPendingForStage(stage)
                .map(this::viewForEvent)
                .orElse(null);
        return new WorkflowInbox(item, counts);
    }

    @Override
    public DispatchPlan get(String planId) {
        return dispatchRepository.findLatestByPlanId(planId)
                .orElseThrow(() -> notFound("调度工单不存在"));
    }

    @Override
    public EmergencyWorkflowView getWorkflow(String workflowId) {
        EmergencyWorkflow workflow = workflowRepository.findWorkflow(workflowId)
                .orElseThrow(() -> workflowNotFound("应急工作流不存在"));
        return view(workflow);
    }

    @Override
    public WorkflowHistoryPage history(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("页码不能小于0");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("每页数量必须在1到100之间");
        }
        List<EmergencyWorkflowView> items = workflowRepository
                .findHistory(page * size, size)
                .stream()
                .map(this::view)
                .toList();
        return new WorkflowHistoryPage(
                items, page, size, workflowRepository.countHistory()
        );
    }

    @Override
    public DispatchPlan generate(String eventId) {
        EmergencyEvent event = requirePendingEvent(eventId);
        requireStructuredCity(event);
        Optional<EmergencyWorkflow> existingWorkflow =
                workflowRepository.findWorkflowByEventId(eventId);
        existingWorkflow.ifPresent(this::requireLevel1Workflow);

        Optional<DispatchPlan> existing = dispatchRepository.findLatestByEventId(eventId);
        if (existing.isPresent()) {
            DispatchPlan current = existing.get();
            EmergencyWorkflow workflow = existingWorkflow.orElseGet(
                    () -> createLegacyWorkflow(event, current)
            );
            if (current.status() == DispatchStatus.WAITING_APPROVAL) {
                return current;
            }
            if (current.status() == DispatchStatus.APPROVED) {
                throw conflict("WORKFLOW_STATE_CONFLICT", "该方案已经最终批准");
            }
            if (current.status() == DispatchStatus.GENERATING && !isStale(current)) {
                return current;
            }
            if (current.status() == DispatchStatus.FAILED
                    || current.status() == DispatchStatus.GENERATING) {
                return retryGeneration(current, workflow);
            }
            throw conflict("DISPATCH_STATE_CONFLICT", "当前工单状态不能重新生成");
        }

        GenerationClaim claim = unitOfWork.required(() -> {
            EmergencyEvent lockedEvent = lockPendingEvent(eventId);
            Optional<EmergencyWorkflow> concurrentWorkflow =
                    workflowRepository.findWorkflowByEventId(eventId);
            if (concurrentWorkflow.isPresent()) {
                EmergencyWorkflow workflow = concurrentWorkflow.get();
                requireLevel1Workflow(workflow);
                DispatchPlan plan = dispatchRepository.findLatestByEventId(eventId)
                        .orElseThrow(() -> conflict(
                                "WORKFLOW_STATE_CONFLICT", "工作流已经创建但方案不存在"
                        ));
                return new GenerationClaim(plan, workflow, false);
            }
            Optional<DispatchPlan> concurrent = dispatchRepository.findLatestByEventId(eventId);
            if (concurrent.isPresent()) {
                EmergencyWorkflow workflow = createLegacyWorkflowRecord(lockedEvent, concurrent.get());
                if (!workflowRepository.insertWorkflow(workflow)) {
                    workflow = workflowRepository.findWorkflowByEventId(eventId)
                            .orElseThrow(() -> conflict(
                                    "WORKFLOW_CREATE_CONFLICT", "工作流已由其他请求创建"
                            ));
                }
                return new GenerationClaim(concurrent.get(), workflow, false);
            }

            Instant now = clock.instant();
            String workflowId = "WF-" + UUID.randomUUID();
            String planId = "DP-" + UUID.randomUUID();
            DispatchPlan created = DispatchPlan.generating(planId, lockedEvent, 1L, now);
            EmergencyWorkflow workflow = EmergencyWorkflow.generating(
                    workflowId, lockedEvent.eventId(), planId, 1L, now
            );
            if (!workflowRepository.insertWorkflow(workflow)) {
                throw conflict("WORKFLOW_CREATE_CONFLICT", "工作流已由其他请求创建");
            }
            if (!dispatchRepository.insert(created)) {
                throw conflict("DISPATCH_CREATE_CONFLICT", "工单已由其他请求创建");
            }
            insertAction(action(
                    workflow,
                    WorkflowActionType.GENERATION_STARTED,
                    null,
                    WorkflowStage.LEVEL_1,
                    null,
                    WorkflowStatus.GENERATING,
                    null,
                    systemKey("generate-start"),
                    now
            ));
            return new GenerationClaim(created, workflow, true);
        });
        if (!claim.claimed()) {
            return claim.plan();
        }
        return generateContent(claim.plan(), null, null, claim.workflow());
    }

    @Override
    public DispatchPlan decide(DispatchApprovalCommand command) {
        if (command == null || command.decision() == null) {
            throw new IllegalArgumentException("审批决定不能为空");
        }
        DispatchPlan current = get(command.planId());
        EmergencyWorkflow workflow = workflowRepository.findWorkflowByPlanId(command.planId())
                .orElseGet(() -> createLegacyWorkflow(
                        requirePendingEvent(current.event().eventId()), current
                ));
        Optional<WorkflowAction> duplicate = workflowRepository.findActionByIdempotencyKey(
                workflow.workflowId(), requireIdempotency(command.idempotencyKey())
        );
        if (duplicate.isPresent()) {
            return get(command.planId());
        }
        requireExpectedVersion(current, command.expectedVersion());
        if (workflow.currentStage() != WorkflowStage.LEVEL_1) {
            throw conflict(
                    "WORKFLOW_STAGE_CONFLICT",
                    "兼容审批接口只允许处理一级方案，请使用对应层级接口"
            );
        }
        Level1Decision decision = command.decision() == ApprovalDecision.APPROVE
                ? Level1Decision.SUBMIT
                : Level1Decision.REJECT;
        EmergencyWorkflowView result = decideLevel1(new Level1DecisionCommand(
                workflow.workflowId(), decision, command.comment(), workflow.lockVersion(),
                command.idempotencyKey()
        ));
        return result.currentPlan();
    }

    @Override
    public EmergencyWorkflowView decideLevel1(Level1DecisionCommand command) {
        requireLevel1Command(command);
        Optional<WorkflowAction> duplicate = duplicateAction(
                command.workflowId(), command.idempotencyKey()
        );
        if (duplicate.isPresent()) {
            return getWorkflow(command.workflowId());
        }
        if (command.decision() == Level1Decision.REJECT) {
            String reason = requireLength(command.comment(), "返工意见", 500);
            RevisionClaim claim = beginRevision(
                    command.workflowId(), command.expectedWorkflowVersion(),
                    command.idempotencyKey(), reason, WorkflowStage.LEVEL_1
            );
            generateContent(claim.nextPlan(), claim.rejectedPlan(), reason, claim.workflow());
            return getWorkflow(command.workflowId());
        }

        unitOfWork.required(() -> {
            EmergencyWorkflow current = lockWorkflow(command.workflowId());
            requireExpectedWorkflowVersion(current, command.expectedWorkflowVersion());
            ensureIdempotencyAvailable(current.workflowId(), command.idempotencyKey());
            if (current.currentStage() != WorkflowStage.LEVEL_1
                    || current.status() != WorkflowStatus.WAITING_LEVEL_1_SUBMISSION) {
                throw conflict("WORKFLOW_STAGE_CONFLICT", "当前事件不在一级待上报状态");
            }
            requireCurrentWaitingPlan(current);
            Instant now = clock.instant();
            ProfessionalReview review = ProfessionalReview.pending(
                    "PR-" + UUID.randomUUID(), current.workflowId(),
                    current.planId(), current.planVersion(), now
            );
            EmergencyWorkflow next = current.submitLevel1(now);
            if (!workflowRepository.insertReview(review)) {
                throw conflict("REVIEW_CREATE_CONFLICT", "二级复核任务已经创建");
            }
            updateWorkflow(next, current.lockVersion());
            insertAction(action(
                    current, WorkflowActionType.LEVEL_1_SUBMITTED,
                    current.currentStage(), next.currentStage(), current.status(), next.status(),
                    command.comment(), command.idempotencyKey(), now
            ));
        });
        return getWorkflow(command.workflowId());
    }

    @Override
    public EmergencyWorkflowView review(ProfessionalReviewCommand command) {
        if (command == null || command.decision() == null) {
            throw new IllegalArgumentException("二级复核决定不能为空");
        }
        requireIdempotency(command.idempotencyKey());
        if (duplicateAction(command.workflowId(), command.idempotencyKey()).isPresent()) {
            return getWorkflow(command.workflowId());
        }
        if (command.decision() == ApprovalDecision.REJECT) {
            String reason = requireLength(command.comment(), "退回意见", 500);
            RevisionClaim claim = beginRevision(
                    command.workflowId(), command.expectedWorkflowVersion(),
                    command.idempotencyKey(), reason, WorkflowStage.LEVEL_2
            );
            generateContent(claim.nextPlan(), claim.rejectedPlan(), reason, claim.workflow());
            return getWorkflow(command.workflowId());
        }

        unitOfWork.required(() -> {
            EmergencyWorkflow current = lockWorkflow(command.workflowId());
            requireExpectedWorkflowVersion(current, command.expectedWorkflowVersion());
            ensureIdempotencyAvailable(current.workflowId(), command.idempotencyKey());
            if (current.currentStage() != WorkflowStage.LEVEL_2
                    || current.status() != WorkflowStatus.WAITING_LEVEL_2_REVIEW) {
                throw conflict("WORKFLOW_STAGE_CONFLICT", "当前事件不在二级待复核状态");
            }
            ProfessionalReview pending = workflowRepository.findPendingReview(current.workflowId())
                    .orElseThrow(() -> conflict("REVIEW_NOT_FOUND", "待处理专业复核记录不存在"));
            Instant now = clock.instant();
            ProfessionalReview passed = pending.pass(
                    command.eventSeverity(), command.resourceFeasibility(),
                    requireLength(command.impactAssessment(), "影响研判", 1000),
                    optionalLength(command.coordinationRequirements(), "协同要求", 1000),
                    requireLength(command.comment(), "专业审核意见", 500), now
            );
            DispatchPlan reviewedPlan = requireCurrentWaitingPlan(current);
            validateFeasibility(reviewedPlan, passed);
            if (!workflowRepository.updateReview(passed)) {
                throw conflict("REVIEW_STATE_CONFLICT", "专业复核状态已变化");
            }
            CommandDecision decision = CommandDecision.pending(
                    "CD-" + UUID.randomUUID(), current.workflowId(), passed.reviewId(),
                    current.planId(), current.planVersion(), now
            );
            if (!workflowRepository.insertDecision(decision)) {
                throw conflict("DECISION_CREATE_CONFLICT", "三级决策任务已经创建");
            }
            EmergencyWorkflow next = current.approveLevel2(now);
            updateWorkflow(next, current.lockVersion());
            insertAction(action(
                    current, WorkflowActionType.LEVEL_2_PASSED,
                    current.currentStage(), next.currentStage(), current.status(), next.status(),
                    command.comment(), command.idempotencyKey(), now
            ));
        });
        return getWorkflow(command.workflowId());
    }

    @Override
    public EmergencyWorkflowView decideCommand(CommandDecisionCommand command) {
        if (command == null || command.decision() == null) {
            throw new IllegalArgumentException("三级决策不能为空");
        }
        requireIdempotency(command.idempotencyKey());
        if (duplicateAction(command.workflowId(), command.idempotencyKey()).isPresent()) {
            return getWorkflow(command.workflowId());
        }
        if (command.decision() == ApprovalDecision.REJECT) {
            String reason = requireLength(command.comment(), "退回意见", 500);
            RevisionClaim claim = beginRevision(
                    command.workflowId(), command.expectedWorkflowVersion(),
                    command.idempotencyKey(), reason, WorkflowStage.LEVEL_3
            );
            generateContent(claim.nextPlan(), claim.rejectedPlan(), reason, claim.workflow());
            return getWorkflow(command.workflowId());
        }

        unitOfWork.required(() -> {
            EmergencyWorkflow current = lockWorkflow(command.workflowId());
            requireExpectedWorkflowVersion(current, command.expectedWorkflowVersion());
            ensureIdempotencyAvailable(current.workflowId(), command.idempotencyKey());
            if (current.currentStage() != WorkflowStage.LEVEL_3
                    || current.status() != WorkflowStatus.WAITING_LEVEL_3_DECISION) {
                throw conflict("WORKFLOW_STAGE_CONFLICT", "当前事件不在三级待决策状态");
            }
            DispatchPlan plan = requireCurrentWaitingPlan(current);
            if (plan.hasResourceShortage()
                    && (command.comment() == null || command.comment().isBlank())) {
                throw new IllegalArgumentException("带资源缺口的方案最终批准时必须填写省级批示");
            }
            ProfessionalReview review = workflowRepository.findLatestReview(current.workflowId())
                    .filter(item -> item.status() == ReviewStatus.PASSED)
                    .orElseThrow(() -> conflict("REVIEW_NOT_PASSED", "专业复核尚未通过"));
            CommandDecision pending = workflowRepository.findPendingDecision(current.workflowId())
                    .orElseThrow(() -> conflict("DECISION_NOT_FOUND", "待处理省级决策不存在"));
            EmergencyEvent event = eventPort.findById(current.eventId())
                    .orElseThrow(() -> eventNotFound("异常事件不存在"));
            Instant now = clock.instant();
            NoticeSnapshot notice = notice(current, event, plan, review, command.comment(), now);
            CommandDecision publishedDecision = pending.publish(command.comment(), notice, now);
            DispatchPlan approvedPlan = plan.approve(now);
            EmergencyWorkflow publishedWorkflow = current.publish(now);
            dispatchReservedResources(current, now);
            if (!dispatchRepository.updateApproved(approvedPlan)) {
                throw conflict("DISPATCH_STATE_CONFLICT", "工单状态已变化，请刷新后重试");
            }
            if (!workflowRepository.updateDecision(publishedDecision)) {
                throw conflict("DECISION_STATE_CONFLICT", "省级决策状态已变化");
            }
            if (!eventPort.markDispatchApproved(current.eventId(), now)) {
                throw conflict("EVENT_STATE_CONFLICT", "事件状态已变化，最终通告未提交");
            }
            updateWorkflow(publishedWorkflow, current.lockVersion());
            insertAction(action(
                    current, WorkflowActionType.LEVEL_3_PUBLISHED,
                    current.currentStage(), null, current.status(), publishedWorkflow.status(),
                    command.comment(), command.idempotencyKey(), now
            ));
        });
        return getWorkflow(command.workflowId());
    }

    @Override
    public EmergencyWorkflowView releaseResources(ReleaseResourcesCommand command) {
        if (command == null) throw new IllegalArgumentException("资源归还请求不能为空");
        String reason = requireLength(command.reason(), "资源归还原因", 500);
        requireIdempotency(command.idempotencyKey());
        if (duplicateAction(command.workflowId(), command.idempotencyKey()).isPresent()) {
            return getWorkflow(command.workflowId());
        }
        unitOfWork.required(() -> {
            EmergencyWorkflow current = lockWorkflow(command.workflowId());
            requireExpectedWorkflowVersion(current, command.expectedWorkflowVersion());
            ensureIdempotencyAvailable(current.workflowId(), command.idempotencyKey());
            if (current.status() != WorkflowStatus.PUBLISHED) {
                throw conflict("RESOURCE_RELEASE_STATE_CONFLICT", "只有已通告流程可以归还资源");
            }
            List<ResourceAllocation> dispatched = resourceAllocationPort
                    .findByPlanVersion(current.planId(), current.planVersion()).stream()
                    .filter(item -> item.status() == ResourceAllocationStatus.DISPATCHED)
                    .sorted(java.util.Comparator.comparing(item -> item.resource().resourceId()))
                    .toList();
            if (dispatched.isEmpty()) {
                throw conflict("RESOURCE_ALREADY_RELEASED", "该方案没有待归还的已调度资源");
            }
            Set<String> resourceIds = dispatched.stream()
                    .map(item -> item.resource().resourceId())
                    .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));
            Map<String, EmergencyResource> lockedResources = resourceDataPort
                    .lockByResourceIds(resourceIds).stream()
                    .collect(java.util.stream.Collectors.toMap(
                            EmergencyResource::resourceId, item -> item,
                            (left, right) -> left, LinkedHashMap::new));
            Instant now = clock.instant();
            for (ResourceAllocation allocation : dispatched) {
                EmergencyResource original = requireLockedResource(
                        lockedResources, allocation.resource().resourceId());
                EmergencyResource updated = original.releaseDispatched(
                        allocation.resource().quantity());
                updateInventory(updated, original.lockVersion());
                lockedResources.put(updated.resourceId(), updated);
                if (!resourceAllocationPort.update(allocation.released(reason, now), allocation)) {
                    throw conflict("RESOURCE_ALLOCATION_CONFLICT", "资源归还状态已变化");
                }
            }
            EmergencyWorkflow released = current.recordResourceRelease(now);
            updateWorkflow(released, current.lockVersion());
            insertAction(action(
                    current, WorkflowActionType.RESOURCES_RELEASED,
                    null, null, WorkflowStatus.PUBLISHED, WorkflowStatus.PUBLISHED,
                    reason, command.idempotencyKey(), now
            ));
        });
        return getWorkflow(command.workflowId());
    }

    @Override
    public void markNoDispatch(NoDispatchCommand command) {
        if (command == null || !command.confirmed()) {
            throw new IllegalArgumentException("必须二次确认无需生成调度工单");
        }
        String reason = requireLength(command.reason(), "无需调度原因", 500);
        unitOfWork.required(() -> {
            EmergencyEvent event = lockPendingEvent(command.eventId());
            if (dispatchRepository.findLatestByEventId(command.eventId()).isPresent()) {
                throw conflict("DISPATCH_ALREADY_EXISTS", "该事件已经生成工单，不能标记为无需调度");
            }
            if (workflowRepository.findWorkflowByEventId(command.eventId()).isPresent()) {
                throw conflict("WORKFLOW_ALREADY_EXISTS", "该事件已经进入三级流程");
            }
            Instant now = clock.instant();
            EmergencyWorkflow workflow = EmergencyWorkflow.noDispatch(
                    "WF-" + UUID.randomUUID(), event.eventId(), now
            );
            if (!workflowRepository.insertWorkflow(workflow)) {
                throw conflict("WORKFLOW_CREATE_CONFLICT", "工作流已由其他请求创建");
            }
            if (!eventPort.markNoDispatch(command.eventId(), reason, now)) {
                throw conflict("EVENT_STATE_CONFLICT", "事件状态已变化，请刷新后重试");
            }
            insertAction(action(
                    workflow, WorkflowActionType.NO_DISPATCH,
                    WorkflowStage.LEVEL_1, null,
                    WorkflowStatus.WAITING_GENERATION, WorkflowStatus.NO_DISPATCH,
                    reason, systemKey("no-dispatch"), now
            ));
        });
    }

    private DispatchPlan retryGeneration(DispatchPlan current, EmergencyWorkflow workflow) {
        GenerationClaim claim = unitOfWork.required(() -> {
            EmergencyWorkflow locked = lockWorkflow(workflow.workflowId());
            requireLevel1Workflow(locked);
            DispatchPlan latest = dispatchRepository.findVersion(
                    locked.planId(), locked.planVersion()
            ).orElseThrow(() -> notFound("待重试方案不存在"));
            if (latest.status() == DispatchStatus.GENERATING && !isStale(latest)) {
                return new GenerationClaim(latest, locked, false);
            }
            DispatchPlan retry = latest.retry(clock.instant());
            if (!dispatchRepository.restartGeneration(
                    retry, clock.instant().minus(staleGeneratingAfter)
            )) {
                throw conflict("DISPATCH_STATE_CONFLICT", "工单状态已变化，请刷新后重试");
            }
            EmergencyWorkflow next = locked.retryGeneration(clock.instant());
            updateWorkflow(next, locked.lockVersion());
            insertAction(action(
                    locked, WorkflowActionType.GENERATION_RETRIED,
                    locked.currentStage(), next.currentStage(), locked.status(), next.status(),
                    null, systemKey("generate-retry"), clock.instant()
            ));
            return new GenerationClaim(retry, next, true);
        });
        if (!claim.claimed()) {
            return claim.plan();
        }
        return generateContent(
                claim.plan(), previousVersion(claim.plan()),
                previousFeedback(claim.plan()), claim.workflow()
        );
    }

    private RevisionClaim beginRevision(
            String workflowId,
            long expectedWorkflowVersion,
            String idempotencyKey,
            String reason,
            WorkflowStage rejectingStage
    ) {
        return unitOfWork.required(() -> {
            EmergencyWorkflow current = lockWorkflow(workflowId);
            requireExpectedWorkflowVersion(current, expectedWorkflowVersion);
            ensureIdempotencyAvailable(current.workflowId(), idempotencyKey);
            if (current.currentStage() != rejectingStage) {
                throw conflict("WORKFLOW_STAGE_CONFLICT", "事件已经不在当前操作阶段");
            }
            WorkflowStatus requiredStatus = switch (rejectingStage) {
                case LEVEL_1 -> WorkflowStatus.WAITING_LEVEL_1_SUBMISSION;
                case LEVEL_2 -> WorkflowStatus.WAITING_LEVEL_2_REVIEW;
                case LEVEL_3 -> WorkflowStatus.WAITING_LEVEL_3_DECISION;
            };
            if (current.status() != requiredStatus) {
                throw conflict("WORKFLOW_STATE_CONFLICT", "当前状态不能退回返工");
            }
            DispatchPlan plan = requireCurrentWaitingPlan(current);
            Instant now = clock.instant();
            if (rejectingStage == WorkflowStage.LEVEL_2) {
                ProfessionalReview review = workflowRepository.findPendingReview(workflowId)
                        .orElseThrow(() -> conflict("REVIEW_NOT_FOUND", "待处理专业复核记录不存在"));
                if (!workflowRepository.updateReview(review.returned(reason, now))) {
                    throw conflict("REVIEW_STATE_CONFLICT", "专业复核状态已变化");
                }
            } else if (rejectingStage == WorkflowStage.LEVEL_3) {
                CommandDecision decision = workflowRepository.findPendingDecision(workflowId)
                        .orElseThrow(() -> conflict("DECISION_NOT_FOUND", "待处理省级决策不存在"));
                if (!workflowRepository.updateDecision(decision.returned(reason, now))) {
                    throw conflict("DECISION_STATE_CONFLICT", "省级决策状态已变化");
                }
            }
            releaseReservedResources(current, plan, reason, now);
            DispatchPlan rejected = plan.reject(reason, now);
            if (!dispatchRepository.updateRejected(rejected)) {
                throw conflict("DISPATCH_STATE_CONFLICT", "方案状态已变化，请刷新后重试");
            }
            DispatchPlan revision = rejected.nextRevision(now);
            if (!dispatchRepository.insert(revision)) {
                throw conflict("DISPATCH_VERSION_CONFLICT", "返工版本已存在，请刷新后重试");
            }
            EmergencyWorkflow revising = current.beginRevision(revision.version(), now);
            updateWorkflow(revising, current.lockVersion());
            WorkflowActionType type = switch (rejectingStage) {
                case LEVEL_1 -> WorkflowActionType.LEVEL_1_RETURNED;
                case LEVEL_2 -> WorkflowActionType.LEVEL_2_RETURNED;
                case LEVEL_3 -> WorkflowActionType.LEVEL_3_RETURNED;
            };
            insertAction(action(
                    current, type, rejectingStage, WorkflowStage.LEVEL_1,
                    current.status(), revising.status(), reason, idempotencyKey, now
            ));
            return new RevisionClaim(rejected, revision, revising);
        });
    }

    private DispatchPlan generateContent(
            DispatchPlan generating,
            DispatchPlan previous,
            String feedback,
            EmergencyWorkflow workflow
    ) {
        List<EmergencyResource> catalog = resourceDataPort
                .listActiveForPlanning(generating.event().eventType());
        if (catalog.isEmpty()) {
            ExternalServiceException exception = new ExternalServiceException(
                    "RESOURCE_INVENTORY", "RESOURCE_CATALOG_EMPTY",
                    "当前没有可用于该事件类型的数据库应急资源"
            );
            persistGenerationFailure(generating, workflow, exception);
            throw exception;
        }
        DispatchPlanProposal proposal;
        List<ResourceRequirement> requirements;
        String rescuePlan;
        try {
            proposal = chatModelPort.generateStructured(
                    proposalRequest(generating.event(), previous, feedback, catalog),
                    DispatchPlanProposal.class
            );
            if (proposal == null) {
                throw new IllegalArgumentException("模型返回的工单内容为空");
            }
            requirements = enrichRequirements(proposal, catalog);
            rescuePlan = requireLength(proposal.rescuePlanText(), "模型救援方案", 10000);
        } catch (IllegalArgumentException exception) {
            ExternalServiceException wrapped = new ExternalServiceException(
                    "CHAT_MODEL", "MODEL_INVALID_OUTPUT",
                    "模型返回的调度工单内容不符合要求：" + exception.getMessage(), exception
            );
            persistGenerationFailure(generating, workflow, wrapped);
            throw wrapped;
        } catch (RuntimeException exception) {
            persistGenerationFailure(generating, workflow, exception);
            throw exception;
        }

        try {
            return unitOfWork.required(() -> {
                EmergencyWorkflow locked = lockWorkflow(workflow.workflowId());
                if (!locked.planId().equals(generating.planId())
                        || locked.planVersion() != generating.version()) {
                    throw conflict("DISPATCH_VERSION_CONFLICT", "返工期间方案版本已变化");
                }
                Set<String> typeCodes = requirements.stream()
                        .map(ResourceRequirement::resourceTypeCode)
                        .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));
                List<EmergencyResource> resources = resourceDataPort.lockByTypeCodes(typeCodes);
                Instant now = clock.instant();
                ResourceAllocationResult result = resourceAllocator.allocate(
                        generating.event(), requirements, resources, locked.workflowId(),
                        generating.planId(), generating.version(), now
                );
                persistReservations(result);
                List<AllocatedResource> allocations = result.allocations().stream()
                        .map(ResourceAllocation::resource).toList();
                DispatchPlan completed = generating.generated(
                        requirements, allocations, result.shortages(),
                        rescuePlanWithFacts(rescuePlan, allocations, result.shortages()), now
                );
                if (!dispatchRepository.updateGenerated(completed)) {
                    throw conflict("DISPATCH_STATE_CONFLICT", "工单生成状态已变化，请刷新后重试");
                }
                EmergencyWorkflow generated = locked.generated(now);
                updateWorkflow(generated, locked.lockVersion());
                insertAction(action(
                        locked, WorkflowActionType.GENERATION_COMPLETED,
                        locked.currentStage(), generated.currentStage(), locked.status(), generated.status(),
                        result.shortages().isEmpty() ? null : "存在资源缺口，需逐级审核确认",
                        systemKey("generate-complete"), now
                ));
                return completed;
            });
        } catch (IllegalArgumentException exception) {
            ExternalServiceException wrapped = new ExternalServiceException(
                    "RESOURCE_INVENTORY", "RESOURCE_ALLOCATION_INVALID",
                    "数据库资源匹配结果不符合要求：" + exception.getMessage(), exception
            );
            persistGenerationFailure(generating, workflow, wrapped);
            throw wrapped;
        } catch (RuntimeException exception) {
            persistGenerationFailure(generating, workflow, exception);
            throw exception;
        }
    }

    private void persistGenerationFailure(
            DispatchPlan generating,
            EmergencyWorkflow workflow,
            RuntimeException exception
    ) {
        try {
            unitOfWork.required(() -> {
                EmergencyWorkflow locked = lockWorkflow(workflow.workflowId());
                if (!locked.planId().equals(generating.planId())
                        || locked.planVersion() != generating.version()) {
                    return;
                }
                Instant now = clock.instant();
                DispatchPlan failed = generating.failed(safeError(exception), now);
                if (!dispatchRepository.updateFailed(failed)) {
                    return;
                }
                EmergencyWorkflow failedWorkflow = locked.generationFailed(now);
                updateWorkflow(failedWorkflow, locked.lockVersion());
                insertAction(action(
                        locked, WorkflowActionType.GENERATION_FAILED,
                        locked.currentStage(), failedWorkflow.currentStage(),
                        locked.status(), failedWorkflow.status(), safeError(exception),
                        systemKey("generate-failed"), now
                ));
            });
        } catch (RuntimeException ignored) {
            // 原始模型异常优先返回；状态竞争由后续查询和重试机制处理。
        }
    }

    private void persistReservations(ResourceAllocationResult result) {
        for (int index = 0; index < result.updatedResources().size(); index++) {
            EmergencyResource original = result.originalResources().get(index);
            EmergencyResource updated = result.updatedResources().get(index);
            updateInventory(updated, original.lockVersion());
        }
        for (ResourceAllocation allocation : result.allocations()) {
            if (!resourceAllocationPort.insert(allocation)) {
                throw conflict("RESOURCE_ALLOCATION_CONFLICT", "资源已被当前方案重复占用");
            }
        }
    }

    private void releaseReservedResources(
            EmergencyWorkflow workflow, DispatchPlan plan, String reason, Instant now
    ) {
        List<ResourceAllocation> reservations = resourceAllocationPort
                .findByPlanVersion(plan.planId(), plan.version()).stream()
                .filter(item -> item.status() == ResourceAllocationStatus.RESERVED)
                .sorted(java.util.Comparator.comparing(item -> item.resource().resourceId()))
                .toList();
        if (reservations.isEmpty()) return;
        Set<String> ids = reservations.stream().map(item -> item.resource().resourceId())
                .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));
        Map<String, EmergencyResource> resources = lockedResourceMap(ids);
        for (ResourceAllocation allocation : reservations) {
            EmergencyResource original = requireLockedResource(resources, allocation.resource().resourceId());
            EmergencyResource updated = original.releaseReserved(allocation.resource().quantity());
            updateInventory(updated, original.lockVersion());
            resources.put(updated.resourceId(), updated);
            if (!resourceAllocationPort.update(
                    allocation.released("方案返工：" + reason, now), allocation)) {
                throw conflict("RESOURCE_ALLOCATION_CONFLICT", "返工释放资源时状态已变化");
            }
        }
    }

    private void dispatchReservedResources(EmergencyWorkflow workflow, Instant now) {
        List<ResourceAllocation> reservations = resourceAllocationPort
                .findByPlanVersion(workflow.planId(), workflow.planVersion()).stream()
                .filter(item -> item.status() == ResourceAllocationStatus.RESERVED)
                .sorted(java.util.Comparator.comparing(item -> item.resource().resourceId()))
                .toList();
        if (reservations.isEmpty()) return;
        Set<String> ids = reservations.stream().map(item -> item.resource().resourceId())
                .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));
        Map<String, EmergencyResource> resources = lockedResourceMap(ids);
        for (ResourceAllocation allocation : reservations) {
            EmergencyResource original = requireLockedResource(resources, allocation.resource().resourceId());
            EmergencyResource updated = original.dispatch(allocation.resource().quantity());
            updateInventory(updated, original.lockVersion());
            resources.put(updated.resourceId(), updated);
            if (!resourceAllocationPort.update(allocation.dispatched(now), allocation)) {
                throw conflict("RESOURCE_ALLOCATION_CONFLICT", "资源调度状态已变化");
            }
        }
    }

    private Map<String, EmergencyResource> lockedResourceMap(Set<String> resourceIds) {
        return resourceDataPort.lockByResourceIds(resourceIds).stream()
                .collect(java.util.stream.Collectors.toMap(
                        EmergencyResource::resourceId, item -> item,
                        (left, right) -> left, LinkedHashMap::new));
    }

    private EmergencyResource requireLockedResource(
            Map<String, EmergencyResource> resources, String resourceId
    ) {
        EmergencyResource resource = resources.get(resourceId);
        if (resource == null) {
            throw conflict("RESOURCE_NOT_FOUND", "资源库存记录不存在或已删除：" + resourceId);
        }
        return resource;
    }

    private void updateInventory(EmergencyResource resource, long expectedLockVersion) {
        if (!resourceDataPort.updateInventory(resource, expectedLockVersion)) {
            throw conflict("RESOURCE_INVENTORY_CONFLICT", "资源库存已被其他事件占用，请重试");
        }
    }

    private void validateFeasibility(DispatchPlan plan, ProfessionalReview review) {
        if (plan.hasResourceShortage()) {
            if (review.resourceFeasibility() != ResourceFeasibility.FEASIBLE_WITH_GAP) {
                throw new IllegalArgumentException("带资源缺口方案通过时必须选择“有缺口但可执行”");
            }
            if (review.coordinationRequirements() == null
                    || review.coordinationRequirements().isBlank()) {
                throw new IllegalArgumentException("带资源缺口方案通过时必须填写协调措施");
            }
        } else if (review.resourceFeasibility() != ResourceFeasibility.FEASIBLE) {
            throw new IllegalArgumentException("无资源缺口方案通过时应选择“可行”");
        }
    }

    private void requireStructuredCity(EmergencyEvent event) {
        if (!event.hasStructuredCity()) {
            throw new IllegalArgumentException("事件缺少发生城市，请先补充event_city_code和event_city_name");
        }
    }

    private EmergencyWorkflow createLegacyWorkflow(EmergencyEvent event, DispatchPlan plan) {
        return unitOfWork.required(() -> {
            Optional<EmergencyWorkflow> existing =
                    workflowRepository.findWorkflowByEventId(event.eventId());
            if (existing.isPresent()) {
                return existing.get();
            }
            EmergencyWorkflow workflow = createLegacyWorkflowRecord(event, plan);
            if (!workflowRepository.insertWorkflow(workflow)) {
                return workflowRepository.findWorkflowByEventId(event.eventId())
                        .orElseThrow(() -> conflict(
                                "WORKFLOW_CREATE_CONFLICT", "工作流已由其他请求创建"
                        ));
            }
            return workflow;
        });
    }

    private EmergencyWorkflow createLegacyWorkflowRecord(EmergencyEvent event, DispatchPlan plan) {
        WorkflowStatus status = switch (plan.status()) {
            case GENERATING -> WorkflowStatus.GENERATING;
            case WAITING_APPROVAL -> WorkflowStatus.WAITING_LEVEL_1_SUBMISSION;
            case FAILED -> WorkflowStatus.GENERATION_FAILED;
            case REJECTED -> WorkflowStatus.WAITING_GENERATION;
            case APPROVED -> WorkflowStatus.PUBLISHED;
        };
        WorkflowStage stage = status.terminal() ? null : WorkflowStage.LEVEL_1;
        return new EmergencyWorkflow(
                "WF-" + UUID.randomUUID(), event.eventId(), stage, status,
                plan.planId(), plan.version(), 0,
                plan.updatedAt(), plan.createdAt(), plan.updatedAt()
        );
    }

    private EmergencyWorkflowView viewForEvent(EmergencyEvent event) {
        Optional<EmergencyWorkflow> workflow =
                workflowRepository.findWorkflowByEventId(event.eventId());
        if (workflow.isPresent()) {
            return view(workflow.get());
        }
        return new EmergencyWorkflowView(
                null, event,
                dispatchRepository.findLatestByEventId(event.eventId()).orElse(null),
                null, null, List.of()
        );
    }

    private EmergencyWorkflowView view(EmergencyWorkflow workflow) {
        EmergencyEvent event = eventPort.findById(workflow.eventId())
                .orElseThrow(() -> eventNotFound("异常事件不存在"));
        DispatchPlan plan = workflow.planId() == null || workflow.planVersion() < 1
                ? null
                : dispatchRepository.findVersion(workflow.planId(), workflow.planVersion())
                .orElse(null);
        List<ResourceAllocation> allocations = plan == null ? List.of()
                : resourceAllocationPort.findByPlanVersion(plan.planId(), plan.version());
        boolean resourcesReleased = !allocations.isEmpty() && allocations.stream()
                .allMatch(item -> item.status() == ResourceAllocationStatus.RELEASED);
        return new EmergencyWorkflowView(
                workflow,
                event,
                plan,
                workflowRepository.findLatestReview(workflow.workflowId()).orElse(null),
                workflowRepository.findLatestDecision(workflow.workflowId()).orElse(null),
                workflowRepository.findActions(workflow.workflowId()),
                resourcesReleased
        );
    }

    private DispatchPlan requireCurrentWaitingPlan(EmergencyWorkflow workflow) {
        DispatchPlan plan = dispatchRepository.findVersion(
                workflow.planId(), workflow.planVersion()
        ).orElseThrow(() -> notFound("当前方案版本不存在"));
        if (plan.status() != DispatchStatus.WAITING_APPROVAL) {
            throw conflict("DISPATCH_STATE_CONFLICT", "当前方案不处于待审核状态");
        }
        return plan;
    }

    private NoticeSnapshot notice(
            EmergencyWorkflow workflow,
            EmergencyEvent event,
            DispatchPlan plan,
            ProfessionalReview review,
            String commandOpinion,
            Instant now
    ) {
        String eventNumber = event.customId().isBlank() ? event.eventId() : event.customId();
        String noticeNumber = "NT-" + workflow.workflowId().substring(3).toUpperCase();
        String title = "关于" + eventNumber + eventTypeName(event.eventType()) + "应急处置方案的通告";
        return new NoticeSnapshot(
                noticeNumber, title, event, plan.planId(), plan.version(),
                plan.resourceRequirements(), plan.allocatedResources(),
                plan.resourceShortages(), plan.rescuePlan(), review.eventSeverity(),
                review.impactAssessment(), review.coordinationRequirements(),
                review.reviewOpinion(), optionalLength(commandOpinion, "省级批示", 500), now
        );
    }

    private ModelRequest proposalRequest(
            EmergencyEvent event,
            DispatchPlan previous,
            String feedback,
            List<EmergencyResource> catalog
    ) {
        String system = """
                你是福建公路应急调度需求分析器。资源数据库是唯一可信来源。
                必须输出严格JSON对象，仅包含resourceRequirements和rescuePlan。
                resourceRequirements是数组，每项仅包含resourceTypeCode、quantity、purpose。
                resourceTypeCode必须逐字选自用户提供的资源类型白名单，不得创造其他类型。
                quantity必须是大于0的整数。不得输出资源ID、资源名称、来源城市、距离、库存或到达时间。
                即使当前库存可能不足，也只能提出白名单内资源的真实需求，缺口由系统计算。
                rescuePlan必须是单个JSON字符串，禁止输出对象或数组；字符串内用中文自然段覆盖现场安全、交通组织、救援处置和信息报送，不得声称工单已经审批或下发。
                输出格式示例：{"resourceRequirements":[{"resourceTypeCode":"ROAD_RESCUE_TEAM","quantity":1,"purpose":"现场抢通"}],"rescuePlan":"先设置警戒并疏导交通，再开展道路抢通，持续报送处置进展。"}
                """.strip();
        StringBuilder user = new StringBuilder("""
                事件ID：%s
                事件类型：%s（%s）
                事件城市：%s（%s）
                发生时间：%s
                事件描述：%s
                可选资源类型及当前城市库存摘要：
                %s
                """.formatted(
                event.customId().isBlank() ? event.eventId() : event.customId(),
                eventTypeName(event.eventType()), event.eventType(),
                event.cityName(), event.cityCode(),
                event.occurrenceTime() == null ? "未知" : event.occurrenceTime(),
                event.description(), catalogPrompt(catalog)
        ));
        if (previous != null) {
            user.append("\n上一版资源需求：").append(previous.resourceRequirements());
            user.append("\n上一版实际分配：").append(previous.allocatedResources());
            user.append("\n上一版资源缺口：").append(previous.resourceShortages());
            user.append("\n上一版救援方案：").append(previous.rescuePlan());
            user.append("\n人工退回意见：").append(feedback);
            user.append("\n请针对退回意见返工，不能原样重复上一版。");
        }
        return new ModelRequest(system, user.toString(), List.of(), 0.1);
    }

    private List<ResourceRequirement> enrichRequirements(
            DispatchPlanProposal proposal,
            List<EmergencyResource> catalog
    ) {
        if (proposal.resourceRequirements().isEmpty()) {
            throw new IllegalArgumentException("模型没有提出资源需求");
        }
        Map<String, EmergencyResource> types = new LinkedHashMap<>();
        catalog.stream().sorted(java.util.Comparator.comparing(EmergencyResource::resourceId))
                .forEach(item -> types.putIfAbsent(item.typeCode(), item));
        Set<String> seen = new java.util.HashSet<>();
        return proposal.resourceRequirements().stream().map(item -> {
            String code = requireLength(item.resourceTypeCode(), "资源类型编码", 40)
                    .toUpperCase(Locale.ROOT);
            EmergencyResource type = types.get(code);
            if (type == null) {
                throw new IllegalArgumentException("模型使用了数据库不存在的资源类型：" + code);
            }
            if (!seen.add(code)) {
                throw new IllegalArgumentException("同一资源类型不能重复提出：" + code);
            }
            if (item.quantity() < 1 || item.quantity() > 999) {
                throw new IllegalArgumentException("资源需求数量必须在1到999之间");
            }
            return new ResourceRequirement(
                    code, type.type(), item.quantity(), type.unit(),
                    requireLength(item.purpose(), "资源用途", 300)
            );
        }).toList();
    }

    private String catalogPrompt(List<EmergencyResource> catalog) {
        Map<String, List<EmergencyResource>> grouped = catalog.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        EmergencyResource::typeCode, java.util.TreeMap::new,
                        java.util.stream.Collectors.toList()));
        StringBuilder result = new StringBuilder();
        grouped.forEach((code, resources) -> {
            EmergencyResource first = resources.get(0);
            result.append(code).append('|').append(first.type()).append('|')
                    .append(first.unit()).append('|').append(first.capability()).append('|');
            resources.stream().sorted(java.util.Comparator.comparing(EmergencyResource::cityCode))
                    .forEach(item -> result.append(item.city()).append(':')
                            .append(item.availableQuantity()).append(item.unit()).append(' '));
            result.append('\n');
        });
        return result.toString().strip();
    }

    private String rescuePlanWithFacts(
            String modelPlan,
            List<AllocatedResource> allocations,
            List<cn.fj.roadagent.domain.dispatch.ResourceShortage> shortages
    ) {
        StringBuilder result = new StringBuilder(requireLength(modelPlan, "模型救援方案", 10000));
        result.append("\n\n数据库资源调度安排：");
        if (allocations.isEmpty()) {
            result.append("当前未匹配到可调度资源。");
        } else {
            allocations.forEach(item -> result.append("\n- ")
                    .append(item.dispatchScope() == cn.fj.roadagent.domain.dispatch.DispatchScope.LOCAL
                            ? "同城调度" : "跨市增援")
                    .append('：').append(item.sourceCityName()).append(item.resourceName())
                    .append(' ').append(item.quantity()).append(item.unit())
                    .append("，城市级估算距离").append(item.estimatedDistanceKm()).append("公里。"));
        }
        if (!shortages.isEmpty()) {
            result.append("\n资源缺口：");
            shortages.forEach(item -> result.append("\n- ").append(item.resourceTypeName())
                    .append("需求").append(item.requiredQuantity()).append(item.unit())
                    .append("，已匹配").append(item.allocatedQuantity()).append(item.unit())
                    .append("，缺口").append(item.shortageQuantity()).append(item.unit()).append('。'));
        }
        result.append("\n上述距离仅用于城市级资源排序，不代表实际道路里程或到达时间。");
        return result.toString();
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

    private WorkflowAction action(
            EmergencyWorkflow workflow,
            WorkflowActionType type,
            WorkflowStage fromStage,
            WorkflowStage toStage,
            WorkflowStatus fromStatus,
            WorkflowStatus toStatus,
            String comment,
            String idempotencyKey,
            Instant now
    ) {
        return new WorkflowAction(
                "WA-" + UUID.randomUUID(), workflow.workflowId(), type,
                fromStage, toStage, fromStatus, toStatus,
                workflow.planId(), workflow.planVersion(), comment, null,
                requireIdempotency(idempotencyKey), now
        );
    }

    private void insertAction(WorkflowAction action) {
        if (!workflowRepository.insertAction(action)) {
            throw conflict("WORKFLOW_IDEMPOTENCY_CONFLICT", "该操作已经提交，请刷新后查看");
        }
    }

    private Optional<WorkflowAction> duplicateAction(String workflowId, String key) {
        return workflowRepository.findActionByIdempotencyKey(
                workflowId, requireIdempotency(key)
        );
    }

    private void ensureIdempotencyAvailable(String workflowId, String key) {
        if (duplicateAction(workflowId, key).isPresent()) {
            throw conflict("WORKFLOW_IDEMPOTENCY_CONFLICT", "该操作已经提交");
        }
    }

    private void updateWorkflow(EmergencyWorkflow next, long expectedLockVersion) {
        if (!workflowRepository.updateWorkflow(next, expectedLockVersion)) {
            throw conflict("WORKFLOW_VERSION_CONFLICT", "工作流版本已变化，请刷新后重试");
        }
    }

    private EmergencyWorkflow lockWorkflow(String workflowId) {
        return workflowRepository.lockWorkflow(workflowId)
                .orElseThrow(() -> workflowNotFound("应急工作流不存在"));
    }

    private void requireLevel1Workflow(EmergencyWorkflow workflow) {
        if (workflow.currentStage() != WorkflowStage.LEVEL_1 || workflow.status().terminal()) {
            throw conflict("WORKFLOW_STAGE_CONFLICT", "事件已经上报，不能在一级重新生成");
        }
    }

    private void requireLevel1Command(Level1DecisionCommand command) {
        if (command == null || command.decision() == null) {
            throw new IllegalArgumentException("一级处理决定不能为空");
        }
        requireIdempotency(command.idempotencyKey());
    }

    private void requireExpectedWorkflowVersion(EmergencyWorkflow current, long expected) {
        if (current.lockVersion() != expected) {
            throw conflict("WORKFLOW_VERSION_CONFLICT", "工作流版本已变化，请刷新后重试");
        }
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

    private String optionalLength(String value, String label, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(label + "不能超过" + maxLength + "个字符");
        }
        return normalized;
    }

    private String requireIdempotency(String value) {
        return requireLength(value, "幂等键", 100);
    }

    private String systemKey(String prefix) {
        return prefix + ":" + UUID.randomUUID();
    }

    private BusinessRuleException notFound(String message) {
        return new BusinessRuleException("DISPATCH_NOT_FOUND", message);
    }

    private BusinessRuleException workflowNotFound(String message) {
        return new BusinessRuleException("WORKFLOW_NOT_FOUND", message);
    }

    private BusinessRuleException eventNotFound(String message) {
        return new BusinessRuleException("EVENT_NOT_FOUND", message);
    }

    private BusinessRuleException conflict(String code, String message) {
        return new BusinessRuleException(code, message);
    }

    private record GenerationClaim(
            DispatchPlan plan,
            EmergencyWorkflow workflow,
            boolean claimed
    ) {
    }

    private record RevisionClaim(
            DispatchPlan rejectedPlan,
            DispatchPlan nextPlan,
            EmergencyWorkflow workflow
    ) {
    }
}
