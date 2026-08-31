package cn.fj.roadagent.application.dispatch;

public interface QueryEmergencyWorkflowUseCase {
    EmergencyWorkflowView getWorkflow(String workflowId);

    WorkflowHistoryPage history(int page, int size);
}
