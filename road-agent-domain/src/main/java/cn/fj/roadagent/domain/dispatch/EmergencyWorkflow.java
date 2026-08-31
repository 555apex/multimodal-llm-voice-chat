package cn.fj.roadagent.domain.dispatch;

import java.time.Instant;
import java.util.Objects;

/** 一条事件对应的一条三级流转实例；方案内容仍由DispatchPlan维护。 */
public record EmergencyWorkflow(
        String workflowId,
        String eventId,
        WorkflowStage currentStage,
        WorkflowStatus status,
        String planId,
        long planVersion,
        long lockVersion,
        Instant stageEnteredAt,
        Instant createdAt,
        Instant updatedAt
) {
    public EmergencyWorkflow {
        workflowId = requireText(workflowId, "workflowId不能为空");
        eventId = requireText(eventId, "eventId不能为空");
        status = Objects.requireNonNull(status, "工作流状态不能为空");
        stageEnteredAt = Objects.requireNonNull(stageEnteredAt, "进入阶段时间不能为空");
        createdAt = Objects.requireNonNull(createdAt, "创建时间不能为空");
        updatedAt = Objects.requireNonNull(updatedAt, "更新时间不能为空");
        planId = normalize(planId);
        if (planVersion < 0 || lockVersion < 0) {
            throw new IllegalArgumentException("方案版本和乐观锁版本不能为负数");
        }
        if (status.terminal() && currentStage != null) {
            throw new IllegalArgumentException("已办结工作流不能保留当前阶段");
        }
        if (!status.terminal() && currentStage == null) {
            throw new IllegalArgumentException("未办结工作流必须有当前阶段");
        }
    }

    public static EmergencyWorkflow generating(
            String workflowId,
            String eventId,
            String planId,
            long planVersion,
            Instant now
    ) {
        return new EmergencyWorkflow(
                workflowId, eventId, WorkflowStage.LEVEL_1, WorkflowStatus.GENERATING,
                planId, planVersion, 0, now, now, now
        );
    }

    public static EmergencyWorkflow noDispatch(String workflowId, String eventId, Instant now) {
        return new EmergencyWorkflow(
                workflowId, eventId, null, WorkflowStatus.NO_DISPATCH,
                null, 0, 0, now, now, now
        );
    }

    public EmergencyWorkflow generated(Instant now) {
        requireStageAndStatus(WorkflowStage.LEVEL_1, WorkflowStatus.GENERATING, WorkflowStatus.REVISING);
        return transition(
                WorkflowStage.LEVEL_1, WorkflowStatus.WAITING_LEVEL_1_SUBMISSION,
                planId, planVersion, now
        );
    }

    public EmergencyWorkflow generationFailed(Instant now) {
        requireStageAndStatus(WorkflowStage.LEVEL_1, WorkflowStatus.GENERATING, WorkflowStatus.REVISING);
        return transition(
                WorkflowStage.LEVEL_1, WorkflowStatus.GENERATION_FAILED,
                planId, planVersion, now
        );
    }

    public EmergencyWorkflow retryGeneration(Instant now) {
        requireStageAndStatus(
                WorkflowStage.LEVEL_1,
                WorkflowStatus.GENERATION_FAILED,
                WorkflowStatus.GENERATING
        );
        return transition(WorkflowStage.LEVEL_1, WorkflowStatus.GENERATING, planId, planVersion, now);
    }

    public EmergencyWorkflow submitLevel1(Instant now) {
        requireStageAndStatus(WorkflowStage.LEVEL_1, WorkflowStatus.WAITING_LEVEL_1_SUBMISSION);
        return transition(
                WorkflowStage.LEVEL_2, WorkflowStatus.WAITING_LEVEL_2_REVIEW,
                planId, planVersion, now
        );
    }

    public EmergencyWorkflow approveLevel2(Instant now) {
        requireStageAndStatus(WorkflowStage.LEVEL_2, WorkflowStatus.WAITING_LEVEL_2_REVIEW);
        return transition(
                WorkflowStage.LEVEL_3, WorkflowStatus.WAITING_LEVEL_3_DECISION,
                planId, planVersion, now
        );
    }

    public EmergencyWorkflow beginRevision(long nextPlanVersion, Instant now) {
        if (status != WorkflowStatus.WAITING_LEVEL_1_SUBMISSION
                && status != WorkflowStatus.WAITING_LEVEL_2_REVIEW
                && status != WorkflowStatus.WAITING_LEVEL_3_DECISION) {
            throw new IllegalStateException("当前工作流状态不能退回返工：" + status);
        }
        if (nextPlanVersion != planVersion + 1) {
            throw new IllegalArgumentException("返工版本必须在当前版本基础上加1");
        }
        return transition(
                WorkflowStage.LEVEL_1, WorkflowStatus.REVISING,
                planId, nextPlanVersion, now
        );
    }

    public EmergencyWorkflow publish(Instant now) {
        requireStageAndStatus(WorkflowStage.LEVEL_3, WorkflowStatus.WAITING_LEVEL_3_DECISION);
        return transition(null, WorkflowStatus.PUBLISHED, planId, planVersion, now);
    }

    public EmergencyWorkflow recordResourceRelease(Instant now) {
        requireStageAndStatus(null, WorkflowStatus.PUBLISHED);
        return new EmergencyWorkflow(
                workflowId, eventId, null, WorkflowStatus.PUBLISHED,
                planId, planVersion, lockVersion + 1,
                stageEnteredAt, createdAt, now
        );
    }

    private EmergencyWorkflow transition(
            WorkflowStage nextStage,
            WorkflowStatus nextStatus,
            String nextPlanId,
            long nextPlanVersion,
            Instant now
    ) {
        return new EmergencyWorkflow(
                workflowId, eventId, nextStage, nextStatus, nextPlanId, nextPlanVersion,
                lockVersion + 1, now, createdAt, now
        );
    }

    private void requireStageAndStatus(
            WorkflowStage requiredStage,
            WorkflowStatus... allowedStatuses
    ) {
        if (currentStage != requiredStage) {
            throw new IllegalStateException("当前工作流不在要求的阶段：" + requiredStage);
        }
        for (WorkflowStatus allowed : allowedStatuses) {
            if (status == allowed) {
                return;
            }
        }
        throw new IllegalStateException("当前工作流状态不能执行该操作：" + status);
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
