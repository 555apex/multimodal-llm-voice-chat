package cn.fj.roadagent.application.dispatch;

public record WorkflowCounts(
        long level1,
        long level2,
        long level3,
        long pendingClassification,
        long classificationFailed
) {
    public WorkflowCounts(long level1, long level2, long level3) {
        this(level1, level2, level3, 0, 0);
    }
}
