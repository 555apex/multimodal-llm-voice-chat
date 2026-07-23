package cn.fj.roadagent.domain.dispatch;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 应急方案状态推进
 * 具体：不可变调度方案。每次状态变化由java规则推进，不受大模型影响，便于检查重复审批。
 * */
public record DispatchPlan(
        String planId,  // 方案ID
        EmergencyEvent event,   // 关联的应急事件（EmergencyEvent.java定义）
        String summary, // 方案摘要，大模型生成
        List<DispatchTask> tasks,   // 调度任务列表
        List<EmergencyResource> resources,  // 调度资源列表
        List<String> warnings,  // 警告信息
        DispatchStatus status,  // 应急方案的调度状态(DispatchStatus.java定义)
        long version,   // 版本号
        Instant createdAt,  // 应急方案的创建时间
        WorkOrderReference workOrder    // 工单引用（WorkOrderReference.java）定义
) {
    // 构造函数（检查成员变量）
    public DispatchPlan {
        planId = Objects.requireNonNull(planId, "planId不能为空");
        event = Objects.requireNonNull(event, "event不能为空");
        summary = summary == null ? "" : summary.trim();
        tasks = tasks == null ? List.of() : List.copyOf(tasks);
        resources = resources == null ? List.of() : List.copyOf(resources);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        status = Objects.requireNonNull(status, "status不能为空");
        createdAt = Objects.requireNonNull(createdAt, "createdAt不能为空");
    }

    // 以下方法均为应急方案状态的推进方法，前置条件判断+指定状态转变
    public DispatchPlan approve() {
        requireStatus(DispatchStatus.WAITING_APPROVAL);
        return withStatus(DispatchStatus.APPROVED, version + 1, workOrder);
    }

    public DispatchPlan reject() {
        requireStatus(DispatchStatus.WAITING_APPROVAL);
        return withStatus(DispatchStatus.REJECTED, version + 1, null);
    }

    public DispatchPlan submitted(WorkOrderReference reference) {
        requireStatus(DispatchStatus.APPROVED);
        return withStatus(DispatchStatus.SUBMITTED, version + 1,
                Objects.requireNonNull(reference, "工单不能为空"));
    }

    // 方法：
    private DispatchPlan withStatus(DispatchStatus next, long nextVersion, WorkOrderReference reference) {
        return new DispatchPlan(planId, event, summary, tasks, resources, warnings,
                next, nextVersion, createdAt, reference);
    }

    private void requireStatus(DispatchStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("状态" + status + "不能执行该操作，要求状态为" + expected);
        }
    }
}
