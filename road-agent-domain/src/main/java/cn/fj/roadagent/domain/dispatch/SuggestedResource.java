package cn.fj.roadagent.domain.dispatch;

/** 模型基于通用知识提出的资源建议，不代表真实库存或可用性。 */
public record SuggestedResource(
        String resourceType,
        String resourceName,
        int quantity,
        String unit,
        String purpose
) {
    public SuggestedResource {
        resourceType = requireText(resourceType, "资源类型不能为空");
        resourceName = requireText(resourceName, "资源名称不能为空");
        if (quantity < 1) {
            throw new IllegalArgumentException("建议资源数量必须大于0");
        }
        unit = requireText(unit, "资源单位不能为空");
        purpose = requireText(purpose, "资源用途不能为空");
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
