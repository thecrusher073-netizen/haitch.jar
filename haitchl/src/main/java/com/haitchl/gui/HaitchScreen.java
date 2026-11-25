package com.haitchl.gui;

import com.haitchl.HaitchL;
import com.haitchl.config.HaitchLConfig;
import com.haitchl.hud.HaitchHud;
import com.haitchl.stats.SessionStats;
import com.haitchl.util.Colours;
import com.haitchl.util.Money;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/** The HaitchL menu. Opens with Right Shift (rebindable in Options > Controls > Key Binds). */
public class HaitchScreen extends Screen {
    private enum Tab {
        STATS("Stats"), PAYWALL("Pay-wall"), COLOURS("Colours"), HUD("HUD"), LOG("Payments");
        final String label;
        Tab(String label) { this.label = label; }
    }

    private static final int PANEL_W = 330;
    private static final int ROW = 24;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final int[] PALETTE = {
            0xFFFFFF, 0xAAAAAA, 0xFF5555, 0xF97316, 0xFACC15, 0x55FF55,
            0x00AA00, 0x55FFFF, 0x3B82F6, 0xA855F7, 0xFF55FF, 0xEC4899
    };
    private static final String[] COLOUR_ROWS = {"Chat", "Received", "Sent", "Win", "Loss"};

    // Remembered between openings
    private static Tab tab = Tab.STATS;
    private static int selectedColourRow = 1;
    private static String resultAmount = "";

    private final Screen parent;
    private int left;
    private int top;
    private int contentY;

    public HaitchScreen(Screen parent) {
        super(Component.literal("HaitchL"));
        this.parent = parent;
    }

    private static HaitchLConfig cfg() {
        return HaitchL.config();
    }

    @Override
    protected void init() {
        left = this.width / 2 - PANEL_W / 2;
        top = Math.max(8, this.height / 2 - 120);
        contentY = top + 46;

        // Tab bar
        int tabW = (PANEL_W - 4 * 3) / Tab.values().length;
        int x = left;
        for (Tab t : Tab.values()) {
            Button b = Button.builder(tabLabel(t), btn -> {
                tab = t;
                this.rebuildWidgets();
            }).bounds(x, top + 18, tabW, 20).build();
            b.active = t != tab;
            this.addRenderableWidget(b);
            x += tabW + 3;
        }

        switch (tab) {
            case STATS -> initStats();
            case PAYWALL -> initPayWall();
            case COLOURS -> initColours();
            case HUD -> initHud();
            case LOG -> { }
        }

        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
                .bounds(this.width / 2 - 50, Math.min(this.height - 26, contentY + 196), 100, 20).build());
    }

    // ---------------------------------------------------------------- Stats

    private void initStats() {
        int y = contentY + 100;
        int half = (PANEL_W - 4) / 2;
        this.addRenderableWidget(Button.builder(Component.literal("Refresh balance (/bal)"), b -> {
            if (this.minecraft.player != null) this.minecraft.player.connection.sendCommand("bal");
        }).bounds(left, y, half, 20).build());
        this.addRenderableWidget(Button.builder(Colours.coloured("Reset session", Colours.BAD), b -> {
            HaitchL.stats().reset();
        }).bounds(left + half + 4, y, half, 20).build());

        y += ROW + 14;
        EditBox amount = amountBox(left, y, 110, resultAmount, v -> resultAmount = v);
        amount.setHint(Component.literal("amount (optional)"));
        int bw = (PANEL_W - 110 - 8) / 2;
        this.addRenderableWidget(Button.builder(Colours.coloured("+ Win", Colours.GOOD), b -> {
            HaitchL.stats().recordWin(Math.max(0, Money.parse(resultAmount)));
        }).bounds(left + 114, y, bw, 20).build());
        this.addRenderableWidget(Button.builder(Colours.coloured("+ Loss", Colours.BAD), b -> {
            HaitchL.stats().recordLoss(Math.max(0, Money.parse(resultAmount)));
        }).bounds(left + 118 + bw, y, bw, 20).build());
    }

    // -------------------------------------------------------------- Pay-wall

    private void initPayWall() {
        int y = contentY;
        this.addRenderableWidget(toggle(left, y, PANEL_W, "Pay-wall",
                () -> cfg().payWallEnabled, v -> cfg().payWallEnabled = v));
        y += ROW + 6;
        amountBox(left + 100, y, 120, cfg().payWallMin > 0 ? Money.plain(cfg().payWallMin) : "",
                v -> { double a = Money.parse(v); cfg().payWallMin = a > 0 ? a : 0; })
                .setHint(Component.literal("none"));
        y += ROW;
        amountBox(left + 100, y, 120, cfg().payWallMax > 0 ? Money.plain(cfg().payWallMax) : "",
                v -> { double a = Money.parse(v); cfg().payWallMax = a > 0 ? a : 0; })
                .setHint(Component.literal("none"));
        y += ROW + 6;
        this.addRenderableWidget(toggle(left, y, PANEL_W, "Count flagged payments in stats",
                () -> !cfg().payWallExcludeFromStats, v -> cfg().payWallExcludeFromStats = !v));
    }

    // --------------------------------------------------------------- Colours

    private void initColours() {
        int y = contentY;
        for (int i = 0; i < COLOUR_ROWS.length; i++) {
            final int row = i;
            Button select = Button.builder(Component.literal((row == selectedColourRow ? "▶ " : "") + COLOUR_ROWS[row]), b -> {
                selectedColourRow = row;
                this.rebuildWidgets();
            }).bounds(left, y, 90, 20).build();
            this.addRenderableWidget(select);

            EditBox box = new EditBox(this.font, left + 96, y, 90, 20, Component.literal(COLOUR_ROWS[row]));
            box.setMaxLength(16);
            box.setValue(getColour(row));
            box.setHint(Component.literal("off"));
            box.setResponder(v -> {
                Integer rgb = Colours.parse(v);
                if (rgb != null) setColour(row, Colours.toHex(rgb));
                else if (v.isBlank()) setColour(row, "");
            });
            this.addRenderableWidget(box);

            this.addRenderableWidget(Button.builder(Component.literal("Off"), b -> {
                setColour(row, "");
                this.rebuildWidgets();
            }).bounds(left + PANEL_W - 44, y, 44, 20).build());
            y += ROW;
        }

        // Palette: applies to the selected row
        y += 14;
        int sw = (PANEL_W - (PALETTE.length - 1) * 2) / PALETTE.length;
        for (int i = 0; i < PALETTE.length; i++) {
            int rgb = PALETTE[i];
            this.addRenderableWidget(Button.builder(Colours.coloured("██", rgb), b -> {
                setColour(selectedColourRow, Colours.toHex(rgb));
                this.rebuildWidgets();
            }).bounds(left + i * (sw + 2), y, sw, 20).build());
        }
        y += ROW + 2;
        this.addRenderableWidget(toggle(left, y, PANEL_W, "Recolour player chat too",
                () -> cfg().recolourPlayerChat, v -> cfg().recolourPlayerChat = v));
    }

    private static String getColour(int row) {
        HaitchLConfig c = cfg();
        return switch (row) {
            case 0 -> c.chatColour;
            case 1 -> c.receivedColour;
            case 2 -> c.sentColour;
            case 3 -> c.winColour;
            default -> c.lossColour;
        };
    }

    private static void setColour(int row, String hex) {
        HaitchLConfig c = cfg();
        switch (row) {
            case 0 -> c.chatColour = hex;
            case 1 -> c.receivedColour = hex;
            case 2 -> c.sentColour = hex;
            case 3 -> c.winColour = hex;
            default -> c.lossColour = hex;
        }
    }

    // ------------------------------------------------------------------- HUD

    private void initHud() {
        int y = contentY;
        this.addRenderableWidget(toggle(left, y, PANEL_W, "Stats HUD",
                () -> cfg().hudEnabled, v -> cfg().hudEnabled = v));
        y += ROW + 4;

        // Corner presets (HUD is roughly 175 x 90)
        int qw = (PANEL_W - 9) / 4;
        String[] names = {"Top left", "Top right", "Bottom left", "Bottom right"};
        for (int i = 0; i < 4; i++) {
            final int corner = i;
            this.addRenderableWidget(Button.builder(Component.literal(names[i]), b -> {
                boolean right = corner == 1 || corner == 3;
                boolean bottom = corner >= 2;
                cfg().hudX = right ? Math.max(0, this.width - 180) : 4;
                cfg().hudY = bottom ? Math.max(0, this.height - 140) : 4;
            }).bounds(left + i * (qw + 3), y, qw, 20).build());
        }
        y += ROW;

        // Nudge buttons
        int nw = 40;
        int cx = left + PANEL_W / 2;
        this.addRenderableWidget(Button.builder(Component.literal("←"), b -> cfg().hudX = Math.max(0, cfg().hudX - 5))
                .bounds(cx - nw * 2 - 6, y, nw, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("↑"), b -> cfg().hudY = Math.max(0, cfg().hudY - 5))
                .bounds(cx - nw - 2, y, nw, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("↓"), b -> cfg().hudY = cfg().hudY + 5)
                .bounds(cx + 2, y, nw, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("→"), b -> cfg().hudX = cfg().hudX + 5)
                .bounds(cx + nw + 6, y, nw, 20).build());
        y += ROW + 16;

        this.addRenderableWidget(toggle(left, y, PANEL_W, "Only run on DonutSMP",
                () -> cfg().onlyOnDonutSmp, v -> cfg().onlyOnDonutSmp = v));
        y += ROW;
        this.addRenderableWidget(toggle(left, y, PANEL_W, "Payments count as wins/losses",
                () -> cfg().paymentsCountAsWinLoss, v -> cfg().paymentsCountAsWinLoss = v));
        y += ROW;
        this.addRenderableWidget(toggle(left, y, PANEL_W, "Log payments to file",
                () -> cfg().logPaymentsToFile, v -> cfg().logPaymentsToFile = v));
    }

    // ------------------------------------------------------------- Drawing

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        // Title
        int titleW = this.font.width("HaitchL");
        int tx = this.width / 2 - titleW / 2;
        graphics.fill(left, top + 12, left + PANEL_W, top + 13, Colours.argb(Colours.BRAND));
        graphics.text(this.font, "Haitch", tx, top, Colours.argb(Colours.BRAND), true);
        graphics.text(this.font, "L", tx + this.font.width("Haitch"), top, Colours.argb(Colours.ACCENT), true);

        switch (tab) {
            case STATS -> drawStats(graphics);
            case PAYWALL -> drawPayWall(graphics);
            case COLOURS -> drawColours(graphics);
            case HUD -> drawHud(graphics);
            case LOG -> drawLog(graphics);
        }
    }

    private void drawStats(GuiGraphicsExtractor g) {
        SessionStats s = HaitchL.stats();
        int y = contentY;
        y = line(g, y, "Balance", Double.isNaN(s.balance) ? "unknown - press Refresh" : Money.format(s.balance),
                Double.isNaN(s.balance) ? Colours.MUTED : Colours.TEXT);
        y = line(g, y, "Received", Money.format(s.received) + "  (" + s.paymentsIn + " payments)", Colours.GOOD);
        y = line(g, y, "Sent", Money.format(s.sent) + "  (" + s.paymentsOut + " payments)", Colours.BAD);
        y = line(g, y, "Payment P/L", Money.formatSigned(s.paymentNet()), sign(s.paymentNet()));
        y = line(g, y, "Won / Lost", Money.format(s.wonAmount) + " / " + Money.format(s.lostAmount)
                + "  (" + Money.formatSigned(s.gambleNet()) + ")", sign(s.gambleNet()));
        y = line(g, y, "Wins / Losses", s.wins + " / " + s.losses + String.format("  (%.1f%% win rate)", s.winRate()), Colours.TEXT);
        y = line(g, y, "Streak", HaitchHud.streakText(s.streak) + "   best W" + s.bestWinStreak,
                s.streak > 0 ? Colours.GOOD : s.streak < 0 ? Colours.BAD : Colours.TEXT);
        if (s.flagged > 0) line(g, y, "Pay-wall flags", String.valueOf(s.flagged), Colours.ACCENT);
        g.text(this.font, "Add a result by hand (amount is optional):", left, contentY + 126, Colours.argb(Colours.MUTED), false);
    }

    private void drawPayWall(GuiGraphicsExtractor g) {
        int y = contentY + ROW + 6 + 6;
        g.text(this.font, "Minimum", left, y, Colours.argb(Colours.TEXT), true);
        g.text(this.font, "e.g. 10k", left + 228, y, Colours.argb(Colours.MUTED), false);
        y += ROW;
        g.text(this.font, "Maximum", left, y, Colours.argb(Colours.TEXT), true);
        g.text(this.font, "e.g. 5m", left + 228, y, Colours.argb(Colours.MUTED), false);
        y += ROW * 2 + 2;
        String[] help = {
                "Payments outside these limits get a warning in chat",
                "and a [Refund] button that fills in /pay for you.",
                "You still press Enter yourself - nothing is sent automatically."
        };
        for (String h : help) {
            g.text(this.font, h, left, y, Colours.argb(Colours.MUTED), false);
            y += 11;
        }
    }

    private void drawColours(GuiGraphicsExtractor g) {
        int y = contentY;
        for (int i = 0; i < COLOUR_ROWS.length; i++) {
            Integer rgb = Colours.parse(getColour(i));
            int sx = left + 194;
            if (rgb != null) {
                g.fill(sx, y + 2, sx + 16, y + 18, 0xFF000000);
                g.fill(sx + 1, y + 3, sx + 15, y + 17, Colours.argb(rgb));
                g.text(this.font, "Sample", sx + 22, y + 6, Colours.argb(rgb), true);
            } else {
                g.text(this.font, "unchanged", sx + 2, y + 6, Colours.argb(Colours.MUTED), false);
            }
            y += ROW;
        }
        g.text(this.font, "Click a swatch to apply it to " + COLOUR_ROWS[selectedColourRow] + ":",
                left, y + 2, Colours.argb(Colours.MUTED), false);
    }

    private void drawHud(GuiGraphicsExtractor g) {
        int y = contentY + ROW * 3 + 4 + 2;
        String pos = "Position: " + cfg().hudX + ", " + cfg().hudY;
        g.text(this.font, pos, this.width / 2 - this.font.width(pos) / 2, y, Colours.argb(Colours.MUTED), false);
    }

    private void drawLog(GuiGraphicsExtractor g) {
        List<SessionStats.Payment> recent = HaitchL.stats().recent(14);
        int y = contentY;
        if (recent.isEmpty()) {
            g.text(this.font, "No payments yet this session.", left, y, Colours.argb(Colours.MUTED), false);
            return;
        }
        for (SessionStats.Payment p : recent) {
            boolean in = p.direction() == SessionStats.Direction.RECEIVED;
            String time = TIME.format(p.time()) + "  ";
            String amt = (in ? "+" : "-") + Money.format(p.amount());
            int x = left;
            g.text(this.font, time, x, y, Colours.argb(Colours.MUTED), false);
            x += this.font.width(time);
            g.text(this.font, amt, x, y, Colours.argb(in ? Colours.GOOD : Colours.BAD), true);
            x += Math.max(70, this.font.width(amt) + 6);
            g.text(this.font, (in ? "from " : "to ") + p.player(), x, y, Colours.argb(Colours.TEXT), false);
            if (p.flagged()) g.text(this.font, "⚠ pay-wall", left + PANEL_W - 60, y, Colours.argb(Colours.BAD), false);
            y += 12;
        }
    }

    // -------------------------------------------------------------- Helpers

    private int line(GuiGraphicsExtractor g, int y, String label, String value, int colour) {
        g.text(this.font, label, left, y, Colours.argb(Colours.MUTED), false);
        g.text(this.font, value, left + 100, y, Colours.argb(colour), true);
        return y + 12;
    }

    private static int sign(double v) {
        return v > 0 ? Colours.GOOD : v < 0 ? Colours.BAD : Colours.TEXT;
    }

    private static MutableComponent tabLabel(Tab t) {
        return t == tab ? Colours.coloured(t.label, Colours.ACCENT) : Component.literal(t.label);
    }

    private Button toggle(int x, int y, int w, String name,
                          java.util.function.BooleanSupplier get, Consumer<Boolean> set) {
        Function<Boolean, Component> label = on -> Component.literal(name + ": ")
                .append(on ? Colours.coloured("ON", Colours.GOOD) : Colours.coloured("OFF", Colours.BAD));
        return Button.builder(label.apply(get.getAsBoolean()), b -> {
            boolean next = !get.getAsBoolean();
            set.accept(next);
            b.setMessage(label.apply(next));
        }).bounds(x, y, w, 20).build();
    }

    /** Text box for money amounts; turns red while the text can't be read. */
    private EditBox amountBox(int x, int y, int w, String initial, Consumer<String> onValid) {
        EditBox box = new EditBox(this.font, x, y, w, 20, Component.literal("Amount"));
        box.setMaxLength(20);
        box.setValue(initial);
        box.setResponder(v -> {
            boolean ok = v.isBlank() || Money.parse(v) >= 0;
            box.setTextColor(Colours.argb(ok ? Colours.TEXT : Colours.BAD));
            if (ok) onValid.accept(v);
        });
        this.addRenderableWidget(box);
        return box;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        cfg().save();
        super.removed();
    }

    @Override
    public void onClose() {
        cfg().save();
        this.minecraft.gui.setScreen(this.parent);
    }
}
