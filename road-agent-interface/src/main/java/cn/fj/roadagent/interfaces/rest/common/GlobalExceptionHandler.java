package cn.fj.roadagent.interfaces.rest.common;

import cn.fj.roadagent.application.exception.ExternalServiceException;
import cn.fj.roadagent.application.exception.BusinessRuleException;
import cn.fj.roadagent.domain.traffic.InvalidTrafficQueryException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public final class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage() == null ? "请求参数不正确" : error.getDefaultMessage())
                .orElse("请求参数不正确");
        return ResponseEntity.badRequest().body(
                ApiResponse.error("INVALID_REQUEST", message, traceId(request))
        );
    }

    @ExceptionHandler(InvalidTrafficQueryException.class)
    public ResponseEntity<ApiResponse<Void>> handleDomainValidation(
            InvalidTrafficQueryException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.badRequest().body(
                ApiResponse.error("INVALID_TRAFFIC_QUERY", exception.getMessage(), traceId(request))
        );
    }

    @ExceptionHandler(ExternalServiceException.class)
    public ResponseEntity<ApiResponse<Void>> handleExternalService(
            ExternalServiceException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(
                ApiResponse.error(exception.errorCode(), exception.getMessage(), traceId(request))
        );
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessRule(
            BusinessRuleException exception,
            HttpServletRequest request
    ) {
        HttpStatus status;
        if ("DISPATCH_NOT_FOUND".equals(exception.errorCode())) {
            status = HttpStatus.NOT_FOUND;
        } else if (exception.errorCode().startsWith("TRAFFIC_")
                || "AREA_QUERY_TOO_LARGE".equals(exception.errorCode())) {
            status = HttpStatus.BAD_REQUEST;
        } else {
            status = HttpStatus.CONFLICT;
        }
        return ResponseEntity.status(status).body(
                ApiResponse.error(exception.errorCode(), exception.getMessage(), traceId(request))
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(
            IllegalArgumentException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.badRequest().body(
                ApiResponse.error("INVALID_REQUEST", exception.getMessage(), traceId(request))
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(
            Exception exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ApiResponse.error("INTERNAL_ERROR", "系统处理请求时发生异常", traceId(request))
        );
    }

    private String traceId(HttpServletRequest request) {
        Object value = request.getAttribute(TraceIdFilter.ATTRIBUTE_NAME);
        return value == null ? "unknown" : value.toString();
    }
}
