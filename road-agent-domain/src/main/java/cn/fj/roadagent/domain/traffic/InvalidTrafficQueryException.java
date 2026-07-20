package cn.fj.roadagent.domain.traffic;

public final class InvalidTrafficQueryException extends IllegalArgumentException {

    public InvalidTrafficQueryException(String message) {
        super(message);
    }
}
