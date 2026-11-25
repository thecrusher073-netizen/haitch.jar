package com.haitchl.hud;

import com.haitchl.HaitchL;
import com.haitchl.config.HaitchLConfig;
import com.haitchl.stats.SessionStats;
import com.haitchl.util.Colours;
import com.haitchl.util.Money;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/** The HaitchL stats panel: balance, session profit/loss, W/L, win rate, streak and pay-wall. */
public final class HaitchHud {
    private record Line(String label, String value, int valueColour) {}

    private HaitchHud() {}

    public static void register() {
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, HaitchL.id("stats_hud"), HaitchHud::extract);
    }

    private static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        HaitchLConfig cfg = HaitchL.config();
        if (cfg == null || !cfg.hudEnabled || mc.player == null || mc.options.hideGui || !HaitchL.isActive()) return;

        SessionStats s = HaitchL.stats();
        Font font = mc.font;

        List<Line> lines = new ArrayList<>();
        lines.add(new Line("Balance", Double.isNaN(s.balance) ? "type /bal" : Money.format(s.balance),
                Double.isNaN(s.balance) ? Colours.MUTED : Colours.TEXT));
        lines.add(new Line("Payments", Money.formatSigned(s.paymentNet())
                + "  (" + s.paymentsIn + " in / " + s.paymentsOut + " out)", signColour(s.paymentNet())));
        lines.add(new Line("Gambling", Money.formatSigned(s.gambleNet()), signColour(s.gambleNet())));
        lines.add(new Line("W / L", s.wins + " / " + s.losses
                + String.format("  (%.0f%%)", s.winRate()), Colours.TEXT));
        lines.add(new Line("Streak", streakText(s.streak) + "  best W" + s.bestWinStreak,
                s.streak > 0 ? Colours.GOOD : s.streak < 0 ? Colours.BAD : Colours.TEXT));
        if (cfg.payWallEnabled) {
            lines.add(new Line("Pay-wall", payWallText(cfg)
                    + (s.flagged > 0 ? "  (" + s.flagged + " flagged)" : ""), Colours.ACCENT));
        }

        String title1 = "Haitch";
        String title2 = "L";
        int lineH = font.lineHeight + 2;
        int width = font.width(title1 + title2);
        for (Line l : lines) width = Math.max(width, font.width(l.label() + ": " + l.value()));

        int x = cfg.hudX;
        int y = cfg.hudY;
        int height = lineH * (lines.size() + 1) + 2;

        graphics.fill(x - 3, y - 3, x + width + 3, y + height, 0x99000000);
        graphics.fill(x - 3, y - 3, x - 1, y + height, Colours.argb(Colours.BRAND)); // brand stripe

        graphics.text(font, title1, x + 1, y, Colours.argb(Colours.BRAND), true);
        graphics.text(font, title2, x + 1 + font.width(title1), y, Colours.argb(Colours.ACCENT), true);
        y += lineH + 2;

        for (Line l : lines) {
            String label = l.label() + ": ";
            graphics.text(font, label, x + 1, y, Colours.argb(Colours.MUTED), true);
            graphics.text(font, l.value(), x + 1 + font.width(label), y, Colours.argb(l.valueColour()), true);
            y += lineH;
        }
    }

    private static int signColour(double v) {
        return v > 0 ? Colours.GOOD : v < 0 ? Colours.BAD : Colours.TEXT;
    }

    public static String streakText(int streak) {
        if (streak > 0) return "W" + streak;
        if (streak < 0) return "L" + (-streak);
        return "-";
    }

    public static String payWallText(HaitchLConfig cfg) {
        String min = cfg.payWallMin > 0 ? Money.format(cfg.payWallMin) : "none";
        String max = cfg.payWallMax > 0 ? Money.format(cfg.payWallMax) : "none";
        return min + " - " + max;
    }
}
