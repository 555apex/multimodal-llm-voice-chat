package cn.fj.roadagent.core.traffic;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 车型出行特征问题中的自然语言日期解析。未出现日期时返回空，由业务层默认当天。 */
public final class VehicleAnalysisDateParser {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Pattern ISO_DATE = Pattern.compile("(?<!\\d)(20\\d{2})[-/](\\d{1,2})[-/](\\d{1,2})(?!\\d)");
    private static final Pattern CHINESE_FULL_DATE = Pattern.compile("(?<!\\d)(20\\d{2})年(\\d{1,2})月(\\d{1,2})日?");
    private static final Pattern CHINESE_MONTH_DAY = Pattern.compile("(?<!\\d)(\\d{1,2})月(\\d{1,2})日");

    private VehicleAnalysisDateParser() {
    }

    public static Optional<LocalDate> parse(String message) {
        return parse(message, Clock.system(BUSINESS_ZONE));
    }

    static Optional<LocalDate> parse(String message, Clock clock) {
        if (message == null || message.isBlank()) return Optional.empty();
        String text = message.replaceAll("\\s+", "");
        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        if (text.contains("前天")) return Optional.of(today.minusDays(2));
        if (text.contains("昨天") || text.contains("昨日")) return Optional.of(today.minusDays(1));
        if (text.contains("今天") || text.contains("今日") || text.contains("当天")) return Optional.of(today);

        Optional<LocalDate> full = match(text, ISO_DATE, true, today.getYear());
        if (full.isPresent()) return full;
        full = match(text, CHINESE_FULL_DATE, true, today.getYear());
        if (full.isPresent()) return full;
        return match(text, CHINESE_MONTH_DAY, false, today.getYear());
    }

    private static Optional<LocalDate> match(String text, Pattern pattern, boolean includesYear, int defaultYear) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) return Optional.empty();
        try {
            int year = includesYear ? Integer.parseInt(matcher.group(1)) : defaultYear;
            int offset = includesYear ? 1 : 0;
            int month = Integer.parseInt(matcher.group(1 + offset));
            int day = Integer.parseInt(matcher.group(2 + offset));
            return Optional.of(LocalDate.of(year, month, day));
        } catch (DateTimeException | NumberFormatException ignored) {
            return Optional.empty();
        }
    }
}
