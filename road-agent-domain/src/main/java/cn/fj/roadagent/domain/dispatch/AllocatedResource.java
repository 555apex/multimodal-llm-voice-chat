package cn.fj.roadagent.domain.dispatch;

/** 已通过库存校验并完成软占用的实际资源快照。 */
public record AllocatedResource(
        String resourceId,
        String resourceTypeCode,
        String resourceTypeName,
        String resourceName,
        String sourceCityCode,
        String sourceCityName,
        int quantity,
        String unit,
        String purpose,
        double estimatedDistanceKm,
        DispatchScope dispatchScope
) {
    public AllocatedResource {
        resourceId = requireText(resourceId, "资源ID不能为空");
        resourceTypeCode = requireText(resourceTypeCode, "资源类型编码不能为空");
        resourceTypeName = requireText(resourceTypeName, "资源类型名称不能为空");
        resourceName = requireText(resourceName, "资源名称不能为空");
        sourceCityCode = requireText(sourceCityCode, "资源来源城市编码不能为空");
        sourceCityName = requireText(sourceCityName, "资源来源城市不能为空");
        if (quantity < 1) throw new IllegalArgumentException("实际调度数量必须大于0");
        unit = requireText(unit, "资源单位不能为空");
        purpose = requireText(purpose, "资源用途不能为空");
        if (!Double.isFinite(estimatedDistanceKm) || estimatedDistanceKm < 0) {
            throw new IllegalArgumentException("城市级估算距离不能为负数");
        }
        dispatchScope = java.util.Objects.requireNonNull(dispatchScope, "调度范围不能为空");
    }

    public SuggestedResource compatibilityProjection() {
        return new SuggestedResource(
                resourceTypeName, resourceName, quantity, unit, purpose
        );
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value.trim();
    }
}
