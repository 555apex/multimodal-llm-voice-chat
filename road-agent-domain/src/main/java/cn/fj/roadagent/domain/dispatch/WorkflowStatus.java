package cn.fj.roadagent.domain.dispatch;

/** 工作流状态独立于事件最终状态和方案内容状态。 */
public enum WorkflowStatus {
    WAITING_GENERATION,
    GENERATING,
    WAITING_LEVEL_1_SUBMISSION,
    WAITING_LEVEL_2_REVIEW,
    WAITING_LEVEL_3_DECISION,
    REVISING,
    GENERATION_FAILED,
    PUBLISHED,
    NO_DISPATCH;

    public boolean terminal() {
        return this == PUBLISHED || this == NO_DISPATCH;
    }
}
