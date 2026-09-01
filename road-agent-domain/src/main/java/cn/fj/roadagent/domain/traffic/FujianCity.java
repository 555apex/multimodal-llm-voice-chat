package cn.fj.roadagent.domain.traffic;

import java.util.Arrays;
import java.util.Optional;

/**
 * 福建地级市枚举
 * 包括可信adcode，adcode为行政区划编码，以下adcode为城市级别的编码关联
 * adcode与对应城市绑定模型只提取城市名称，编码由Java映射
 * 从而LLM 只能提取城市名称字符串，但最终使用的 adcode 必须Java 枚举映射的，杜绝 LLM 编造不存在的编码
 */
public enum FujianCity {
    FUZHOU("福州", "350100"),
    XIAMEN("厦门", "350200"),
    PUTIAN("莆田", "350300"),
    SANMING("三明", "350400"),
    QUANZHOU("泉州", "350500"),
    ZHANGZHOU("漳州", "350600"),
    NANPING("南平", "350700"),
    LONGYAN("龙岩", "350800"),
    NINGDE("宁德", "350900");

    private final String displayName;
    private final String adcode;

    FujianCity(String displayName, String adcode) {
        this.displayName = displayName;
        this.adcode = adcode;
    }

    public String displayName() {
        return displayName;
    }

    public String adcode() {
        return adcode;
    }

    public static Optional<FujianCity> fromName(String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }
        String normalized = input.trim().replace("福建省", "").replace("市", "");
        return Arrays.stream(values())
                .filter(city -> normalized.equals(city.displayName))
                .findFirst();
    }

    public static Optional<FujianCity> fromAdcode(String adcode) {
        return Arrays.stream(values()).filter(city -> city.adcode.equals(adcode)).findFirst();
    }
}
