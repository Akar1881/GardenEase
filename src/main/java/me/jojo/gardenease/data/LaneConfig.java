package me.jojo.gardenease.data;

public class LaneConfig {
    private int id;
    private String name;
    private float duration;
    private MovementPattern movementPattern;
    private boolean gaussianMovement = false;
    private double endX = 0.0;
    private double endY = 0.0;
    private double endZ = 0.0;
    private boolean cordsSet = false;

    public LaneConfig(int id) {
        this.id = id;
        this.name = "Lane " + id;
        this.duration = 10.0f;
        this.movementPattern = MovementPattern.WA;
        this.gaussianMovement = false;
        this.endX = 0.0;
        this.endY = 0.0;
        this.endZ = 0.0;
        this.cordsSet = false;
    }

    public LaneConfig(int id, String name, float duration, MovementPattern movementPattern, boolean gaussianMovement) {
        this.id = id;
        this.name = name;
        this.duration = duration;
        this.movementPattern = movementPattern;
        this.gaussianMovement = gaussianMovement;
        this.endX = 0.0;
        this.endY = 0.0;
        this.endZ = 0.0;
        this.cordsSet = false;
    }

    public LaneConfig(int id, String name, float duration, MovementPattern movementPattern, boolean gaussianMovement,
                      double endX, double endY, double endZ, boolean cordsSet) {
        this.id = id;
        this.name = name;
        this.duration = duration;
        this.movementPattern = movementPattern;
        this.gaussianMovement = gaussianMovement;
        this.endX = endX;
        this.endY = endY;
        this.endZ = endZ;
        this.cordsSet = cordsSet;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public float getDuration() { return duration; }
    public void setDuration(float duration) { this.duration = duration; }

    public MovementPattern getMovementPattern() { return movementPattern; }
    public void setMovementPattern(MovementPattern movementPattern) { this.movementPattern = movementPattern; }

    public boolean isGaussianMovement() { return gaussianMovement; }
    public void setGaussianMovement(boolean gaussianMovement) { this.gaussianMovement = gaussianMovement; }

    public double getEndX() { return endX; }
    public void setEndX(double endX) { this.endX = endX; }

    public double getEndY() { return endY; }
    public void setEndY(double endY) { this.endY = endY; }

    public double getEndZ() { return endZ; }
    public void setEndZ(double endZ) { this.endZ = endZ; }

    public boolean isCordsSet() { return cordsSet; }
    public void setCordsSet(boolean cordsSet) { this.cordsSet = cordsSet; }

    /** Sets X, Y, Z end coordinates and marks this lane as configured. */
    public void setEndCoords(double x, double y, double z) {
        this.endX = x;
        this.endY = y;
        this.endZ = z;
        this.cordsSet = true;
    }

    public LaneConfig copy() {
        return new LaneConfig(id, name, duration, movementPattern, gaussianMovement, endX, endY, endZ, cordsSet);
    }
}
