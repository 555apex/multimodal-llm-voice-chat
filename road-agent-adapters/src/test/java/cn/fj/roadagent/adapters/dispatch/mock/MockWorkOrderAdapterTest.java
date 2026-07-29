package cn.fj.roadagent.adapters.dispatch.mock;

import cn.fj.roadagent.domain.dispatch.DispatchPlan;
import cn.fj.roadagent.domain.dispatch.DispatchStatus;
import cn.fj.roadagent.domain.dispatch.EmergencyEvent;
import cn.fj.roadagent.domain.dispatch.SuggestedResource;
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
                "DP-1",
                new EmergencyEvent("1", "EVT-1", Instant.EPOCH, "DT01", "塌方"),
                List.of(new SuggestedResource("队伍", "抢险队", 1, "组", "警戒")),
                "建议封控",
                DispatchStatus.APPROVED,
                1L,
                Instant.EPOCH,
                Instant.EPOCH,
                null,
                null
        );

        var first = adapter.submit(plan, "idem-1");
        var second = adapter.submit(plan, "idem-1");

        assertEquals(first.workOrderId(), second.workOrderId());
    }
}
