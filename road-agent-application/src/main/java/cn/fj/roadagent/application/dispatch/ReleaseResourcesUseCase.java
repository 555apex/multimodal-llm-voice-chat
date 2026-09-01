package cn.fj.roadagent.application.dispatch;

public interface ReleaseResourcesUseCase {
    EmergencyWorkflowView releaseResources(ReleaseResourcesCommand command);
}
