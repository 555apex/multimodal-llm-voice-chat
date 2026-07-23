package cn.fj.roadagent.domain.traffic;

/**
 * 交通查询的内部标准输入。这里不包含高德Key、Mock场景等技术参数。
 * 描述行政区代码，道路名称，方向
 */
public record TrafficQuery(String areaCode, String roadName, String direction) {

    public TrafficQuery {
        areaCode = requireText(areaCode, "行政区划代码不能为空");
        roadName = requireText(roadName, "道路名称不能为空");
        direction = normalizeOptional(direction);

        if (!areaCode.matches("\\d{6}")) {
            throw new InvalidTrafficQueryException("行政区划代码必须是6位数字");
        }
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new InvalidTrafficQueryException(message);
        }
        return value.trim();
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
