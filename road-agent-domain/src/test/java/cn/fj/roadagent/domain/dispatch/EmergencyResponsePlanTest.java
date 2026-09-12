package cn.fj.roadagent.domain.dispatch;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EmergencyResponsePlanTest {
    @Test
    void responsePlanMayDefineTextWithoutRestrictingResources() {
        EmergencyResponsePlan plan = new EmergencyResponsePlan(
                "ERP-DT04", "DT04", "沉陷与塌陷", 3,
                List.of("坑洞范围"), "核查【现场事实简述】。",
                List.of(), "a".repeat(64)
        );

        assertTrue(plan.resourceBaseline().isEmpty());
        assertTrue(plan.snapshot().resourceBaseline().isEmpty());
    }
}
