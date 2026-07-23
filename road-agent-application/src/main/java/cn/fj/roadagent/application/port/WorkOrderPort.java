package cn.fj.roadagent.application.port;

import cn.fj.roadagent.domain.dispatch.DispatchPlan;
import cn.fj.roadagent.domain.dispatch.WorkOrderReference;

public interface WorkOrderPort {
    WorkOrderReference submit(DispatchPlan approvedPlan, String idempotencyKey);
}
