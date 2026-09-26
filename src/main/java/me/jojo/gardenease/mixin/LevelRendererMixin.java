package me.jojo.gardenease.mixin;

import me.jojo.gardenease.render.ResumeHighlightRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void gardenease$submitResumeHighlight(CallbackInfo callbackInfo) {
        ResumeHighlightRenderer.submit((LevelRenderer) (Object) this);
    }
}