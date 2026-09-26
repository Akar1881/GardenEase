package me.jojo.gardenease.macro;

import me.jojo.gardenease.GardenEase;
import me.jojo.gardenease.config.ModConfig;
import me.jojo.gardenease.data.LaneConfig;
import me.jojo.gardenease.data.MovementPattern;
import me.jojo.gardenease.data.StartDirection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MacroState {
    public enum RunMode { NORMAL, PROFILE }

    private volatile boolean running = false;
    private volatile RunMode runMode = RunMode.NORMAL;
    private transient volatile StartDirection startDirection = StartDirection.LEFT;
    private volatile boolean expectingTeleport = false;
    private volatile long loopCommandSentAt = 0L;
    private volatile int currentIteration = 0;
    private volatile LaneConfig currentLane = null;
    private volatile LaneConfig lastActiveLane = null;
    private volatile float laneProgress = 0f;

    private String pausedGroupName = null;
    private int pausedLaneIndex = -1;
    private float pausedRemainingDuration = 0f;
    private double pausedX, pausedY, pausedZ;
    private float pausedYaw, pausedPitch;
    private volatile boolean profilePauseAvailable = false;
    private volatile int profileIndex = -1;
    private volatile int profileMovementIndex = 0;
    private volatile MovementPattern profileMovementPattern = MovementPattern.NONE;
    private volatile boolean profileAdvancingRow = false;

    // Position captured at the instant a failsafe fires — before a teleport takes effect
    private volatile boolean preFailsafeCaptured = false;
    private volatile double preFailsafeX, preFailsafeY, preFailsafeZ;
    private volatile float preFailsafeYaw, preFailsafePitch;
    private long startTime;
    
    private final List<LogEntry> logs = new CopyOnWriteArrayList<>();
    private static final int MAX_LOGS = 100;
    
    // Global reference to the active thread
    private static volatile FarmingThread activeThread = null;
    
    // ========== FAILSAFE CACHE VARIABLES ==========
    // Hotbar slot cache
    private static int cachedHotbarSlot = -1;
    private static ItemStack cachedHeldItem = ItemStack.EMPTY;
    
    // Speed cache with delay
    private static int cachedSpeed = -1;
    private static boolean hasCachedSpeed = false;
    private static boolean speedCacheReady = false; // NEW: true after 3 second delay
    private static long macroStartTime = 0; // NEW: when macro started
    private static final long SPEED_CACHE_DELAY_MS = 3000; // NEW: 3 second delay
    private static final Pattern SPEED_PATTERN = Pattern.compile("Speed:\\s*✦?(\\d+)");
    private static final Pattern SPEED_PATTERN_ALT = Pattern.compile("✦(\\d+)");
    
    public enum LogLevel {
        INFO, SUCCESS, WARNING, ERROR
    }
    
    public static class LogEntry {
        public final long timestamp;
        public final String message;
        public final LogLevel level;
        
        public LogEntry(String message, LogLevel level) {
            this.timestamp = System.currentTimeMillis();
            this.message = message;
            this.level = level;
        }
        
        public String getFormattedMessage() {
            return "[" + level + "] " + message;
        }
    }
    
    public void log(String message, LogLevel level) {
        GardenEase.LOGGER.info("[{}] {}", level, message);
        logs.add(new LogEntry(message, level));
        while (logs.size() > MAX_LOGS) logs.remove(0);
    }
    
    public void log(String message) {
        log(message, LogLevel.INFO);
    }
    
    public List<LogEntry> getLogs() {
        return new ArrayList<>(logs);
    }
    
    public void clearLogs() {
        logs.clear();
    }

    public static void setActiveThread(FarmingThread thread) {
        activeThread = thread;
    }

    public static FarmingThread getActiveThread() {
        return activeThread;
    }

    // --- Global check for Mixins ---
    public static boolean isMacroRunning() {
        return activeThread != null && activeThread.isRunning();
    }
    
    public static MacroState getInstance() {
    return MacroController.getInstance().getState();
    }

    public void markExpectingTeleport() {
        loopCommandSentAt = System.currentTimeMillis();
        expectingTeleport = true;
    }

    public boolean isExpectingTeleport() {
        long elapsed = System.currentTimeMillis() - loopCommandSentAt;
        return expectingTeleport && elapsed >= 0 && elapsed <= ModConfig.INSTANCE.getTeleportWaitMs();
    }

    public void consumeExpectedTeleport() {
        expectingTeleport = false;
    }

    public RunMode getRunMode() {
        return runMode;
    }

    public void setRunMode(RunMode runMode) {
        this.runMode = runMode;
    }

    public StartDirection getStartDirection() {
        return startDirection;
    }

    public void setStartDirection(StartDirection startDirection) {
        this.startDirection = startDirection;
    }


    // ========== FAILSAFE CACHE METHODS ==========
    
    /**
     * Called when macro starts - initializes slot/item caches immediately
     * Speed cache will be initialized after 3 second delay
     */
    public static void onMacroStart() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        
        // Record start time for speed delay
        macroStartTime = System.currentTimeMillis();
        
        // Cache hotbar slot and item immediately
        Inventory inventory = client.player.getInventory();
        cachedHotbarSlot = inventory.getSelectedSlot();
        cachedHeldItem = client.player.getMainHandItem().copy();
        
        // Reset speed cache (will be set after delay)
        cachedSpeed = -1;
        hasCachedSpeed = false;
        speedCacheReady = false;
        
        GardenEase.LOGGER.info("[MacroState] Slot/Item caches initialized - Slot: " + cachedHotbarSlot + 
            ", Item: " + cachedHeldItem.getHoverName().getString());
        GardenEase.LOGGER.info("[MacroState] Speed cache will be ready in " + (SPEED_CACHE_DELAY_MS / 1000) + " seconds...");
    }
    
    /**
     * Check if speed cache delay has passed and update if needed
     * Call this from mixins when checking speed
     */
    public static void checkSpeedCacheDelay() {
        if (!isMacroRunning()) return;
        if (speedCacheReady) return; // Already cached
        
        long elapsed = System.currentTimeMillis() - macroStartTime;
        if (elapsed >= SPEED_CACHE_DELAY_MS) {
            // 3 seconds passed, now cache speed
            updateSpeedCache();
            speedCacheReady = true;
            
            if (hasCachedSpeed) {
                GardenEase.LOGGER.info("[MacroState] Speed cache initialized after delay: " + cachedSpeed);
            } else {
                GardenEase.LOGGER.warn("[MacroState] Could not detect speed after delay");
            }
        }
    }
    
    /**
     * Called when macro stops - clears caches
     */
    public static void onMacroStop() {
        cachedHotbarSlot = -1;
        cachedHeldItem = ItemStack.EMPTY;
        cachedSpeed = -1;
        hasCachedSpeed = false;
        speedCacheReady = false;
        macroStartTime = 0;

        // Clear pre-failsafe position
        MacroState inst = getInstance();
        if (inst != null) inst.preFailsafeCaptured = false;
        
        GardenEase.LOGGER.info("[MacroState] All caches cleared");
    }
    
    /**
     * Updates the speed cache from current TAB list
     */
    public static void updateSpeedCache() {
        Minecraft client = Minecraft.getInstance();
        ClientPacketListener networkHandler = client.getConnection();
        if (networkHandler == null) {
            cachedSpeed = -1;
            hasCachedSpeed = false;
            return;
        }
        
        // Use reflection to get header/footer
        Component header = null;
        Component footer = null;
        
        try {
            java.lang.reflect.Field headerField = ClientPacketListener.class.getDeclaredField("serverBrand");
            java.lang.reflect.Field footerField = ClientPacketListener.class.getDeclaredField("serverBrand");
            headerField.setAccessible(true);
            footerField.setAccessible(true);
            header = null;
            footer = null;
        } catch (Exception e) {
            return;
        }
        
        String combined = "";
        if (header != null) combined += header.getString() + " ";
        if (footer != null) combined += footer.getString();
        
        // Try primary pattern
        Matcher matcher = SPEED_PATTERN.matcher(combined);
        if (!matcher.find()) {
            // Try alternative pattern
            matcher = SPEED_PATTERN_ALT.matcher(combined);
        }
        
        if (matcher.find()) {
            try {
                cachedSpeed = Integer.parseInt(matcher.group(1));
                hasCachedSpeed = true;
            } catch (NumberFormatException e) {
                cachedSpeed = -1;
                hasCachedSpeed = false;
            }
        }
    }
    
    /**
     * Check if hotbar slot has changed from cached value
     */
    public static boolean hasHotbarSlotChanged(int currentSlot) {
        if (!isMacroRunning()) return false;
        if (cachedHotbarSlot == -1) return false;
        return currentSlot != cachedHotbarSlot;
    }
    
    /**
     * Check if held item has changed from cached value
     */
    public static boolean hasHeldItemChanged(ItemStack currentItem) {
        if (!isMacroRunning()) return false;
        if (cachedHeldItem == null) return false;
        return !ItemStack.isSameItemSameComponents(currentItem, cachedHeldItem);
    }
    
    /**
     * Check if speed has changed from cached value
     * Only returns true if 3 second delay has passed and speed changed
     */
    public static boolean hasSpeedChanged(int currentSpeed) {
        if (!isMacroRunning()) return false;
        
        // First check if delay has passed and update cache if needed
        checkSpeedCacheDelay();
        
        // If still not ready or not cached, don't trigger
        if (!speedCacheReady) return false;
        if (!hasCachedSpeed) return false;
        
        return currentSpeed != cachedSpeed;
    }
    
    /**
     * Get cached hotbar slot
     */
    public static int getCachedHotbarSlot() {
        return cachedHotbarSlot;
    }
    
    /**
     * Get cached held item
     */
    public static ItemStack getCachedHeldItem() {
        return cachedHeldItem;
    }
    
    /**
     * Get cached speed
     */
    public static int getCachedSpeed() {
        return cachedSpeed;
    }
    
    /**
     * Check if speed was cached (after delay)
     */
    public static boolean hasCachedSpeed() {
        return hasCachedSpeed && speedCacheReady;
    }
    
    /**
     * Check if speed cache delay has passed
     */
    public static boolean isSpeedCacheReady() {
        return speedCacheReady;
    }

    public void capturePreFailsafePosition(double x, double y, double z, float yaw, float pitch) {
        this.preFailsafeX = x;
        this.preFailsafeY = y;
        this.preFailsafeZ = z;
        this.preFailsafeYaw = yaw;
        this.preFailsafePitch = pitch;
        this.preFailsafeCaptured = true;
    }

    public boolean isPreFailsafeCaptured() { return preFailsafeCaptured; }
    public double getPreFailsafeX() { return preFailsafeX; }
    public double getPreFailsafeY() { return preFailsafeY; }
    public double getPreFailsafeZ() { return preFailsafeZ; }
    public float getPreFailsafeYaw() { return preFailsafeYaw; }
    public float getPreFailsafePitch() { return preFailsafePitch; }

    public static void triggerFailsafe(String reason) {
        // 1. Get Instance
        MacroState instance = null;
        try {
            instance = MacroController.getInstance().getState();
        } catch (Exception ignored) {}

        if (instance != null) {
            instance.log(reason, LogLevel.ERROR);

            // Capture current player position immediately — this is the pre-teleport position.
            // By the time the failsafe delay elapses and cleanup() runs, the player may already
            // be at the teleported destination, so we must save coords right now.
            Minecraft clientNow = Minecraft.getInstance();
            if (clientNow.player != null) {
                instance.capturePreFailsafePosition(
                    clientNow.player.getX(), clientNow.player.getY(), clientNow.player.getZ(),
                    clientNow.player.getYRot(), clientNow.player.getXRot()
                );
            }
            
            // 2. Calculate Delay
            int delay = 800 + new Random().nextInt(601);
            instance.log("Reacting in " + String.format("%.2f", delay / 1000.0) + "s...", LogLevel.WARNING);

            // 3. Start Failsafe Thread (Sound + Stop)
            new Thread(() -> {
                Minecraft client = Minecraft.getInstance();
                long endTime = System.currentTimeMillis() + 5000;

                // A. Wait the "Human Reaction" time
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ignored) {}

                // B. Stop the Macro
                FarmingThread t = getActiveThread();
                if (t != null) {
                    t.stopMacro();
                }

                // C. Play Alarm Sound
                while (System.currentTimeMillis() < endTime) {
                    client.execute(() -> {
                        if (client.player != null) {
                            client.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                        }
                    });
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ignored) {}
                }

            }, "Macro-Failsafe-Trigger").start();
        } else {
            GardenEase.LOGGER.warn("Failsafe triggered but state is null: " + reason);
        }
    }
    
    // Getters and Setters
    public boolean isRunning() { return running; }
    public void setRunning(boolean running) { this.running = running; }
    public int getCurrentIteration() { return currentIteration; }
    public void setCurrentIteration(int currentIteration) { this.currentIteration = currentIteration; }
    public LaneConfig getCurrentLane() { return currentLane; }
    public void setCurrentLane(LaneConfig currentLane) { this.currentLane = currentLane; }
    public LaneConfig getLastActiveLane() { return lastActiveLane; }
    public void setLastActiveLane(LaneConfig lastActiveLane) { this.lastActiveLane = lastActiveLane; }
    public float getLaneProgress() { return laneProgress; }
    public void setLaneProgress(float laneProgress) { this.laneProgress = laneProgress; }

    public void setPauseData(String groupName, int laneIndex, float remainingDuration, double x, double y, double z, float yaw, float pitch) {
        this.pausedGroupName = groupName;
        this.pausedLaneIndex = laneIndex;
        this.pausedRemainingDuration = remainingDuration;
        this.pausedX = x; this.pausedY = y; this.pausedZ = z;
        this.pausedYaw = yaw; this.pausedPitch = pitch;
    }

    public String getPausedGroupName() { return pausedGroupName; }
    public int getPausedLaneIndex() { return pausedLaneIndex; }
    public float getPausedRemainingDuration() { return pausedRemainingDuration; }
    public double getPausedX() { return pausedX; }
    public double getPausedY() { return pausedY; }
    public double getPausedZ() { return pausedZ; }
    public float getPausedYaw() { return pausedYaw; }
    public float getPausedPitch() { return pausedPitch; }
    
    public void clearPauseData() {
        this.pausedGroupName = null;
        this.pausedLaneIndex = -1;
        this.pausedRemainingDuration = 0f;
        this.pausedX = 0; this.pausedY = 0; this.pausedZ = 0;
        this.pausedYaw = 0; this.pausedPitch = 0;
    }

    public void setProfilePauseData(int profileIndex, int movementIndex, MovementPattern movementPattern,
                                    boolean advancingRow, double x, double y, double z,
                                    float yaw, float pitch) {
        this.profilePauseAvailable = true;
        this.profileIndex = profileIndex;
        this.profileMovementIndex = movementIndex;
        this.profileMovementPattern = movementPattern;
        this.profileAdvancingRow = advancingRow;
        this.pausedX = x;
        this.pausedY = y;
        this.pausedZ = z;
        this.pausedYaw = yaw;
        this.pausedPitch = pitch;
    }

    public boolean hasProfilePauseData() { return profilePauseAvailable; }
    public int getProfileIndex() { return profileIndex; }
    public int getProfileMovementIndex() { return profileMovementIndex; }
    public MovementPattern getProfileMovementPattern() { return profileMovementPattern; }
    public boolean isProfileAdvancingRow() { return profileAdvancingRow; }

    public void setActiveProfileMovement(int movementIndex, MovementPattern movementPattern, boolean advancingRow) {
        this.profileMovementIndex = movementIndex;
        this.profileMovementPattern = movementPattern;
        this.profileAdvancingRow = advancingRow;
    }

    public void clearProfilePauseData() {
        profilePauseAvailable = false;
        profileIndex = -1;
        profileMovementIndex = 0;
        profileMovementPattern = MovementPattern.NONE;
        profileAdvancingRow = false;
    }

    public void clearAllPauseData() {
        clearPauseData();
        clearProfilePauseData();
    }
    
    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getStartTime() {
        return startTime;
    }
    
    public String getStatusText() {
        if (!running) return "Stopped";
        if (currentLane != null) return "Lane " + currentLane.getId() + ": " + currentLane.getName();
        return "Running";
    }
}
