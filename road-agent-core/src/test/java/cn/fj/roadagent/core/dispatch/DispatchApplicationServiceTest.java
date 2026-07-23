package cn.fj.roadagent.core.dispatch;

import cn.fj.roadagent.application.dispatch.DispatchApprovalCommand;
import cn.fj.roadagent.application.port.DispatchRepository;
import cn.fj.roadagent.domain.dispatch.ApprovalDecision;
import cn.fj.roadagent.domain.dispatch.DispatchPlan;
import cn.fj.roadagent.domain.dispatch.DispatchStatus;
import cn.fj.roadagent.domain.dispatch.DispatchTask;
import cn.fj.roadagent.domain.dispatch.EmergencyEvent;
import cn.fj.roadagent.domain.dispatch.WorkOrderReference;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DispatchApplicationServiceTest {

    @Test
    void shouldApproveOnceAndReturnSameWorkOrderOnDuplicateClick() {
        TestRepository repository = new TestRepository(waitingPlan());
        AtomicInteger submissions = new AtomicInteger();
        DispatchApplicationService service = new DispatchApplicationService(repository, (plan, key) -> {
            submissions.incrementAndGet();
            return new WorkOrderReference("MOCK-WO-1", "SUBMITTED", Instant.EPOCH, true);
        });
        var command = new DispatchApprovalCommand(
                "DP-1", ApprovalDecision.APPROVE, "同意", 1L, "idem-1"
        );

        DispatchPlan first = service.decide(command);
        DispatchPlan duplicate = service.decide(command);

        assertEquals(DispatchStatus.SUBMITTED, first.status());
        assertEquals("MOCK-WO-1", duplicate.workOrder().workOrderId());
        assertEquals(1, submissions.get());
    }

    @Test
    void shouldRejectWithoutCreatingWorkOrder() {
        TestRepository repository = new TestRepository(waitingPlan());
        AtomicInteger submissions = new AtomicInteger();
        DispatchApplicationService service = new DispatchApplicationService(repository, (plan, key) -> {
            submissions.incrementAndGet();
            return null;
        });

        DispatchPlan rejected = service.decide(new DispatchApprovalCommand(
                "DP-1", ApprovalDecision.REJECT, "信息不足", 1L, "idem-2"
        ));

        assertEquals(DispatchStatus.REJECTED, rejected.status());
        assertEquals(0, submissions.get());
    }

    private DispatchPlan waitingPlan() {
        return new DispatchPlan(
                "DP-1",
                new EmergencyEvent("道路塌方", "福州", "五四路", "HIGH", "道路发生塌方"),
                "建议封控并清障",
                List.of(new DispatchTask(1, "设置警戒", "属地单位", null)),
                List.of(), List.of(), DispatchStatus.WAITING_APPROVAL, 1L, Instant.EPOCH, null
        );
    }

    private static final class TestRepository implements DispatchRepository {
        private DispatchPlan plan;

        private TestRepository(DispatchPlan plan) {
            this.plan = plan;
        }

        @Override
        public void save(DispatchPlan plan) {
            this.plan = plan;
        }

        @Override
        public Optional<DispatchPlan> findById(String planId) {
            return Optional.ofNullable(plan);
        }
    }
}
