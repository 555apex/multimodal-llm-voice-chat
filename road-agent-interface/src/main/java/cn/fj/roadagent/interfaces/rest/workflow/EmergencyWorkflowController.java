package cn.fj.roadagent.interfaces.rest.workflow;

import cn.fj.roadagent.application.dispatch.CommandDecisionCommand;
import cn.fj.roadagent.application.dispatch.CommandDecisionUseCase;
import cn.fj.roadagent.application.dispatch.CorrectEventTypeCommand;
import cn.fj.roadagent.application.dispatch.CorrectEventTypeUseCase;
import cn.fj.roadagent.application.dispatch.Level1DecisionCommand;
import cn.fj.roadagent.application.dispatch.Level1DecisionUseCase;
import cn.fj.roadagent.application.dispatch.ProfessionalReviewCommand;
import cn.fj.roadagent.application.dispatch.ProfessionalReviewUseCase;
import cn.fj.roadagent.application.dispatch.QueryEmergencyWorkflowUseCase;
import cn.fj.roadagent.application.dispatch.QueryWorkflowInboxUseCase;
import cn.fj.roadagent.application.dispatch.ReleaseResourcesCommand;
import cn.fj.roadagent.application.dispatch.ReleaseResourcesUseCase;
import cn.fj.roadagent.domain.dispatch.WorkflowStage;
import cn.fj.roadagent.interfaces.rest.common.ApiResponse;
import cn.fj.roadagent.interfaces.rest.common.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/emergency-workflows")
public final class EmergencyWorkflowController {
    private final QueryWorkflowInboxUseCase inboxUseCase;
    private final Level1DecisionUseCase level1UseCase;
    private final ProfessionalReviewUseCase reviewUseCase;
    private final CommandDecisionUseCase commandUseCase;
    private final QueryEmergencyWorkflowUseCase queryUseCase;
    private final ReleaseResourcesUseCase releaseResourcesUseCase;
    private final CorrectEventTypeUseCase correctEventTypeUseCase;

    @Autowired
    public EmergencyWorkflowController(
            QueryWorkflowInboxUseCase inboxUseCase,
            Level1DecisionUseCase level1UseCase,
            ProfessionalReviewUseCase reviewUseCase,
            CommandDecisionUseCase commandUseCase,
            QueryEmergencyWorkflowUseCase queryUseCase,
            ReleaseResourcesUseCase releaseResourcesUseCase,
            CorrectEventTypeUseCase correctEventTypeUseCase
    ) {
        this.inboxUseCase = inboxUseCase;
        this.level1UseCase = level1UseCase;
        this.reviewUseCase = reviewUseCase;
        this.commandUseCase = commandUseCase;
        this.queryUseCase = queryUseCase;
        this.releaseResourcesUseCase = releaseResourcesUseCase;
        this.correctEventTypeUseCase = correctEventTypeUseCase;
    }

    EmergencyWorkflowController(
            QueryWorkflowInboxUseCase inboxUseCase,
            Level1DecisionUseCase level1UseCase,
            ProfessionalReviewUseCase reviewUseCase,
            CommandDecisionUseCase commandUseCase,
            QueryEmergencyWorkflowUseCase queryUseCase,
            ReleaseResourcesUseCase releaseResourcesUseCase
    ) {
        this(inboxUseCase, level1UseCase, reviewUseCase, commandUseCase, queryUseCase,
                releaseResourcesUseCase, command -> queryUseCase.getWorkflow(command.workflowId()));
    }

    @GetMapping("/inbox")
    public ApiResponse<WorkflowInboxResponse> inbox(
            @RequestParam WorkflowStage stage,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                WorkflowInboxResponse.from(inboxUseCase.inbox(stage)), traceId(request)
        );
    }

    @PostMapping("/{workflowId}/level-1-decisions")
    public ApiResponse<EmergencyWorkflowResponse> decideLevel1(
            @PathVariable String workflowId,
            @Valid @RequestBody Level1DecisionRequest body,
            HttpServletRequest request
    ) {
        var result = level1UseCase.decideLevel1(new Level1DecisionCommand(
                workflowId, body.decision(), body.comment(), body.expectedWorkflowVersion(),
                body.idempotencyKey()
        ));
        return ApiResponse.success(EmergencyWorkflowResponse.from(result), traceId(request));
    }

    @PostMapping("/{workflowId}/professional-reviews")
    public ApiResponse<EmergencyWorkflowResponse> review(
            @PathVariable String workflowId,
            @Valid @RequestBody ProfessionalReviewRequest body,
            HttpServletRequest request
    ) {
        var result = reviewUseCase.review(new ProfessionalReviewCommand(
                workflowId, body.decision(), body.eventSeverity(), body.resourceFeasibility(),
                body.impactAssessment(), body.coordinationRequirements(), body.comment(),
                body.expectedWorkflowVersion(), body.idempotencyKey()
        ));
        return ApiResponse.success(EmergencyWorkflowResponse.from(result), traceId(request));
    }

    @PostMapping("/{workflowId}/command-decisions")
    public ApiResponse<EmergencyWorkflowResponse> decideCommand(
            @PathVariable String workflowId,
            @Valid @RequestBody CommandDecisionRequest body,
            HttpServletRequest request
    ) {
        var result = commandUseCase.decideCommand(new CommandDecisionCommand(
                workflowId, body.decision(), body.comment(),
                body.expectedWorkflowVersion(), body.idempotencyKey()
        ));
        return ApiResponse.success(EmergencyWorkflowResponse.from(result), traceId(request));
    }

    @PostMapping("/{workflowId}/resource-releases")
    public ApiResponse<EmergencyWorkflowResponse> releaseResources(
            @PathVariable String workflowId,
            @Valid @RequestBody ResourceReleaseRequest body,
            HttpServletRequest request
    ) {
        var result = releaseResourcesUseCase.releaseResources(new ReleaseResourcesCommand(
                workflowId, body.reason(), body.expectedWorkflowVersion(), body.idempotencyKey()
        ));
        return ApiResponse.success(EmergencyWorkflowResponse.from(result), traceId(request));
    }

    @PostMapping("/{workflowId}/event-type-corrections")
    public ApiResponse<EmergencyWorkflowResponse> correctEventType(
            @PathVariable String workflowId,
            @Valid @RequestBody EventTypeCorrectionRequest body,
            HttpServletRequest request
    ) {
        var result = correctEventTypeUseCase.correctEventType(new CorrectEventTypeCommand(
                workflowId, body.eventType(), body.reason(),
                body.expectedWorkflowVersion(), body.idempotencyKey()));
        return ApiResponse.success(EmergencyWorkflowResponse.from(result), traceId(request));
    }

    @GetMapping("/{workflowId}")
    public ApiResponse<EmergencyWorkflowResponse> get(
            @PathVariable String workflowId,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                EmergencyWorkflowResponse.from(queryUseCase.getWorkflow(workflowId)),
                traceId(request)
        );
    }

    @GetMapping("/history")
    public ApiResponse<WorkflowHistoryResponse> history(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                WorkflowHistoryResponse.from(queryUseCase.history(page, size)), traceId(request)
        );
    }

    private String traceId(HttpServletRequest request) {
        Object value = request.getAttribute(TraceIdFilter.ATTRIBUTE_NAME);
        return value == null ? "unknown" : value.toString();
    }
}
