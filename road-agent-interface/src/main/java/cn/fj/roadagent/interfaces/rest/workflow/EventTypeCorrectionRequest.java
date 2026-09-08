package cn.fj.roadagent.interfaces.rest.workflow;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record EventTypeCorrectionRequest(
        @NotBlank @Size(max = 10) String eventType,
        @NotBlank @Size(max = 500) String reason,
        @PositiveOrZero long expectedWorkflowVersion,
        @NotBlank @Size(max = 100) String idempotencyKey
) { }
