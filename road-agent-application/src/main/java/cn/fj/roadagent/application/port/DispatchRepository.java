package cn.fj.roadagent.application.port;

import cn.fj.roadagent.domain.dispatch.DispatchPlan;

import java.util.Optional;

public interface DispatchRepository {
    void save(DispatchPlan plan);

    Optional<DispatchPlan> findById(String planId);
}
