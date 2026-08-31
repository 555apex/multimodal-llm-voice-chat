package cn.fj.roadagent.application.port;

import cn.fj.roadagent.domain.dispatch.EmergencyEvent;
import cn.fj.roadagent.domain.dispatch.WorkflowStage;

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
}
