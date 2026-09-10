package cn.fj.roadagent.interfaces.rest.traffic;

import cn.fj.roadagent.application.traffic.HighwayTrafficQuery;
import cn.fj.roadagent.application.traffic.QueryHighwayTrafficUseCase;
import cn.fj.roadagent.interfaces.rest.common.ApiResponse;
import cn.fj.roadagent.interfaces.rest.common.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController    // 声明这个类为REST控制器，用于处理HTTP请求，返回值会自动序列化为JSON对象
@RequestMapping("/api/v1/traffic")  // 定义基础路径，这个类里所有方法的URL都以/api/v1/traffic开头
public final class TrafficController {  // 声明该类不可继承

    private final QueryHighwayTrafficUseCase useCase;

    public TrafficController(QueryHighwayTrafficUseCase useCase) {
        this.useCase = useCase;
    }   // 使用了Spring框架的构造器注入

    @PostMapping("/queries")    // 声明这个方法处理HTTP POST请求
    //完整路径为基础路径+方法路径 即：/api/v1/traffic/queries。使用 /queries 作为资源名体现了 CQRS（命令查询职责分离） 思想，将查询操作与命令操作在 URL 上区分开
    public ApiResponse<TrafficQueryResponse> query( // 泛型语法，ApiResponse为包装类，里面表示具体包装的数据类型为TrafficQueryResponse
            @Valid @RequestBody TrafficQueryRequest request,
            // @Valid：自动校验request中的约束注解（包括@NotBlank等等，在TrafficQueryRequest.java有相关注解）
            // @RequestBody：HTTP请求体（前端发来的JSON）反序列化为Java对象 TrafficQueryRequest DTO对象
            HttpServletRequest servletRequest   // Servlet的请求对象，包含HTTP层面所有信息，用于获取traceId
    )
    {
        String traceId = servletRequest.getAttribute(TraceIdFilter.ATTRIBUTE_NAME).toString();  // 取出traceId
        var result = useCase.query(new HighwayTrafficQuery(
                request.queryType(), request.originCity(), request.destinationCity(),
                request.routeCode(), request.routeName(), request.selectedCities(), request.analysisCity(), traceId,
                Boolean.TRUE.equals(request.includeTrend()), false, request.analysisDate()
        ));
        return ApiResponse.success(TrafficQueryResponse.from(result), result.traceId());  // 将结果result包装为ApiResponse，返回JSON到前端
    }
}
