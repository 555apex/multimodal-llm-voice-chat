package cn.fj.roadagent.domain.dispatch;

/** 旧预案保留的资源建议快照；生成资源需求时不再作为允许清单或必选项。 */
public record ResponsePlanResourceBaseline(
        String resourceTypeCode,
        int quantity,
        String purpose,
        ResponsePlanResourceMode mode
) {
    public ResponsePlanResourceBaseline {
        resourceTypeCode = requireText(resourceTypeCode, "资源类型编码不能为空");
        purpose = requireText(purpose, "资源用途不能为空");
        if (quantity < 1 || quantity > 999) {
            throw new IllegalArgumentException("预案资源基数必须在1到999之间");
        }
        if (mode == null) throw new IllegalArgumentException("资源基线模式不能为空");
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value.trim();
    }
}
