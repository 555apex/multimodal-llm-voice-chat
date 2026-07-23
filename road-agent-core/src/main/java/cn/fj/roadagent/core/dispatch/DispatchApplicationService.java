package cn.fj.roadagent.core.dispatch;

import cn.fj.roadagent.application.dispatch.DispatchApprovalCommand;
import cn.fj.roadagent.application.dispatch.DispatchApprovalUseCase;
import cn.fj.roadagent.application.dispatch.DispatchQueryUseCase;
import cn.fj.roadagent.application.exception.BusinessRuleException;
import cn.fj.roadagent.application.port.DispatchRepository;
import cn.fj.roadagent.application.port.WorkOrderPort;
import cn.fj.roadagent.domain.dispatch.ApprovalDecision;
import cn.fj.roadagent.domain.dispatch.DispatchPlan;
import cn.fj.roadagent.domain.dispatch.DispatchStatus;

/** 审批和工单下发均由确定性Java规则控制。 */
public final class DispatchApplicationService implements DispatchQueryUseCase, DispatchApprovalUseCase {
    private final DispatchRepository dispatchRepository;
    private final WorkOrderPort workOrderPort;

    public DispatchApplicationService(DispatchRepository dispatchRepository, WorkOrderPort workOrderPort) {
        this.dispatchRepository = dispatchRepository;
        this.workOrderPort = workOrderPort;
    }

    @Override
    public DispatchPlan get(String planId) {
        return dispatchRepository.findById(planId)
                .orElseThrow(() -> new BusinessRuleException("DISPATCH_NOT_FOUND", "调度方案不存在"));
    }

    @Override
    public synchronized DispatchPlan decide(DispatchApprovalCommand command) {
        DispatchPlan current = get(command.planId());
        // 重复点击批准时直接返回已提交结果，绝不创建第二张工单。
        if (command.decision() == ApprovalDecision.APPROVE
                && current.status() == DispatchStatus.SUBMITTED) {
            return current;
        }
        if (current.version() != command.expectedVersion()) {
            throw new BusinessRuleException("DISPATCH_VERSION_CONFLICT", "方案版本已变化，请刷新后重试");
        }
        if (command.decision() == ApprovalDecision.REJECT) {
            DispatchPlan rejected = current.reject();
            dispatchRepository.save(rejected);
            return rejected;
        }

        DispatchPlan approved = current.approve();
        var workOrder = workOrderPort.submit(approved, command.idempotencyKey());
        DispatchPlan submitted = approved.submitted(workOrder);
        dispatchRepository.save(submitted);
        return submitted;
    }
}
