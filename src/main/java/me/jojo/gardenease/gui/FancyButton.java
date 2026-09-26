package me.jojo.gardenease.gui;

import me.jojo.gardenease.GardenEase;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;

public class FancyButton {
    public int x, y, width, height;
    public String text;
    public Runnable action;
    public boolean selected = false;
    
    public int bgColor = 0xFF2A2A3A;
    public int hoverColor = 0xFF3A3A4A;
    public int selectedColor = 0xFF6B5BFF;
    public int textColor = 0xFFE0E0E0;

    private boolean hovered = false;
    private float hoverProgress = 0f;

    // FIX: Increased animation speed. 
    // 0.1f was too slow on low FPS. 0.6f makes it almost instant.
    private static final float ANIMATION_SPEED = 0.6f; 

    public FancyButton(int x, int y, int width, int height, String text, Runnable action) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.text = text;
        this.action = action;
    }

    public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, Font textRenderer) {
        hovered = isMouseOver(mouseX, mouseY);
        
        // FIX: Using ANIMATION_SPEED constant for snappy feel
        if (hovered && hoverProgress < 1f) {
            hoverProgress = Math.min(1f, hoverProgress + ANIMATION_SPEED);
        } else if (!hovered && hoverProgress > 0f) {
            hoverProgress = Math.max(0f, hoverProgress - ANIMATION_SPEED);
        }

        int currentBg;
        if (selected) {
            currentBg = selectedColor;
        } else {
            currentBg = RenderUtils.lerpColor(bgColor, hoverColor, hoverProgress);
        }

        context.fill(x, y, x + width, y + height, currentBg);
        
        int borderAlpha = (int)(40 + hoverProgress * 60);
        int borderColor = (borderAlpha << 24) | (0xFFFFFF & 0x00FFFFFF);
        if (selected) borderColor = 0x88FFFFFF;
        
        context.fill(x, y, x + width, y + 1, borderColor);
        context.fill(x, y + height - 1, x + width, y + height, borderColor);
        context.fill(x, y, x + 1, y + height, borderColor);
        context.fill(x + width - 1, y, x + width, y + height, borderColor);

        int textWidth = textRenderer.width(text);
        int textX = x + (width - textWidth) / 2;
        int textY = y + (height - 8) / 2;
        
        int renderColor = selected ? 0xFFFFFFFF : (hovered ? 0xFFFFFFFF : textColor);
        context.text(textRenderer, Component.literal(text), textX, textY, renderColor, false);
    }

    // FIX: Use double for precision (Minecraft sends doubles)
    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    // FIX: Add this method to handle clicks directly
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isMouseOver(mouseX, mouseY) && button == 0) { // 0 is Left Click
            onClick();
            return true;
        }
        return false;
    }

    public void onClick() {
        // Plays click sound (Optional but good for feedback)
        Minecraft client = Minecraft.getInstance();
        client.player.playSound(GardenEase.COOKIE_CLICK_EVENT, 1.0f, 1.0f);
        
        if (action != null) {
            action.run();
        }
    }
}
