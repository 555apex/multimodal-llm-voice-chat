package cn.fj.roadagent.application.dispatch;

public interface ProfessionalReviewUseCase {
    EmergencyWorkflowView review(ProfessionalReviewCommand command);
}
