package me.jojo.gardenease.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import java.awt.Color;

public class RenderUtils {
    public static final Identifier FONT = Identifier.fromNamespaceAndPath("gardenease", "arial");

    public static void drawRoundedRect(GuiGraphicsExtractor context, int x, int y, int width, int height, float radius, int color) {
        context.fill(x, y, x + width, y + height, color);
    }

    public static void drawGradientRoundedRect(GuiGraphicsExtractor context, int x, int y, int width, int height, float radius, int colorStart, int colorEnd) {
        // Vertical gradient
        context.fillGradient(x, y, x + width, y + height, colorStart, colorEnd);
    }

    public static int lerpColor(int c1, int c2, float t) {
        int a1 = (c1 >> 24) & 0xFF, r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int a2 = (c2 >> 24) & 0xFF, r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        int a = (int)(a1 + (a2 - a1) * t);
        int r = (int)(r1 + (r2 - r1) * t);
        int g = (int)(g1 + (g2 - g1) * t);
        int b = (int)(b1 + (b2 - b1) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static void drawScrollbar(GuiGraphicsExtractor context, int x, int y, int trackHeight,
                                     int contentSize, int visibleSize, int offset, int maxScroll, int color) {
        if (contentSize <= visibleSize) return;

        context.fill(x, y, x + 4, y + trackHeight, 0x55333344);
        int thumbHeight = Math.max(12, trackHeight * visibleSize / contentSize);
        int thumbTravel = trackHeight - thumbHeight;
        int thumbY = y + (maxScroll == 0 ? 0 : thumbTravel * offset / maxScroll);
        context.fill(x, thumbY, x + 4, thumbY + thumbHeight, color);
    }
}
