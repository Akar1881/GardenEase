package me.jojo.gardenease.mixin;

import me.jojo.gardenease.macro.MacroState;
import me.jojo.gardenease.macro.FarmingThread;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPlayNetworkHandlerMixin {
    
    @Inject(method = "handleMovePlayer", at = @At("HEAD"))
    private void onHandleMovePlayer(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        // 1. Immediate exit if the macro isn't running globally
        if (!MacroState.isMacroRunning()) {
            return;
        }

        // 2. Double check the specific thread state
        FarmingThread thread = MacroState.getActiveThread();
        if (thread == null || !thread.isRunning()) {
            return;
        }

        // Intentional loop teleports remain protected even after the next iteration starts.
        MacroState state = MacroState.getInstance();
        if (state.isExpectingTeleport()) {
            state.consumeExpectedTeleport();
            return;
        }

        // Keep the existing running-state check for real position anomalies.
        if (state.isRunning()) {
            MacroState.triggerFailsafe("Server position/look packet received (Possible TP/Check)");
        }
    }
}
