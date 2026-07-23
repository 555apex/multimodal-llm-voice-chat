package cn.fj.roadagent.domain.dispatch;

/**
 * 调度任务
 * 具体：调度方案中的一个步骤。每个方案包含若干 task，按 sequence 排序执行。因此作为 DispatchPlan.tasks 列表的成员
 * @param sequence  任务序号（1开始）
 * @param action    任务动作：如设置警戒、清理塌方等
 * @param responsibleUnit   责任单位
 * @param resourceId    关联的资源ID（为调度资源和调度任务建立联系）
 */
public record DispatchTask(
        int sequence,
        String action,
        String responsibleUnit,
        String resourceId
) {
}
