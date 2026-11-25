package com.haitchl.stats;

import com.haitchl.HaitchL;
import com.haitchl.util.Money;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Everything the HUD shows for the current play session. Reset with /hl reset. */
public class SessionStats {
    public enum Direction { RECEIVED, SENT }

    public record Payment(LocalDateTime time, Direction direction, String player, double amount, boolean flagged) {}

    private static final Path CSV = FabricLoader.getInstance().getConfigDir().resolve("haitchl-payments.csv");
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_LOG = 50;

    /** Last known balance, or NaN until the server tells us (run /bal or /hl bal). */
    public double balance = Double.NaN;

    public double received;
    public double sent;
    public int paymentsIn;
    public int paymentsOut;
    public int flagged;

    public int wins;
    public int losses;
    public double wonAmount;
    public double lostAmount;
    /** Positive = current win streak, negative = current loss streak. */
    public int streak;
    public int bestWinStreak;

    private final Deque<Payment> log = new ArrayDeque<>();

    public void reset() {
        double keepBalance = balance;
        received = sent = wonAmount = lostAmount = 0;
        paymentsIn = paymentsOut = flagged = wins = losses = streak = bestWinStreak = 0;
        log.clear();
        balance = keepBalance;
    }

    public void recordPayment(Direction dir, String player, double amount, boolean isFlagged, boolean countInStats) {
        Payment p = new Payment(LocalDateTime.now(), dir, player, amount, isFlagged);
        log.addFirst(p);
        while (log.size() > MAX_LOG) log.removeLast();
        if (isFlagged) flagged++;

        if (countInStats) {
            if (dir == Direction.RECEIVED) { received += amount; paymentsIn++; }
            else { sent += amount; paymentsOut++; }
        }
        // The real balance changes whether or not we count it in stats.
        if (!Double.isNaN(balance)) balance += (dir == Direction.RECEIVED ? amount : -amount);

        if (HaitchL.config().logPaymentsToFile) appendCsv(p);
    }

    public void recordWin(double amount) {
        wins++;
        wonAmount += Math.max(0, amount);
        streak = streak >= 0 ? streak + 1 : 1;
        bestWinStreak = Math.max(bestWinStreak, streak);
    }

    public void recordLoss(double amount) {
        losses++;
        lostAmount += Math.max(0, amount);
        streak = streak <= 0 ? streak - 1 : -1;
    }

    public double paymentNet() { return received - sent; }

    public double gambleNet() { return wonAmount - lostAmount; }

    public double winRate() {
        int total = wins + losses;
        return total == 0 ? 0 : (wins * 100.0) / total;
    }

    public List<Payment> recent(int n) {
        List<Payment> out = new ArrayList<>();
        for (Payment p : log) {
            if (out.size() >= n) break;
            out.add(p);
        }
        return out;
    }

    private static void appendCsv(Payment p) {
        try {
            boolean newFile = !Files.exists(CSV);
            StringBuilder sb = new StringBuilder();
            if (newFile) sb.append("time,direction,player,amount,flagged\n");
            sb.append(TS.format(p.time())).append(',')
              .append(p.direction()).append(',')
              .append(p.player()).append(',')
              .append(Money.plain(p.amount())).append(',')
              .append(p.flagged()).append('\n');
            Files.writeString(CSV, sb.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            HaitchL.LOGGER.warn("[HaitchL] Could not write payment log", e);
        }
    }
}
