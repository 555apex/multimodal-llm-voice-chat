package cn.fj.roadagent.application.port;

import cn.fj.roadagent.domain.dispatch.EmergencyEvent;
import cn.fj.roadagent.domain.dispatch.WorkflowStage;
import cn.fj.roadagent.domain.dispatch.UnclassifiedEmergencyEvent;

import java.time.Instant;
import java.util.Optional;

public interface AbnormalEventPort {
    default Optional<EmergencyEvent> findById(String eventId) {
        return findPendingById(eventId);
    }

    Optional<EmergencyEvent> findPendingById(String eventId);

    /** 在应用层事务内锁定待处理事件，串行化“生成”和“无需调度”的互斥决定。 */
    Optional<EmergencyEvent> lockPendingById(String eventId);

    Optional<EmergencyEvent> findNextPending();

    default Optional<EmergencyEvent> findNextPendingForStage(WorkflowStage stage) {
        return stage == WorkflowStage.LEVEL_1 ? findNextPending() : Optional.empty();
    }

    long countPending();

    default long countPendingForStage(WorkflowStage stage) {
        return stage == WorkflowStage.LEVEL_1 ? countPending() : 0;
    }

    boolean markDispatchApproved(String eventId, Instant updateTime);

    boolean markNoDispatch(String eventId, String reason, Instant updateTime);

    default Optional<UnclassifiedEmergencyEvent> findNextUnclassified(Instant retryBefore) {
        return Optional.empty();
    }

    default Optional<UnclassifiedEmergencyEvent> findUnclassifiedById(String eventId) {
        return Optional.empty();
    }

    default long countUnclassified() {
        return 0;
    }

    default long countClassificationFailures() {
        return 0;
    }

    default boolean assignEventTypeIfAbsent(String eventId, String eventType) {
        return false;
    }

    default boolean correctEventType(String eventId, String expectedType, String nextType) {
        return false;
    }
}
