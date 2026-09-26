package me.jojo.gardenease.mixin;

import me.jojo.gardenease.macro.MacroState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class ClientPlayerEntityMixin {

    @Inject(method = "setYRot", at = @At("HEAD"))
    private void onSetYaw(float yaw, CallbackInfo ci) {
        if (!MacroState.isMacroRunning()) return;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null || (Object)this != client.player) return;

        float current = client.player.getYRot();
        if (Math.abs(yaw - current) > 1.0f) {
            MacroState.triggerFailsafe("External rotation detected (yaw)");
        }
    }

    @Inject(method = "setXRot", at = @At("HEAD"))
    private void onSetPitch(float pitch, CallbackInfo ci) {
        if (!MacroState.isMacroRunning()) return;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null || (Object)this != client.player) return;

        float current = client.player.getXRot();
        if (Math.abs(pitch - current) > 1.0f) {
            MacroState.triggerFailsafe("External rotation detected (pitch)");
        }
    }
}
