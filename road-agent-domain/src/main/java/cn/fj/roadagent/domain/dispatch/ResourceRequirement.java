package cn.fj.roadagent.domain.dispatch;

/** 模型在数据库资源类型白名单内提出的需求，不等于已完成调度。 */
public record ResourceRequirement(
        String resourceTypeCode,
        String resourceTypeName,
        int quantity,
        String unit,
        String purpose
) {
    public ResourceRequirement {
        resourceTypeCode = requireText(resourceTypeCode, "资源类型编码不能为空");
        resourceTypeName = requireText(resourceTypeName, "资源类型名称不能为空");
        if (quantity < 1) {
            throw new IllegalArgumentException("资源需求数量必须大于0");
        }
        unit = requireText(unit, "资源单位不能为空");
        purpose = requireText(purpose, "资源用途不能为空");
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value.trim();
    }
}
