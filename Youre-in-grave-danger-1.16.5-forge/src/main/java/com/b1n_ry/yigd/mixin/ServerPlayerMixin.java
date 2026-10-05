package com.b1n_ry.yigd.mixin;

import com.b1n_ry.yigd.DeathHandler;
import com.b1n_ry.yigd.block.entity.GraveBlockEntity;
import com.b1n_ry.yigd.components.GraveComponent;
import com.b1n_ry.yigd.impl.ServerPlayerImpl;
import net.minecraft.block.BlockState;
import net.minecraft.util.DamageSource;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.server.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
public class ServerPlayerMixin implements ServerPlayerImpl {
    @Unique
    private Vector3d youre_in_grave_danger$lastGroundPos = Vector3d.ZERO;  // Initial value. WILL change as soon as game ticks once

    @Inject(method = "tick", at = @At(value = "HEAD"))
    private void updateGroundPos(CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        if (!player.isOnGround()) return;

        this.youre_in_grave_danger$lastGroundPos = player.position();
    }

//    @Inject(method = "onDeath", at = @At(value = "HEAD"))
//    private void onDeath(DamageSource damageSource, CallbackInfo ci) {
//        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
//
//        ServerWorld world = player.getServerWorld();
//
//        if (!player.isDead()) return;  // If some weird shit happens, this is a failsafe
//        if (player.isSpectator()) return;  // Spectators don't generate graves
//
//        if (world.getGameRules().getBoolean(GameRules.KEEP_INVENTORY)) return;  // KeepInv should be handled by vanilla. No need to complicate things
//
//        DeathHandler deathHandler = new DeathHandler();
//        deathHandler.onPlayerDeath(player, world, player.getPos(), damageSource);
//    }

    @Redirect(method = "createEndPlatform", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/server/ServerWorld;setBlockAndUpdate(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;)Z"))
    private boolean createEndSpawnPlatform(ServerWorld world, BlockPos blockPos, BlockState blockState) {
        if (world.getBlockEntity(blockPos) instanceof GraveBlockEntity) {
            GraveBlockEntity grave = (GraveBlockEntity) world.getBlockEntity(blockPos);
            GraveComponent component = grave.getComponent();
            if (component != null)
                return false;
        }
        return world.setBlockAndUpdate(blockPos, blockState);
    }

    @Override
    public Vector3d youre_in_grave_danger$getLastGroundPos() {
        return this.youre_in_grave_danger$lastGroundPos;
    }
}
