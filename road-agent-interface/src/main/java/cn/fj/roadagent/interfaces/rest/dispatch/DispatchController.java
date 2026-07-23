package cn.fj.roadagent.interfaces.rest.dispatch;

import cn.fj.roadagent.application.dispatch.DispatchApprovalCommand;
import cn.fj.roadagent.application.dispatch.DispatchApprovalUseCase;
import cn.fj.roadagent.application.dispatch.DispatchQueryUseCase;
import cn.fj.roadagent.interfaces.rest.common.ApiResponse;
import cn.fj.roadagent.interfaces.rest.common.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dispatches")
public final class DispatchController {
    private final DispatchQueryUseCase queryUseCase;
    private final DispatchApprovalUseCase approvalUseCase;

    public DispatchController(
            DispatchQueryUseCase queryUseCase,
            DispatchApprovalUseCase approvalUseCase
    ) {
        this.queryUseCase = queryUseCase;
        this.approvalUseCase = approvalUseCase;
    }

    @GetMapping("/{planId}")
    public ApiResponse<DispatchResponse> get(
            @PathVariable String planId,
            HttpServletRequest request
    ) {
        String traceId = traceId(request);
        return ApiResponse.success(DispatchResponse.from(queryUseCase.get(planId)), traceId);
    }

    @PostMapping("/{planId}/approvals")
    public ApiResponse<DispatchResponse> decide(
            @PathVariable String planId,
            @Valid @RequestBody DispatchApprovalRequest request,
            HttpServletRequest servletRequest
    ) {
        var command = new DispatchApprovalCommand(
                planId, request.decision(), request.comment(), request.expectedVersion(), request.idempotencyKey()
        );
        var plan = approvalUseCase.decide(command);
        return ApiResponse.success(DispatchResponse.from(plan), traceId(servletRequest));
    }

    private String traceId(HttpServletRequest request) {
        return request.getAttribute(TraceIdFilter.ATTRIBUTE_NAME).toString();
    }
}
