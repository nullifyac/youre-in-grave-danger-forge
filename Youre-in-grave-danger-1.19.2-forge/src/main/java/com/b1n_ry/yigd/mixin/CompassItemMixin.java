package com.b1n_ry.yigd.mixin;

import com.b1n_ry.yigd.config.YigdConfig;
import com.b1n_ry.yigd.data.DeathInfoManager;
import com.b1n_ry.yigd.data.GraveStatus;
import com.b1n_ry.yigd.util.GraveCompassHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.CompassItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(CompassItem.class)
public class CompassItemMixin {
    @Inject(method = "inventoryTick", at = @At("TAIL"))
    private void onInventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected, CallbackInfo ci) {
        if (level.isClientSide) return;

        // This mixin is to make the grave compass disappear when the grave is broken (or redirect to the closest grave)
        if (!YigdConfig.getConfig().extraFeatures.graveCompass.deleteWhenUnlinked) return;
        CompoundTag itemNbt = stack.getTag();
        if (itemNbt == null) return;

        if (level.getGameTime() % 200 == 0) {
            GraveCompassHelper.updateClosestNbt(level.dimension(), entity.blockPosition(), entity.getUUID(), stack);
        }

        if (!itemNbt.contains("linked_grave")) return;

        UUID graveId = itemNbt.getUUID("linked_grave");
        DeathInfoManager.INSTANCE.getGrave(graveId).ifPresent(grave -> {
            if (grave.getStatus() != GraveStatus.UNCLAIMED) {
                stack.setCount(0);
            }
        });
    }
}
