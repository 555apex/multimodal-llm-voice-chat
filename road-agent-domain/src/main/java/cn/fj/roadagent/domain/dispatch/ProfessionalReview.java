package cn.fj.roadagent.domain.dispatch;

import java.time.Instant;
import java.util.Objects;

/** 二级市交通应急办针对某一确定方案版本形成的专业会商记录。 */
public record ProfessionalReview(
        String reviewId,
        String workflowId,
        String planId,
        long planVersion,
        ReviewStatus status,
        EventSeverity eventSeverity,
        ResourceFeasibility resourceFeasibility,
        String impactAssessment,
        String coordinationRequirements,
        String reviewOpinion,
        Instant createdAt,
        Instant updatedAt
) {
    public ProfessionalReview {
        reviewId = requireText(reviewId, "reviewId不能为空");
        workflowId = requireText(workflowId, "workflowId不能为空");
        planId = requireText(planId, "planId不能为空");
        if (planVersion < 1) {
            throw new IllegalArgumentException("审核方案版本必须大于0");
        }
        status = Objects.requireNonNull(status, "专业复核状态不能为空");
        impactAssessment = normalize(impactAssessment);
        coordinationRequirements = normalize(coordinationRequirements);
        reviewOpinion = normalize(reviewOpinion);
        createdAt = Objects.requireNonNull(createdAt, "创建时间不能为空");
        updatedAt = Objects.requireNonNull(updatedAt, "更新时间不能为空");
    }

    public static ProfessionalReview pending(
            String reviewId,
            String workflowId,
            String planId,
            long planVersion,
            Instant now
    ) {
        return new ProfessionalReview(
                reviewId, workflowId, planId, planVersion, ReviewStatus.PENDING,
                null, null, null, null, null, now, now
        );
    }

    public ProfessionalReview pass(
            EventSeverity severity,
            ResourceFeasibility feasibility,
            String impact,
            String coordination,
            String opinion,
            Instant now
    ) {
        requirePending();
        Objects.requireNonNull(severity, "事件初判等级不能为空");
        if (feasibility != ResourceFeasibility.FEASIBLE
                && feasibility != ResourceFeasibility.FEASIBLE_WITH_GAP) {
            throw new IllegalArgumentException("资源建议不可行时不能复核通过");
        }
        return new ProfessionalReview(
                reviewId, workflowId, planId, planVersion, ReviewStatus.PASSED,
                severity, feasibility, requireText(impact, "影响研判不能为空"),
                normalize(coordination), requireText(opinion, "专业审核意见不能为空"),
                createdAt, now
        );
    }

    public ProfessionalReview returned(String opinion, Instant now) {
        requirePending();
        return new ProfessionalReview(
                reviewId, workflowId, planId, planVersion, ReviewStatus.RETURNED,
                eventSeverity, ResourceFeasibility.NEEDS_ADJUSTMENT,
                impactAssessment, coordinationRequirements,
                requireText(opinion, "退回意见不能为空"), createdAt, now
        );
    }

    private void requirePending() {
        if (status != ReviewStatus.PENDING) {
            throw new IllegalStateException("专业复核记录已经处理");
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
