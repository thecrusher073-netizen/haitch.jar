package com.haitchl.command;

import com.haitchl.HaitchL;
import com.haitchl.config.HaitchLConfig;
import com.haitchl.gui.HaitchKeys;
import com.haitchl.hud.HaitchHud;
import com.haitchl.stats.SessionStats;
import com.haitchl.util.Colours;
import com.haitchl.util.Money;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.time.format.DateTimeFormatter;
import java.util.function.BiConsumer;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * All settings live under /haitchl (alias /hl). Client-side only: nothing here is sent to the server
 * except /hl bal, which runs the server's /bal for you.
 */
public final class HaitchCommands {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private HaitchCommands() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(build("haitchl"));
            dispatcher.register(build("hl"));
        });
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> build(String name) {
        return literal(name)
                .executes(ctx -> { HaitchKeys.openSoon(); return 1; })
                .then(literal("menu").executes(ctx -> { HaitchKeys.openSoon(); return 1; }))
                .then(literal("help").executes(HaitchCommands::help))
                .then(literal("stats").executes(HaitchCommands::stats))
                .then(literal("reset").executes(ctx -> {
                    HaitchL.stats().reset();
                    return ok(ctx, "Session stats reset.");
                }))
                .then(literal("bal").executes(ctx -> {
                    var player = Minecraft.getInstance().player;
                    if (player != null) player.connection.sendCommand("bal");
                    return ok(ctx, "Asked the server for your balance.");
                }))
                .then(literal("log").executes(HaitchCommands::log))
                .then(literal("win")
                        .executes(ctx -> { HaitchL.stats().recordWin(0); return ok(ctx, "Win added. Streak: " + HaitchHud.streakText(HaitchL.stats().streak)); })
                        .then(argument("amount", StringArgumentType.word()).executes(ctx -> {
                            double a = amountArg(ctx);
                            if (a < 0) return bad(ctx, "Couldn't read that amount. Try 500, 1.5k or 2m.");
                            HaitchL.stats().recordWin(a);
                            return ok(ctx, "Win of " + Money.format(a) + " added. Streak: " + HaitchHud.streakText(HaitchL.stats().streak));
                        })))
                .then(literal("loss")
                        .executes(ctx -> { HaitchL.stats().recordLoss(0); return ok(ctx, "Loss added. Streak: " + HaitchHud.streakText(HaitchL.stats().streak)); })
                        .then(argument("amount", StringArgumentType.word()).executes(ctx -> {
                            double a = amountArg(ctx);
                            if (a < 0) return bad(ctx, "Couldn't read that amount. Try 500, 1.5k or 2m.");
                            HaitchL.stats().recordLoss(a);
                            return ok(ctx, "Loss of " + Money.format(a) + " added. Streak: " + HaitchHud.streakText(HaitchL.stats().streak));
                        })))
                .then(literal("paywall")
                        .executes(ctx -> ok(ctx, "Pay-wall is " + (cfg().payWallEnabled ? "ON" : "OFF") + ": " + HaitchHud.payWallText(cfg())))
                        .then(literal("on").executes(ctx -> set(ctx, c -> c.payWallEnabled = true, "Pay-wall ON: " + HaitchHud.payWallText(cfg()))))
                        .then(literal("off").executes(ctx -> set(ctx, c -> c.payWallEnabled = false, "Pay-wall OFF.")))
                        .then(literal("min").then(argument("amount", StringArgumentType.word()).executes(ctx ->
                                setAmount(ctx, (c, a) -> c.payWallMin = a, "Pay-wall minimum"))))
                        .then(literal("max").then(argument("amount", StringArgumentType.word()).executes(ctx ->
                                setAmount(ctx, (c, a) -> c.payWallMax = a, "Pay-wall maximum"))))
                        .then(literal("count").then(argument("yes_or_no", StringArgumentType.word()).executes(ctx -> {
                            boolean yes = StringArgumentType.getString(ctx, "yes_or_no").toLowerCase().startsWith("y");
                            return set(ctx, c -> c.payWallExcludeFromStats = !yes,
                                    yes ? "Flagged payments now count in your stats." : "Flagged payments are left out of your stats.");
                        }))))
                .then(literal("chatcolour")
                        .then(argument("colour", StringArgumentType.word()).executes(ctx ->
                                setColour(ctx, "colour", (c, v) -> c.chatColour = v, "Chat colour"))))
                .then(literal("chatcolor")
                        .then(argument("colour", StringArgumentType.word()).executes(ctx ->
                                setColour(ctx, "colour", (c, v) -> c.chatColour = v, "Chat colour"))))
                .then(literal("playerchat").then(argument("on_or_off", StringArgumentType.word()).executes(ctx -> {
                    boolean on = StringArgumentType.getString(ctx, "on_or_off").equalsIgnoreCase("on");
                    return set(ctx, c -> c.recolourPlayerChat = on, "Player chat recolour " + (on ? "ON" : "OFF") + ".");
                })))
                .then(literal("colour")
                        .then(literal("received").then(argument("colour", StringArgumentType.word()).executes(ctx ->
                                setColour(ctx, "colour", (c, v) -> c.receivedColour = v, "Received-payment colour"))))
                        .then(literal("sent").then(argument("colour", StringArgumentType.word()).executes(ctx ->
                                setColour(ctx, "colour", (c, v) -> c.sentColour = v, "Sent-payment colour"))))
                        .then(literal("win").then(argument("colour", StringArgumentType.word()).executes(ctx ->
                                setColour(ctx, "colour", (c, v) -> c.winColour = v, "Win colour"))))
                        .then(literal("loss").then(argument("colour", StringArgumentType.word()).executes(ctx ->
                                setColour(ctx, "colour", (c, v) -> c.lossColour = v, "Loss colour")))))
                .then(literal("hud")
                        .then(literal("on").executes(ctx -> set(ctx, c -> c.hudEnabled = true, "HUD ON.")))
                        .then(literal("off").executes(ctx -> set(ctx, c -> c.hudEnabled = false, "HUD OFF.")))
                        .then(literal("pos").then(argument("x", IntegerArgumentType.integer(0))
                                .then(argument("y", IntegerArgumentType.integer(0)).executes(ctx -> {
                                    int x = IntegerArgumentType.getInteger(ctx, "x");
                                    int y = IntegerArgumentType.getInteger(ctx, "y");
                                    return set(ctx, c -> { c.hudX = x; c.hudY = y; }, "HUD moved to " + x + ", " + y + ".");
                                })))))
                .then(literal("autowl").then(argument("on_or_off", StringArgumentType.word()).executes(ctx -> {
                    boolean on = StringArgumentType.getString(ctx, "on_or_off").equalsIgnoreCase("on");
                    return set(ctx, c -> c.paymentsCountAsWinLoss = on,
                            on ? "Received payments now count as wins, sent payments as losses."
                               : "Payments no longer count as wins/losses.");
                })))
                .then(literal("anyserver").then(argument("on_or_off", StringArgumentType.word()).executes(ctx -> {
                    boolean on = StringArgumentType.getString(ctx, "on_or_off").equalsIgnoreCase("on");
                    return set(ctx, c -> c.onlyOnDonutSmp = !on,
                            on ? "HaitchL now runs on every server." : "HaitchL now runs only on DonutSMP.");
                })))
                .then(literal("reload").executes(ctx -> {
                    HaitchL.reloadConfig();
                    return ok(ctx, "Reloaded config/haitchl.json.");
                }));
    }

    // --- Command bodies ------------------------------------------------------

    private static int help(CommandContext<FabricClientCommandSource> ctx) {
        String[] lines = {
                "Right Shift or /hl - open the HaitchL menu",
                "/hl stats - session summary",
                "/hl log - last 10 payments",
                "/hl bal - refresh balance (/bal)",
                "/hl win|loss [amount] - add a result by hand",
                "/hl reset - reset session stats",
                "/hl paywall on|off|min <amt>|max <amt>|count yes|no",
                "/hl chatcolour <#hex|name|off>",
                "/hl playerchat on|off - recolour player chat too",
                "/hl colour received|sent|win|loss <#hex|name|off>",
                "/hl hud on|off|pos <x> <y>",
                "/hl autowl on|off - payments count as W/L",
                "/hl anyserver on|off - run outside DonutSMP",
                "/hl reload - reload config file"
        };
        send(ctx, Colours.prefix().append(Colours.coloured("Commands", Colours.ACCENT)));
        for (String l : lines) send(ctx, Colours.coloured("  " + l, Colours.TEXT));
        return 1;
    }

    private static int stats(CommandContext<FabricClientCommandSource> ctx) {
        SessionStats s = HaitchL.stats();
        send(ctx, Colours.prefix().append(Colours.coloured("Session stats", Colours.ACCENT)));
        send(ctx, row("Balance", Double.isNaN(s.balance) ? "unknown (run /bal)" : Money.format(s.balance)));
        send(ctx, row("Received", Money.format(s.received) + " from " + s.paymentsIn + " payments"));
        send(ctx, row("Sent", Money.format(s.sent) + " in " + s.paymentsOut + " payments"));
        send(ctx, row("Payment P/L", Money.formatSigned(s.paymentNet())));
        send(ctx, row("Wins / Losses", s.wins + " / " + s.losses + String.format(" (%.1f%% win rate)", s.winRate())));
        send(ctx, row("Won / Lost", Money.format(s.wonAmount) + " / " + Money.format(s.lostAmount)
                + " (" + Money.formatSigned(s.gambleNet()) + ")"));
        send(ctx, row("Streak", HaitchHud.streakText(s.streak) + ", best W" + s.bestWinStreak));
        if (s.flagged > 0) send(ctx, row("Pay-wall flags", String.valueOf(s.flagged)));
        return 1;
    }

    private static int log(CommandContext<FabricClientCommandSource> ctx) {
        var recent = HaitchL.stats().recent(10);
        send(ctx, Colours.prefix().append(Colours.coloured("Last payments", Colours.ACCENT)));
        if (recent.isEmpty()) {
            send(ctx, Colours.coloured("  None yet this session.", Colours.MUTED));
            return 1;
        }
        for (SessionStats.Payment p : recent) {
            boolean in = p.direction() == SessionStats.Direction.RECEIVED;
            MutableComponent line = Colours.coloured("  " + TIME.format(p.time()) + " ", Colours.MUTED)
                    .append(Colours.coloured(in ? "+" + Money.format(p.amount()) : "-" + Money.format(p.amount()),
                            in ? Colours.GOOD : Colours.BAD))
                    .append(Colours.coloured((in ? " from " : " to ") + p.player(), Colours.TEXT));
            if (p.flagged()) line.append(Colours.coloured(" ⚠", Colours.BAD));
            send(ctx, line);
        }
        return 1;
    }

    // --- Helpers -------------------------------------------------------------

    private static HaitchLConfig cfg() {
        return HaitchL.config();
    }

    private static double amountArg(CommandContext<FabricClientCommandSource> ctx) {
        return Money.parse(StringArgumentType.getString(ctx, "amount"));
    }

    private static int set(CommandContext<FabricClientCommandSource> ctx,
                           java.util.function.Consumer<HaitchLConfig> change, String message) {
        change.accept(cfg());
        cfg().save();
        return ok(ctx, message);
    }

    private static int setAmount(CommandContext<FabricClientCommandSource> ctx,
                                 BiConsumer<HaitchLConfig, Double> change, String what) {
        double a = amountArg(ctx);
        if (a < 0) return bad(ctx, "Couldn't read that amount. Try 500, 1.5k or 2m (0 = no limit).");
        change.accept(cfg(), a);
        cfg().save();
        return ok(ctx, what + " set to " + (a > 0 ? Money.format(a) : "none") + ".");
    }

    private static int setColour(CommandContext<FabricClientCommandSource> ctx, String arg,
                                 BiConsumer<HaitchLConfig, String> change, String what) {
        String raw = StringArgumentType.getString(ctx, arg);
        if (raw.equalsIgnoreCase("off") || raw.equalsIgnoreCase("none")) {
            change.accept(cfg(), "");
            cfg().save();
            return ok(ctx, what + " turned off.");
        }
        Integer rgb = Colours.parse(raw);
        if (rgb == null) return bad(ctx, "Unknown colour. Use #RRGGBB or a name like gold, aqua, light_purple.");
        String hex = Colours.toHex(rgb);
        change.accept(cfg(), hex);
        cfg().save();
        send(ctx, Colours.prefix().append(Colours.coloured(what + " set to ", Colours.TEXT))
                .append(Colours.coloured(hex + " ■", rgb)));
        return 1;
    }

    private static MutableComponent row(String label, String value) {
        return Colours.coloured("  " + label + ": ", Colours.MUTED).append(Colours.coloured(value, Colours.TEXT));
    }

    private static int ok(CommandContext<FabricClientCommandSource> ctx, String msg) {
        send(ctx, Colours.prefix().append(Colours.coloured(msg, Colours.TEXT)));
        return 1;
    }

    private static int bad(CommandContext<FabricClientCommandSource> ctx, String msg) {
        send(ctx, Colours.prefix().append(Colours.coloured(msg, Colours.BAD)));
        return 0;
    }

    private static void send(CommandContext<FabricClientCommandSource> ctx, Component c) {
        ctx.getSource().sendFeedback(c);
    }
}
