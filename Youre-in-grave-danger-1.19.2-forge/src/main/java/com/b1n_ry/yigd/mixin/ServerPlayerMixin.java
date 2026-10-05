package com.b1n_ry.yigd.mixin;

import com.b1n_ry.yigd.DeathHandler;
import com.b1n_ry.yigd.block.entity.GraveBlockEntity;
import com.b1n_ry.yigd.components.GraveComponent;
import com.b1n_ry.yigd.impl.ServerPlayerImpl;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public class ServerPlayerMixin implements ServerPlayerImpl {
    @Unique
    private Vec3 youre_in_grave_danger$lastGroundPos = Vec3.ZERO;  // Initial value. WILL change as soon as game ticks once

    @Inject(method = "tick", at = @At(value = "HEAD"))
    private void updateGroundPos(CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (!player.isOnGround()) return;

        this.youre_in_grave_danger$lastGroundPos = player.position();
    }

//    @Inject(method = "onDeath", at = @At(value = "HEAD"))
//    private void onDeath(DamageSource damageSource, CallbackInfo ci) {
//        ServerPlayer player = (ServerPlayer) (Object) this;
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

    @Redirect(method = "createEndPlatform", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean createEndSpawnPlatform(ServerLevel world, BlockPos blockPos, BlockState blockState) {
        if (world.getBlockEntity(blockPos) instanceof GraveBlockEntity grave) {
            GraveComponent component = grave.getComponent();
            if (component != null)
                return false;
        }
        return world.setBlockAndUpdate(blockPos, blockState);
    }

    @Override
    public Vec3 youre_in_grave_danger$getLastGroundPos() {
        return this.youre_in_grave_danger$lastGroundPos;
    }
}
