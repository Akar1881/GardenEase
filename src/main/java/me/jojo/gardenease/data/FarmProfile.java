package me.jojo.gardenease.data;

public enum FarmProfile {
    WHEAT("S-SHAPE Wheat", MovementPattern.D, MovementPattern.W, false, null, "A/D lanes + W row advance"),
    CARROT("S-SHAPE Carrot", MovementPattern.D, MovementPattern.W, false, null, "A/D lanes + W row advance"),
    POTATO("S-SHAPE Potato", MovementPattern.D, MovementPattern.W, false, null, "A/D lanes + W row advance"),
    SUGAR_CANE("S-SHAPE Sugar Cane", MovementPattern.D, MovementPattern.W, false, new MovementPattern[]{MovementPattern.A, MovementPattern.S}, "A then S, alternating lanes"),
    NETHER_WART("S-SHAPE Nether Wart", MovementPattern.D, MovementPattern.W, false, null, "A/D lanes + W row advance"),
    COCOA_BEANS("S-SHAPE Cocoa Beans", MovementPattern.D, MovementPattern.W, false, new MovementPattern[]{MovementPattern.S, MovementPattern.A, MovementPattern.W, MovementPattern.A, MovementPattern.S, MovementPattern.A, MovementPattern.W}, "S, A, W, A, S, A, W"),
    MUSHROOM("S-SHAPE Mushroom", MovementPattern.D, MovementPattern.W, true, new MovementPattern[]{MovementPattern.W, MovementPattern.S}, "W then S, alternating lanes"),
    CACTUS("S-SHAPE Cactus", MovementPattern.D, MovementPattern.W, false, null, "A/D lanes + W row advance"),
    MELON("S-SHAPE Melon", MovementPattern.D, MovementPattern.W, false, null, "A/D lanes + W row advance"),
    PUMPKIN("S-SHAPE Pumpkin", MovementPattern.D, MovementPattern.W, false, null, "A/D lanes + W row advance"),
    WILD_ROSE("S-SHAPE Wild Rose", MovementPattern.D, MovementPattern.W, false, null, "A/D lanes + W row advance"),
    SUNFLOWER_MOONFLOWER("S-SHAPE Sunflower & MoonFlower", MovementPattern.D, MovementPattern.W, false, null, "A/D lanes + W row advance");

    private final String displayName;
    private final MovementPattern rowTraversal;
    private final MovementPattern rowAdvance;
    private final boolean sShape;
    private final MovementPattern[] sequentialPattern;
    private final String movementDescription;

    FarmProfile(String displayName, MovementPattern rowTraversal, MovementPattern rowAdvance,
                boolean sShape,
                MovementPattern[] sequentialPattern, String movementDescription) {
        this.displayName = displayName;
        this.rowTraversal = rowTraversal;
        this.rowAdvance = rowAdvance;
        this.sShape = sShape;
        this.sequentialPattern = sequentialPattern;
        this.movementDescription = movementDescription;
    }

    public String getDisplayName() {
        return displayName;
    }

    public MovementPattern getRowTraversal() {
        return rowTraversal;
    }

    public MovementPattern getRowAdvance() {
        return rowAdvance;
    }

    public boolean isSShape() {
        return sShape;
    }

    public boolean hasSequentialPattern() {
        return sequentialPattern != null && sequentialPattern.length > 0;
    }

    public MovementPattern getSequentialMovement(int index) {
        return sequentialPattern[index % sequentialPattern.length];
    }

    public String getMovementDescription() {
        return movementDescription;
    }

    @Override
    public String toString() {
        return displayName;
    }
}