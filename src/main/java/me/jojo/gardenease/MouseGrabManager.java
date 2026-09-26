package me.jojo.gardenease;

import me.jojo.gardenease.config.ModConfig;
import me.jojo.gardenease.macro.MacroState;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

public class MouseGrabManager {
    private static boolean initialized = false;
    private static boolean applied = false;

    public static void register() {
        if (initialized) return;
        initialized = true;

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client == null) return;

            boolean shouldApply = ModConfig.INSTANCE.isAutoUngrabMouse() && MacroState.isMacroRunning();
            if (shouldApply == applied) return;

            if (shouldApply) {
                client.mouseHandler.releaseMouse();
            } else {
                client.mouseHandler.grabMouse();
            }
            applied = shouldApply;
        });
    }
}