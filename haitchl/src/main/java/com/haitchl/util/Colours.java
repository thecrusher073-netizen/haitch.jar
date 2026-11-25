package com.haitchl.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.Locale;

public final class Colours {
    /** HaitchL brand purple and gold. */
    public static final int BRAND = 0xA855F7;
    public static final int ACCENT = 0xFACC15;
    public static final int GOOD = 0x55FF55;
    public static final int BAD = 0xFF5555;
    public static final int TEXT = 0xE5E7EB;
    public static final int MUTED = 0x9CA3AF;

    private Colours() {}

    /**
     * Accepts "#RRGGBB", "RRGGBB" or a Minecraft colour name ("gold", "light_purple"...).
     * @return the RGB value, or null if empty / not understood.
     */
    public static Integer parse(String input) {
        if (input == null) return null;
        String s = input.trim();
        if (s.isEmpty() || s.equalsIgnoreCase("off") || s.equalsIgnoreCase("none")) return null;
        String hex = s.startsWith("#") ? s.substring(1) : s;
        if (hex.matches("[0-9a-fA-F]{6}")) return Integer.parseInt(hex, 16);
        ChatFormatting f = ChatFormatting.getByName(s.toLowerCase(Locale.ROOT));
        if (f != null && f.getColor() != null) return f.getColor();
        return null;
    }

    public static String toHex(int rgb) {
        return String.format("#%06X", rgb & 0xFFFFFF);
    }

    /** Opaque ARGB for GUI drawing (26.x text colours are ARGB). */
    public static int argb(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    /** Rebuilds the message as plain text in a single colour. */
    public static MutableComponent recolour(Component message, int rgb) {
        return Component.literal(message.getString()).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)));
    }

    public static MutableComponent coloured(String text, int rgb) {
        return Component.literal(text).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)));
    }

    /** "[HaitchL] " chat prefix. */
    public static MutableComponent prefix() {
        return coloured("[", MUTED)
                .append(coloured("Haitch", BRAND))
                .append(coloured("L", ACCENT))
                .append(coloured("] ", MUTED));
    }
}
