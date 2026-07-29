package cn.fj.roadagent.application.dispatch;

public record NoDispatchCommand(
        String eventId,
        String reason,
        boolean confirmed
) {
}
