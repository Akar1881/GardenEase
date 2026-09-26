package me.jojo.gardenease.util;

import me.jojo.gardenease.config.ModConfig;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

/**
 * Utility class for managing mod messages.
 * Checks the hideModMessages config option before sending messages.
 */
public class MessageUtil {

    /**
     * Send feedback through a command source if message hiding is disabled.
     */
    public static void sendFeedback(FabricClientCommandSource source, Component message) {
        if (!ModConfig.INSTANCE.isHideModMessages()) {
            source.sendFeedback(message);
        }
    }

    /**
     * Send a client-side message if message hiding is disabled.
     */
    public static void sendClientMessage(Component message) {
        if (!ModConfig.INSTANCE.isHideModMessages()) {
            net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
            if (client.player != null) {
                client.player.sendSystemMessage(message);
            }
        }
    }
}
