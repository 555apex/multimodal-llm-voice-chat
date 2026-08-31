package cn.fj.roadagent.application.dispatch;

public interface Level1DecisionUseCase {
    EmergencyWorkflowView decideLevel1(Level1DecisionCommand command);
}
