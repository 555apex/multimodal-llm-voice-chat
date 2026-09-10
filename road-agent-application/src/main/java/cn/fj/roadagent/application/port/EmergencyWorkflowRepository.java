package cn.fj.roadagent.application.port;

import cn.fj.roadagent.domain.dispatch.CommandDecision;
import cn.fj.roadagent.domain.dispatch.EmergencyWorkflow;
import cn.fj.roadagent.domain.dispatch.ProfessionalReview;
import cn.fj.roadagent.domain.dispatch.WorkflowAction;

import java.util.List;
import java.util.Optional;

/** 三级工作流持久化端口；实现可使用MySQL，核心层不感知SQL。 */
public interface EmergencyWorkflowRepository {
    boolean insertWorkflow(EmergencyWorkflow workflow);

    Optional<EmergencyWorkflow> findWorkflow(String workflowId);

    Optional<EmergencyWorkflow> findWorkflowByEventId(String eventId);

    Optional<EmergencyWorkflow> findWorkflowByPlanId(String planId);

    Optional<EmergencyWorkflow> lockWorkflow(String workflowId);

    boolean updateWorkflow(EmergencyWorkflow workflow, long expectedLockVersion);

    boolean insertReview(ProfessionalReview review);

    Optional<ProfessionalReview> findLatestReview(String workflowId);

    Optional<ProfessionalReview> findPendingReview(String workflowId);

    boolean updateReview(ProfessionalReview review);

    boolean insertDecision(CommandDecision decision);

    Optional<CommandDecision> findLatestDecision(String workflowId);

    Optional<CommandDecision> findPendingDecision(String workflowId);

    boolean updateDecision(CommandDecision decision);

    boolean insertAction(WorkflowAction action);

    Optional<WorkflowAction> findActionByIdempotencyKey(
            String workflowId,
            String idempotencyKey
    );

    List<WorkflowAction> findActions(String workflowId);

    List<EmergencyWorkflow> findHistory(int offset, int limit);

    long countHistory();

    default List<EmergencyWorkflow> findNotices(boolean pending, int offset, int limit) {
        throw new UnsupportedOperationException("通告查询尚未实现");
    }

    default long countNotices(boolean pending) {
        throw new UnsupportedOperationException("通告统计尚未实现");
    }
}
