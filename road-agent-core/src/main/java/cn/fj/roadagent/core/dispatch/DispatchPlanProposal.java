package cn.fj.roadagent.core.dispatch;

import java.util.List;

/** 大模型返回的数据库应急调度工单内容。 */
public record DispatchPlanProposal(
        List<ProposedResource> suggestedResources,
        String rescuePlan
) {
    public DispatchPlanProposal {
        suggestedResources = suggestedResources == null ? List.of() : List.copyOf(suggestedResources);
    }

    public record ProposedResource(
            String resourceType,
            String resourceName,
            int quantity,
            String unit,
            String purpose
    ) {
    }
}
