package me.jojo.gardenease.macro;

import me.jojo.gardenease.GardenEase;
import me.jojo.gardenease.config.ModConfig;
import me.jojo.gardenease.data.GroupConfig;
import me.jojo.gardenease.macro.MacroController;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public class EmergencyHotkey {
    private static boolean initialized = false;
    
    public static void register() {
        if (initialized) return;
        initialized = true;
        
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            
            while (GardenEase.EMERGENCY_STOP_KEY.consumeClick()) {
                if (MacroController.getInstance().isRunning()) {
                    MacroController.getInstance().getState().log("EMERGENCY STOP ACTIVATED!", MacroState.LogLevel.WARNING);
                    MacroController.getInstance().stop();
                }
            }

            while (GardenEase.QUICK_RESUME_KEY.consumeClick()) {
                if (MacroController.getInstance().isRunning()) continue;

                MacroState state = MacroController.getInstance().getState();
                if (state.hasProfilePauseData()) {
                    MacroController.getInstance().resumeProfile();
                    continue;
                }

                String pausedGroupName = state.getPausedGroupName();
                double dx = client.player.getX() - state.getPausedX();
                double dy = client.player.getY() - state.getPausedY();
                double dz = client.player.getZ() - state.getPausedZ();
                if (pausedGroupName == null || Math.abs(dx) > 2.0 || Math.abs(dy) > 2.0 || Math.abs(dz) > 2.0) {
                    state.log("QUICK RESUME: No matching pause data at this position", MacroState.LogLevel.WARNING);
                    continue;
                }

                for (int groupIndex = 0; groupIndex < ModConfig.INSTANCE.getGroups().size(); groupIndex++) {
                    GroupConfig group = ModConfig.INSTANCE.getGroups().get(groupIndex);
                    if (group.getName().equalsIgnoreCase(pausedGroupName)) {
                        ModConfig.INSTANCE.setSelectedGroupIndex(groupIndex);
                        int laneIndex = state.getPausedLaneIndex();
                        float remainingDuration = state.getPausedRemainingDuration();
                        state.clearPauseData();
                        MacroController.getInstance().start(laneIndex, remainingDuration);
                        break;
                    }
                }
            }

        });
        
        GardenEase.LOGGER.info("Emergency hotkey registered in Minecraft Controls");
    }

}
