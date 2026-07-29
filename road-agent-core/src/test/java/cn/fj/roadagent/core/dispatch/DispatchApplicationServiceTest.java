package cn.fj.roadagent.core.dispatch;

import cn.fj.roadagent.application.dispatch.DispatchApprovalCommand;
import cn.fj.roadagent.application.dispatch.NoDispatchCommand;
import cn.fj.roadagent.application.exception.BusinessRuleException;
import cn.fj.roadagent.application.model.ModelRequest;
import cn.fj.roadagent.application.model.ModelResponse;
import cn.fj.roadagent.application.model.ModelStreamListener;
import cn.fj.roadagent.application.port.AbnormalEventPort;
import cn.fj.roadagent.application.port.ChatModelPort;
import cn.fj.roadagent.application.port.DispatchRepository;
import cn.fj.roadagent.application.port.UnitOfWork;
import cn.fj.roadagent.domain.dispatch.ApprovalDecision;
import cn.fj.roadagent.domain.dispatch.DispatchPlan;
import cn.fj.roadagent.domain.dispatch.DispatchStatus;
import cn.fj.roadagent.domain.dispatch.EmergencyEvent;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchApplicationServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-28T00:00:00Z");

    @Test
    void shouldGenerateAndApproveExactlyOnce() {
        TestEventPort eventPort = new TestEventPort(event());
        TestRepository repository = new TestRepository();
        DispatchApplicationService service = service(eventPort, repository);

        DispatchPlan generated = service.generate(event().eventId());
        var command = new DispatchApprovalCommand(
                generated.planId(), ApprovalDecision.APPROVE, "同意",
                generated.version(), "idem-1"
        );

        DispatchPlan first = service.decide(command);
        DispatchPlan duplicate = service.decide(command);

        assertEquals(DispatchStatus.APPROVED, first.status());
        assertEquals(first, duplicate);
        assertTrue(eventPort.approved);
        assertFalse(service.nextPending().isPresent());
    }

    @Test
    void shouldKeepRejectedVersionAndGenerateRevision() {
        TestEventPort eventPort = new TestEventPort(event());
        TestRepository repository = new TestRepository();
        DispatchApplicationService service = service(eventPort, repository);
        DispatchPlan first = service.generate(event().eventId());

        DispatchPlan revised = service.decide(new DispatchApprovalCommand(
                first.planId(), ApprovalDecision.REJECT, "补充夜间照明",
                first.version(), "idem-2"
        ));

        DispatchPlan rejected = repository.findVersion(first.planId(), 1L).orElseThrow();
        assertEquals(DispatchStatus.REJECTED, rejected.status());
        assertEquals("补充夜间照明", rejected.rejectionReason());
        assertEquals(2L, revised.version());
        assertEquals(DispatchStatus.WAITING_APPROVAL, revised.status());
    }

    @Test
    void shouldRecordNoDispatchReasonWithoutCreatingOrder() {
        TestEventPort eventPort = new TestEventPort(event());
        TestRepository repository = new TestRepository();
        DispatchApplicationService service = service(eventPort, repository);

        service.markNoDispatch(new NoDispatchCommand(event().eventId(), "现场已自行恢复", true));

        assertTrue(eventPort.noDispatch);
        assertEquals("现场已自行恢复", eventPort.reason);
        assertTrue(repository.versions.isEmpty());
    }

    @Test
    void shouldPersistFailureAndRetrySameVersion() {
        TestEventPort eventPort = new TestEventPort(event());
        TestRepository repository = new TestRepository();
        FailOnceModel model = new FailOnceModel();
        DispatchApplicationService service = service(eventPort, repository, model);

        assertThrows(RuntimeException.class, () -> service.generate(event().eventId()));
        DispatchPlan failed = repository.findLatestByEventId(event().eventId()).orElseThrow();
        assertEquals(DispatchStatus.FAILED, failed.status());
        assertEquals(1L, failed.version());

        DispatchPlan retried = service.generate(event().eventId());
        assertEquals(DispatchStatus.WAITING_APPROVAL, retried.status());
        assertEquals(1L, retried.version());
        assertEquals(2, model.calls);
    }

    @Test
    void shouldSafelyRetryStaleGeneratingVersion() {
        TestEventPort eventPort = new TestEventPort(event());
        TestRepository repository = new TestRepository();
        repository.insert(DispatchPlan.generating(
                "DP-STALE", event(), 1L, NOW.minus(Duration.ofMinutes(3))
        ));
        DispatchApplicationService service = service(eventPort, repository);

        DispatchPlan retried = service.generate(event().eventId());

        assertEquals("DP-STALE", retried.planId());
        assertEquals(DispatchStatus.WAITING_APPROVAL, retried.status());
        assertEquals(1L, retried.version());
    }

    @Test
    void shouldRejectStaleApprovalVersionAndKeepCurrentState() {
        TestEventPort eventPort = new TestEventPort(event());
        TestRepository repository = new TestRepository();
        DispatchApplicationService service = service(eventPort, repository);
        DispatchPlan first = service.generate(event().eventId());

        BusinessRuleException exception = assertThrows(
                BusinessRuleException.class,
                () -> service.decide(new DispatchApprovalCommand(
                        first.planId(), ApprovalDecision.APPROVE, "同意",
                        99L, "idem-stale"
                ))
        );

        assertEquals("DISPATCH_VERSION_CONFLICT", exception.errorCode());
        assertEquals(DispatchStatus.WAITING_APPROVAL, service.get(first.planId()).status());
        assertTrue(eventPort.pending);
    }

    @Test
    void shouldPreserveAllVersionsAcrossMultipleReworks() {
        TestEventPort eventPort = new TestEventPort(event());
        TestRepository repository = new TestRepository();
        DispatchApplicationService service = service(eventPort, repository);
        DispatchPlan version1 = service.generate(event().eventId());
        DispatchPlan version2 = service.decide(new DispatchApprovalCommand(
                version1.planId(), ApprovalDecision.REJECT, "增加照明",
                1L, "idem-r1"
        ));
        DispatchPlan version3 = service.decide(new DispatchApprovalCommand(
                version2.planId(), ApprovalDecision.REJECT, "补充绕行方案",
                2L, "idem-r2"
        ));

        assertEquals(DispatchStatus.REJECTED, repository.findVersion(version1.planId(), 1L).orElseThrow().status());
        assertEquals(DispatchStatus.REJECTED, repository.findVersion(version1.planId(), 2L).orElseThrow().status());
        assertEquals(DispatchStatus.WAITING_APPROVAL, version3.status());
        assertEquals(3L, version3.version());
    }

    @Test
    void shouldNotAllowNoDispatchAfterOrderExists() {
        TestEventPort eventPort = new TestEventPort(event());
        TestRepository repository = new TestRepository();
        DispatchApplicationService service = service(eventPort, repository);
        service.generate(event().eventId());

        BusinessRuleException exception = assertThrows(
                BusinessRuleException.class,
                () -> service.markNoDispatch(new NoDispatchCommand(
                        event().eventId(), "误报", true
                ))
        );

        assertEquals("DISPATCH_ALREADY_EXISTS", exception.errorCode());
        assertTrue(eventPort.pending);
        assertFalse(eventPort.noDispatch);
    }

    @Test
    void shouldNotInvokeModelWhenConcurrentRequestAlreadyClaimedGeneration() {
        TestEventPort eventPort = new TestEventPort(event());
        DispatchPlan concurrent = DispatchPlan.generating(
                "DP-CONCURRENT", event(), 1L, NOW
        );
        RaceRepository repository = new RaceRepository(concurrent);
        CountingModel model = new CountingModel();
        DispatchApplicationService service = service(eventPort, repository, model);

        DispatchPlan returned = service.generate(event().eventId());

        assertEquals(concurrent, returned);
        assertEquals(0, model.calls);
    }

    private DispatchApplicationService service(
            TestEventPort eventPort,
            TestRepository repository
    ) {
        return service(eventPort, repository, new FixedModel());
    }

    private DispatchApplicationService service(
            TestEventPort eventPort,
            TestRepository repository,
            ChatModelPort model
    ) {
        return new DispatchApplicationService(
                eventPort,
                repository,
                model,
                new DirectUnitOfWork(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofMinutes(2)
        );
    }

    private EmergencyEvent event() {
        return new EmergencyEvent(
                "202607280000000001",
                "AGT20260728EVT000000000000000001",
                NOW.minusSeconds(3600),
                "DT01",
                "福州市某道路发生边坡崩塌"
        );
    }

    private static class FixedModel implements ChatModelPort {
        @Override
        public ModelResponse generate(ModelRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T generateStructured(ModelRequest request, Class<T> resultType) {
            return resultType.cast(new DispatchPlanProposal(
                    java.util.List.of(new DispatchPlanProposal.ProposedResource(
                            "抢险队伍", "道路抢险人员", 1, "组", "现场警戒和抢通"
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

    private static final class CountingModel extends FixedModel {
        private int calls;

        @Override
        public <T> T generateStructured(ModelRequest request, Class<T> resultType) {
            calls++;
            return super.generateStructured(request, resultType);
        }
    }

    private static final class DirectUnitOfWork implements UnitOfWork {
        @Override
        public <T> T required(Supplier<T> operation) {
            return operation.get();
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
            if (!pending || !event.eventId().equals(eventId)) return false;
            pending = false;
            approved = true;
            return true;
        }

        @Override
        public boolean markNoDispatch(String eventId, String reason, Instant updateTime) {
            if (!pending || !event.eventId().equals(eventId)) return false;
            pending = false;
            noDispatch = true;
            this.reason = reason;
            return true;
        }
    }

    private static class TestRepository implements DispatchRepository {
        private final Map<String, DispatchPlan> versions = new ConcurrentHashMap<>();

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
            versions.put(key(plan), plan);
            return true;
        }

        private String key(DispatchPlan plan) {
            return plan.planId() + ":" + plan.version();
        }
    }

    private static final class RaceRepository extends TestRepository {
        private final DispatchPlan concurrent;
        private int eventQueries;

        private RaceRepository(DispatchPlan concurrent) {
            this.concurrent = concurrent;
        }

        @Override
        public Optional<DispatchPlan> findLatestByEventId(String eventId) {
            eventQueries++;
            return eventQueries == 1 ? Optional.empty() : Optional.of(concurrent);
        }
    }
}
