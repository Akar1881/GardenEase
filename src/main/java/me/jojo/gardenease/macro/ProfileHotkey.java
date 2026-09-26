package me.jojo.gardenease.macro;

import me.jojo.gardenease.GardenEase;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public final class ProfileHotkey {
    private static boolean initialized = false;

    private ProfileHotkey() {
    }

    public static void register() {
        if (initialized) return;
        initialized = true;

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            while (GardenEase.START_PROFILE_KEY.consumeClick()) {
                MacroController.getInstance().startProfile();
            }
        });
    }
}