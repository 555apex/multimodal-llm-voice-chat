package cn.fj.roadagent.domain.dispatch;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmergencyWorkflowTest {
    private static final Instant NOW = Instant.parse("2026-08-19T00:00:00Z");

    @Test
    void shouldFollowAllThreeStagesAndIncrementOptimisticVersion() {
        EmergencyWorkflow generated = EmergencyWorkflow.generating(
                "WF-1", "1", "DP-1", 1L, NOW
        ).generated(NOW);
        EmergencyWorkflow level2 = generated.submitLevel1(NOW);
        EmergencyWorkflow level3 = level2.approveLevel2(NOW);
        EmergencyWorkflow published = level3.publish(NOW);

        assertEquals(WorkflowStage.LEVEL_2, level2.currentStage());
        assertEquals(WorkflowStage.LEVEL_3, level3.currentStage());
        assertEquals(WorkflowStatus.PUBLISHED, published.status());
        assertEquals(4L, published.lockVersion());
        assertNull(published.currentStage());
    }

    @Test
    void shouldRejectIllegalLevelSkipAndWrongRevisionVersion() {
        EmergencyWorkflow workflow = EmergencyWorkflow.generating(
                "WF-1", "1", "DP-1", 1L, NOW
        ).generated(NOW);

        assertThrows(IllegalStateException.class, () -> workflow.approveLevel2(NOW));
        assertThrows(IllegalArgumentException.class, () -> workflow.beginRevision(3L, NOW));
    }

    @Test
    void professionalReviewCannotPassWithInfeasibleResources() {
        ProfessionalReview review = ProfessionalReview.pending(
                "PR-1", "WF-1", "DP-1", 1L, NOW
        );

        assertThrows(IllegalArgumentException.class, () -> review.pass(
                EventSeverity.LARGER, ResourceFeasibility.NEEDS_ADJUSTMENT,
                "影响交通", null, "同意", NOW
        ));
    }
}
