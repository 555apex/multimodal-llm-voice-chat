package cn.fj.roadagent.interfaces.rest.traffic;

import cn.fj.roadagent.application.traffic.AreaTrafficQueryCommand;
import cn.fj.roadagent.application.traffic.QueryAreaTrafficUseCase;
import cn.fj.roadagent.interfaces.rest.common.ApiResponse;
import cn.fj.roadagent.interfaces.rest.common.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/traffic")
public final class AreaTrafficController {
    private final QueryAreaTrafficUseCase useCase;

    public AreaTrafficController(QueryAreaTrafficUseCase useCase) {
        this.useCase = useCase;
    }

    @PostMapping("/area-queries")
    public ApiResponse<AreaTrafficResponse> query(
            @Valid @RequestBody AreaTrafficQueryRequest request,
            HttpServletRequest servletRequest
    ) {
        String traceId = servletRequest.getAttribute(TraceIdFilter.ATTRIBUTE_NAME).toString();
        var result = useCase.queryArea(new AreaTrafficQueryCommand(
                request.city(), request.areaName(), request.scope(), traceId
        ));
        return ApiResponse.success(AreaTrafficResponse.from(result), result.traceId());
    }
}
