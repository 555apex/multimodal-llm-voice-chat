package cn.fj.roadagent.application.dispatch;

public interface QueryEmergencyWorkflowUseCase {
    EmergencyWorkflowView getWorkflow(String workflowId);

    WorkflowHistoryPage history(int page, int size);

    default NoticePage notices(String completionStatus, int page, int size) {
        throw new UnsupportedOperationException("通告摘要查询尚未实现");
    }
}
