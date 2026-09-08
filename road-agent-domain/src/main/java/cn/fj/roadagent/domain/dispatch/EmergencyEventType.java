package cn.fj.roadagent.domain.dispatch;

import java.util.Arrays;
import java.util.Optional;

/** 项目当前支持的16类公路应急事件受控字典。 */
public enum EmergencyEventType {
    DT01("崩塌（落石）"),
    DT02("滑坡（坡体位移）"),
    DT03("泥石流"),
    DT04("沉陷与塌陷"),
    DT05("水毁"),
    ET101("拥堵"),
    ET102("明火（火灾）"),
    ET103("抛撒物"),
    ET104("设备故障"),
    ET105("占用应急车道"),
    ET106("交通事故"),
    ET107("异常停车"),
    ET108("浓雾检测"),
    ET109("路障"),
    ET110("施工"),
    ET112("道路积雪");

    private final String displayName;

    EmergencyEventType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static Optional<EmergencyEventType> fromCode(String code) {
        if (code == null || code.isBlank()) return Optional.empty();
        return Arrays.stream(values())
                .filter(type -> type.name().equalsIgnoreCase(code.trim()))
                .findFirst();
    }

    public static EmergencyEventType require(String code) {
        return fromCode(code).orElseThrow(() ->
                new IllegalArgumentException("不支持的应急事件类型：" + code));
    }
}
