package cn.fj.roadagent.application.port;

import cn.fj.roadagent.domain.dispatch.EmergencyEvent;

import java.time.Instant;
import java.util.Optional;

public interface AbnormalEventPort {
    Optional<EmergencyEvent> findPendingById(String eventId);

    /** 在应用层事务内锁定待处理事件，串行化“生成”和“无需调度”的互斥决定。 */
    Optional<EmergencyEvent> lockPendingById(String eventId);

    Optional<EmergencyEvent> findNextPending();

    long countPending();

    boolean markDispatchApproved(String eventId, Instant updateTime);

    boolean markNoDispatch(String eventId, String reason, Instant updateTime);
}
