package cn.fj.roadagent.adapters.dispatch.memory;

import cn.fj.roadagent.application.port.DispatchRepository;
import cn.fj.roadagent.domain.dispatch.DispatchPlan;

import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** 仅供单元测试和教学使用；生产环境绑定MySQL实现。 */
public final class InMemoryDispatchRepository implements DispatchRepository {
    private final Map<String, DispatchPlan> versions = new ConcurrentHashMap<>();

    @Override
    public boolean insert(DispatchPlan plan) {
        return versions.putIfAbsent(key(plan.planId(), plan.version()), plan) == null;
    }

    @Override
    public Optional<DispatchPlan> findLatestByPlanId(String planId) {
        return versions.values().stream()
                .filter(plan -> plan.planId().equals(planId))
                .max(Comparator.comparingLong(DispatchPlan::version));
    }

    @Override
    public Optional<DispatchPlan> findLatestByEventId(String eventId) {
        return versions.values().stream()
                .filter(plan -> plan.event().eventId().equals(eventId))
                .max(Comparator.comparingLong(DispatchPlan::version));
    }

    @Override
    public Optional<DispatchPlan> findVersion(String planId, long version) {
        return Optional.ofNullable(versions.get(key(planId, version)));
    }

    @Override
    public boolean restartGeneration(DispatchPlan plan, Instant staleBefore) {
        return replace(plan);
    }

    @Override
    public boolean updateGenerated(DispatchPlan plan) {
        return replace(plan);
    }

    @Override
    public boolean updateRejected(DispatchPlan plan) {
        return replace(plan);
    }

    @Override
    public boolean updateApproved(DispatchPlan plan) {
        return replace(plan);
    }

    @Override
    public boolean updateFailed(DispatchPlan plan) {
        return replace(plan);
    }

    private boolean replace(DispatchPlan plan) {
        String key = key(plan.planId(), plan.version());
        return versions.computeIfPresent(key, (ignored, current) -> plan) != null;
    }

    private String key(String planId, long version) {
        return planId + ":" + version;
    }
}
