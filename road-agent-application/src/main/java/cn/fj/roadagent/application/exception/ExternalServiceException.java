package cn.fj.roadagent.application.exception;

/**
 * 将不同外部平台的错误统一成我方可识别的异常（项目自定义异常）
 */
public final class ExternalServiceException extends RuntimeException {

    private final String service;
    private final String errorCode;

    public ExternalServiceException(String service, String errorCode, String message) {
        super(message);
        this.service = service;
        this.errorCode = errorCode;
    }

    public ExternalServiceException(String service, String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.service = service;
        this.errorCode = errorCode;
    }

    public String service() {
        return service;
    }

    public String errorCode() {
        return errorCode;
    }
}
