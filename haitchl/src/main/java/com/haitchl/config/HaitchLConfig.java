package com.haitchl.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.haitchl.HaitchL;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Settings saved to config/haitchl.json. Every chat pattern can be edited in that file
 * if DonutSMP changes its message wording; run /hl reload afterwards.
 */
public class HaitchLConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("haitchl.json");

    // --- General -------------------------------------------------------------
    /** Only run while connected to a server whose address contains "donutsmp". */
    public boolean onlyOnDonutSmp = true;
    /** Append every tracked payment to config/haitchl-payments.csv. */
    public boolean logPaymentsToFile = true;

    // --- HUD -----------------------------------------------------------------
    public boolean hudEnabled = true;
    public int hudX = 4;
    public int hudY = 4;

    // --- Pay-wall ------------------------------------------------------------
    /** Flags incoming payments outside [payWallMin, payWallMax]. 0 = no limit on that side. */
    public boolean payWallEnabled = false;
    public double payWallMin = 0;
    public double payWallMax = 0;
    /** If true, flagged payments are left out of the session profit/loss. */
    public boolean payWallExcludeFromStats = true;

    // --- Chat colours (hex "#RRGGBB" or empty = leave untouched) ---------------
    /** Recolours all other server/system messages. */
    public String chatColour = "";
    /** Also recolour player chat messages with chatColour. */
    public boolean recolourPlayerChat = false;
    public String receivedColour = "#55FF55";
    public String sentColour = "#FF5555";
    public String winColour = "#FACC15";
    public String lossColour = "#F97316";

    // --- Win/loss counting ---------------------------------------------------
    /** Count every received payment as a win and every sent payment as a loss (for hosts / simple setups). */
    public boolean paymentsCountAsWinLoss = false;

    // --- Chat patterns (Java regex; use named groups "player" and "amount") ---
    public String receivedPattern = "(?<player>[A-Za-z0-9_.]{2,17}) paid you \\$(?<amount>[\\d.,]+\\s*[KkMmBbTt]?)";
    public String sentPattern = "You paid (?<player>[A-Za-z0-9_.]{2,17}) \\$(?<amount>[\\d.,]+\\s*[KkMmBbTt]?)";
    public String balancePattern = "(?i)(?:your balance(?: is)?|balance:|you have)\\s*:?\\s*\\$(?<amount>[\\d.,]+\\s*[KkMmBbTt]?)";
    public String winPattern = "(?i)\\byou (?:won|win)\\b.*?\\$(?<amount>[\\d.,]+\\s*[KkMmBbTt]?)";
    public String lossPattern = "(?i)\\byou (?:lost|lose)\\b.*?\\$(?<amount>[\\d.,]+\\s*[KkMmBbTt]?)";

    public static HaitchLConfig load() {
        if (Files.exists(FILE)) {
            try (Reader r = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
                HaitchLConfig cfg = GSON.fromJson(r, HaitchLConfig.class);
                if (cfg != null) {
                    cfg.save(); // writes any newly added fields back to disk
                    return cfg;
                }
            } catch (Exception e) {
                HaitchL.LOGGER.error("[HaitchL] Could not read {}, using defaults", FILE, e);
            }
        }
        HaitchLConfig cfg = new HaitchLConfig();
        cfg.save();
        return cfg;
    }

    public void save() {
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer w = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
                GSON.toJson(this, w);
            }
        } catch (IOException e) {
            HaitchL.LOGGER.error("[HaitchL] Could not save {}", FILE, e);
        }
    }
}
