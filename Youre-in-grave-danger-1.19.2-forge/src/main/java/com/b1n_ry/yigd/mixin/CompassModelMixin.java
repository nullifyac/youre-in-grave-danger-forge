package com.b1n_ry.yigd.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.CompassItemPropertyFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(CompassItemPropertyFunction.class)
public class CompassModelMixin {
    @Redirect(
            method = "getCompassRotation",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/item/CompassItemPropertyFunction$CompassTarget;getPos(Lnet/minecraft/client/multiplayer/ClientLevel;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/core/GlobalPos;"
            )
    )
    private GlobalPos changeCompassDirection(CompassItemPropertyFunction.CompassTarget compassTarget, ClientLevel world, ItemStack stack, Entity entity) {
        CompoundTag itemNbt = stack.getTag();
        final String graveDimensionKey = "grave_dimension";
        final String gravePosKey = "grave_pos";

        if (itemNbt != null && itemNbt.contains(graveDimensionKey) && itemNbt.contains(gravePosKey)) {
            ResourceLocation dimensionId = ResourceLocation.tryParse(itemNbt.getString(graveDimensionKey));
            if (dimensionId == null) {
                return null;
            }
            ResourceKey<Level> dimensionKey = ResourceKey.create(Registry.DIMENSION_REGISTRY, dimensionId);
            BlockPos blockPos = NbtUtils.readBlockPos(itemNbt.getCompound(gravePosKey));
            return GlobalPos.of(dimensionKey, blockPos);
        }

        return compassTarget.getPos(world, stack, entity);
    }
}
