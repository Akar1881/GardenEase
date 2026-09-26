package me.jojo.gardenease.macro;

import me.jojo.gardenease.GardenEase;
import me.jojo.gardenease.render.ResumeHighlightRenderer;
import me.jojo.gardenease.config.ModConfig;
import me.jojo.gardenease.data.GroupConfig;
import me.jojo.gardenease.data.GroupMode;
import me.jojo.gardenease.data.FarmProfile;
import me.jojo.gardenease.data.LaneConfig;
import me.jojo.gardenease.data.MovementPattern;
import me.jojo.gardenease.data.StartDirection;
import me.jojo.gardenease.util.MessageUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.List;
import java.util.Random;

public class FarmingThread extends Thread {
    private static volatile boolean attackHeld = false;
    private final ModConfig config;
    private final MacroState state;
    private final Random random = new Random();

    private volatile boolean running = true;
    private int currentIteration = 0;
    private int startLaneIndex = 0;
    private float startOffsetSeconds = 0f;
    private final StartDirection requestedStartDirection;
    private boolean mirrored = false;
    private double startX;
    private double startZ;
    private double startYawRadians;
    private static final int END_OF_LANE_GRACE_PERIOD = 3000; // 3 seconds
    private static final double CORDS_CLOSE_THRESHOLD = 0.6; // when to start watching for pass-through
    // Consider player "near" the end if within this many blocks on any axis (X, Y or Z).
    private static final int NEAR_END_BLOCKS = 5; // in blocks

    // Position detection variables
    private double cachedX = 0;
    private double cachedY = 0;
    private double cachedZ = 0;
    private double lastCheckedX = 0;
    private double lastCheckedY = 0;
    private double lastCheckedZ = 0;
    private boolean positionCached = false;
    private long stuckDetectionTime = -1;
    private long wrongDirectionTime = -1;
    private int positionPendingDelay = -1;
    private MovementPattern currentPattern = MovementPattern.NONE;
    private boolean inCordsMode = false;
    private volatile boolean nearCordsTarget = false; // suppresses stuck detection near end coord
    private volatile boolean profileMovementStalled = false;
    private volatile boolean profileAdvancingRow = false;
    private boolean profileCompleted = false;
    private int profileMovementIndex = 0;

    public FarmingThread(ModConfig config, MacroState state) {
        this(config, state, 0, 0f, StartDirection.LEFT);
    }

    public FarmingThread(ModConfig config, MacroState state, int startLaneIndex) {
        this(config, state, startLaneIndex, 0f, StartDirection.LEFT);
    }

    public FarmingThread(ModConfig config, MacroState state, int startLaneIndex, float startOffsetSeconds) {
        this(config, state, startLaneIndex, startOffsetSeconds, StartDirection.LEFT);
    }

    public FarmingThread(ModConfig config, MacroState state, int startLaneIndex,
                         float startOffsetSeconds, StartDirection startDirection) {
        super("GardenEase-Thread");
        this.config = config;
        this.state = state;
        this.startLaneIndex = startLaneIndex;
        this.startOffsetSeconds = startOffsetSeconds;
        this.requestedStartDirection = startDirection;
        this.setDaemon(true);
        MacroState.setActiveThread(this);
    }

    private void captureStartPosition() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        startX = client.player.getX();
        startZ = client.player.getZ();
        startYawRadians = Math.toRadians(client.player.getYRot());
    }

    @Override
    public void run() {
        MacroState.onMacroStart();
        try {
            captureStartPosition();
            if (state.getRunMode() == MacroState.RunMode.PROFILE) {
                runProfile();
                return;
            }

            GroupConfig group = config.getSelectedGroup();
            if (group == null || group.getLanes().isEmpty()) {
                state.log("No group selected or no lanes configured!", MacroState.LogLevel.ERROR);
                return;
            }

            mirrored = requestedStartDirection != group.getNaturalStartDirection();

            inCordsMode = group.getMode() == GroupMode.CORDS;
            currentIteration = 1;
            boolean looping = config.isLoopEnabled();

            state.log("Starting group '" + group.getName() + "' with " + group.getLanes().size() + " lanes [Mode: " + group.getMode().name() + "]", MacroState.LogLevel.SUCCESS);

            if (looping) {
                state.log("Loop mode enabled - will execute: " + group.getLoopCommand(), MacroState.LogLevel.INFO);
            }

            if (inCordsMode) {
                validateCordsModeSetup(group);
            }

            waitForPreparation();
            if (!running) return;

            while (running) {
                state.setCurrentIteration(currentIteration);

                if (currentIteration > 1) {
                    state.log("Starting loop iteration " + currentIteration, MacroState.LogLevel.INFO);
                    state.setRunning(true);
                    startAttacking();
                    sleepThread(200);
                }

                for (int i = (currentIteration == 1 ? startLaneIndex : 0); i < group.getLanes().size(); i++) {
                    if (!running) break;
                    float offset = (currentIteration == 1 && i == startLaneIndex) ? startOffsetSeconds : 0f;
                    farmLane(group.getLanes().get(i), offset);
                }

                if (running) {
                    if (looping) {
                        int delay = 1000 + random.nextInt(501);
                        state.log("Waiting " + String.format("%.1f", delay / 1000.0) + "s before loop command...", MacroState.LogLevel.INFO);
                        sleepThread(delay);

                        if (running) {
                            state.setRunning(false);
                            state.markExpectingTeleport();
                            executeLoopCommand(group.getLoopCommand());
                        }
                    }

                    state.log("Completed iteration " + currentIteration, MacroState.LogLevel.SUCCESS);
                }

                if (!looping || !running) {
                    break;
                }

                sleepThread(1000);
                currentIteration++;
            }

            if (looping && currentIteration > 1) {
                state.log("Completed " + (currentIteration - 1) + " loop(s)", MacroState.LogLevel.SUCCESS);
            }

        } catch (Exception e) {
            state.log("Error in farming macro: " + e.getMessage(), MacroState.LogLevel.ERROR);
            GardenEase.LOGGER.error("Farming error", e);
        } finally {
            cleanup();
        }
    }

    private void validateCordsModeSetup(GroupConfig group) {
        boolean allSet = true;
        for (LaneConfig lane : group.getLanes()) {
            if (!lane.isCordsSet()) {
                state.log("WARNING: Lane " + lane.getId() + " (" + lane.getName() + ") has no end coordinates set! Use /fm setcords " + group.getName() + " " + lane.getId(), MacroState.LogLevel.WARNING);
                allSet = false;
            }
        }
        if (allSet) {
            state.log("All lanes have end coordinates set.", MacroState.LogLevel.INFO);
        }
    }

    private void checkPosition() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        if (currentPattern == MovementPattern.NONE) return;
        if (!state.isRunning()) return;

        double x = client.player.getX();
        double y = client.player.getY();
        double z = client.player.getZ();
        long now = System.currentTimeMillis();

        if (!positionCached) {
            cachedX = x;
            cachedY = y;
            cachedZ = z;
            lastCheckedX = x;
            lastCheckedY = y;
            lastCheckedZ = z;
            positionCached = true;
            state.log("Position detected", MacroState.LogLevel.INFO);
            return;
        }

        double deltaX = x - lastCheckedX;
        double deltaY = y - lastCheckedY;
        double deltaZ = z - lastCheckedZ;
        // Include Y so that players falling between farm layers are not flagged as stuck
        double actualMovement = Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);

        // In CORDS mode, suppress stuck detection once we are near the end coordinate —
        // the player is legitimately stopping at the wall/crop end, not truly stuck.
        if (inCordsMode && nearCordsTarget) return;

        // Grace period only applies in duration mode
        if (!inCordsMode && state.getCurrentLane() != null) {
            float laneDuration = state.getCurrentLane().getDuration();
            long elapsed = now - state.getStartTime();
            if (elapsed >= (laneDuration * 1000) - END_OF_LANE_GRACE_PERIOD) {
                if (actualMovement < 0.01) {
                    return;
                }
            }
        }

        if (actualMovement < 0.01) {
            if (stuckDetectionTime == -1) {
                stuckDetectionTime = now;
                positionPendingDelay = state.getRunMode() == MacroState.RunMode.PROFILE ? 0 : 1500;
                state.log("Movement blocked detected! Reacting in " + String.format("%.2f", positionPendingDelay / 1000.0) + "s...", MacroState.LogLevel.WARNING);
            } else if (now - stuckDetectionTime >= positionPendingDelay) {
                if (state.getRunMode() == MacroState.RunMode.PROFILE) {
                    profileMovementStalled = true;
                    return;
                }
                triggerPositionAlert("BLOCKED! Player stuck!");
                return;
            }
        } else {
            stuckDetectionTime = -1;
        }

        lastCheckedX = x;
        lastCheckedY = y;
        lastCheckedZ = z;
    }

    private void triggerPositionAlert(String reason) {
        Minecraft client = Minecraft.getInstance();
        state.log(reason + " Stopping macro...", MacroState.LogLevel.ERROR);

        new Thread(() -> {
            long end = System.currentTimeMillis() + 5000;
            while (System.currentTimeMillis() < end) {
                client.execute(() -> {
                    if (client.player != null) {
                        client.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                    }
                });
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            }
        }, "Position-Alert-Sound").start();

        stopMacro();
    }

    private void resetPositionDetection() {
        positionCached = false;
        stuckDetectionTime = -1;
        wrongDirectionTime = -1;
        positionPendingDelay = -1;
        profileMovementStalled = false;
        profileAdvancingRow = false;
    }

    private void runProfile() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            state.log("No player available for profile farm.", MacroState.LogLevel.ERROR);
            return;
        }

        FarmProfile profile = getSelectedProfile();
        while (running) {
            profileCompleted = false;
            runProfilePass(profile, client);

            if (!running || !profileCompleted || !config.isLoopEnabled()) {
                break;
            }

            String loopCommand = getProfileLoopCommand();
            if (loopCommand == null || loopCommand.isEmpty()) {
                state.log("Profile completed; no loop command is configured.", MacroState.LogLevel.WARNING);
                break;
            }

            state.log("Profile completed. Executing loop command: " + loopCommand, MacroState.LogLevel.INFO);
            state.setRunning(false);
            state.markExpectingTeleport();
            executeLoopCommand(loopCommand);
            if (!running) break;

            state.setRunning(true);
            state.clearProfilePauseData();
        }
    }

    private void runProfilePass(FarmProfile profile, Minecraft client) {
        if (profile.hasSequentialPattern()) {
            runSequentialProfile(profile, client);
            return;
        }

        boolean resuming = state.hasProfilePauseData()
            && state.getProfileIndex() == config.getSelectedProfileIndex();
        MovementPattern traversal = resuming && state.getProfileMovementPattern() != MovementPattern.NONE
            && !state.isProfileAdvancingRow()
            ? state.getProfileMovementPattern()
            : chooseInitialTraversal(profile);
        profileMovementIndex = resuming ? state.getProfileMovementIndex() : 0;
        double targetX = config.getFarmEndX();
        double targetY = config.getFarmEndY();
        double targetZ = config.getFarmEndZ();

        state.log("Starting profile farm: " + profile.getDisplayName(), MacroState.LogLevel.SUCCESS);
        currentPattern = traversal;
        state.setActiveProfileMovement(profileMovementIndex, traversal, false);
        resetPositionDetection();
        startAttacking();
        pressMovementKeys(traversal, true);

        while (running) {
            if (isNearProfileEnd(client, targetX, targetY, targetZ)) {
                profileCompleted = true;
                break;
            }

            checkPosition();
            if (profileMovementStalled) {
                pressMovementKeys(traversal, false);
                profileMovementIndex++;
                state.setActiveProfileMovement(profileMovementIndex, profile.getRowAdvance(), true);
                advanceProfileRow(profile.getRowAdvance());
                traversal = traversal == MovementPattern.A ? MovementPattern.D : MovementPattern.A;
                currentPattern = traversal;
                state.setActiveProfileMovement(profileMovementIndex, traversal, false);
                resetPositionDetection();
                pressMovementKeys(traversal, true);
            }
            sleepThread(100);
        }

        pressMovementKeys(traversal, false);
        stopAttacking();
        currentPattern = MovementPattern.NONE;
        if (profileCompleted) state.clearProfilePauseData();
        if (profileCompleted) {
            state.log("Profile farm reached its end coordinates.", MacroState.LogLevel.SUCCESS);
        }
    }

    private String getProfileLoopCommand() {
        GroupConfig group = config.getSelectedGroup();
        return group != null ? group.getLoopCommand() : null;
    }

    private void runSequentialProfile(FarmProfile profile, Minecraft client) {
        boolean resuming = state.hasProfilePauseData()
            && state.getProfileIndex() == config.getSelectedProfileIndex();
        int movementIndex = resuming ? state.getProfileMovementIndex() : 0;
        MovementPattern movement = resuming && state.getProfileMovementPattern() != MovementPattern.NONE
            ? state.getProfileMovementPattern()
            : profile.getSequentialMovement(movementIndex);
        profileMovementIndex = movementIndex;
        double targetX = config.getFarmEndX();
        double targetY = config.getFarmEndY();
        double targetZ = config.getFarmEndZ();

        state.log("Starting profile movement: " + profile.getMovementDescription(), MacroState.LogLevel.INFO);
        currentPattern = movement;
        state.setActiveProfileMovement(movementIndex, movement, false);
        resetPositionDetection();
        startAttacking();
        pressMovementKeys(movement, true);

        while (running) {
            if (isNearProfileEnd(client, targetX, targetY, targetZ)) {
                profileCompleted = true;
                break;
            }

            checkPosition();
            if (profileMovementStalled) {
                pressMovementKeys(movement, false);
                movementIndex++;
                movement = profile.getSequentialMovement(movementIndex);
                profileMovementIndex = movementIndex;
                currentPattern = movement;
                state.setActiveProfileMovement(movementIndex, movement, false);
                resetPositionDetection();
                pressMovementKeys(movement, true);
            }
            sleepThread(100);
        }

        pressMovementKeys(movement, false);
        stopAttacking();
        currentPattern = MovementPattern.NONE;
        if (profileCompleted) state.clearProfilePauseData();
        if (profileCompleted) {
            state.log("Profile farm reached its end coordinates.", MacroState.LogLevel.SUCCESS);
        }
    }

    private void advanceProfileRow(MovementPattern advancePattern) {
        currentPattern = advancePattern;
        state.setActiveProfileMovement(profileMovementIndex, advancePattern, true);
        resetPositionDetection();
        profileAdvancingRow = true;
        pressMovementKeys(advancePattern, true);

        while (running && !profileMovementStalled) {
            checkPosition();
            sleepThread(100);
        }

        pressMovementKeys(advancePattern, false);
        profileAdvancingRow = false;
    }

    private FarmProfile getSelectedProfile() {
        FarmProfile[] profiles = FarmProfile.values();
        int index = Math.max(0, Math.min(config.getSelectedProfileIndex(), profiles.length - 1));
        return profiles[index];
    }

    private MovementPattern chooseInitialTraversal(FarmProfile profile) {
        double forwardX = -Math.sin(startYawRadians);
        double forwardZ = Math.cos(startYawRadians);
        double rightX = forwardZ;
        double rightZ = -forwardX;
        double perpendicularCoordinate = startX * rightX + startZ * rightZ;
        boolean onLeftSide = perpendicularCoordinate - Math.floor(perpendicularCoordinate) < 0.5;
        return onLeftSide ? profile.getRowTraversal() : profile.getRowTraversal().mirror();
    }

    private boolean isNearProfileEnd(Minecraft client, double targetX, double targetY, double targetZ) {
        if (client.player == null) return false;
        return Math.abs(client.player.getX() - targetX) <= CORDS_CLOSE_THRESHOLD
                && Math.abs(client.player.getY() - targetY) <= CORDS_CLOSE_THRESHOLD
                && Math.abs(client.player.getZ() - targetZ) <= CORDS_CLOSE_THRESHOLD;
    }

    private void waitForPreparation() {
        state.log("Preparing to farm in 1 second...", MacroState.LogLevel.WARNING);

        if (!running) return;
        sleepThread(1000);

        startAttacking();
        sleepThread(200);
        state.log("FARMING STARTED!", MacroState.LogLevel.SUCCESS);
    }

    private void farmLane(LaneConfig lane, float offsetSeconds) {
        state.log("Started lane " + lane.getId() + ": " + lane.getName() + (offsetSeconds > 0 ? " (Resumed)" : ""), MacroState.LogLevel.INFO);
        state.setCurrentLane(lane);
        state.setLastActiveLane(lane);

        MovementPattern pattern = lane.getMovementPattern();
        MovementPattern playbackPattern = mirrored ? pattern.mirror() : pattern;
        currentPattern = pattern;
        resetPositionDetection();

        pressMovementKeys(playbackPattern, true);
        if (config.isSprintEnabled()) {
            Minecraft.getInstance().execute(() -> {
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.setSprinting(true);
                }
            });
        }

        startAttacking();

        if (inCordsMode) {
            farmLaneCords(lane);
        } else {
            farmLaneDuration(lane, offsetSeconds);
        }

        pressMovementKeys(playbackPattern, false);
        if (config.isSprintEnabled()) {
            Minecraft.getInstance().execute(() -> {
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.setSprinting(false);
                }
            });
        }
        stopAttacking();
        currentPattern = MovementPattern.NONE;

        if (running) {
            state.log("Ended lane " + lane.getId() + ": " + lane.getName(), MacroState.LogLevel.SUCCESS);
            state.setCurrentLane(null);
            state.setLaneProgress(0);
        }
    }

    private void farmLaneDuration(LaneConfig lane, float offsetSeconds) {
        float duration = lane.getDuration();
        long startTime = System.currentTimeMillis() - (long)(offsetSeconds * 1000);
        state.setStartTime(startTime);

        while (running) {
            checkPosition();

            long preGauss = System.currentTimeMillis();
            if (lane.isGaussianMovement()) {
                applyGaussianMovement(mirrored ? lane.getMovementPattern().mirror() : lane.getMovementPattern());
            }
            long postGauss = System.currentTimeMillis();

            if (postGauss - preGauss > 10) {
                startTime += (postGauss - preGauss);
            }

            long elapsed = System.currentTimeMillis() - startTime;
            float elapsedSeconds = elapsed / 1000f;

            if (elapsed >= (duration * 1000) - END_OF_LANE_GRACE_PERIOD && elapsed < duration * 1000) {
                if (elapsedSeconds >= duration) {
                    break;
                }
            } else if (elapsedSeconds >= duration) {
                break;
            }

            state.setLaneProgress(elapsedSeconds / duration);
            sleepThread(100);
        }
    }

    private void farmLaneCords(LaneConfig lane) {
        if (!lane.isCordsSet()) {
            state.log("Lane " + lane.getId() + " has no end coordinates! Skipping after 2s...", MacroState.LogLevel.ERROR);
            sleepThread(2000);
            return;
        }

        double targetX = lane.getEndX();
        double targetY = lane.getEndY();
        double targetZ = lane.getEndZ();
        if (mirrored) {
            double vectorX = targetX - startX;
            double vectorZ = targetZ - startZ;
            double forwardX = -Math.sin(startYawRadians);
            double forwardZ = Math.cos(startYawRadians);
            double projection = vectorX * forwardX + vectorZ * forwardZ;
            targetX = startX + (2 * projection * forwardX - vectorX);
            targetZ = startZ + (2 * projection * forwardZ - vectorZ);
        }
        state.log("Lane " + lane.getId() + ": moving to X=" + String.format("%.1f", targetX)
                + " Y=" + String.format("%.1f", targetY)
                + " Z=" + String.format("%.1f", targetZ), MacroState.LogLevel.INFO);
        state.setStartTime(System.currentTimeMillis());

        double prevDistance = Double.MAX_VALUE;
        boolean wasClose = false;
        int stuckAtTargetTicks = 0;
        nearCordsTarget = false;

        while (running) {
            checkPosition();

            Minecraft client = Minecraft.getInstance();
            if (client.player != null) {
                double px = client.player.getX();
                double py = client.player.getY();
                double pz = client.player.getZ();
                double dx = px - targetX;
                double dy = py - targetY;
                double dz = pz - targetZ;
                double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

                // If player is within NEAR_END_BLOCKS on any axis, consider 'near end' and suppress stuck detection.
                if (Math.abs(px - targetX) <= NEAR_END_BLOCKS || Math.abs(py - targetY) <= NEAR_END_BLOCKS || Math.abs(pz - targetZ) <= NEAR_END_BLOCKS) {
                    if (!wasClose) {
                        wasClose = true;
                        nearCordsTarget = true;
                    }
                }

                if (wasClose) {
                    // Case 1: player passed through — distance started increasing again
                    if (distance > prevDistance) {
                        state.log("Passed through end coordinates for lane " + lane.getId(), MacroState.LogLevel.SUCCESS);
                        break;
                    }
                    // Case 2: player stopped right at the wall/end — not moving, stuck at target
                    // Switch after ~300ms of being stationary near the coordinate
                    if (Math.abs(distance - prevDistance) < 0.05) {
                        stuckAtTargetTicks++;
                        if (stuckAtTargetTicks >= 3) {
                            state.log("Arrived at end coordinates for lane " + lane.getId() + " (wall stop)", MacroState.LogLevel.SUCCESS);
                            break;
                        }
                    } else {
                        stuckAtTargetTicks = 0;
                    }
                }

                prevDistance = distance;
            }

            if (lane.isGaussianMovement()) {
                applyGaussianMovement(mirrored ? lane.getMovementPattern().mirror() : lane.getMovementPattern());
            }

            state.setLaneProgress(0);
            sleepThread(100);
        }

        nearCordsTarget = false;
    }

    private void pressMovementKeys(MovementPattern pattern, boolean press) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.options == null) return;

        client.execute(() -> {
            if (client.options == null) return;
            KeyMapping forward = client.options.keyUp;
            KeyMapping back = client.options.keyDown;
            KeyMapping left = client.options.keyLeft;
            KeyMapping right = client.options.keyRight;

            switch (pattern) {
                case W -> forward.setDown(press);
                case A -> left.setDown(press);
                case S -> back.setDown(press);
                case D -> right.setDown(press);
                case WA -> { forward.setDown(press); left.setDown(press); }
                case WD -> { forward.setDown(press); right.setDown(press); }
                case SA -> { back.setDown(press); left.setDown(press); }
                case SD -> { back.setDown(press); right.setDown(press); }
                default -> {}
            }
        });
    }

    private void applyGaussianMovement(MovementPattern pattern) {
        if (random.nextFloat() < 0.05f) {
            Minecraft client = Minecraft.getInstance();
            if (client == null || client.options == null) return;

            List<KeyMapping> keys = new java.util.ArrayList<>();
            switch (pattern) {
                case W -> keys.add(client.options.keyUp);
                case A -> keys.add(client.options.keyLeft);
                case S -> keys.add(client.options.keyDown);
                case D -> keys.add(client.options.keyRight);
                case WA -> { keys.add(client.options.keyUp); keys.add(client.options.keyLeft); }
                case WD -> { keys.add(client.options.keyUp); keys.add(client.options.keyRight); }
                case SA -> { keys.add(client.options.keyDown); keys.add(client.options.keyLeft); }
                case SD -> { keys.add(client.options.keyDown); keys.add(client.options.keyRight); }
                default -> {}
            }

            if (!keys.isEmpty()) {
                KeyMapping keyToFlicker = keys.get(random.nextInt(keys.size()));
                long delay = (long) (random.nextGaussian() * 20 + 50);
                if (delay < 10) delay = 10;
                if (delay > 150) delay = 150;

                keyToFlicker.setDown(false);

                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                if (running) keyToFlicker.setDown(true);
            }
        }
    }

    private void startAttacking() {
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.options != null) {
            attackHeld = true;
            client.execute(() -> {
                if (client.options == null) return;
                KeyMapping attack = client.options.keyAttack;
                attack.setDown(true);
                KeyMapping.click(InputConstants.Type.MOUSE.getOrCreate(0));
            });
        }
    }

    private void stopAttacking() {
        attackHeld = false;
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.options != null) {
            client.execute(() -> {
                if (client.options != null) client.options.keyAttack.setDown(false);
            });
        }
    }

    public static boolean isAttackHeld() {
        return attackHeld;
    }

    private void executeLoopCommand(String command) {
        if (command == null || command.isEmpty()) return;
        state.log("Executing loop command: " + command, MacroState.LogLevel.INFO);

        int decisionDelay = 500 + random.nextInt(501);
        sleepThread(decisionDelay);

        long typingDuration = 0;
        for (int i = 0; i < command.length(); i++) {
            typingDuration += 60 + random.nextInt(61);
        }

        sleepThread(typingDuration);

        if (!running) return;

        sendChatCommand(command);

        sleepThread(1000);
    }

    private void sendChatCommand(String command) {
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.player != null) {
            if (command.startsWith("/")) {
            client.player.connection.sendCommand(command.substring(1));
            } else {
                client.player.connection.sendChat(command);
            }
        }
    }

    private void releaseAllKeys() {
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.options != null) {
            client.execute(() -> {
                if (client.options == null) return;
                client.options.keyUp.setDown(false);
                client.options.keyDown.setDown(false);
                client.options.keyLeft.setDown(false);
                client.options.keyRight.setDown(false);
                client.options.keyAttack.setDown(false);
            });
        }
    }

    private void cleanup() {
        boolean wasRunning = running;
        running = false;
        attackHeld = false;
        releaseAllKeys();

        Minecraft client = Minecraft.getInstance();

        LaneConfig capturedLane = state.getCurrentLane();
        float capturedProgress = state.getLaneProgress();
        GroupConfig group = config.getSelectedGroup();

        GardenEase.LOGGER.info("Cleaning up FarmingThread. Group: " + (group != null ? group.getName() : "null") + ", Lane: " + (capturedLane != null ? capturedLane.getName() : "null"));

        if (group != null && capturedLane != null && client.player != null) {
            float duration = capturedLane.getDuration();
            float elapsedSeconds = duration * capturedProgress;
            int laneIndex = -1;

            for (int i = 0; i < group.getLanes().size(); i++) {
                if (group.getLanes().get(i).getId() == capturedLane.getId()) {
                    laneIndex = i;
                    break;
                }
            }

            if (laneIndex != -1) {
                // Use the pre-failsafe position if one was captured (e.g. teleport failsafe).
                // This is the position FROM which the player was teleported, not the destination.
                // Fall back to current position for normal stop/pause.
                double x, y, z;
                float yaw, pitch;
                if (state.isPreFailsafeCaptured()) {
                    x = state.getPreFailsafeX();
                    y = state.getPreFailsafeY();
                    z = state.getPreFailsafeZ();
                    yaw = state.getPreFailsafeYaw();
                    pitch = state.getPreFailsafePitch();
                } else {
                    x = client.player.getX();
                    y = client.player.getY();
                    z = client.player.getZ();
                    yaw = client.player.getYRot();
                    pitch = client.player.getXRot();
                }

                state.setPauseData(
                        group.getName(),
                        laneIndex,
                        elapsedSeconds,
                        x, y, z, yaw, pitch
                );

                GardenEase.LOGGER.info("Saved pause data for group " + group.getName() + " at lane " + (laneIndex + 1));

                String coords = String.format("%.1f, %.1f, %.1f", x, y, z);
                String stopLine  = "§cMacro stopped. Lane: §e" + capturedLane.getId() + " §c| Group: §e" + group.getName();
                String msg1 = "§cYou didn't finished the macro you stoped/paused at §e<" + coords + ">";
                String msg2 = "§cyou can go there and set yaw & pitch and type §e/fm resume " + group.getName() + " §cto resume where you stopped";

                // Activate the block highlight so the player can visually navigate back
                ResumeHighlightRenderer.show(x, y, z);

                client.execute(() -> {
                    if (client.player != null) {
                        MessageUtil.sendClientMessage(Component.literal(stopLine));
                        MessageUtil.sendClientMessage(Component.literal(msg1));
                        MessageUtil.sendClientMessage(Component.literal(msg2));
                    }
                });
            }
        }

        if (state.getRunMode() == MacroState.RunMode.PROFILE && !profileCompleted && client.player != null) {
            double x = state.isPreFailsafeCaptured() ? state.getPreFailsafeX() : client.player.getX();
            double y = state.isPreFailsafeCaptured() ? state.getPreFailsafeY() : client.player.getY();
            double z = state.isPreFailsafeCaptured() ? state.getPreFailsafeZ() : client.player.getZ();
            float yaw = state.isPreFailsafeCaptured() ? state.getPreFailsafeYaw() : client.player.getYRot();
            float pitch = state.isPreFailsafeCaptured() ? state.getPreFailsafePitch() : client.player.getXRot();
            state.setProfilePauseData(
                    config.getSelectedProfileIndex(),
                    state.getProfileMovementIndex(),
                    state.getProfileMovementPattern(),
                    state.isProfileAdvancingRow(),
                    x, y, z, yaw, pitch
            );
            ResumeHighlightRenderer.show(x, y, z);
        }

        MacroState.onMacroStop();
        MacroState.setActiveThread(null);
        state.setRunning(false);
        config.setEnabled(false);
        state.setCurrentLane(null);
        state.setLaneProgress(0);
        state.setCurrentIteration(0);

        if (wasRunning) {
            if (state.getRunMode() == MacroState.RunMode.PROFILE) {
                String message = profileCompleted
                    ? "§aProfile farm completed."
                    : "§cProfile farm stopped. Use Profiles > Resume to continue from this movement segment.";
                client.execute(() -> MessageUtil.sendClientMessage(Component.literal(message)));
            }
            state.log("Macro stopped", MacroState.LogLevel.WARNING);
        }
    }

    public void stopMacro() {
        running = false;
    }

    public boolean isRunning() {
        return running;
    }

    private void sleepThread(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
