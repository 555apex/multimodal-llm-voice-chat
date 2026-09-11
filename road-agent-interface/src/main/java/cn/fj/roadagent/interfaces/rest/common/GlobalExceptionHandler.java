package cn.fj.roadagent.interfaces.rest.common;

import cn.fj.roadagent.application.exception.ExternalServiceException;
import cn.fj.roadagent.application.exception.BusinessRuleException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
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
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body(
                ApiResponse.error("INVALID_REQUEST", message, traceId(request))
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableRequest(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body(
                ApiResponse.error("INVALID_REQUEST", "请求JSON格式或枚举值不正确", traceId(request))
        );
    }

    @ExceptionHandler(ExternalServiceException.class)
    public ResponseEntity<ApiResponse<Void>> handleExternalService(
            ExternalServiceException exception,
            HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.BAD_GATEWAY;
        if ("SPEECH".equals(exception.service())) {
            String code = exception.errorCode();
            status = code.endsWith("_NO_SPEECH") ? HttpStatus.UNPROCESSABLE_ENTITY
                    : code.endsWith("_INVALID_AUDIO") ? HttpStatus.UNSUPPORTED_MEDIA_TYPE
                    : code.endsWith("_INVALID_TEXT") ? HttpStatus.BAD_REQUEST
                    : code.endsWith("_TOO_LARGE") ? HttpStatus.PAYLOAD_TOO_LARGE
                    : code.endsWith("_BUSY") ? HttpStatus.TOO_MANY_REQUESTS
                    : code.endsWith("_CANCELLED") ? HttpStatus.CONFLICT
                    : code.endsWith("_TIMEOUT") ? HttpStatus.GATEWAY_TIMEOUT
                    : HttpStatus.SERVICE_UNAVAILABLE;
        }
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(
                ApiResponse.error(exception.errorCode(), exception.getMessage(), traceId(request))
        );
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessRule(
            BusinessRuleException exception,
            HttpServletRequest request
    ) {
        HttpStatus status;
        if ("DISPATCH_NOT_FOUND".equals(exception.errorCode())
                || "EVENT_NOT_FOUND".equals(exception.errorCode())
                || "WORKFLOW_NOT_FOUND".equals(exception.errorCode())
                || "FACILITY_ALERT_NOT_FOUND".equals(exception.errorCode())) {
            status = HttpStatus.NOT_FOUND;
        } else if (exception.errorCode().startsWith("TRAFFIC_")
                || "AREA_QUERY_TOO_LARGE".equals(exception.errorCode())) {
            status = HttpStatus.BAD_REQUEST;
        } else {
            status = HttpStatus.CONFLICT;
        }
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(
                ApiResponse.error(exception.errorCode(), exception.getMessage(), traceId(request))
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(
            IllegalArgumentException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body(
                ApiResponse.error("INVALID_REQUEST", exception.getMessage(), traceId(request))
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(
            Exception exception,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        // A binary/SSE response that has started cannot be replaced by a JSON body.
        if (response.isCommitted()
                || org.springframework.web.util.DisconnectedClientHelper.isClientDisconnectedException(exception)) {
            return null;
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).contentType(MediaType.APPLICATION_JSON).body(
                ApiResponse.error("INTERNAL_ERROR", "系统处理请求时发生异常", traceId(request))
        );
    }

    private String traceId(HttpServletRequest request) {
        Object value = request.getAttribute(TraceIdFilter.ATTRIBUTE_NAME);
        return value == null ? "unknown" : value.toString();
    }
}
