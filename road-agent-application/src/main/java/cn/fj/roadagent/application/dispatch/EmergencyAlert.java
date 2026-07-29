package cn.fj.roadagent.application.dispatch;

import cn.fj.roadagent.domain.dispatch.DispatchPlan;
import cn.fj.roadagent.domain.dispatch.EmergencyEvent;

/** 顶部告警卡片一次需要的完整数据。 */
public record EmergencyAlert(
        EmergencyEvent event,
        DispatchPlan dispatch,
        long pendingCount
) {
}
