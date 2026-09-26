package me.jojo.gardenease.mixin;

import net.minecraft.client.gui.components.PlayerTabOverlay;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Player list hooks were removed from the 26.2 HUD extraction pipeline.
 * Keep the mixin entry point so existing configuration remains compatible.
 */
@Mixin(PlayerTabOverlay.class)
public class PlayerListHudMixin {
}