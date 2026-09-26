package me.jojo.gardenease.mixin;

import me.jojo.gardenease.macro.MacroState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Inventory.class)
public class PlayerInventoryMixin {

    @Inject(method = "setSelectedSlot(I)V", at = @At("HEAD"))
    private void onSetSelectedSlot(int slot, CallbackInfo ci) {
        if (!MacroState.isMacroRunning()) return;
        
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        
        if (MacroState.hasHotbarSlotChanged(slot)) {
            int cachedSlot = MacroState.getCachedHotbarSlot();
            MacroState.triggerFailsafe(
                "Hotbar slot changed: " + cachedSlot + " -> " + slot
            );
        }
        
        ItemStack currentItem = client.player.getMainHandItem();
        if (MacroState.hasHeldItemChanged(currentItem)) {
            ItemStack cachedItem = MacroState.getCachedHeldItem();
            MacroState.triggerFailsafe(
                "Held item changed: " + cachedItem.getHoverName().getString() + " -> " + currentItem.getHoverName().getString()
            );
        }
    }
}
