package me.jojo.gardenease.render;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.world.phys.AABB;

public final class ResumeHighlightRenderer {
    private static volatile boolean active;
    private static double targetX;
    private static double targetY;
    private static double targetZ;

    private ResumeHighlightRenderer() {
    }

    public static void register() {
    }

    public static void submit(LevelRenderer levelRenderer) {
        if (!active) return;

        try (Gizmos.TemporaryCollection ignored = levelRenderer.collectPerFrameRenderThreadGizmos()) {
            Gizmos.cuboid(new AABB(targetX - 0.5, targetY, targetZ - 0.5,
                            targetX + 0.5, targetY + 2.0, targetZ + 0.5),
                    GizmoStyle.stroke(0xFFFFAA00, 3.0f)).setAlwaysOnTop();
        }
    }

    public static void show(double x, double y, double z) {
        targetX = x;
        targetY = y;
        targetZ = z;
        active = true;
    }

    public static void clear() {
        active = false;
    }

    public static boolean isActive() {
        return active;
    }

    public static double getTargetX() {
        return targetX;
    }

    public static double getTargetY() {
        return targetY;
    }

    public static double getTargetZ() {
        return targetZ;
    }
}