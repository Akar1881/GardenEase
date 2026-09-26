package me.jojo.gardenease.macro;

import me.jojo.gardenease.GardenEase;
import me.jojo.gardenease.config.ModConfig;
import me.jojo.gardenease.data.StartDirection;
import me.jojo.gardenease.render.ResumeHighlightRenderer;
import me.jojo.gardenease.util.MessageUtil;
import net.minecraft.network.chat.Component;

public class MacroController {
    private static MacroController INSTANCE;
    
    private final MacroState state;
    private FarmingThread farmingThread;
    
    private MacroController() {
        this.state = new MacroState();
    }
    
    public static MacroController getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new MacroController();
        }
        return INSTANCE;
    }
    
    public void start() {
        start(0, 0f);
    }

    public void start(int startLaneIndex) {
        start(startLaneIndex, 0f);
    }

    public void start(int startLaneIndex, float startOffsetSeconds) {
        ModConfig config = ModConfig.INSTANCE;
        StartDirection naturalDirection = config.getSelectedGroup() != null
                ? config.getSelectedGroup().getNaturalStartDirection()
                : StartDirection.LEFT;
        start(startLaneIndex, startOffsetSeconds, naturalDirection);
    }

    public void start(int startLaneIndex, float startOffsetSeconds, StartDirection startDirection) {
        if (isRunning()) {
            state.log("Macro is already running!", MacroState.LogLevel.WARNING);
            return;
        }
        
        ModConfig config = ModConfig.INSTANCE;
        state.setRunMode(MacroState.RunMode.NORMAL);
        state.setStartDirection(startDirection);
        state.clearAllPauseData();
        
        if (config.getSelectedGroup() == null) {
            state.log("No group selected!", MacroState.LogLevel.ERROR);
            return;
        }
        
        if (config.getSelectedGroup().getLanes().isEmpty()) {
            state.log("Selected group has no lanes!", MacroState.LogLevel.ERROR);
            return;
        }
        
        state.setRunning(true);
        config.setEnabled(true);

        // Clear any resume-position highlight now that the macro is (re)starting
        ResumeHighlightRenderer.clear();

        farmingThread = new FarmingThread(config, state, startLaneIndex, startOffsetSeconds, startDirection);
        farmingThread.start();
        
        GardenEase.LOGGER.info("Farming macro started at lane index " + startLaneIndex + " with offset " + startOffsetSeconds);
    }

    public void startProfile() {
        startProfile(false);
    }

    public void resumeProfile() {
        startProfile(true);
    }

    private void startProfile(boolean resume) {
        if (isRunning()) {
            MessageUtil.sendClientMessage(Component.literal("§cA macro is already running."));
            return;
        }

        ModConfig config = ModConfig.INSTANCE;
        if (!config.isFarmEndSet()) {
            MessageUtil.sendClientMessage(Component.literal("§cSet farm end coordinates before starting a profile."));
            return;
        }

        if (resume && !state.hasProfilePauseData()) {
            MessageUtil.sendClientMessage(Component.literal("§cNo saved profile position is available."));
            return;
        }
        if (resume) {
            net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
            if (client.player == null) return;
            double distanceX = Math.abs(client.player.getX() - state.getPausedX());
            double distanceY = Math.abs(client.player.getY() - state.getPausedY());
            double distanceZ = Math.abs(client.player.getZ() - state.getPausedZ());
            if (distanceX > 2.0 || distanceY > 2.0 || distanceZ > 2.0) {
                MessageUtil.sendClientMessage(Component.literal("§cReturn to the saved profile position before resuming."));
                return;
            }
            config.setSelectedProfileIndex(state.getProfileIndex());
        }
        if (!resume) state.clearAllPauseData();

        state.setRunMode(MacroState.RunMode.PROFILE);
        state.setRunning(true);
        config.setEnabled(true);
        ResumeHighlightRenderer.clear();
        farmingThread = new FarmingThread(config, state);
        farmingThread.start();
        MessageUtil.sendClientMessage(Component.literal("§aProfile farm started: §f" + getSelectedProfileName()));
    }

    private String getSelectedProfileName() {
        me.jojo.gardenease.data.FarmProfile[] profiles = me.jojo.gardenease.data.FarmProfile.values();
        int index = Math.max(0, Math.min(ModConfig.INSTANCE.getSelectedProfileIndex(), profiles.length - 1));
        return profiles[index].getDisplayName();
    }
    
    public void stop() {
        if (farmingThread != null && farmingThread.isAlive()) {
            farmingThread.stopMacro();
            try {
                // Wait longer and ensure we don't block the UI thread if called from there
                farmingThread.join(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        // Only set running to false AFTER the thread has joined/finished
        state.setRunning(false);
        ModConfig.INSTANCE.setEnabled(false);
        
        GardenEase.LOGGER.info("Farming macro stop sequence completed");
    }
    
    public void toggle() {
        if (isRunning()) {
            stop();
        } else {
            start();
        }
    }
    
    public boolean isRunning() {
        return farmingThread != null && farmingThread.isRunning();
    }
    
    public MacroState getState() {
        return state;
    }
}
