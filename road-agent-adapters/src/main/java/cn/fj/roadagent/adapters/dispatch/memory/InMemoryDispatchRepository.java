package cn.fj.roadagent.adapters.dispatch.memory;

import cn.fj.roadagent.application.port.DispatchRepository;
import cn.fj.roadagent.domain.dispatch.DispatchPlan;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryDispatchRepository implements DispatchRepository {
    private final Map<String, DispatchPlan> plans = new ConcurrentHashMap<>();

    @Override
    public void save(DispatchPlan plan) {
        plans.put(plan.planId(), plan);
    }

    @Override
    public Optional<DispatchPlan> findById(String planId) {
        return Optional.ofNullable(plans.get(planId));
    }
}
