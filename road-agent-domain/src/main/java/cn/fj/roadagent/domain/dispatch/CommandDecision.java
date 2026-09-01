package cn.fj.roadagent.domain.dispatch;

import java.time.Instant;
import java.util.Objects;

/** 三级省级决策席位针对已通过市级专业复核的方案作出的决定。 */
public record CommandDecision(
        String decisionId,
        String workflowId,
        String reviewId,
        String planId,
        long planVersion,
        CommandDecisionStatus status,
        String decisionOpinion,
        NoticeSnapshot noticeSnapshot,
        Instant createdAt,
        Instant updatedAt
) {
    public CommandDecision {
        decisionId = requireText(decisionId, "decisionId不能为空");
        workflowId = requireText(workflowId, "workflowId不能为空");
        reviewId = requireText(reviewId, "reviewId不能为空");
        planId = requireText(planId, "planId不能为空");
        if (planVersion < 1) {
            throw new IllegalArgumentException("决策方案版本必须大于0");
        }
        status = Objects.requireNonNull(status, "决策状态不能为空");
        decisionOpinion = normalize(decisionOpinion);
        createdAt = Objects.requireNonNull(createdAt, "创建时间不能为空");
        updatedAt = Objects.requireNonNull(updatedAt, "更新时间不能为空");
        if (status == CommandDecisionStatus.PUBLISHED && noticeSnapshot == null) {
            throw new IllegalArgumentException("已通告决策必须包含通告快照");
        }
    }

    public static CommandDecision pending(
            String decisionId,
            String workflowId,
            String reviewId,
            String planId,
            long planVersion,
            Instant now
    ) {
        return new CommandDecision(
                decisionId, workflowId, reviewId, planId, planVersion,
                CommandDecisionStatus.PENDING, null, null, now, now
        );
    }

    public CommandDecision returned(String opinion, Instant now) {
        requirePending();
        return new CommandDecision(
                decisionId, workflowId, reviewId, planId, planVersion,
                CommandDecisionStatus.RETURNED,
                requireText(opinion, "退回意见不能为空"), null, createdAt, now
        );
    }

    public CommandDecision publish(
            String opinion,
            NoticeSnapshot snapshot,
            Instant now
    ) {
        requirePending();
        return new CommandDecision(
                decisionId, workflowId, reviewId, planId, planVersion,
                CommandDecisionStatus.PUBLISHED, normalize(opinion),
                Objects.requireNonNull(snapshot), createdAt, now
        );
    }

    private void requirePending() {
        if (status != CommandDecisionStatus.PENDING) {
            throw new IllegalStateException("省级决策记录已经处理");
        }
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
