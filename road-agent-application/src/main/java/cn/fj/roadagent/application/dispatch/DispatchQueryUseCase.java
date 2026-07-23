package cn.fj.roadagent.application.dispatch;

import cn.fj.roadagent.domain.dispatch.DispatchPlan;

public interface DispatchQueryUseCase {
    DispatchPlan get(String planId);
}
