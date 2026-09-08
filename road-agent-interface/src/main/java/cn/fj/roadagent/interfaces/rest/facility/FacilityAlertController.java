package cn.fj.roadagent.interfaces.rest.facility;

import cn.fj.roadagent.application.facility.FacilityAlertTransitionCommand;
import cn.fj.roadagent.application.facility.QueryFacilityAlertsUseCase;
import cn.fj.roadagent.application.facility.TransitionFacilityAlertUseCase;
import cn.fj.roadagent.domain.facility.AlarmLevel;
import cn.fj.roadagent.domain.facility.FacilityAlertStatus;
import cn.fj.roadagent.interfaces.rest.common.ApiResponse;
import cn.fj.roadagent.interfaces.rest.common.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/facility-alerts")
public final class FacilityAlertController {
    private final QueryFacilityAlertsUseCase queryUseCase;
    private final TransitionFacilityAlertUseCase transitionUseCase;

    public FacilityAlertController(
            QueryFacilityAlertsUseCase queryUseCase,
            TransitionFacilityAlertUseCase transitionUseCase
    ) {
        this.queryUseCase = queryUseCase;
        this.transitionUseCase = transitionUseCase;
    }

    @GetMapping
    public ApiResponse<FacilityAlertPageResponse> query(
            @RequestParam(defaultValue = "PENDING") FacilityAlertStatus status,
            @RequestParam(required = false) AlarmLevel alarmLevel,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                FacilityAlertPageResponse.from(queryUseCase.query(status, alarmLevel, page, size)),
                traceId(request)
        );
    }

    @GetMapping("/health-report")
    public ApiResponse<FacilityHealthReportResponse> healthReport(HttpServletRequest request) {
        return ApiResponse.success(
                FacilityHealthReportResponse.from(queryUseCase.healthReport()), traceId(request)
        );
    }

    @GetMapping("/focus")
    public ApiResponse<List<FacilityFocusResponse>> focus(
            @RequestParam(defaultValue = "10") int limit,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                queryUseCase.focus(limit).stream().map(FacilityFocusResponse::from).toList(),
                traceId(request)
        );
    }

    @PostMapping("/{alertId}/status-transitions")
    public ApiResponse<FacilityAlertResponse> transition(
            @PathVariable long alertId,
            @Valid @RequestBody FacilityAlertTransitionRequest body,
            HttpServletRequest request
    ) {
        return ApiResponse.success(FacilityAlertResponse.from(transitionUseCase.transition(
                new FacilityAlertTransitionCommand(
                        alertId, body.expectedStatus(), body.targetStatus(),
                        body.resolutionType(), body.remark()
                )
        )), traceId(request));
    }

    private String traceId(HttpServletRequest request) {
        Object value = request.getAttribute(TraceIdFilter.ATTRIBUTE_NAME);
        return value == null ? "unknown" : value.toString();
    }
}
