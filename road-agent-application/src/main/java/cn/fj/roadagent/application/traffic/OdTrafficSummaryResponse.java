package cn.fj.roadagent.application.traffic;

/** OD摘要不按句数和固定措辞拒绝合法回答。 */
public record OdTrafficSummaryResponse(String summary) {
    public OdTrafficSummaryResponse {
        if (summary == null || summary.isBlank() || summary.length() > 2000
                || summary.codePoints().noneMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN)) {
            throw new IllegalArgumentException("模型OD摘要必须是非空中文文本");
        }
        summary = summary.trim();
    }
}
