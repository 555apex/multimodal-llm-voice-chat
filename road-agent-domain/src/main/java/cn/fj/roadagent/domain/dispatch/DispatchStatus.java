package cn.fj.roadagent.domain.dispatch;

/** 正式应急调度工单状态，只能由领域规则推进，模型不能直接修改。 */
public enum DispatchStatus {
    GENERATING,
    WAITING_APPROVAL,
    REJECTED,
    APPROVED,
    FAILED
}
