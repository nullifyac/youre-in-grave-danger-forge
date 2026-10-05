package com.b1n_ry.yigd.mixin;

import com.b1n_ry.yigd.DeathHandler;
import com.b1n_ry.yigd.Yigd;
import com.b1n_ry.yigd.config.YigdConfig;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LivingEntity.class, priority = 500)
public class LivingEntityMixin {
    @Inject(method = "dropAllDeathLoot", at = @At("HEAD"))
    private void dropAllDeathLoot(DamageSource damageSource, CallbackInfo ci) {
        LivingEntity e = (LivingEntity) (Object) this;

        if (e.level.isClientSide || !(e instanceof ServerPlayer player)) return;

        ServerLevel world = (ServerLevel) player.level;

        if (!player.isDeadOrDying()) return;  // If some weird shit happens, this is a failsafe
        if (player.isSpectator()) return;  // Spectators don't generate graves

        if (world.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {  // KeepInv should be handled by vanilla. No need to complicate things
            if (YigdConfig.getConfig().graveConfig.logGraveGenerationFailures) {
                Yigd.LOGGER.info("Not generating grave: gamerule keepInventory=true player={} uuid={} dim={}",
                        player.getGameProfile().getName(), player.getUUID(), world.dimension().location());
            }
            return;
        }

        DeathHandler deathHandler = new DeathHandler();
        deathHandler.onPlayerDeath(player, world, player.position(), damageSource);
    }
}
