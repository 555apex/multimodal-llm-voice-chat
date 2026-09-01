package cn.fj.roadagent.application.traffic;

import java.util.Set;

/** 路况查询模型必须严格返回的当前态势摘要与短时定性趋势。 */
public record TrafficForecastSummaryResponse(
        String summary,
        String trend,
        String trendForecast
) {
    private static final Set<String> ALLOWED_TRENDS = Set.of(
            "基本稳定", "持续拥堵", "可能加剧", "逐渐缓解", "局部分化"
    );
    private static final Set<String> CAUTIOUS_WORDS = Set.of("预计", "可能", "有望");
    private static final Set<String> FORBIDDEN_CAUSE_TERMS = Set.of(
            "事故", "车祸", "施工", "天气", "降雨", "流量变化"
    );
    private static final Set<String> FORECAST_ONLY_TERMS = Set.of(
            "未来1至2小时", "未来1-2小时", "未来一至两小时", "发展趋势"
    );
    private static final Set<String> FORBIDDEN_FORECAST_TERMS = Set.of(
            "km/h", "km／h", "公里/小时", "公里／小时", "辆/小时", "辆／小时",
            "%", "％", "概率", "解除时间"
    );

    public TrafficForecastSummaryResponse {
        summary = requireChinese(summary, "模型当前态势摘要不能为空");
        long summarySentenceCount = sentenceCount(summary);
        if (summarySentenceCount < 2 || summarySentenceCount > 6
                || summary.length() < 50 || summary.length() > 800
                || !endsWithSentenceTerminator(summary)) {
            throw new IllegalArgumentException("模型当前态势摘要必须是2至6句、50至800字的中文研判");
        }
        if (FORBIDDEN_CAUSE_TERMS.stream().anyMatch(summary::contains)) {
            throw new IllegalArgumentException("模型当前态势摘要不得推测或讨论拥堵原因");
        }
        if (FORECAST_ONLY_TERMS.stream().anyMatch(summary::contains)) {
            throw new IllegalArgumentException("未来趋势只能写入模型短时趋势研判字段");
        }

        trend = normalizeTrend(trend);
        if (!ALLOWED_TRENDS.contains(trend)) {
            throw new IllegalArgumentException("模型短时趋势标签不合法");
        }

        trendForecast = requireChinese(trendForecast, "模型短时趋势研判不能为空");
        trendForecast = normalizeHorizon(trendForecast);
        String forecastWithoutHorizon = trendForecast.replace("未来1至2小时", "");
        boolean safePresentation = sentenceCount(trendForecast) == 1
                && endsWithSentenceTerminator(trendForecast)
                && trendForecast.contains("未来1至2小时")
                && !forecastWithoutHorizon.chars().anyMatch(Character::isDigit)
                && trendForecast.contains(trend)
                && CAUTIOUS_WORDS.stream().anyMatch(trendForecast::contains)
                && FORBIDDEN_CAUSE_TERMS.stream().noneMatch(trendForecast::contains)
                && FORBIDDEN_FORECAST_TERMS.stream().noneMatch(trendForecast::contains);
        if (!safePresentation) {
            // trend已通过白名单。模型给出的趋势句若只是时间写法、措辞或标点不规范，
            // 由Java按同一趋势标签生成安全句，避免把完整路况回答整体判失败。
            trendForecast = safeForecast(trend);
        }
    }

    public String combinedSummary() {
        return summary + trendForecast;
    }

    private static String requireChinese(String value, String emptyMessage) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(emptyMessage);
        }
        String normalized = value.trim();
        boolean containsChinese = normalized.codePoints()
                .anyMatch(codePoint -> Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN);
        if (!containsChinese) {
            throw new IllegalArgumentException("模型交通研判必须使用中文");
        }
        return normalized;
    }

    private static String normalizeTrend(String value) {
        String normalized = value == null ? "" : value.trim();
        return switch (normalized) {
            case "保持稳定", "总体稳定", "趋于稳定" -> "基本稳定";
            case "拥堵持续", "维持拥堵" -> "持续拥堵";
            case "趋于加剧", "可能恶化" -> "可能加剧";
            case "趋于缓解", "逐步缓解", "有望缓解" -> "逐渐缓解";
            case "分化", "局部差异" -> "局部分化";
            default -> normalized;
        };
    }

    private static String normalizeHorizon(String value) {
        return value
                .replaceAll("未来\\s*[1１一]\\s*[-—至到~～]\\s*[2２二两]\\s*小时", "未来1至2小时")
                .replace("未来一到两小时", "未来1至2小时")
                .replace("未来一至二小时", "未来1至2小时");
    }

    private static String safeForecast(String trend) {
        return switch (trend) {
            case "基本稳定" -> "未来1至2小时，预计相关道路通行态势基本稳定。";
            case "持续拥堵" -> "未来1至2小时，预计相关道路仍将持续拥堵。";
            case "可能加剧" -> "未来1至2小时，相关道路拥堵态势可能加剧。";
            case "逐渐缓解" -> "未来1至2小时，相关道路通行态势有望逐渐缓解。";
            case "局部分化" -> "未来1至2小时，预计不同道路通行态势呈局部分化。";
            default -> throw new IllegalArgumentException("模型短时趋势标签不合法");
        };
    }

    private static long sentenceCount(String value) {
        return value.chars()
                .filter(character -> character == '。' || character == '！' || character == '？'
                        || character == '!' || character == '?')
                .count();
    }

    private static boolean endsWithSentenceTerminator(String value) {
        char lastCharacter = value.charAt(value.length() - 1);
        return lastCharacter == '。' || lastCharacter == '！' || lastCharacter == '？'
                || lastCharacter == '!' || lastCharacter == '?';
    }
}
