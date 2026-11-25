package com.haitchl.chat;

import com.haitchl.HaitchL;
import com.haitchl.config.HaitchLConfig;
import com.haitchl.stats.SessionStats;
import com.haitchl.stats.SessionStats.Direction;
import com.haitchl.util.Colours;
import com.haitchl.util.Money;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Reads incoming chat: tracks payments, wins/losses and balance, applies the pay-wall and chat colours. */
public final class ChatHandler {
    private static Pattern received;
    private static Pattern sent;
    private static Pattern balance;
    private static Pattern win;
    private static Pattern loss;

    private ChatHandler() {}

    public static void compilePatterns() {
        HaitchLConfig c = HaitchL.config();
        HaitchLConfig d = new HaitchLConfig();
        received = compile(c.receivedPattern, d.receivedPattern, "receivedPattern");
        sent = compile(c.sentPattern, d.sentPattern, "sentPattern");
        balance = compile(c.balancePattern, d.balancePattern, "balancePattern");
        win = compile(c.winPattern, d.winPattern, "winPattern");
        loss = compile(c.lossPattern, d.lossPattern, "lossPattern");
    }

    private static Pattern compile(String custom, String fallback, String name) {
        try {
            return Pattern.compile(custom);
        } catch (PatternSyntaxException | NullPointerException e) {
            HaitchL.LOGGER.warn("[HaitchL] Bad regex in {}, using the default", name);
            return Pattern.compile(fallback);
        }
    }

    public static void register() {
        // Server/plugin messages (DonutSMP sends payments, /bal etc. as system messages).
        ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) -> {
            if (overlay || !HaitchL.isActive()) return message;
            try {
                return handleGameMessage(message);
            } catch (Exception e) {
                HaitchL.LOGGER.warn("[HaitchL] Error handling chat message", e);
                return message;
            }
        });

        // Player chat: only touched when the player-chat recolour option is on.
        ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
            HaitchLConfig cfg = HaitchL.config();
            if (!HaitchL.isActive() || !cfg.recolourPlayerChat) return true;
            Integer colour = Colours.parse(cfg.chatColour);
            if (colour == null) return true;
            Minecraft.getInstance().gui.getChat().addMessage(Colours.recolour(message, colour));
            return false;
        });
    }

    private static Component handleGameMessage(Component message) {
        HaitchLConfig cfg = HaitchL.config();
        SessionStats stats = HaitchL.stats();
        String text = ChatFormatting.stripFormatting(message.getString());
        if (text == null) return message;

        // 1) Incoming payment
        Matcher m = received.matcher(text);
        if (m.find()) {
            double amount = Money.parse(group(m, "amount"));
            String player = group(m, "player");
            if (amount > 0) {
                String wallReason = payWallReason(amount);
                boolean flagged = wallReason != null;
                boolean count = !(flagged && cfg.payWallExcludeFromStats);
                stats.recordPayment(Direction.RECEIVED, player, amount, flagged, count);
                if (count && cfg.paymentsCountAsWinLoss) stats.recordWin(amount);

                MutableComponent out = colourOr(message, cfg.receivedColour);
                if (flagged) out = out.append(payWallTag(wallReason, player, amount));
                return out;
            }
        }

        // 2) Outgoing payment
        m = sent.matcher(text);
        if (m.find()) {
            double amount = Money.parse(group(m, "amount"));
            if (amount > 0) {
                stats.recordPayment(Direction.SENT, group(m, "player"), amount, false, true);
                if (cfg.paymentsCountAsWinLoss) stats.recordLoss(amount);
                return colourOr(message, cfg.sentColour);
            }
        }

        // 3) Game results (coinflip etc.)
        m = win.matcher(text);
        if (m.find()) {
            stats.recordWin(Math.max(0, Money.parse(group(m, "amount"))));
            return colourOr(message, cfg.winColour);
        }
        m = loss.matcher(text);
        if (m.find()) {
            stats.recordLoss(Math.max(0, Money.parse(group(m, "amount"))));
            return colourOr(message, cfg.lossColour);
        }

        // 4) Balance reply (/bal)
        m = balance.matcher(text);
        if (m.find()) {
            double bal = Money.parse(group(m, "amount"));
            if (bal >= 0) stats.balance = bal;
        }

        // 5) Everything else: general chat colour, if set
        Integer colour = Colours.parse(cfg.chatColour);
        return colour == null ? message : Colours.recolour(message, colour);
    }

    /** @return why the amount breaks the pay-wall, or null if it's fine / pay-wall is off. */
    public static String payWallReason(double amount) {
        HaitchLConfig cfg = HaitchL.config();
        if (!cfg.payWallEnabled) return null;
        if (cfg.payWallMin > 0 && amount < cfg.payWallMin) return "below min " + Money.format(cfg.payWallMin);
        if (cfg.payWallMax > 0 && amount > cfg.payWallMax) return "above max " + Money.format(cfg.payWallMax);
        return null;
    }

    private static MutableComponent payWallTag(String reason, String player, double amount) {
        MutableComponent tag = Colours.coloured(" ⚠ Pay-wall: " + reason + " ", Colours.BAD);
        if (player != null && !player.isBlank()) {
            String cmd = "/pay " + player + " " + Money.plain(amount);
            Style refund = Style.EMPTY
                    .withColor(TextColor.fromRgb(Colours.ACCENT))
                    .withUnderlined(true)
                    .withClickEvent(new ClickEvent.SuggestCommand(cmd))
                    .withHoverEvent(new HoverEvent.ShowText(
                            Component.literal("Puts " + cmd + " in your chat box (you still press Enter)")));
            tag.append(Component.literal("[Refund]").withStyle(refund));
        }
        return tag;
    }

    private static MutableComponent colourOr(Component message, String hex) {
        Integer colour = Colours.parse(hex);
        return colour == null ? message.copy() : Colours.recolour(message, colour);
    }

    /** Reads a named group, falling back to group 1/2 for custom patterns without names. */
    private static String group(Matcher m, String name) {
        try {
            return m.group(name);
        } catch (IllegalArgumentException e) {
            if (name.equals("player")) return m.groupCount() >= 2 ? m.group(1) : "";
            return m.groupCount() >= 2 ? m.group(2) : (m.groupCount() >= 1 ? m.group(1) : "");
        }
    }
}
