package cn.fj.roadagent.core.traffic;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Validate factual numbers, including explicit display rounding and quantity units. */
final class ModelFactNumberValidator {
    private static final Pattern NUMBER = Pattern.compile(
            "(?<![A-Za-z0-9])((?:\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.\\d+)?)(?:\\s*(%|％|万|亿))?");
    private static final Pattern ROUTE = Pattern.compile("(?i)(?<![A-Za-z0-9])(?:FJ|G|S)\\d+(?![A-Za-z0-9])");
    private static final Pattern RATIO = Pattern.compile(
            "(shareRatio|morningPeakRatio|eveningPeakRatio|tendencyRatio|utilizationRatio)=([0-9]+(?:\\.[0-9]+)?)");

    private ModelFactNumberValidator() { }

    static String presentationFacts(String facts) {
        StringBuilder result = new StringBuilder(facts);
        Matcher matcher = RATIO.matcher(facts);
        while (matcher.find()) {
            BigDecimal percentage = new BigDecimal(matcher.group(2)).multiply(BigDecimal.valueOf(100));
            result.append("\n").append(matcher.group(1)).append("展示百分比=")
                    .append(percentage.setScale(2, RoundingMode.HALF_UP).toPlainString()).append('%');
        }
        return result.toString();
    }

    static void validate(String modelText, String serializedFacts) {
        List<Value> allowed = extract(presentationFacts(serializedFacts));
        Matcher routes = ROUTE.matcher(modelText);
        String upperFacts = serializedFacts.toUpperCase(Locale.ROOT);
        while (routes.find()) {
            if (!Pattern.compile("(?<![A-Za-z0-9])" + Pattern.quote(routes.group().toUpperCase(Locale.ROOT)) + "(?![A-Za-z0-9])")
                    .matcher(upperFacts).find()) {
                throw new IllegalArgumentException("解读引用了事实之外的路线：" + routes.group());
            }
        }
        for (Value value : extract(modelText)) {
            if (allowed.stream().noneMatch(fact -> matches(value, fact))) {
                throw new IllegalArgumentException("解读引用了事实之外的数值：" + value.number + value.unit);
            }
        }
    }

    private static boolean matches(Value display, Value fact) {
        boolean percentage = display.unit.equals("%");
        if (percentage != fact.unit.equals("%")) return false;
        BigDecimal divisor = switch (display.unit) {
            case "万" -> BigDecimal.valueOf(10_000);
            case "亿" -> BigDecimal.valueOf(100_000_000);
            default -> BigDecimal.ONE;
        };
        BigDecimal factBase = fact.number.multiply(switch (fact.unit) {
            case "万" -> BigDecimal.valueOf(10_000);
            case "亿" -> BigDecimal.valueOf(100_000_000);
            default -> BigDecimal.ONE;
        });
        BigDecimal converted = factBase.divide(divisor);
        if (converted.compareTo(display.number) == 0) return true;
        // Permit only explicit <=2 decimal display rounding; never round arbitrary integer counts.
        return display.scale <= 2 && (display.scale > 0 || divisor.compareTo(BigDecimal.ONE) > 0)
                && converted.setScale(display.scale, RoundingMode.HALF_UP).compareTo(display.number) == 0;
    }

    private static List<Value> extract(String text) {
        List<Value> result = new ArrayList<>();
        if (text == null || text.isBlank()) return result;
        Matcher matcher = NUMBER.matcher(text);
        while (matcher.find()) {
            BigDecimal number = new BigDecimal(matcher.group(1).replace(",", ""));
            String unit = matcher.group(2) == null ? "" : matcher.group(2).replace('％', '%');
            result.add(new Value(number, unit, Math.max(0, number.scale())));
        }
        return result;
    }

    private record Value(BigDecimal number, String unit, int scale) { }
}
