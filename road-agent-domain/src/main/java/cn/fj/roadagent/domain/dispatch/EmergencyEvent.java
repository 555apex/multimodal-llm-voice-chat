package cn.fj.roadagent.domain.dispatch;

import java.util.Objects;

/**
 * 应急事件（输入）
 * 具体：用户描述经模型提取、再经Java校验后的应急事件。
 * */
public record EmergencyEvent(
        String eventType,   // 事件类型（比如：道路塌方、交通事故）
        String city,    // 城市
        String locationDescription, // 位置具体描述：比如某某路交会处
        String severity,    // 严重程度（比如HIGH、MEDIUM）
        String description  // 事件描述
) {
    // 构造函数
    public EmergencyEvent {
        eventType = requireText(eventType, "事件类型不能为空");
        city = requireText(city, "事件城市不能为空");
        locationDescription = requireText(locationDescription, "事件位置不能为空");
        severity = Objects.requireNonNullElse(severity, "UNKNOWN").trim().toUpperCase();
        description = requireText(description, "事件描述不能为空");
    }
    // 方法：检查参数是否为空
    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
