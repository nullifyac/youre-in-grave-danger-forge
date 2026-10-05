package com.b1n_ry.yigd.events;

import com.b1n_ry.yigd.Yigd;
import com.b1n_ry.yigd.block.entity.GraveBlockEntity;
import com.b1n_ry.yigd.client.render.GraveBlockEntityRenderer;
import com.b1n_ry.yigd.config.YigdConfig;
import com.b1n_ry.yigd.config.ExtraFeaturesConfig.DeathSightConfig;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;

public final class YigdClientEventHandler {
    private YigdClientEventHandler() { }

    public static boolean shouldRenderGlowing(GraveBlockEntity grave, LocalPlayer player) {
        YigdConfig config = YigdConfig.getConfig();

        if (!config.graveRendering.useGlowingEffect || !GraveBlockEntityRenderer.syncedGlowing) {
            return false;
        }

        GameProfile graveOwner = grave.getGraveSkull();

        double distance = Math.min(config.graveRendering.glowingDistance, GraveBlockEntityRenderer.syncedGlowingMaxDistance);
        boolean isOwner = graveOwner != null && graveOwner.equals(player.getGameProfile());
        DeathSightConfig deathSightConfig = config.extraFeatures.deathSightEnchant;
        if (deathSightConfig.enabled) {
            ItemStack headStack = player.getItemBySlot(EquipmentSlot.HEAD);
            if (!headStack.isEmpty() && EnchantmentHelper.getEnchantments(headStack).containsKey(Yigd.DEATH_SIGHT_ENCHANTMENT)) {
                distance = Math.min(deathSightConfig.range, GraveBlockEntityRenderer.syncedDeathSightDistance);

                // This doesn't actually mean that the user is the grave owner, but that the graves should light up
                isOwner = deathSightConfig.targets == DeathSightConfig.GraveTargets.ALL_GRAVES
                        || (graveOwner != null && deathSightConfig.targets == DeathSightConfig.GraveTargets.PLAYER_GRAVES);
                // If targets are OWN_GRAVES, the owner is already correct
            }
        }

        Vec3 gravePos = Vec3.atCenterOf(grave.getBlockPos());
        double actualDistance = player.position().distanceTo(gravePos);

        return isOwner && actualDistance <= distance;
    }
}
