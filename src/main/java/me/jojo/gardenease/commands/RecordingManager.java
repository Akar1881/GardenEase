package me.jojo.gardenease.commands;

import me.jojo.gardenease.GardenEase;
import me.jojo.gardenease.config.ConfigManager;
import me.jojo.gardenease.data.GroupConfig;
import me.jojo.gardenease.data.GroupMode;
import me.jojo.gardenease.data.LaneConfig;
import me.jojo.gardenease.data.MovementPattern;
import me.jojo.gardenease.util.MessageUtil;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class RecordingManager {
    private static boolean recording = false;
    private static GroupConfig currentGroup = null;
    private static String lastGroupName = "";
    private static int lanesRecordedThisSession = 0;

    // Duration mode state
    private static boolean keyPressed = false;
    private static long keyPressStartTime = 0;
    private static MovementPattern currentPattern = null;

    // CORDS mode state
    private static long cordsDebounceStart = -1;
    private static boolean requireKeyRelease = false; // must release movement keys before next save

    private static boolean initialized = false;

    public static void init() {
        if (initialized) return;
        initialized = true;

        // Block movement keys client-side while RIGHT SHIFT is held in CORDS recording.
        // This runs at the START of each tick, before the game reads key states in tickMovement().
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (!recording || currentGroup == null || currentGroup.getMode() != GroupMode.CORDS) return;
            if (client.options == null) return;
            long windowHandle = client.getWindow().handle();
            boolean rightShift = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
            if (rightShift) {
                client.options.keyUp.setDown(false);
                client.options.keyDown.setDown(false);
                client.options.keyLeft.setDown(false);
                client.options.keyRight.setDown(false);
            }
        });

        // Main recording logic runs at END of each tick.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!recording || client.player == null) return;

            if (currentGroup.getMode() == GroupMode.CORDS) {
                handleCordsRecording(client);
            } else {
                handleDurationRecording(client);
            }
        });

        GardenEase.LOGGER.info("Recording manager initialized");
    }

    // ─── CORDS MODE ──────────────────────────────────────────────────────────

    private static void handleCordsRecording(Minecraft client) {
        long windowHandle = client.getWindow().handle();
        boolean rightShift = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;

        boolean w = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_W) == GLFW.GLFW_PRESS;
        boolean a = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_A) == GLFW.GLFW_PRESS;
        boolean s = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_S) == GLFW.GLFW_PRESS;
        boolean d = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_D) == GLFW.GLFW_PRESS;

        boolean anyMovement = w || a || s || d;

        // Reset when RIGHT SHIFT released or all movement keys released
        if (!rightShift || !anyMovement) {
            cordsDebounceStart = -1;
            if (!anyMovement) requireKeyRelease = false; // keys fully released: ready for next save
            return;
        }

        if (requireKeyRelease) return; // waiting for all movement keys to be lifted before next save

        // Start the debounce timer on the first tick the combo appears
        if (cordsDebounceStart == -1) {
            cordsDebounceStart = System.currentTimeMillis();
            return;
        }

        // 150ms debounce window: player can add a second key (e.g. W then A → WA)
        // without triggering the save prematurely on the single key
        if (System.currentTimeMillis() - cordsDebounceStart < 150) return;

        // Debounce elapsed — read the full current combo and save
        MovementPattern pattern = detectPattern(w, a, s, d);
        if (pattern == null || pattern == MovementPattern.NONE) {
            MessageUtil.sendClientMessage(Component.literal(
                "§cCould not read key combo. Use W / A / S / D or WA / WD / SA / SD."
            ));
            requireKeyRelease = true;
            cordsDebounceStart = -1;
            return;
        }

        double px = client.player.getX();
        double py = client.player.getY();
        double pz = client.player.getZ();

        int nextLaneId = currentGroup.getLanes().size() + 1;
        String laneName = "Lane " + nextLaneId;

        LaneConfig newLane = new LaneConfig(nextLaneId, laneName, 10.0f, pattern, false);
        newLane.setEndCoords(px, py, pz);
        currentGroup.getLanes().add(newLane);
        lanesRecordedThisSession++;

        ConfigManager.saveGroup(currentGroup);

        MessageUtil.sendClientMessage(Component.literal(
            "§a" + laneName + " §7saved! Key: §e" + pattern.getKeys()
            + " §7Cords: §eX=" + String.format("%.2f", px)
            + " §7Y=§e" + String.format("%.2f", py)
            + " §7Z=§e" + String.format("%.2f", pz)
        ));

        cordsDebounceStart = -1;
        requireKeyRelease = true; // must release movement keys before recording the next lane
    }

    // ─── DURATION MODE ───────────────────────────────────────────────────────

    private static void handleDurationRecording(Minecraft client) {
        long windowHandle = client.getWindow().handle();

        boolean w = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_W) == GLFW.GLFW_PRESS;
        boolean a = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_A) == GLFW.GLFW_PRESS;
        boolean s = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_S) == GLFW.GLFW_PRESS;
        boolean d = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_D) == GLFW.GLFW_PRESS;

        MovementPattern detectedPattern = detectPattern(w, a, s, d);

        if (!keyPressed && detectedPattern != null && detectedPattern != MovementPattern.NONE) {
            keyPressed = true;
            keyPressStartTime = System.currentTimeMillis();
            currentPattern = detectedPattern;

            int nextLaneId = currentGroup.getLanes().size() + 1;
            MessageUtil.sendClientMessage(Component.literal(
                "§e[" + currentPattern.getKeys() + "] §7Pressed - recording started for §6Lane " + nextLaneId
            ));
        } else if (keyPressed && (detectedPattern == null || detectedPattern == MovementPattern.NONE)) {
            long duration = System.currentTimeMillis() - keyPressStartTime;
            float durationSeconds = duration / 1000f;

            if (durationSeconds >= 0.5f) {
                int nextLaneId = currentGroup.getLanes().size() + 1;
                String laneName = "Lane " + nextLaneId;

                LaneConfig newLane = new LaneConfig(nextLaneId, laneName, durationSeconds, currentPattern, false);
                currentGroup.getLanes().add(newLane);
                lanesRecordedThisSession++;

                ConfigManager.saveGroup(currentGroup);

                MessageUtil.sendClientMessage(Component.literal(
                    "§a" + laneName + " §7recorded! Key: §e" + currentPattern.getKeys()
                    + "§7, Time: §e" + String.format("%.1f", durationSeconds) + "s"
                ));
            } else {
                MessageUtil.sendClientMessage(Component.literal("§cHeld less than 0.5s, lane not recorded."));
            }

            keyPressed = false;
            currentPattern = null;
        }
    }

    // ─── SHARED ──────────────────────────────────────────────────────────────

    private static MovementPattern detectPattern(boolean w, boolean a, boolean s, boolean d) {
        if (w && a && !s && !d) return MovementPattern.WA;
        if (w && d && !s && !a) return MovementPattern.WD;
        if (s && a && !w && !d) return MovementPattern.SA;
        if (s && d && !w && !a) return MovementPattern.SD;
        if (w && !a && !s && !d) return MovementPattern.W;
        if (a && !w && !s && !d) return MovementPattern.A;
        if (s && !w && !a && !d) return MovementPattern.S;
        if (d && !w && !a && !s) return MovementPattern.D;
        if (!w && !a && !s && !d) return MovementPattern.NONE;
        return null;
    }

    public static void startRecording(GroupConfig group) {
        recording = true;
        currentGroup = group;
        lastGroupName = group.getName();
        lanesRecordedThisSession = 0;
        keyPressed = false;
        currentPattern = null;
        cordsDebounceStart = -1;
        requireKeyRelease = false;
    }

    public static int stopRecording() {
        recording = false;
        int lanes = lanesRecordedThisSession;

        if (currentGroup != null) {
            ConfigManager.saveGroup(currentGroup);
        }

        currentGroup = null;
        keyPressed = false;
        currentPattern = null;
        cordsDebounceStart = -1;
        requireKeyRelease = false;

        return lanes;
    }

    public static boolean isRecording() {
        return recording;
    }

    public static String getLastGroupName() {
        return lastGroupName;
    }

    public static GroupConfig getCurrentGroup() {
        return currentGroup;
    }
}
