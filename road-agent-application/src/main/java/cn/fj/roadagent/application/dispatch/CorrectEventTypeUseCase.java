package cn.fj.roadagent.application.dispatch;

public interface CorrectEventTypeUseCase {
    EmergencyWorkflowView correctEventType(CorrectEventTypeCommand command);
}
