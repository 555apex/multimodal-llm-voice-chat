package cn.fj.roadagent.interfaces.rest.emergency;

import cn.fj.roadagent.application.dispatch.ClassifyEmergencyEventsUseCase;
import cn.fj.roadagent.interfaces.rest.common.ApiResponse;
import cn.fj.roadagent.interfaces.rest.common.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/emergency-events")
public final class EventClassificationController {
    private final ClassifyEmergencyEventsUseCase useCase;

    public EventClassificationController(ClassifyEmergencyEventsUseCase useCase) {
        this.useCase = useCase;
    }

    @PostMapping("/{eventId}/classification-retries")
    public ApiResponse<Void> retry(@PathVariable String eventId, HttpServletRequest request) {
        useCase.retry(eventId);
        Object value = request.getAttribute(TraceIdFilter.ATTRIBUTE_NAME);
        return ApiResponse.success(null, value == null ? "unknown" : value.toString());
    }

    @PostMapping("/classification-retries/next")
    public ApiResponse<Boolean> retryNext(HttpServletRequest request) {
        boolean processed = useCase.retryNext();
        Object value = request.getAttribute(TraceIdFilter.ATTRIBUTE_NAME);
        return ApiResponse.success(processed, value == null ? "unknown" : value.toString());
    }
}
