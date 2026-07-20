package cn.fj.roadagent.interfaces.rest.traffic;

import cn.fj.roadagent.application.traffic.QueryRealtimeTrafficUseCase;
import cn.fj.roadagent.application.traffic.TrafficQueryCommand;
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

    private final QueryRealtimeTrafficUseCase useCase;  // 构造实时交通查询业务的用例

    public TrafficController(QueryRealtimeTrafficUseCase useCase) {
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
        var command = new TrafficQueryCommand(
                request.areaCode(), request.roadName(), request.direction(), traceId
        );// var是指类型判断，可以不用写死变量类型，会自动判断为TrafficQueryCommand
        // request（即TrafficQueryRequest）是接口层的数据传输对象（DTO），表示HTTP层的表达
        // TrafficQueryCommand是应用层的命令对象，表达业务
        // DTO转化为Command的意义：实现接口层和应用层解耦
        var result = useCase.query(command);    // 调用QueryRealtimeTrafficUseCase接口的query方法，拿到结果
        // Controller（TrafficController）只负责接收请求-转换参数-调用业务-返回结果，体现分层思想
        return ApiResponse.success(TrafficQueryResponse.from(result), result.traceId());  // 将结果result包装为ApiResponse，返回JSON到前端
    }
}
