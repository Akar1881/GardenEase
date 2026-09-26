package me.jojo.gardenease.mixin;

import me.jojo.gardenease.macro.FarmingThread;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftClientMixin {
	@Invoker("continueAttack")
	protected abstract void gardenease$continueAttack(boolean leftClickHeld);

	@Inject(method = "tick", at = @At("TAIL"))
	private void gardenease$holdAttack(CallbackInfo callbackInfo) {
		if (FarmingThread.isAttackHeld()) {
			gardenease$continueAttack(true);
		}
	}
}
