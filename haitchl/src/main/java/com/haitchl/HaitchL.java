package com.haitchl;

import com.haitchl.chat.ChatHandler;
import com.haitchl.command.HaitchCommands;
import com.haitchl.config.HaitchLConfig;
import com.haitchl.gui.HaitchKeys;
import com.haitchl.hud.HaitchHud;
import com.haitchl.stats.SessionStats;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

public class HaitchL implements ClientModInitializer {
    public static final String MOD_ID = "haitchl";
    public static final Logger LOGGER = LoggerFactory.getLogger("HaitchL");

    private static HaitchLConfig config;
    private static final SessionStats STATS = new SessionStats();

    @Override
    public void onInitializeClient() {
        config = HaitchLConfig.load();
        ChatHandler.compilePatterns();
        ChatHandler.register();
        HaitchHud.register();
        HaitchCommands.register();
        HaitchKeys.register();
        LOGGER.info("[HaitchL] Loaded. Press Right Shift in game for the menu.");
    }

    public static HaitchLConfig config() {
        return config;
    }

    public static void reloadConfig() {
        config = HaitchLConfig.load();
        ChatHandler.compilePatterns();
    }

    public static SessionStats stats() {
        return STATS;
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    /** True when the mod should be doing things (on DonutSMP, or anywhere if that check is off). */
    public static boolean isActive() {
        if (config == null) return false;
        if (!config.onlyOnDonutSmp) return true;
        ServerData server = Minecraft.getInstance().getCurrentServer();
        return server != null && server.ip != null && server.ip.toLowerCase(Locale.ROOT).contains("donutsmp");
    }
}
