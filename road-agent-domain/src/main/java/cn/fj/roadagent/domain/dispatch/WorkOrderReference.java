package cn.fj.roadagent.domain.dispatch;

import java.time.Instant;

/**
 * 工单参考信息
 * 具体： 工单下发后的不可变凭证。一旦生成就不再修改。
 * 作为 DispatchPlan 的一个字段（初始为 null，只有 SUBMITTED 状态时才有值），证明"工单确实发出去了"
 * @param workOrderId   工单ID
 * @param status    工单状态
 * @param submittedAt   提交时间
 * @param mock  是否为mock模拟工单
 */
public record WorkOrderReference(
        String workOrderId,
        String status,
        Instant submittedAt,
        boolean mock
) {
}
