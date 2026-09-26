package me.jojo.gardenease.mixin;

import me.jojo.gardenease.macro.MacroState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class LivingEntityVelocityMixin {
    
    @Unique
    private double gardenease_lastVelX = 0;
    @Unique
    private double gardenease_lastVelY = 0;
    @Unique
    private double gardenease_lastVelZ = 0;
    @Unique
    private boolean gardenease_firstTick = true;
    
    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        if (!MacroState.isMacroRunning()) {
            gardenease_firstTick = true;
            return;
        }
        
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        
        // Only check for local player
        if ((Object) this != client.player) return;
        
        LivingEntity player = (LivingEntity) (Object) this;
        
        double velX = player.getDeltaMovement().x;
        double velY = player.getDeltaMovement().y;
        double velZ = player.getDeltaMovement().z;
        
        if (gardenease_firstTick) {
            gardenease_lastVelX = velX;
            gardenease_lastVelY = velY;
            gardenease_lastVelZ = velZ;
            gardenease_firstTick = false;
            return;
        }
        
        // Check for sudden velocity spikes (knockback)
        double deltaX = velX - gardenease_lastVelX;
        double deltaY = velY - gardenease_lastVelY;
        double deltaZ = velZ - gardenease_lastVelZ;
        
        double deltaMagnitude = Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
        
        // If velocity changed significantly and player was hurt recently
        if (deltaMagnitude > 0.3 && player.hurtTime > 0) {
            MacroState.triggerFailsafe(
                "Knockback detected: velocity spike=" + String.format("%.2f", deltaMagnitude)
            );
        }
        
        gardenease_lastVelX = velX;
        gardenease_lastVelY = velY;
        gardenease_lastVelZ = velZ;
    }
}
