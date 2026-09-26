package me.jojo.gardenease.mixin;

import me.jojo.gardenease.macro.MacroState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public class ChatHudMixin {
    @Inject(method = "addMessage", at = @At("HEAD"))
    private void onAddMessage(Component message, MessageSignature signature,
                               GuiMessageSource source, GuiMessageTag tag, CallbackInfo ci) {
        if (!MacroState.isMacroRunning()) return;
        Minecraft client = Minecraft.getInstance();
        if (client == null) return;

        String playerName = null;
        try {
            if (client.getUser() != null) playerName = client.getUser().getName();
            if (playerName == null && client.player != null) playerName = client.player.getName().getString();
        } catch (Exception ignored) {}

        if (playerName == null) return;

        if (message != null && message.getString().contains(playerName)) {
            client.execute(() -> {
                if (client.player != null) {
                    client.player.playSound(SoundEvents.AMETHYST_BLOCK_PLACE, 1.0f, 1.0f);
                }
            });
        }
    }
}
