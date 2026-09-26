package me.jojo.gardenease.config;

import me.jojo.gardenease.data.GroupConfig;
import me.jojo.gardenease.data.LaneConfig;
import me.jojo.gardenease.data.MovementPattern;

import java.util.ArrayList;
import java.util.List;

public class ModConfig {
    public static ModConfig INSTANCE = new ModConfig();

    private boolean enabled = false;
    private boolean loopEnabled = true;
    private long teleportWaitMs = 5000;
    private int selectedGroupIndex = 0;
    private int selectedProfileIndex = 0;
    private double farmEndX = 0.0;
    private double farmEndY = 0.0;
    private double farmEndZ = 0.0;
    private boolean farmEndSet = false;

    private String emergencyHotkey = "0";

    private boolean sprintEnabled = true;
    private boolean hideModMessages = false;
    private boolean autoUngrabMouse = false;

    private int lastRunGroupId = -1;

    private List<GroupConfig> groups = new ArrayList<>();

    public ModConfig() {
        GroupConfig defaultGroup = new GroupConfig(1, "Default_Farm", "Your first farming group", "/warp garden");
        defaultGroup.addLane();
        groups.add(defaultGroup);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isLoopEnabled() {
        return loopEnabled;
    }

    public void setLoopEnabled(boolean loopEnabled) {
        this.loopEnabled = loopEnabled;
    }

    public long getTeleportWaitMs() {
        return teleportWaitMs;
    }

    public int getSelectedGroupIndex() {
        return selectedGroupIndex;
    }

    public void setSelectedGroupIndex(int selectedGroupIndex) {
        this.selectedGroupIndex = selectedGroupIndex;
    }

    public int getSelectedProfileIndex() {
        return selectedProfileIndex;
    }

    public void setSelectedProfileIndex(int selectedProfileIndex) {
        this.selectedProfileIndex = selectedProfileIndex;
    }

    public double getFarmEndX() {
        return farmEndX;
    }

    public void setFarmEndX(double farmEndX) {
        this.farmEndX = farmEndX;
    }

    public double getFarmEndY() {
        return farmEndY;
    }

    public void setFarmEndY(double farmEndY) {
        this.farmEndY = farmEndY;
    }

    public double getFarmEndZ() {
        return farmEndZ;
    }

    public void setFarmEndZ(double farmEndZ) {
        this.farmEndZ = farmEndZ;
    }

    public boolean isFarmEndSet() {
        return farmEndSet;
    }

    public void setFarmEndSet(boolean farmEndSet) {
        this.farmEndSet = farmEndSet;
    }

    public String getEmergencyHotkey() {
        return emergencyHotkey;
    }

    public void setEmergencyHotkey(String emergencyHotkey) {
        this.emergencyHotkey = emergencyHotkey;
    }

    public boolean isSprintEnabled() {
        return sprintEnabled;
    }

    public void setSprintEnabled(boolean sprintEnabled) {
        this.sprintEnabled = sprintEnabled;
    }

    public boolean isHideModMessages() {
        return hideModMessages;
    }

    public void setHideModMessages(boolean hideModMessages) {
        this.hideModMessages = hideModMessages;
    }

    public boolean isAutoUngrabMouse() {
        return autoUngrabMouse;
    }

    public void setAutoUngrabMouse(boolean autoUngrabMouse) {
        this.autoUngrabMouse = autoUngrabMouse;
    }

    public int getLastRunGroupId() {
        return lastRunGroupId;
    }

    public void setLastRunGroupId(int lastRunGroupId) {
        this.lastRunGroupId = lastRunGroupId;
    }

    public List<GroupConfig> getGroups() {
        return groups;
    }

    public void setGroups(List<GroupConfig> groups) {
        this.groups = groups;
    }

    public GroupConfig getSelectedGroup() {
        if (selectedGroupIndex >= 0 && selectedGroupIndex < groups.size()) {
            return groups.get(selectedGroupIndex);
        }
        return null;
    }

    public GroupConfig addGroup() {
        GroupConfig group = new GroupConfig(groups.size() + 1);
        groups.add(group);
        return group;
    }

    public void removeGroup(int groupId) {
        groups.removeIf(g -> g.getId() == groupId);
        for (int i = 0; i < groups.size(); i++) {
            groups.get(i).setId(i + 1);
        }
        if (selectedGroupIndex >= groups.size()) {
            selectedGroupIndex = Math.max(0, groups.size() - 1);
        }
    }
}
