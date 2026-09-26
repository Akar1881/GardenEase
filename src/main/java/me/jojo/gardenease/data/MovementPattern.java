package me.jojo.gardenease.data;

public enum MovementPattern {
    NONE("None", ""),
    W("Forward", "W"),
    A("Left", "A"),
    S("Backward", "S"),
    D("Right", "D"),
    WA("Forward-Left", "W+A"),
    WD("Forward-Right", "W+D"),
    SA("Backward-Left", "S+A"),
    SD("Backward-Right", "S+D");

    private final String displayName;
    private final String keys;

    MovementPattern(String displayName, String keys) {
        this.displayName = displayName;
        this.keys = keys;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getKeys() {
        return keys;
    }

    public MovementPattern mirror() {
        return switch (this) {
            case A -> D;
            case D -> A;
            case WA -> WD;
            case WD -> WA;
            case SA -> SD;
            case SD -> SA;
            default -> this;
        };
    }

    @Override
    public String toString() {
        return displayName;
    }
}
