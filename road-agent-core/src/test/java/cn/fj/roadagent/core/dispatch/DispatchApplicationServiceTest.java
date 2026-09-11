package cn.fj.roadagent.core.dispatch;

import cn.fj.roadagent.application.dispatch.CommandDecisionCommand;
import cn.fj.roadagent.application.dispatch.DispatchApprovalCommand;
import cn.fj.roadagent.application.dispatch.Level1Decision;
import cn.fj.roadagent.application.dispatch.Level1DecisionCommand;
import cn.fj.roadagent.application.dispatch.NoDispatchCommand;
import cn.fj.roadagent.application.dispatch.ProfessionalReviewCommand;
import cn.fj.roadagent.application.dispatch.ReleaseResourcesCommand;
import cn.fj.roadagent.application.dispatch.ResourceQuery;
import cn.fj.roadagent.application.exception.BusinessRuleException;
import cn.fj.roadagent.application.model.ModelRequest;
import cn.fj.roadagent.application.model.ModelResponse;
import cn.fj.roadagent.application.model.ModelStreamListener;
import cn.fj.roadagent.application.port.AbnormalEventPort;
import cn.fj.roadagent.application.port.ChatModelPort;
import cn.fj.roadagent.application.port.DispatchRepository;
import cn.fj.roadagent.application.port.EmergencyWorkflowRepository;
import cn.fj.roadagent.application.port.ResourceAllocationPort;
import cn.fj.roadagent.application.port.ResourceDataPort;
import cn.fj.roadagent.application.port.UnitOfWork;
import cn.fj.roadagent.domain.dispatch.ApprovalDecision;
import cn.fj.roadagent.domain.dispatch.CommandDecision;
import cn.fj.roadagent.domain.dispatch.CommandDecisionStatus;
import cn.fj.roadagent.domain.dispatch.DispatchPlan;
import cn.fj.roadagent.domain.dispatch.DispatchStatus;
import cn.fj.roadagent.domain.dispatch.EmergencyEvent;
import cn.fj.roadagent.domain.dispatch.EmergencyWorkflow;
import cn.fj.roadagent.domain.dispatch.EmergencyResource;
import cn.fj.roadagent.domain.dispatch.EmergencyResourceStatus;
import cn.fj.roadagent.domain.dispatch.GeoPoint;
import cn.fj.roadagent.domain.dispatch.EventSeverity;
import cn.fj.roadagent.domain.dispatch.ProfessionalReview;
import cn.fj.roadagent.domain.dispatch.ResourceFeasibility;
import cn.fj.roadagent.domain.dispatch.ResourceAllocation;
import cn.fj.roadagent.domain.dispatch.ResourceAllocationStatus;
import cn.fj.roadagent.domain.dispatch.ReviewStatus;
import cn.fj.roadagent.domain.dispatch.WorkflowAction;
import cn.fj.roadagent.domain.dispatch.WorkflowStatus;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchApplicationServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-19T00:00:00Z");

    @Test
    void shouldCompleteThreeLevelWorkflowAndFreezeNotice() {
        Fixture fixture = fixture(new FixedModel());

        DispatchPlan generated = fixture.service.generate(event().eventId());
        EmergencyWorkflow workflow = fixture.workflows.findWorkflowByEventId(event().eventId()).orElseThrow();
        assertEquals(WorkflowStatus.WAITING_LEVEL_1_SUBMISSION, workflow.status());

        var level2 = fixture.service.decideLevel1(new Level1DecisionCommand(
                workflow.workflowId(), Level1Decision.SUBMIT, "现场确认后上报",
                workflow.lockVersion(), "l1-submit"
        ));
        assertEquals(WorkflowStatus.WAITING_LEVEL_2_REVIEW, level2.workflow().status());

        var level3 = fixture.service.review(new ProfessionalReviewCommand(
                workflow.workflowId(), ApprovalDecision.APPROVE,
                EventSeverity.LARGER, ResourceFeasibility.FEASIBLE,
                "预计影响主线交通两小时", "协调交警实施临时管制",
                "专业会商认为方案可行", level2.workflow().lockVersion(), "l2-pass"
        ));
        assertEquals(WorkflowStatus.WAITING_LEVEL_3_DECISION, level3.workflow().status());

        var published = fixture.service.decideCommand(new CommandDecisionCommand(
                workflow.workflowId(), ApprovalDecision.APPROVE, "同意发布",
                level3.workflow().lockVersion(), "l3-publish"
        ));

        assertEquals(WorkflowStatus.PUBLISHED, published.workflow().status());
        assertNull(published.workflow().currentStage());
        assertEquals(DispatchStatus.APPROVED, published.currentPlan().status());
        assertEquals(CommandDecisionStatus.PUBLISHED, published.commandDecision().status());
        assertNotNull(published.commandDecision().noticeSnapshot());
        assertEquals(generated.planId(), published.commandDecision().noticeSnapshot().planId());
        assertTrue(fixture.events.approved);
        assertFalse(fixture.events.pending);
        assertEquals(ResourceAllocationStatus.DISPATCHED,
                fixture.allocations.values.get(0).status());
        assertEquals(1, fixture.resources.resources.get("ER-FZ-ROAD").dispatchedQuantity());
        assertEquals(5, published.timeline().size());
        assertTrue(published.canReleaseResources());

        var released = fixture.service.releaseResources(new ReleaseResourcesCommand(
                workflow.workflowId(), "演练结束，资源归队",
                published.workflow().lockVersion(), "release-all"
        ));
        assertTrue(released.resourcesReleased());
        assertFalse(released.canReleaseResources());
        assertEquals(ResourceAllocationStatus.RELEASED,
                fixture.allocations.values.get(0).status());
        assertEquals(5, fixture.resources.resources.get("ER-FZ-ROAD").availableQuantity());
    }

    @Test
    void legacyApprovalEndpointShouldOnlySubmitLevelOne() {
        Fixture fixture = fixture(new FixedModel());
        DispatchPlan generated = fixture.service.generate(event().eventId());

        DispatchPlan returned = fixture.service.decide(new DispatchApprovalCommand(
                generated.planId(), ApprovalDecision.APPROVE, "一级同意上报",
                generated.version(), "legacy-submit"
        ));

        EmergencyWorkflow workflow = fixture.workflows.findWorkflowByPlanId(generated.planId()).orElseThrow();
        assertEquals(WorkflowStatus.WAITING_LEVEL_2_REVIEW, workflow.status());
        assertEquals(DispatchStatus.WAITING_APPROVAL, returned.status());
        assertFalse(fixture.events.approved);
    }

    @Test
    void legacyApprovalShouldCreateMissingWorkflowBeforeSubmitting() {
        Fixture fixture = fixture(new FixedModel());
        DispatchPlan legacy = DispatchPlan.generating("DP-LEGACY", event(), 1L, NOW)
                .generated(List.of(new cn.fj.roadagent.domain.dispatch.SuggestedResource(
                        "抢险队伍", "道路抢险人员", 1, "组", "现场抢通"
                )), "设置警戒并抢通。", NOW);
        fixture.plans.insert(legacy);

        DispatchPlan returned = fixture.service.decide(new DispatchApprovalCommand(
                legacy.planId(), ApprovalDecision.APPROVE, "纳入三级并上报",
                legacy.version(), "legacy-migrate"
        ));

        assertEquals(DispatchStatus.WAITING_APPROVAL, returned.status());
        assertEquals(WorkflowStatus.WAITING_LEVEL_2_REVIEW,
                fixture.workflows.findWorkflowByPlanId(legacy.planId()).orElseThrow().status());
        assertFalse(fixture.events.approved);
    }

    @Test
    void secondLevelReturnShouldKeepHistoryAndGenerateNextVersion() {
        Fixture fixture = fixture(new FixedModel());
        DispatchPlan version1 = fixture.service.generate(event().eventId());
        EmergencyWorkflow workflow = fixture.workflows.findWorkflowByEventId(event().eventId()).orElseThrow();
        var level2 = fixture.service.decideLevel1(new Level1DecisionCommand(
                workflow.workflowId(), Level1Decision.SUBMIT, "上报",
                workflow.lockVersion(), "l1-submit"
        ));

        var revised = fixture.service.review(new ProfessionalReviewCommand(
                workflow.workflowId(), ApprovalDecision.REJECT,
                null, ResourceFeasibility.NEEDS_ADJUSTMENT,
                null, null, "补充夜间照明和绕行措施",
                level2.workflow().lockVersion(), "l2-return"
        ));

        assertEquals(WorkflowStatus.WAITING_LEVEL_1_SUBMISSION, revised.workflow().status());
        assertEquals(2L, revised.currentPlan().version());
        assertEquals(DispatchStatus.WAITING_APPROVAL, revised.currentPlan().status());
        assertEquals(DispatchStatus.REJECTED,
                fixture.plans.findVersion(version1.planId(), 1L).orElseThrow().status());
        assertEquals(ReviewStatus.RETURNED,
                fixture.workflows.findLatestReview(workflow.workflowId()).orElseThrow().status());
        assertFalse(fixture.events.approved);
    }

    @Test
    void modelFailureShouldRemainAtLevelOneAndRetrySameVersion() {
        FailOnceModel model = new FailOnceModel();
        Fixture fixture = fixture(model);

        assertThrows(RuntimeException.class, () -> fixture.service.generate(event().eventId()));
        EmergencyWorkflow failedWorkflow = fixture.workflows
                .findWorkflowByEventId(event().eventId()).orElseThrow();
        assertEquals(WorkflowStatus.GENERATION_FAILED, failedWorkflow.status());
        assertEquals(DispatchStatus.FAILED,
                fixture.plans.findLatestByEventId(event().eventId()).orElseThrow().status());

        DispatchPlan retried = fixture.service.generate(event().eventId());
        assertEquals(1L, retried.version());
        assertEquals(DispatchStatus.WAITING_APPROVAL, retried.status());
        assertEquals(2, model.calls);
    }

    @Test
    void interruptedStaleGenerationShouldRecoverSamePlanAndKeepAuditTrail() {
        Fixture fixture = fixture(new FixedModel());
        Instant interruptedAt = NOW.minus(Duration.ofMinutes(10));
        DispatchPlan stuckPlan = DispatchPlan.generating(
                "DP-STUCK", event(), 1L, interruptedAt
        );
        EmergencyWorkflow stuckWorkflow = EmergencyWorkflow.generating(
                "WF-STUCK", event().eventId(), stuckPlan.planId(), stuckPlan.version(),
                interruptedAt
        );
        assertTrue(fixture.plans.insert(stuckPlan));
        assertTrue(fixture.workflows.insertWorkflow(stuckWorkflow));

        DispatchPlan recovered = fixture.service.generate(event().eventId());

        assertEquals("DP-STUCK", recovered.planId());
        assertEquals(1L, recovered.version());
        assertEquals(DispatchStatus.WAITING_APPROVAL, recovered.status());
        assertEquals(WorkflowStatus.WAITING_LEVEL_1_SUBMISSION,
                fixture.workflows.findWorkflow("WF-STUCK").orElseThrow().status());
        assertTrue(fixture.workflows.findActions("WF-STUCK").stream()
                .anyMatch(action -> action.actionType()
                        == cn.fj.roadagent.domain.dispatch.WorkflowActionType.GENERATION_RETRIED));
    }

    @Test
    void shouldRejectStaleWorkflowVersionWithoutChangingStage() {
        Fixture fixture = fixture(new FixedModel());
        fixture.service.generate(event().eventId());
        EmergencyWorkflow workflow = fixture.workflows.findWorkflowByEventId(event().eventId()).orElseThrow();

        BusinessRuleException exception = assertThrows(BusinessRuleException.class, () ->
                fixture.service.decideLevel1(new Level1DecisionCommand(
                        workflow.workflowId(), Level1Decision.SUBMIT, "上报",
                        99L, "stale"
                ))
        );

        assertEquals("WORKFLOW_VERSION_CONFLICT", exception.errorCode());
        assertEquals(WorkflowStatus.WAITING_LEVEL_1_SUBMISSION,
                fixture.workflows.findWorkflow(workflow.workflowId()).orElseThrow().status());
    }

    @Test
    void shouldRecordConfirmedNoDispatchWithoutCreatingOrder() {
        Fixture fixture = fixture(new FixedModel());

        fixture.service.markNoDispatch(new NoDispatchCommand(
                event().eventId(), "现场核实为误报", true
        ));

        EmergencyWorkflow workflow = fixture.workflows.findWorkflowByEventId(event().eventId()).orElseThrow();
        assertEquals(WorkflowStatus.NO_DISPATCH, workflow.status());
        assertTrue(fixture.events.noDispatch);
        assertEquals("现场核实为误报", fixture.events.reason);
        assertTrue(fixture.plans.versions.isEmpty());
        assertEquals(1, fixture.workflows.findActions(workflow.workflowId()).size());
    }

    @Test
    void shortageShouldRequireConditionalReviewAndCommandOpinion() {
        Fixture fixture = fixture(new DemandModel(10));
        DispatchPlan plan = fixture.service.generate(event().eventId());
        assertEquals(5, plan.resourceShortages().get(0).shortageQuantity());
        EmergencyWorkflow workflow = fixture.workflows.findWorkflowByEventId(event().eventId()).orElseThrow();
        var level2 = fixture.service.decideLevel1(new Level1DecisionCommand(
                workflow.workflowId(), Level1Decision.SUBMIT, "带缺口上报",
                workflow.lockVersion(), "gap-l1"
        ));

        assertThrows(IllegalArgumentException.class, () -> fixture.service.review(
                new ProfessionalReviewCommand(
                        workflow.workflowId(), ApprovalDecision.APPROVE,
                        EventSeverity.LARGER, ResourceFeasibility.FEASIBLE,
                        "道路暂时中断", "协调省级资源",
                        "有缺口仍可执行", level2.workflow().lockVersion(), "gap-invalid"
                )
        ));
        var level3 = fixture.service.review(new ProfessionalReviewCommand(
                workflow.workflowId(), ApprovalDecision.APPROVE,
                EventSeverity.LARGER, ResourceFeasibility.FEASIBLE_WITH_GAP,
                "道路暂时中断", "协调后续批次资源补充",
                "先使用已匹配资源处置", level2.workflow().lockVersion(), "gap-pass"
        ));

        assertThrows(IllegalArgumentException.class, () -> fixture.service.decideCommand(
                new CommandDecisionCommand(
                        workflow.workflowId(), ApprovalDecision.APPROVE, " ",
                        level3.workflow().lockVersion(), "gap-command-invalid"
                )
        ));
        var published = fixture.service.decideCommand(new CommandDecisionCommand(
                workflow.workflowId(), ApprovalDecision.APPROVE, "同意先期执行并继续协调缺口资源",
                level3.workflow().lockVersion(), "gap-command-pass"
        ));
        assertEquals(1, published.commandDecision().noticeSnapshot().resourceShortages().size());
    }

    @Test
    void unknownModelResourceTypeShouldFailWithoutInventoryReservation() {
        Fixture fixture = fixture(new UnknownResourceModel());

        assertThrows(RuntimeException.class, () -> fixture.service.generate(event().eventId()));

        assertTrue(fixture.allocations.values.isEmpty());
        assertEquals(5, fixture.resources.resources.get("ER-FZ-ROAD").availableQuantity());
        assertEquals(WorkflowStatus.GENERATION_FAILED,
                fixture.workflows.findWorkflowByEventId(event().eventId()).orElseThrow().status());
    }

    private Fixture fixture(ChatModelPort model) {
        TestEventPort events = new TestEventPort(event());
        TestDispatchRepository plans = new TestDispatchRepository();
        TestWorkflowRepository workflows = new TestWorkflowRepository();
        TestResourceDataPort resources = new TestResourceDataPort();
        TestResourceAllocationPort allocations = new TestResourceAllocationPort();
        DispatchApplicationService service = new DispatchApplicationService(
                events, plans, workflows, model, resources, allocations,
                new EmergencyResourceAllocator((from, to) -> from.equals(to) ? 0D : 100D),
                new DirectUnitOfWork(),
                Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(2)
        );
        return new Fixture(service, events, plans, workflows, resources, allocations);
    }

    @Test
    void lateSuccessOrFailureMustNotOverwriteANewerGenerationClaim() {
        for (boolean fail : List.of(false, true)) {
            Fixture[] active = new Fixture[1];
            FixedModel model = new FixedModel() {
                @Override
                public <T> T generateStructured(ModelRequest request, Class<T> resultType) {
                    // While this model call was running, a retry acquired the same plan version.
                    var repository = active[0].workflows;
                    var current = repository.findWorkflowByEventId(event().eventId()).orElseThrow();
                    var retry = current.retryGeneration(NOW.plusSeconds(180));
                    assertTrue(repository.updateWorkflow(retry, current.lockVersion()));
                    if (fail) throw new RuntimeException("old model request failed late");
                    return super.generateStructured(request, resultType);
                }
            };
            active[0] = fixture(model);
            Fixture f = active[0];
            assertThrows(RuntimeException.class, () -> f.service.generate(event().eventId()));
            var latest = f.workflows.findWorkflowByEventId(event().eventId()).orElseThrow();
            assertEquals(WorkflowStatus.GENERATING, latest.status());
            assertEquals(1, latest.lockVersion());
            assertEquals(DispatchStatus.GENERATING, f.plans.findLatestByEventId(event().eventId()).orElseThrow().status());
            assertTrue(f.allocations.values.isEmpty());
            assertEquals(5, f.resources.resources.get("ER-FZ-ROAD").availableQuantity());
        }
    }

    private EmergencyEvent event() {
        return new EmergencyEvent(
                "202607280000000001",
                "AGT20260728EVT000000000000000001",
                NOW.minusSeconds(3600),
                "DT01",
                "福州市某道路发生边坡崩塌",
                "350100",
                "福州"
        );
    }

    private record Fixture(
            DispatchApplicationService service,
            TestEventPort events,
            TestDispatchRepository plans,
            TestWorkflowRepository workflows,
            TestResourceDataPort resources,
            TestResourceAllocationPort allocations
    ) {
    }

    private static class FixedModel implements ChatModelPort {
        @Override
        public ModelResponse generate(ModelRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T generateStructured(ModelRequest request, Class<T> resultType) {
            return resultType.cast(new DispatchPlanProposal(
                    List.of(new DispatchPlanProposal.ProposedResource(
                            "ROAD_RESCUE_TEAM", 1, "现场警戒和抢通"
                    )),
                    "先设置警戒并疏导交通，再开展边坡排查和道路抢通。"
            ));
        }

        @Override
        public void stream(ModelRequest request, ModelStreamListener listener) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FailOnceModel extends FixedModel {
        private int calls;

        @Override
        public <T> T generateStructured(ModelRequest request, Class<T> resultType) {
            calls++;
            if (calls == 1) {
                throw new RuntimeException("模型暂时不可用");
            }
            return super.generateStructured(request, resultType);
        }
    }

    private static final class DemandModel extends FixedModel {
        private final int quantity;

        private DemandModel(int quantity) {
            this.quantity = quantity;
        }

        @Override
        public <T> T generateStructured(ModelRequest request, Class<T> resultType) {
            return resultType.cast(new DispatchPlanProposal(
                    List.of(new DispatchPlanProposal.ProposedResource(
                            "ROAD_RESCUE_TEAM", quantity, "现场抢通"
                    )),
                    "先设置警戒并疏导交通，再开展道路抢通和持续信息报送。"
            ));
        }
    }

    private static final class UnknownResourceModel extends FixedModel {
        @Override
        public <T> T generateStructured(ModelRequest request, Class<T> resultType) {
            return resultType.cast(new DispatchPlanProposal(
                    List.of(new DispatchPlanProposal.ProposedResource(
                            "MADE_UP_RESOURCE", 1, "测试"
                    )),
                    "测试非法资源类型不会进入库存占用。"
            ));
        }
    }

    private static final class DirectUnitOfWork implements UnitOfWork {
        @Override
        public <T> T required(Supplier<T> operation) {
            return operation.get();
        }
    }

    private static final class TestResourceDataPort implements ResourceDataPort {
        private final Map<String, EmergencyResource> resources = new LinkedHashMap<>();

        private TestResourceDataPort() {
            EmergencyResource resource = new EmergencyResource(
                    "ER-FZ-ROAD", "ROAD_RESCUE_TEAM", "公路抢险队伍",
                    "福州市公路抢险队伍资源池", "350100", "福州", "组",
                    "道路抢通、边坡应急处置", List.of("DT01", "ET101"),
                    5, 5, 0, 0, 1, EmergencyResourceStatus.ACTIVE, 0
            );
            resources.put(resource.resourceId(), resource);
        }

        @Override
        public List<EmergencyResource> search(ResourceQuery query) {
            return resources.values().stream().filter(EmergencyResource::available).toList();
        }

        @Override
        public List<EmergencyResource> listActiveForPlanning(String eventType) {
            return resources.values().stream()
                    .filter(EmergencyResource::available)
                    .filter(item -> item.appliesTo(eventType))
                    .toList();
        }

        @Override
        public List<EmergencyResource> lockByTypeCodes(Set<String> typeCodes) {
            return resources.values().stream()
                    .filter(item -> typeCodes.contains(item.typeCode()))
                    .sorted(Comparator.comparing(EmergencyResource::resourceId))
                    .toList();
        }

        @Override
        public List<EmergencyResource> lockByResourceIds(Set<String> resourceIds) {
            return resources.values().stream()
                    .filter(item -> resourceIds.contains(item.resourceId()))
                    .sorted(Comparator.comparing(EmergencyResource::resourceId))
                    .toList();
        }

        @Override
        public Map<String, GeoPoint> cityCenters() {
            return Map.of("350100", new GeoPoint(119.2965, 26.0745));
        }

        @Override
        public boolean updateInventory(EmergencyResource resource, long expectedLockVersion) {
            EmergencyResource current = resources.get(resource.resourceId());
            if (current == null || current.lockVersion() != expectedLockVersion) {
                return false;
            }
            resources.put(resource.resourceId(), resource);
            return true;
        }
    }

    private static final class TestResourceAllocationPort implements ResourceAllocationPort {
        private final List<ResourceAllocation> values = new ArrayList<>();

        @Override
        public boolean insert(ResourceAllocation allocation) {
            boolean duplicate = values.stream().anyMatch(item ->
                    item.planId().equals(allocation.planId())
                            && item.planVersion() == allocation.planVersion()
                            && item.resource().resourceId().equals(allocation.resource().resourceId()));
            if (duplicate) return false;
            values.add(allocation);
            return true;
        }

        @Override
        public List<ResourceAllocation> findByPlanVersion(String planId, long planVersion) {
            return values.stream().filter(item -> item.planId().equals(planId)
                    && item.planVersion() == planVersion).toList();
        }

        @Override
        public boolean update(ResourceAllocation allocation, ResourceAllocation expected) {
            int index = values.indexOf(expected);
            if (index < 0) return false;
            values.set(index, allocation);
            return true;
        }
    }

    private static final class TestEventPort implements AbnormalEventPort {
        private final EmergencyEvent event;
        private boolean pending = true;
        private boolean approved;
        private boolean noDispatch;
        private String reason;

        private TestEventPort(EmergencyEvent event) {
            this.event = event;
        }

        @Override
        public Optional<EmergencyEvent> findById(String eventId) {
            return event.eventId().equals(eventId) ? Optional.of(event) : Optional.empty();
        }

        @Override
        public Optional<EmergencyEvent> findPendingById(String eventId) {
            return pending && event.eventId().equals(eventId) ? Optional.of(event) : Optional.empty();
        }

        @Override
        public Optional<EmergencyEvent> lockPendingById(String eventId) {
            return findPendingById(eventId);
        }

        @Override
        public Optional<EmergencyEvent> findNextPending() {
            return pending ? Optional.of(event) : Optional.empty();
        }

        @Override
        public long countPending() {
            return pending ? 1 : 0;
        }

        @Override
        public boolean markDispatchApproved(String eventId, Instant updateTime) {
            if (!pending || !event.eventId().equals(eventId)) {
                return false;
            }
            pending = false;
            approved = true;
            return true;
        }

        @Override
        public boolean markNoDispatch(String eventId, String reason, Instant updateTime) {
            if (!pending || !event.eventId().equals(eventId)) {
                return false;
            }
            pending = false;
            noDispatch = true;
            this.reason = reason;
            return true;
        }
    }

    private static final class TestDispatchRepository implements DispatchRepository {
        private final Map<String, DispatchPlan> versions = new LinkedHashMap<>();

        @Override
        public boolean insert(DispatchPlan plan) {
            return versions.putIfAbsent(key(plan), plan) == null;
        }

        @Override
        public Optional<DispatchPlan> findLatestByPlanId(String planId) {
            return versions.values().stream()
                    .filter(plan -> plan.planId().equals(planId))
                    .max(Comparator.comparingLong(DispatchPlan::version));
        }

        @Override
        public Optional<DispatchPlan> findLatestByEventId(String eventId) {
            return versions.values().stream()
                    .filter(plan -> plan.event().eventId().equals(eventId))
                    .max(Comparator.comparingLong(DispatchPlan::version));
        }

        @Override
        public Optional<DispatchPlan> findVersion(String planId, long version) {
            return Optional.ofNullable(versions.get(planId + ":" + version));
        }

        @Override
        public boolean restartGeneration(DispatchPlan plan, Instant staleBefore) {
            return replace(plan);
        }

        @Override
        public boolean updateGenerated(DispatchPlan plan) {
            return replace(plan);
        }

        @Override
        public boolean updateRejected(DispatchPlan plan) {
            return replace(plan);
        }

        @Override
        public boolean updateApproved(DispatchPlan plan) {
            return replace(plan);
        }

        @Override
        public boolean updateFailed(DispatchPlan plan) {
            return replace(plan);
        }

        private boolean replace(DispatchPlan plan) {
            if (!versions.containsKey(key(plan))) {
                return false;
            }
            versions.put(key(plan), plan);
            return true;
        }

        private String key(DispatchPlan plan) {
            return plan.planId() + ":" + plan.version();
        }
    }

    private static final class TestWorkflowRepository implements EmergencyWorkflowRepository {
        private final Map<String, EmergencyWorkflow> workflows = new LinkedHashMap<>();
        private final Map<String, ProfessionalReview> reviews = new LinkedHashMap<>();
        private final Map<String, CommandDecision> decisions = new LinkedHashMap<>();
        private final List<WorkflowAction> actions = new ArrayList<>();

        @Override
        public boolean insertWorkflow(EmergencyWorkflow workflow) {
            if (workflows.values().stream().anyMatch(item -> item.eventId().equals(workflow.eventId()))) {
                return false;
            }
            return workflows.putIfAbsent(workflow.workflowId(), workflow) == null;
        }

        @Override
        public Optional<EmergencyWorkflow> findWorkflow(String workflowId) {
            return Optional.ofNullable(workflows.get(workflowId));
        }

        @Override
        public Optional<EmergencyWorkflow> findWorkflowByEventId(String eventId) {
            return workflows.values().stream().filter(item -> item.eventId().equals(eventId)).findFirst();
        }

        @Override
        public Optional<EmergencyWorkflow> findWorkflowByPlanId(String planId) {
            return workflows.values().stream().filter(item -> planId.equals(item.planId())).findFirst();
        }

        @Override
        public Optional<EmergencyWorkflow> lockWorkflow(String workflowId) {
            return findWorkflow(workflowId);
        }

        @Override
        public boolean updateWorkflow(EmergencyWorkflow workflow, long expectedLockVersion) {
            EmergencyWorkflow current = workflows.get(workflow.workflowId());
            if (current == null || current.lockVersion() != expectedLockVersion) {
                return false;
            }
            workflows.put(workflow.workflowId(), workflow);
            return true;
        }

        @Override
        public boolean insertReview(ProfessionalReview review) {
            return reviews.putIfAbsent(review.reviewId(), review) == null;
        }

        @Override
        public Optional<ProfessionalReview> findLatestReview(String workflowId) {
            return reviews.values().stream()
                    .filter(item -> item.workflowId().equals(workflowId))
                    .reduce((first, second) -> second);
        }

        @Override
        public Optional<ProfessionalReview> findPendingReview(String workflowId) {
            return reviews.values().stream()
                    .filter(item -> item.workflowId().equals(workflowId))
                    .filter(item -> item.status() == ReviewStatus.PENDING)
                    .reduce((first, second) -> second);
        }

        @Override
        public boolean updateReview(ProfessionalReview review) {
            ProfessionalReview current = reviews.get(review.reviewId());
            if (current == null || current.status() != ReviewStatus.PENDING) {
                return false;
            }
            reviews.put(review.reviewId(), review);
            return true;
        }

        @Override
        public boolean insertDecision(CommandDecision decision) {
            return decisions.putIfAbsent(decision.decisionId(), decision) == null;
        }

        @Override
        public Optional<CommandDecision> findLatestDecision(String workflowId) {
            return decisions.values().stream()
                    .filter(item -> item.workflowId().equals(workflowId))
                    .reduce((first, second) -> second);
        }

        @Override
        public Optional<CommandDecision> findPendingDecision(String workflowId) {
            return decisions.values().stream()
                    .filter(item -> item.workflowId().equals(workflowId))
                    .filter(item -> item.status() == CommandDecisionStatus.PENDING)
                    .reduce((first, second) -> second);
        }

        @Override
        public boolean updateDecision(CommandDecision decision) {
            CommandDecision current = decisions.get(decision.decisionId());
            if (current == null || current.status() != CommandDecisionStatus.PENDING) {
                return false;
            }
            decisions.put(decision.decisionId(), decision);
            return true;
        }

        @Override
        public boolean insertAction(WorkflowAction action) {
            boolean duplicate = actions.stream().anyMatch(item ->
                    item.workflowId().equals(action.workflowId())
                            && item.idempotencyKey().equals(action.idempotencyKey()));
            if (duplicate) {
                return false;
            }
            actions.add(action);
            return true;
        }

        @Override
        public Optional<WorkflowAction> findActionByIdempotencyKey(
                String workflowId,
                String idempotencyKey
        ) {
            return actions.stream().filter(item ->
                    item.workflowId().equals(workflowId)
                            && item.idempotencyKey().equals(idempotencyKey)).findFirst();
        }

        @Override
        public List<WorkflowAction> findActions(String workflowId) {
            return actions.stream().filter(item -> item.workflowId().equals(workflowId)).toList();
        }

        @Override
        public List<EmergencyWorkflow> findHistory(int offset, int limit) {
            return workflows.values().stream().filter(item -> item.status().terminal())
                    .skip(offset).limit(limit).toList();
        }

        @Override
        public long countHistory() {
            return workflows.values().stream().filter(item -> item.status().terminal()).count();
        }
    }
}
