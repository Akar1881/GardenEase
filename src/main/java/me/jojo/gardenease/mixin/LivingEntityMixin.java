// File: LivingEntityMixin.java
package me.jojo.gardenease.mixin;

import me.jojo.gardenease.macro.MacroState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {
    
    /**
     * Detects knockback from attacks (knockback is called when an entity receives knockback).
     */
    @Inject(method = "knockback(DDDLnet/minecraft/world/damagesource/DamageSource;F)V", at = @At("HEAD"))
    private void onKnockback(double strength, double x, double z, DamageSource damageSource, float friction, CallbackInfo ci) {
        if (!MacroState.isMacroRunning()) return;
        
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        
        // Only trigger for the local player
        if ((Object) this == client.player) {
            MacroState.triggerFailsafe("Knockback detected (strength: " + String.format("%.2f", strength) + ")");
        }
    }
    
    /**
     * Detect when hurtTime is set (player took damage)
     */
    @Inject(method = "animateHurt", at = @At("HEAD"))
    private void onAnimateHurt(float yaw, CallbackInfo ci) {
        if (!MacroState.isMacroRunning()) return;
        
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        
        if ((Object) this == client.player) {
            MacroState.triggerFailsafe("Damage animation triggered (took damage)");
        }
    }
}
