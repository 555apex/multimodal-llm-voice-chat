package cn.fj.roadagent.interfaces.rest.emergency;

import cn.fj.roadagent.application.dispatch.GenerateDispatchUseCase;
import cn.fj.roadagent.application.dispatch.NoDispatchCommand;
import cn.fj.roadagent.application.dispatch.NoDispatchUseCase;
import cn.fj.roadagent.application.dispatch.QueryPendingEmergencyUseCase;
import cn.fj.roadagent.interfaces.rest.common.ApiResponse;
import cn.fj.roadagent.interfaces.rest.common.TraceIdFilter;
import cn.fj.roadagent.interfaces.rest.dispatch.DispatchResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/emergency-events")
public final class EmergencyEventController {
    private final QueryPendingEmergencyUseCase queryUseCase;
    private final GenerateDispatchUseCase generateUseCase;
    private final NoDispatchUseCase noDispatchUseCase;

    public EmergencyEventController(
            QueryPendingEmergencyUseCase queryUseCase,
            GenerateDispatchUseCase generateUseCase,
            NoDispatchUseCase noDispatchUseCase
    ) {
        this.queryUseCase = queryUseCase;
        this.generateUseCase = generateUseCase;
        this.noDispatchUseCase = noDispatchUseCase;
    }

    @GetMapping("/pending/next")
    public ApiResponse<EmergencyAlertResponse> next(HttpServletRequest request) {
        EmergencyAlertResponse response = queryUseCase.nextPending()
                .map(EmergencyAlertResponse::from)
                .orElse(null);
        return ApiResponse.success(response, traceId(request));
    }

    @PostMapping("/{eventId}/dispatches")
    public ApiResponse<DispatchResponse> generate(
            @PathVariable String eventId,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                DispatchResponse.from(generateUseCase.generate(eventId)),
                traceId(request)
        );
    }

    @PostMapping("/{eventId}/no-dispatch")
    public ApiResponse<Void> noDispatch(
            @PathVariable String eventId,
            @Valid @RequestBody NoDispatchRequest body,
            HttpServletRequest request
    ) {
        noDispatchUseCase.markNoDispatch(
                new NoDispatchCommand(eventId, body.reason(), body.confirmed())
        );
        return ApiResponse.success(null, traceId(request));
    }

    private String traceId(HttpServletRequest request) {
        Object value = request.getAttribute(TraceIdFilter.ATTRIBUTE_NAME);
        return value == null ? "unknown" : value.toString();
    }
}
