package cn.fj.roadagent.interfaces.rest.emergency;

import cn.fj.roadagent.application.dispatch.EmergencyAlert;
import cn.fj.roadagent.interfaces.rest.dispatch.DispatchResponse;

public record EmergencyAlertResponse(
        EmergencyEventResponse event,
        DispatchResponse dispatch,
        long pendingCount
) {
    public static EmergencyAlertResponse from(EmergencyAlert alert) {
        return new EmergencyAlertResponse(
                EmergencyEventResponse.from(alert.event()),
                alert.dispatch() == null ? null : DispatchResponse.from(alert.dispatch()),
                alert.pendingCount()
        );
    }
}
