package me.jojo.gardenease.data;

import java.util.ArrayList;
import java.util.List;

public class GroupConfig {
    private int id;
    private String name;
    private String description;
    private String loopCommand;
    private List<LaneConfig> lanes;
    private GroupMode mode = GroupMode.DURATION;

    public GroupConfig(int id) {
        this.id = id;
        this.name = "New Group";
        this.description = "";
        this.loopCommand = "/warp garden";
        this.lanes = new ArrayList<>();
        this.mode = GroupMode.DURATION;
    }

    public GroupConfig(int id, String name, String description, String loopCommand) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.loopCommand = loopCommand;
        this.lanes = new ArrayList<>();
        this.mode = GroupMode.DURATION;
    }

    public GroupConfig(int id, String name, String description, String loopCommand, GroupMode mode) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.loopCommand = loopCommand;
        this.lanes = new ArrayList<>();
        this.mode = mode != null ? mode : GroupMode.DURATION;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLoopCommand() {
        return loopCommand;
    }

    public void setLoopCommand(String loopCommand) {
        this.loopCommand = loopCommand;
    }

    public GroupMode getMode() {
        return mode != null ? mode : GroupMode.DURATION;
    }

    public void setMode(GroupMode mode) {
        this.mode = mode != null ? mode : GroupMode.DURATION;
    }

    public void cycleMode() {
        if (this.mode == GroupMode.DURATION) {
            this.mode = GroupMode.CORDS;
        } else {
            this.mode = GroupMode.DURATION;
        }
    }

    public List<LaneConfig> getLanes() {
        return lanes;
    }

    public void setLanes(List<LaneConfig> lanes) {
        this.lanes = lanes;
    }

    public LaneConfig addLane() {
        LaneConfig lane = new LaneConfig(lanes.size() + 1);
        lanes.add(lane);
        return lane;
    }

    public void removeLane(int laneId) {
        lanes.removeIf(lane -> lane.getId() == laneId);
        for (int i = 0; i < lanes.size(); i++) {
            lanes.get(i).setId(i + 1);
        }
    }

    public LaneConfig getLane(int laneId) {
        return lanes.stream()
                .filter(lane -> lane.getId() == laneId)
                .findFirst()
                .orElse(null);
    }

    public float getTotalDuration() {
        return (float) lanes.stream()
                .mapToDouble(LaneConfig::getDuration)
                .sum();
    }

    public StartDirection getNaturalStartDirection() {
        for (LaneConfig lane : lanes) {
            MovementPattern pattern = lane.getMovementPattern();
            if (pattern == MovementPattern.A || pattern == MovementPattern.WA || pattern == MovementPattern.SA) {
                return StartDirection.LEFT;
            }
            if (pattern == MovementPattern.D || pattern == MovementPattern.WD || pattern == MovementPattern.SD) {
                return StartDirection.RIGHT;
            }
        }
        return StartDirection.LEFT;
    }
}
