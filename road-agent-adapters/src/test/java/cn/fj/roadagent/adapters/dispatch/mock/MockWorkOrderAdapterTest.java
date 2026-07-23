package cn.fj.roadagent.adapters.dispatch.mock;

import cn.fj.roadagent.domain.dispatch.DispatchPlan;
import cn.fj.roadagent.domain.dispatch.DispatchStatus;
import cn.fj.roadagent.domain.dispatch.DispatchTask;
import cn.fj.roadagent.domain.dispatch.EmergencyEvent;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MockWorkOrderAdapterTest {

    @Test
    void shouldReturnSameOrderForSameIdempotencyKey() {
        Clock clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
        MockWorkOrderAdapter adapter = new MockWorkOrderAdapter(clock);
        DispatchPlan plan = new DispatchPlan(
                "DP-1", new EmergencyEvent("塌方", "福州", "五四路", "HIGH", "塌方"),
                "摘要", List.of(new DispatchTask(1, "警戒", "属地", null)), List.of(), List.of(),
                DispatchStatus.APPROVED, 2L, Instant.EPOCH, null
        );

        var first = adapter.submit(plan, "idem-1");
        var second = adapter.submit(plan, "idem-1");

        assertEquals(first.workOrderId(), second.workOrderId());
    }
}
