package com.b1n_ry.yigd.mixin;

import com.b1n_ry.yigd.Yigd;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.CompassAngleState;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(CompassAngleState.class)
public class CompassModelMixin {
    // Keep vanilla dimension checks and wobble, replacing only the selected target position.
    @ModifyVariable(method = "calculate", at = @At("STORE"), ordinal = 0)
    private GlobalPos changeCompassDirection(GlobalPos original, ItemStack stack, ClientLevel level, int seed, ItemOwner owner) {
        GlobalPos gravePos = stack.get(Yigd.GRAVE_LOCATION);
        return gravePos != null ? gravePos : original;
    }
}