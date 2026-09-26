package me.jojo.gardenease.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import me.jojo.gardenease.config.ConfigManager;
import me.jojo.gardenease.config.ModConfig;
import me.jojo.gardenease.data.GroupConfig;
import me.jojo.gardenease.data.LaneConfig;
import me.jojo.gardenease.data.GroupMode;
import me.jojo.gardenease.gui.GardenEaseScreen;
import me.jojo.gardenease.macro.MacroController;
import me.jojo.gardenease.util.MessageUtil;
import me.jojo.gardenease.util.Text;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public class FarmMacroCommand {

    // Pending confirmation map: key = "groupName:laneNumber", value = timestamp when first requested
    private static final Map<String, Long> pendingCoordsConfirm = new HashMap<>();
    private static final long CONFIRM_TIMEOUT_MS = 30_000; // 30 seconds to confirm

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext registryAccess) {
        dispatcher.register(literal("fm")
                .executes(context -> openGui(context))
                .then(literal("gui")
                        .executes(context -> openGui(context)))
                .then(literal("help")
                        .executes(context -> showHelp(context)))
                .then(literal("list")
                        .executes(context -> listGroups(context)))
                .then(literal("create")
                        .executes(context -> {
                            MessageUtil.sendFeedback(context.getSource(), Component.literal("§cMissing argument! Usage: §e/fm create <name> [loopcommand]"));
                            MessageUtil.sendFeedback(context.getSource(), Component.literal("§7Example: §e/fm create mygroup /warp garden"));
                            return 0;
                        })
                        .then(argument("name", StringArgumentType.word())
                                .executes(context -> createGroup(context, StringArgumentType.getString(context, "name"), "/warp garden"))
                                .then(argument("loopcommand", StringArgumentType.greedyString())
                                        .executes(context -> createGroup(context,
                                                StringArgumentType.getString(context, "name"),
                                                StringArgumentType.getString(context, "loopcommand"))))))
                .then(literal("delete")
                        .executes(context -> {
                            MessageUtil.sendFeedback(context.getSource(), Component.literal("§cMissing argument! Usage: §e/fm delete <name>"));
                            MessageUtil.sendFeedback(context.getSource(), Component.literal("§7Use §e/fm list §7to see available groups."));
                            return 0;
                        })
                        .then(argument("name", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    ConfigManager.loadGroups();
                                    for (GroupConfig group : ModConfig.INSTANCE.getGroups()) {
                                        builder.suggest(group.getName());
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(context -> deleteGroup(context, StringArgumentType.getString(context, "name")))))
                .then(literal("start")
                        .executes(context -> {
                            MessageUtil.sendFeedback(context.getSource(), Component.literal("§cMissing argument! Usage: §e/fm start <name> [lane]"));
                            MessageUtil.sendFeedback(context.getSource(), Component.literal("§7Use §e/fm list §7to see available groups."));
                            return 0;
                        })
                        .then(argument("name", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    ConfigManager.loadGroups();
                                    for (GroupConfig group : ModConfig.INSTANCE.getGroups()) {
                                        builder.suggest(group.getName());
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(context -> startGroup(context, StringArgumentType.getString(context, "name"), 1))
                                .then(argument("lane", IntegerArgumentType.integer(1))
                                        .executes(context -> startGroup(context,
                                                StringArgumentType.getString(context, "name"),
                                                IntegerArgumentType.getInteger(context, "lane"))))))
                .then(literal("resume")
                        .executes(context -> {
                            MessageUtil.sendFeedback(context.getSource(), Component.literal("§cMissing argument! Usage: §e/fm resume <name>"));
                            return 0;
                        })
                        .then(argument("name", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    ConfigManager.loadGroups();
                                    for (GroupConfig group : ModConfig.INSTANCE.getGroups()) {
                                        builder.suggest(group.getName());
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(context -> resumeGroup(context, StringArgumentType.getString(context, "name")))))
                .then(literal("stop")
                        .executes(context -> stopMacro(context)))
                .then(literal("record")
                        .executes(context -> {
                            if (RecordingManager.isRecording()) {
                                int lanesRecorded = RecordingManager.stopRecording();
                                MessageUtil.sendFeedback(context.getSource(), Text.literal("§aSuccessfully exited recording mode for §6" + RecordingManager.getLastGroupName() + "§a with §e" + lanesRecorded + "§a new lane(s)!"));
                                return 1;
                            }
                            MessageUtil.sendFeedback(context.getSource(), Component.literal("§cMissing argument! Usage: §e/fm record <name>"));
                            MessageUtil.sendFeedback(context.getSource(), Component.literal("§7Use §e/fm list §7to see available groups."));
                            return 0;
                        })
                        .then(argument("name", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    ConfigManager.loadGroups();
                                    for (GroupConfig group : ModConfig.INSTANCE.getGroups()) {
                                        builder.suggest(group.getName());
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(context -> toggleRecording(context, StringArgumentType.getString(context, "name")))))
                .then(literal("setcords")
                        .executes(context -> {
                            MessageUtil.sendFeedback(context.getSource(), Component.literal("§cUsage: §e/fm setcords <group> <lane>"));
                            MessageUtil.sendFeedback(context.getSource(), Component.literal("§7Sets the end coordinates of a lane to your current position."));
                            return 0;
                        })
                        .then(argument("group", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    ConfigManager.loadGroups();
                                    for (GroupConfig group : ModConfig.INSTANCE.getGroups()) {
                                        builder.suggest(group.getName());
                                    }
                                    return builder.buildFuture();
                                })
                                .then(argument("lane", IntegerArgumentType.integer(1))
                                        .executes(context -> setCords(context,
                                                StringArgumentType.getString(context, "group"),
                                                IntegerArgumentType.getInteger(context, "lane"))))))
        );

        dispatcher.register(literal("gardenease").redirect(dispatcher.getRoot().getChild("fm")));
    }

    private static int openGui(CommandContext<FabricClientCommandSource> context) {
        Minecraft.getInstance().execute(() -> {
            GardenEaseScreen.open();
        });
        return 1;
    }

    private static int showHelp(CommandContext<FabricClientCommandSource> context) {
        MessageUtil.sendFeedback(context.getSource(), Component.literal("§6=== GardenEase Commands ==="));
        MessageUtil.sendFeedback(context.getSource(), Component.literal("§e/fm §7- Open the GUI"));
        MessageUtil.sendFeedback(context.getSource(), Component.literal("§e/fm list §7- Show all available groups"));
        MessageUtil.sendFeedback(context.getSource(), Component.literal("§e/fm create <name> [loopcommand] §7- Create a new group"));
        MessageUtil.sendFeedback(context.getSource(), Component.literal("§e/fm delete <name> §7- Delete a group"));
        MessageUtil.sendFeedback(context.getSource(), Component.literal("§e/fm start <name> §7- Start farming with a group"));
        MessageUtil.sendFeedback(context.getSource(), Component.literal("§e/fm stop §7- Stop the current macro"));
        MessageUtil.sendFeedback(context.getSource(), Component.literal("§e/fm record <name> §7- Toggle recording mode for a group"));
        MessageUtil.sendFeedback(context.getSource(), Component.literal("§e/fm setcords <group> <lane> §7- Set end coordinates for a lane (CORDS mode)"));
        return 1;
    }

    private static int listGroups(CommandContext<FabricClientCommandSource> context) {
        ConfigManager.loadGroups();
        var groups = ModConfig.INSTANCE.getGroups();

        if (groups.isEmpty()) {
            MessageUtil.sendFeedback(context.getSource(), Component.literal("§cNo groups found! Use §e/fm create <name>§c to create one."));
            return 0;
        }

        MessageUtil.sendFeedback(context.getSource(), Component.literal("§6=== Available Groups ==="));
        for (GroupConfig group : groups) {
            int laneCount = group.getLanes().size();
            String modeTag = group.getMode() == GroupMode.CORDS ? "§d[CORDS]" : "§a[DUR]";
            MessageUtil.sendFeedback(context.getSource(), Component.literal("§7- §a" + group.getName() + " " + modeTag + " §7(" + laneCount + " lanes, loop: " + group.getLoopCommand() + ")"));
        }
        return 1;
    }

    private static int createGroup(CommandContext<FabricClientCommandSource> context, String name, String loopCommand) {
        ConfigManager.loadGroups();
        var config = ModConfig.INSTANCE;

        for (GroupConfig group : config.getGroups()) {
            if (group.getName().equalsIgnoreCase(name)) {
                MessageUtil.sendFeedback(context.getSource(), Text.literal("§cGroup '" + name + "' already exists!"));
                return 0;
            }
        }

        int nextId = config.getGroups().stream()
                .mapToInt(GroupConfig::getId)
                .max()
                .orElse(0) + 1;

        GroupConfig newGroup = new GroupConfig(nextId, name, "", loopCommand);
        config.getGroups().add(newGroup);
        ConfigManager.saveGroup(newGroup);

        MessageUtil.sendFeedback(context.getSource(), Text.literal("§aGroup '" + name + "' created successfully!"));
        MessageUtil.sendFeedback(context.getSource(), Text.literal("§7Loop command: §e" + loopCommand));
        MessageUtil.sendFeedback(context.getSource(), Text.literal("§7Use §e/fm record " + name + "§7 to add lanes."));
        return 1;
    }

    private static int deleteGroup(CommandContext<FabricClientCommandSource> context, String name) {
        ConfigManager.loadGroups();
        var config = ModConfig.INSTANCE;

        GroupConfig toDelete = null;
        for (GroupConfig group : config.getGroups()) {
            if (group.getName().equalsIgnoreCase(name)) {
                toDelete = group;
                break;
            }
        }

        if (toDelete == null) {
            MessageUtil.sendFeedback(context.getSource(), Text.literal("§cGroup '" + name + "' not found!"));
            return 0;
        }

        config.getGroups().remove(toDelete);

        try {
            Path groupsPath = net.fabricmc.loader.api.FabricLoader.getInstance()
                    .getConfigDir().resolve("farmmacro/groups");
            String fileName = toDelete.getName().replaceAll("[^a-zA-Z0-9.-]", "_") + ".json";
            Files.deleteIfExists(groupsPath.resolve(fileName));
        } catch (Exception e) {
            // Ignore
        }

        MessageUtil.sendFeedback(context.getSource(), Text.literal("§aGroup '" + name + "' deleted successfully!"));
        return 1;
    }

    private static int startGroup(CommandContext<FabricClientCommandSource> context, String name, int laneNumber) {
        ConfigManager.loadGroups();
        var config = ModConfig.INSTANCE;

        int groupIndex = -1;
        for (int i = 0; i < config.getGroups().size(); i++) {
            if (config.getGroups().get(i).getName().equalsIgnoreCase(name)) {
                groupIndex = i;
                break;
            }
        }

        if (groupIndex == -1) {
            MessageUtil.sendFeedback(context.getSource(), Text.literal("§cGroup '" + name + "' not found!"));
            return 0;
        }

        GroupConfig group = config.getGroups().get(groupIndex);
        if (group.getLanes().isEmpty()) {
            MessageUtil.sendFeedback(context.getSource(), Text.literal("§cGroup '" + name + "' has no lanes! Use §e/fm record " + name + "§c to add lanes."));
            return 0;
        }

        if (laneNumber < 1 || laneNumber > group.getLanes().size()) {
            MessageUtil.sendFeedback(context.getSource(), Text.literal("§cInvalid lane number! Group '" + name + "' has " + group.getLanes().size() + " lanes."));
            return 0;
        }

        config.setSelectedGroupIndex(groupIndex);
        MacroController.getInstance().getState().clearPauseData();
        MacroController.getInstance().start(laneNumber - 1);

        MessageUtil.sendFeedback(context.getSource(), Text.literal("§aStarting GardenEase with group: §6" + name + "§a at lane: §e" + laneNumber));
        return 1;
    }

    private static int resumeGroup(CommandContext<FabricClientCommandSource> context, String name) {
        return executeResume(name, text -> MessageUtil.sendFeedback(context.getSource(), text));
    }

    public static int executeResume(String name, java.util.function.Consumer<Component> feedback) {
        var state = MacroController.getInstance().getState();
        String pausedGroup = state.getPausedGroupName();

        if (pausedGroup == null || !pausedGroup.equalsIgnoreCase(name)) {
            feedback.accept(Text.literal("§cYou haven't runned this group yet no pause to data found to resume"));
            return 0;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return 0;

        double px = client.player.getX();
        double py = client.player.getY();
        double pz = client.player.getZ();

        double dx = Math.abs(px - state.getPausedX());
        double dy = Math.abs(py - state.getPausedY());
        double dz = Math.abs(pz - state.getPausedZ());

        if (dx > 2.0 || dy > 2.0 || dz > 2.0) {
            String coords = String.format("%.1f, %.1f, %.1f", state.getPausedX(), state.getPausedY(), state.getPausedZ());
            feedback.accept(Text.literal("§cGo to §e" + coords + " §cyou cant resume here"));
            return 0;
        }

        ConfigManager.loadGroups();
        var config = ModConfig.INSTANCE;
        int groupIndex = -1;
        for (int i = 0; i < config.getGroups().size(); i++) {
            if (config.getGroups().get(i).getName().equalsIgnoreCase(name)) {
                groupIndex = i;
                break;
            }
        }

        if (groupIndex == -1) {
            feedback.accept(Text.literal("§cGroup '" + name + "' not found!"));
            return 0;
        }

        config.setSelectedGroupIndex(groupIndex);
        int laneIndex = state.getPausedLaneIndex();
        float offsetSeconds = state.getPausedRemainingDuration();
        state.clearPauseData();

        MacroController.getInstance().start(laneIndex, offsetSeconds);
        feedback.accept(Text.literal("§aResuming group: §6" + name + "§a at lane: §e" + (laneIndex + 1)));

        return 1;
    }

    private static int stopMacro(CommandContext<FabricClientCommandSource> context) {
        if (MacroController.getInstance().isRunning()) {
            MacroController.getInstance().stop();
            MessageUtil.sendFeedback(context.getSource(), Text.literal("§aMacro stopped!"));
        } else {
            MessageUtil.sendFeedback(context.getSource(), Text.literal("§cNo macro is currently running!"));
        }
        return 1;
    }

    private static int toggleRecording(CommandContext<FabricClientCommandSource> context, String name) {
        if (RecordingManager.isRecording()) {
            int lanesRecorded = RecordingManager.stopRecording();
            MessageUtil.sendFeedback(context.getSource(), Text.literal("§aSuccessfully exited recording mode for §6" + RecordingManager.getLastGroupName() + "§a with §e" + lanesRecorded + "§a new lane(s)!"));
            return 1;
        }

        ConfigManager.loadGroups();
        var config = ModConfig.INSTANCE;

        GroupConfig targetGroup = null;
        for (GroupConfig group : config.getGroups()) {
            if (group.getName().equalsIgnoreCase(name)) {
                targetGroup = group;
                break;
            }
        }

        if (targetGroup == null) {
            MessageUtil.sendFeedback(context.getSource(), Text.literal("§cGroup '" + name + "' not found! Create it first with §e/fm create " + name));
            return 0;
        }

        RecordingManager.startRecording(targetGroup);
        MessageUtil.sendFeedback(context.getSource(), Text.literal("§a=== RECORDING MODE ACTIVATED ==="));
        MessageUtil.sendFeedback(context.getSource(), Text.literal("§7Recording for group: §6" + name));
        if (targetGroup.getMode() == GroupMode.CORDS) {
            MessageUtil.sendFeedback(context.getSource(), Text.literal("§dCORDS mode: §7Walk to each lane end, hold §eRIGHT SHIFT §7to freeze movement, then press a movement key to save."));
            MessageUtil.sendFeedback(context.getSource(), Text.literal("§7For combos (WA, WD, SA, SD): press both keys within 150ms. Release all movement keys between saves."));
        } else {
            MessageUtil.sendFeedback(context.getSource(), Text.literal("§aDURATION mode: §7Hold a movement key for the lane duration, then release to save."));
        }
        MessageUtil.sendFeedback(context.getSource(), Text.literal("§7To stop recording, type §e/fm record§7 again."));
        return 1;
    }

    private static int setCords(CommandContext<FabricClientCommandSource> context, String groupName, int laneNumber) {
        ConfigManager.loadGroups();
        var config = ModConfig.INSTANCE;

        // Find group
        GroupConfig targetGroup = null;
        for (GroupConfig group : config.getGroups()) {
            if (group.getName().equalsIgnoreCase(groupName)) {
                targetGroup = group;
                break;
            }
        }

        if (targetGroup == null) {
            MessageUtil.sendFeedback(context.getSource(), Text.literal("§cGroup '" + groupName + "' not found!"));
            return 0;
        }

        if (laneNumber < 1 || laneNumber > targetGroup.getLanes().size()) {
            MessageUtil.sendFeedback(context.getSource(), Text.literal("§cInvalid lane number! Group '" + groupName + "' has " + targetGroup.getLanes().size() + " lane(s)."));
            return 0;
        }

        LaneConfig lane = targetGroup.getLanes().get(laneNumber - 1);

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            MessageUtil.sendFeedback(context.getSource(), Text.literal("§cCan't detect player position!"));
            return 0;
        }

        double playerX = client.player.getX();
        double playerY = client.player.getY();
        double playerZ = client.player.getZ();

        String confirmKey = groupName.toLowerCase() + ":" + laneNumber;

        // If lane already has coords set, require confirmation
        if (lane.isCordsSet()) {
            Long pendingTime = pendingCoordsConfirm.get(confirmKey);
            boolean hasPending = pendingTime != null && (System.currentTimeMillis() - pendingTime) < CONFIRM_TIMEOUT_MS;

            if (!hasPending) {
                // First invocation - warn and ask for confirmation
                pendingCoordsConfirm.put(confirmKey, System.currentTimeMillis());
                MessageUtil.sendFeedback(context.getSource(), Text.literal("§eThis lane already has end coordinates set:"));
                MessageUtil.sendFeedback(context.getSource(), Text.literal("§7  X: §f" + String.format("%.2f", lane.getEndX()) + " §7Y: §f" + String.format("%.2f", lane.getEndY()) + " §7Z: §f" + String.format("%.2f", lane.getEndZ())));
                MessageUtil.sendFeedback(context.getSource(), Text.literal("§eRun the command again within 30s to overwrite with your current position:"));
                MessageUtil.sendFeedback(context.getSource(), Text.literal("§7  X: §f" + String.format("%.2f", playerX) + " §7Y: §f" + String.format("%.2f", playerY) + " §7Z: §f" + String.format("%.2f", playerZ)));
                return 1;
            }

            // Second invocation within timeout - confirm and apply
            pendingCoordsConfirm.remove(confirmKey);
        }

        // Set the coordinates
        lane.setEndCoords(playerX, playerY, playerZ);
        ConfigManager.saveGroup(targetGroup);

        MessageUtil.sendFeedback(context.getSource(), Text.literal("§aEnd coordinates set for group §6" + targetGroup.getName() + " §alane §e" + laneNumber + "§a:"));
        MessageUtil.sendFeedback(context.getSource(), Text.literal("§7  X: §f" + String.format("%.2f", playerX) + " §7Y: §f" + String.format("%.2f", playerY) + " §7Z: §f" + String.format("%.2f", playerZ)));

        // Warn if group is not in CORDS mode
        if (targetGroup.getMode() != GroupMode.CORDS) {
            MessageUtil.sendFeedback(context.getSource(), Text.literal("§e⚠ Group is currently in DURATION mode. Switch to CORDS mode via /fm gui or the GUI to use these coordinates."));
        }

        return 1;
    }
}
