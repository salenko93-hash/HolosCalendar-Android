package ua.holoscalendar.redmi;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Explicit duration cues are separate from start offsets such as "через годину". */
final class DurationParser {
    private DurationParser() { }

    static final class Result {
        final Integer minutes;
        final String warning;
        Result(Integer minutes, String warning) { this.minutes = minutes; this.warning = warning; }
    }

    private static final String ONES = "одну|один|одна|дві|два|три|чотири|п['’ʼ]ять|шість|сім|вісім|дев['’ʼ]ять";
    private static final String TENS = "двадцять|тридцять|сорок|п['’ʼ]ятдесят";
    private static final String QUANTITY = "(?:[+-]?\\d+(?:[.,]\\d+)?|півтори|півтора|пів|(?:" + TENS
            + ")(?:\\s+(?:" + ONES + "))?|десять|п['’ʼ]ятнадцять|" + ONES + ")";
    private static final String MINUTE = "(?:хвилина|хвилину|хвилини|хвилин|хв)";
    private static final String HOUR = "(?:година|годину|години|годин|год)";
    private static final Pattern DURATION = Pattern.compile("(?<![\\p{L}\\d])"
            + "(?:на|тривалістю|триватиме|протягом|тривалість\\s*:?)\\s+"
            + "(?:((?:" + QUANTITY + "))\\s*)?(" + HOUR + "|" + MINUTE + ")\\b\\.?"
            + "(?:\\s+(?:і\\s+)?(" + QUANTITY + ")\\s*(" + MINUTE + ")\\b\\.?)?",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    static Result consume(StringBuilder remaining) {
        Matcher matcher = DURATION.matcher(remaining);
        Integer minutes = null;
        int count = 0;
        boolean invalid = false;
        while (matcher.find()) {
            count++;
            try {
                boolean hours = matcher.group(2).toLowerCase(Locale.ROOT).startsWith("год");
                BigDecimal total = quantity(matcher.group(1)).multiply(BigDecimal.valueOf(hours ? 60 : 1));
                if (matcher.group(3) != null) {
                    if (!hours) throw new IllegalArgumentException("Repeated minute unit");
                    total = total.add(quantity(matcher.group(3)));
                }
                minutes = total.intValueExact();
                if (minutes < 1 || minutes > 1440) invalid = true;
            } catch (IllegalArgumentException | ArithmeticException e) { invalid = true; }
            // Keep indexes stable for subsequent matches and the date/time parser.
            for (int i = matcher.start(); i < matcher.end(); i++) remaining.setCharAt(i, ' ');
        }
        if (count > 1) return new Result(null, "Знайдено кілька тривалостей — оберіть одну вручну");
        if (invalid) return new Result(null, "Некоректна тривалість — оберіть від 1 до 1440 хвилин вручну");
        return new Result(minutes, null);
    }

    private static BigDecimal quantity(String text) {
        if (text == null) return BigDecimal.ONE;
        String value = text.toLowerCase(Locale.ROOT).replace('’', '\'').replace('ʼ', '\'');
        if (value.matches("[+-]?\\d+(?:[.,]\\d+)?")) return new BigDecimal(value.replace(',', '.'));
        if (value.equals("пів")) return new BigDecimal("0.5");
        if (value.equals("півтори") || value.equals("півтора")) return new BigDecimal("1.5");
        int total = 0;
        for (String word : value.split("\\s+")) {
            switch (word) {
                case "одну": case "один": case "одна": total += 1; break;
                case "дві": case "два": total += 2; break;
                case "три": total += 3; break;
                case "чотири": total += 4; break;
                case "п'ять": total += 5; break;
                case "шість": total += 6; break;
                case "сім": total += 7; break;
                case "вісім": total += 8; break;
                case "дев'ять": total += 9; break;
                case "десять": total += 10; break;
                case "п'ятнадцять": total += 15; break;
                case "двадцять": total += 20; break;
                case "тридцять": total += 30; break;
                case "сорок": total += 40; break;
                case "п'ятдесят": total += 50; break;
                default: throw new IllegalArgumentException("Unsupported quantity");
            }
        }
        return BigDecimal.valueOf(total);
    }
}
