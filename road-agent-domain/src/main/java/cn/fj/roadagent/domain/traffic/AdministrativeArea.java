package cn.fj.roadagent.domain.traffic;

import java.util.Objects;

/** 经过外部行政区服务解析、再由Java校验的福建行政区。 */
public record AdministrativeArea(
        String name,
        String city,
        String adcode,
        AdministrativeAreaLevel level,
        GeoBoundary boundary
) {
    public AdministrativeArea {
        name = requireText(name, "行政区名称不能为空");
        city = requireText(city, "所属城市不能为空");
        adcode = requireText(adcode, "行政区编码不能为空");
        if (!adcode.matches("35\\d{4}")) {
            throw new IllegalArgumentException("行政区不属于福建省");
        }
        level = Objects.requireNonNull(level, "行政区级别不能为空");
        boundary = Objects.requireNonNull(boundary, "行政区边界不能为空");
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
