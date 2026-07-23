package cn.fj.roadagent.adapters.dispatch.mock;

import cn.fj.roadagent.application.port.WorkOrderPort;
import cn.fj.roadagent.domain.dispatch.DispatchPlan;
import cn.fj.roadagent.domain.dispatch.WorkOrderReference;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** 相同幂等键永远返回同一张Mock工单。 */
public final class MockWorkOrderAdapter implements WorkOrderPort {
    private final Map<String, WorkOrderReference> submitted = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();
    private final Clock clock;

    public MockWorkOrderAdapter(Clock clock) {
        this.clock = clock;
    }

    @Override
    public WorkOrderReference submit(DispatchPlan approvedPlan, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("幂等键不能为空");
        }
        return submitted.computeIfAbsent(idempotencyKey, ignored -> new WorkOrderReference(
                "MOCK-WO-%06d".formatted(sequence.incrementAndGet()),
                "SUBMITTED",
                clock.instant(),
                true
        ));
    }
}
