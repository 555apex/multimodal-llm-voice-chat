package cn.fj.roadagent.interfaces.rest.workflow;

import cn.fj.roadagent.application.dispatch.WorkflowCounts;

public record WorkflowCountsResponse(long level1, long level2, long level3) {
    public static WorkflowCountsResponse from(WorkflowCounts counts) {
        return new WorkflowCountsResponse(counts.level1(), counts.level2(), counts.level3());
    }
}
