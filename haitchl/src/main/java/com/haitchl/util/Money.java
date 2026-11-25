package com.haitchl.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses and formats DonutSMP-style money amounts such as "$1.5K", "250,000" or "2b". */
public final class Money {
    private static final Pattern AMOUNT = Pattern.compile("(-?[\\d,]*\\.?\\d+)\\s*([kmbtKMBT])?");

    private Money() {}

    /** @return the parsed value, or -1 if the text holds no amount. */
    public static double parse(String text) {
        if (text == null) return -1;
        String cleaned = text.replace("$", "").trim();
        Matcher m = AMOUNT.matcher(cleaned);
        if (!m.find()) return -1;
        double value;
        try {
            value = Double.parseDouble(m.group(1).replace(",", ""));
        } catch (NumberFormatException e) {
            return -1;
        }
        String suffix = m.group(2);
        if (suffix != null) {
            switch (Character.toLowerCase(suffix.charAt(0))) {
                case 'k' -> value *= 1_000d;
                case 'm' -> value *= 1_000_000d;
                case 'b' -> value *= 1_000_000_000d;
                case 't' -> value *= 1_000_000_000_000d;
                default -> { }
            }
        }
        return value;
    }

    /** Formats like DonutSMP: 1500 -> "1.5K", 2_000_000 -> "2M". */
    public static String format(double value) {
        double abs = Math.abs(value);
        String sign = value < 0 ? "-" : "";
        String suffix = "";
        double shown = abs;
        if (abs >= 1e12) { shown = abs / 1e12; suffix = "T"; }
        else if (abs >= 1e9) { shown = abs / 1e9; suffix = "B"; }
        else if (abs >= 1e6) { shown = abs / 1e6; suffix = "M"; }
        else if (abs >= 1e3) { shown = abs / 1e3; suffix = "K"; }
        String num = BigDecimal.valueOf(shown).setScale(2, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
        return sign + "$" + num + suffix;
    }

    /** Signed format for profit/loss lines: "+$1.2K" / "-$300". */
    public static String formatSigned(double value) {
        return (value > 0 ? "+" : "") + format(value);
    }

    /** Plain number suitable for a /pay command (no $ and no suffix rounding loss). */
    public static String plain(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString().toLowerCase(Locale.ROOT);
    }
}
