package com.haitchl.gui;

import com.haitchl.HaitchL;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;

/** Right Shift opens the HaitchL menu. Rebind it in Options > Controls > Key Binds > HaitchL. */
public final class HaitchKeys {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(HaitchL.id("main"));
    private static KeyMapping openMenu;
    /** Set by /hl so the menu opens after the chat box has closed. */
    private static boolean openNextTick;

    private HaitchKeys() {}

    public static void register() {
        openMenu = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.haitchl.open_menu",
                InputConstants.Type.KEYSYM,
                InputConstants.KEY_RSHIFT,
                CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            boolean pressed = false;
            while (openMenu.consumeClick()) pressed = true;
            if (mc.player == null) {
                openNextTick = false;
                return;
            }
            // /hl waits until the chat box has closed before opening the menu
            if ((pressed || openNextTick) && mc.gui.screen() == null) {
                openNextTick = false;
                mc.gui.setScreen(new HaitchScreen(null));
            }
        });
    }

    public static void openSoon() {
        openNextTick = true;
    }
}
