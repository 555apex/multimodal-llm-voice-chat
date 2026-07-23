package cn.fj.roadagent.application.exception;

/** 可预期的业务拒绝，例如重复审批或版本冲突。 */
public final class BusinessRuleException extends RuntimeException {
    private final String errorCode;

    public BusinessRuleException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
