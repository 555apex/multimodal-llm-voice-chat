package cn.fj.roadagent.application.dispatch;

public interface CommandDecisionUseCase {
    EmergencyWorkflowView decideCommand(CommandDecisionCommand command);
}
