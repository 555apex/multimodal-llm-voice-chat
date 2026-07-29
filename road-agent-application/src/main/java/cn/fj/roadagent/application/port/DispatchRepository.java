package cn.fj.roadagent.application.port;

import cn.fj.roadagent.domain.dispatch.DispatchPlan;

import java.time.Instant;
import java.util.Optional;

public interface DispatchRepository {
    boolean insert(DispatchPlan plan);

    Optional<DispatchPlan> findLatestByPlanId(String planId);

    Optional<DispatchPlan> findLatestByEventId(String eventId);

    Optional<DispatchPlan> findVersion(String planId, long version);

    boolean restartGeneration(DispatchPlan plan, Instant staleBefore);

    boolean updateGenerated(DispatchPlan plan);

    boolean updateRejected(DispatchPlan plan);

    boolean updateApproved(DispatchPlan plan);

    boolean updateFailed(DispatchPlan plan);
}
