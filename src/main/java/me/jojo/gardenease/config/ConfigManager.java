package me.jojo.gardenease.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import me.jojo.gardenease.GardenEase;
import me.jojo.gardenease.data.GroupConfig;
import me.jojo.gardenease.data.GroupMode;
import me.jojo.gardenease.data.LaneConfig;
import me.jojo.gardenease.data.MovementPattern;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ConfigManager {
    private static final String CONFIG_FILE = "gardenease.json";
    private static final String GROUPS_DIR = "farmmacro/groups";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void load() {
        Path configPath = getConfigPath();

        if (Files.exists(configPath)) {
            try {
                String json = Files.readString(configPath);
                ConfigData data = GSON.fromJson(json, ConfigData.class);

                if (data != null) {
                    applyConfig(data);
                    GardenEase.LOGGER.info("Config loaded from {}", configPath);
                }
            } catch (IOException e) {
                GardenEase.LOGGER.error("Failed to load config", e);
            }
        } else {
            createDefaultConfig();
            save();
        }

        loadGroups();
    }

    public static void loadGroups() {
        Path groupsPath = getGroupsDirPath();
        ModConfig config = ModConfig.INSTANCE;
        config.getGroups().clear();

        if (Files.exists(groupsPath)) {
            try (var stream = Files.newDirectoryStream(groupsPath, "*.json")) {
                for (Path entry : stream) {
                    try {
                        String json = Files.readString(entry);
                        GroupData gd = GSON.fromJson(json, GroupData.class);
                        if (gd != null) {
                            GroupMode mode = GroupMode.DURATION;
                            if (gd.mode != null) {
                                try {
                                    mode = GroupMode.valueOf(gd.mode);
                                } catch (Exception e) {
                                    mode = GroupMode.DURATION;
                                }
                            }
                            GroupConfig group = new GroupConfig(gd.id, gd.name, gd.description, gd.loopCommand, mode);
                            if (gd.lanes != null) {
                                for (LaneData ld : gd.lanes) {
                                    MovementPattern pattern;
                                    try {
                                        pattern = MovementPattern.valueOf(ld.movementPattern);
                                    } catch (Exception e) {
                                        pattern = MovementPattern.WA;
                                    }
                                    group.getLanes().add(new LaneConfig(
                                            ld.id, ld.name, ld.duration, pattern, ld.gaussianMovement,
                                            ld.endX, ld.endY, ld.endZ, ld.cordsSet
                                    ));
                                }
                            }
                            config.getGroups().add(group);
                        }
                    } catch (IOException e) {
                        GardenEase.LOGGER.error("Failed to load group file: " + entry, e);
                    }
                }
            } catch (IOException e) {
                GardenEase.LOGGER.error("Failed to list groups directory", e);
            }
        }
    }

    public static void saveGroup(GroupConfig group) {
        Path groupsPath = getGroupsDirPath();
        try {
            Files.createDirectories(groupsPath);

            // Prepare new filename
            String newFileName = group.getName().replaceAll("[^a-zA-Z0-9.-]", "_") + ".json";

            // Clean up any stale file that contains the same group id but different name
            try (var stream = Files.newDirectoryStream(groupsPath, "*.json")) {
                for (Path entry : stream) {
                    try {
                        String json = Files.readString(entry);
                        GroupData existing = GSON.fromJson(json, GroupData.class);
                        if (existing != null && existing.id == group.getId()) {
                            String existingName = entry.getFileName().toString();
                            if (!existingName.equals(newFileName)) {
                                Files.deleteIfExists(entry);
                            }
                        }
                    } catch (Exception ignore) {
                        // ignore malformed files
                    }
                }
            } catch (IOException ignore) {
                // ignore directory listing errors
            }

            GroupData gd = new GroupData();
            gd.id = group.getId();
            gd.name = group.getName();
            gd.description = group.getDescription();
            gd.loopCommand = group.getLoopCommand();
            gd.mode = group.getMode().name();
            gd.lanes = new ArrayList<>();
            for (LaneConfig lane : group.getLanes()) {
                LaneData ld = new LaneData();
                ld.id = lane.getId();
                ld.name = lane.getName();
                ld.duration = lane.getDuration();
                ld.movementPattern = lane.getMovementPattern().name();
                ld.gaussianMovement = lane.isGaussianMovement();
                ld.endX = lane.getEndX();
                ld.endY = lane.getEndY();
                ld.endZ = lane.getEndZ();
                ld.cordsSet = lane.isCordsSet();
                gd.lanes.add(ld);
            }

            Files.writeString(groupsPath.resolve(newFileName), GSON.toJson(gd));
        } catch (IOException e) {
            GardenEase.LOGGER.error("Failed to save group: " + group.getName(), e);
        }
    }

    public static void save() {
        Path configPath = getConfigPath();
        ConfigData data = extractConfig();

        try {
            Files.createDirectories(configPath.getParent());
            Files.writeString(configPath, GSON.toJson(data));
            GardenEase.LOGGER.info("Config saved to {}", configPath);
        } catch (IOException e) {
            GardenEase.LOGGER.error("Failed to save config", e);
        }

        for (GroupConfig group : ModConfig.INSTANCE.getGroups()) {
            saveGroup(group);
        }
    }

    private static Path getConfigPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE);
    }

    private static Path getGroupsDirPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(GROUPS_DIR);
    }

    private static void createDefaultConfig() {
        ModConfig config = ModConfig.INSTANCE;
        config.setSelectedGroupIndex(0);
        GardenEase.LOGGER.info("Created default config");
    }

    private static ConfigData extractConfig() {
        ModConfig config = ModConfig.INSTANCE;
        ConfigData data = new ConfigData();

        data.enabled = config.isEnabled();
        data.loopEnabled = config.isLoopEnabled();
        data.selectedGroupIndex = config.getSelectedGroupIndex();
        data.selectedProfileIndex = config.getSelectedProfileIndex();
        data.farmEndX = config.getFarmEndX();
        data.farmEndY = config.getFarmEndY();
        data.farmEndZ = config.getFarmEndZ();
        data.farmEndSet = config.isFarmEndSet();

        data.emergencyHotkey = config.getEmergencyHotkey();
        data.sprintEnabled = config.isSprintEnabled();
        data.autoUngrabMouse = config.isAutoUngrabMouse();
        data.lastRunGroupId = config.getLastRunGroupId();

        return data;
    }

    private static void applyConfig(ConfigData data) {
        ModConfig config = ModConfig.INSTANCE;

        config.setEnabled(data.enabled);
        config.setLoopEnabled(data.loopEnabled);
        config.setSelectedGroupIndex(data.selectedGroupIndex);
        config.setSelectedProfileIndex(data.selectedProfileIndex);
        config.setFarmEndX(data.farmEndX);
        config.setFarmEndY(data.farmEndY);
        config.setFarmEndZ(data.farmEndZ);
        config.setFarmEndSet(data.farmEndSet);

        config.setEmergencyHotkey(data.emergencyHotkey);
        config.setSprintEnabled(data.sprintEnabled);
        config.setAutoUngrabMouse(data.autoUngrabMouse);
        config.setLastRunGroupId(data.lastRunGroupId);
    }

    private static class ConfigData {
        boolean enabled;
        boolean loopEnabled = true;
        int selectedGroupIndex;
        int selectedProfileIndex = 0;
        double farmEndX = 0.0;
        double farmEndY = 0.0;
        double farmEndZ = 0.0;
        boolean farmEndSet = false;

        String emergencyHotkey = "0";
        boolean sprintEnabled = true;
        boolean autoUngrabMouse = false;
        int lastRunGroupId = -1;
    }

    private static class GroupData {
        int id;
        String name;
        String description;
        String loopCommand;
        String mode;
        List<LaneData> lanes;
    }

    private static class LaneData {
        int id;
        String name;
        float duration;
        String movementPattern;
        boolean gaussianMovement;
        double endX = 0.0;
        double endY = 0.0;
        double endZ = 0.0;
        boolean cordsSet = false;
    }
}
