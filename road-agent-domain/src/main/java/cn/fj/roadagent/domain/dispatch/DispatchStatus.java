package cn.fj.roadagent.domain.dispatch;

/** 应急方案的调度状态
 * 统一由Java推进，模型不能直接修改。
 * 状态转换由DispatchPlan中定义的方法实现
 * */
public enum DispatchStatus {
    DRAFT,  // 草案
    WAITING_APPROVAL,   // 等待批准
    APPROVED,   // 批准
    SUBMITTED,  // 提交（工单已下发）
    REJECTED,   // 拒绝（人工驳回）
    // 预留状态（暂未启用）
    IN_PROGRESS,    // 进程中
    COMPLETED,  // 完成
    FAILED, // 失败
    CANCELLED   // 取消
}
