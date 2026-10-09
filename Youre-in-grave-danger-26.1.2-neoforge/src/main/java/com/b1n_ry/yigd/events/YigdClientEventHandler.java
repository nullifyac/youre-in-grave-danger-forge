package com.b1n_ry.yigd.events;

import com.b1n_ry.yigd.block.entity.GraveBlockEntity;
import com.b1n_ry.yigd.client.render.GraveBlockEntityRenderer;
import com.b1n_ry.yigd.config.ExtraFeaturesConfig;
import com.b1n_ry.yigd.config.YigdConfig;
import com.b1n_ry.yigd.util.YigdTags;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.bus.api.SubscribeEvent;

public class YigdClientEventHandler {
    @SubscribeEvent
    public void renderGlowingGrave(YigdEvents.RenderGlowingGraveEvent event) {
        GraveBlockEntity be = event.getGrave();
        LocalPlayer player = event.getPlayer();
        YigdConfig config = YigdConfig.getConfig();

        if (player == null || !config.graveRendering.useGlowingEffect || !GraveBlockEntityRenderer.syncedGlowing) return;

        ResolvableProfile graveOwner = be.getGraveSkull();

        double distance = Integer.min(config.graveRendering.glowingDistance, GraveBlockEntityRenderer.syncedGlowingMaxDistance);
        boolean isOwner = graveOwner != null && graveOwner.partialProfile().id().equals(player.getUUID());
        ExtraFeaturesConfig.DeathSightConfig deathSightConfig = config.extraFeatures.deathSightEnchant;

        ItemStack headStack = player.getItemBySlot(EquipmentSlot.HEAD);
        if (!headStack.isEmpty() && EnchantmentHelper.hasTag(headStack, YigdTags.DEATH_SIGHT)) {
            distance = Double.min(deathSightConfig.range, GraveBlockEntityRenderer.syncedDeathSightDistance);

            isOwner = switch (deathSightConfig.targets) {
                case OWN_GRAVES -> isOwner;
                case PLAYER_GRAVES -> graveOwner != null;
                case ALL_GRAVES -> true;
            };
        }

        boolean inRange = be.getBlockPos().closerToCenterThan(player.position(), distance);

        if (isOwner && inRange)
            event.setRenderGlowing(true);
    }
}
