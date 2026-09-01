package cn.fj.roadagent.domain.dispatch;

/** 全省可调度库存仍不足时形成的明确缺口。 */
public record ResourceShortage(
        String resourceTypeCode,
        String resourceTypeName,
        int requiredQuantity,
        int allocatedQuantity,
        int shortageQuantity,
        String unit,
        String reason
) {
    public ResourceShortage {
        if (resourceTypeCode == null || resourceTypeCode.isBlank()) {
            throw new IllegalArgumentException("缺口资源类型编码不能为空");
        }
        if (resourceTypeName == null || resourceTypeName.isBlank()) {
            throw new IllegalArgumentException("缺口资源类型名称不能为空");
        }
        if (requiredQuantity < 1 || allocatedQuantity < 0 || shortageQuantity < 1
                || allocatedQuantity + shortageQuantity != requiredQuantity) {
            throw new IllegalArgumentException("资源缺口数量不一致");
        }
        if (unit == null || unit.isBlank()) throw new IllegalArgumentException("资源单位不能为空");
        reason = reason == null || reason.isBlank() ? "省内当前可调度库存不足" : reason.trim();
    }
}
