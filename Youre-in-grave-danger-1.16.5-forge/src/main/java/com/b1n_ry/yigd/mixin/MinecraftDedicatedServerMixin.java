package com.b1n_ry.yigd.mixin;

import com.b1n_ry.yigd.block.entity.GraveBlockEntity;
import com.b1n_ry.yigd.config.YigdConfig;
import net.minecraft.util.math.BlockPos;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DedicatedServer.class)
public class MinecraftDedicatedServerMixin {
    @Inject(method = "isUnderSpawnProtection", at = @At(value = "RETURN"), cancellable = true)
    private void isSpawnProtected(ServerWorld world, BlockPos pos, PlayerEntity player, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue() || !YigdConfig.getConfig().graveConfig.overrideSpawnProtection) return;

        TileEntity be = world.getBlockEntity(pos);
        if (be instanceof GraveBlockEntity) cir.setReturnValue(false);
    }
}
