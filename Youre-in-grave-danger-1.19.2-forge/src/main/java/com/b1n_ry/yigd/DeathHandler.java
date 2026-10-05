package com.b1n_ry.yigd;

import com.b1n_ry.yigd.components.ExpComponent;
import com.b1n_ry.yigd.components.GraveComponent;
import com.b1n_ry.yigd.components.InventoryComponent;
import com.b1n_ry.yigd.components.RespawnComponent;
import com.b1n_ry.yigd.config.YigdConfig;
import com.b1n_ry.yigd.data.DeathContext;
import com.b1n_ry.yigd.data.TranslatableDeathMessage;
import com.b1n_ry.yigd.events.DelayGraveGenerationEvent;
import com.b1n_ry.yigd.impl.ServerPlayerImpl;
import com.b1n_ry.yigd.util.DropRule;
import com.mojang.authlib.GameProfile;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;

import java.util.UUID;

public class DeathHandler {
    public void onPlayerDeath(ServerPlayer player, ServerLevel world, Vec3 pos, DamageSource deathSource) {
        YigdConfig config = YigdConfig.getConfig();

        UUID killerId;
        if (deathSource.getEntity() instanceof ServerPlayer killer) {
            killerId = killer.getUUID();
        } else {
            killerId = null;
        }

        DeathContext context = new DeathContext(player, world, pos, deathSource);

        RespawnComponent respawnComponent = new RespawnComponent(player);  // Will keep track of data used on respawn

        InventoryComponent inventoryComponent = new InventoryComponent(player);  // Will keep track of all items
        ExpComponent expComponent = new ExpComponent(player);  // Will keep track of XP

        InventoryComponent.clearPlayer(player);  // No use for actual inventory when inventory component is created
        ExpComponent.clearXp(player);  // No use for actual exp when exp component is created

        // Here would be an if statement for keepInventory, if the mod didn't let vanilla handle keepInventory
        // There once was code here, but is no more since removing it was the easiest fix a duplication bug

        // Handle drop rules
        inventoryComponent.onDeath(context);

        // Set kept items as soulbound in respawn component
        InventoryComponent soulboundInventory = inventoryComponent.filteredInv(dropRule -> dropRule == DropRule.KEEP);
        respawnComponent.setSoulboundInventory(soulboundInventory);
        // Keep XP
        ExpComponent keepExp = expComponent.getSoulboundExp();
        respawnComponent.setSoulboundExp(keepExp);

        Vec3 graveGenerationPos = !config.graveConfig.generateOnLastGroundPos ? pos : ((ServerPlayerImpl) player).youre_in_grave_danger$getLastGroundPos();
        GraveComponent graveComponent = new GraveComponent(player.getGameProfile(), inventoryComponent, expComponent,
                world, graveGenerationPos.add(0D, .5D, 0D), new TranslatableDeathMessage(deathSource, player), killerId);  // Will keep track of player grave (if enabled)

        GameProfile profile = player.getGameProfile();
        if (!graveComponent.isEmpty()) {
            graveComponent.backUp();
        } else {
            Yigd.LOGGER.info("Did not backup data (grave data empty)");  // There is literally no information worth saving
        }

        respawnComponent.primeForRespawn(profile);

        Direction playerDirection = player.getDirection();

        DelayGraveGenerationEvent event = new DelayGraveGenerationEvent(graveComponent, playerDirection, context, respawnComponent, "vanilla");
        MinecraftForge.EVENT_BUS.post(event);
        if (!event.generationIsDelayed()) {
            graveComponent.generateOrDrop(playerDirection, context, respawnComponent);
        } else if (config.graveConfig.logGraveGenerationFailures) {
            Yigd.LOGGER.warn("Grave generation delayed by another mod: player={} uuid={} dim={} deathPos=({}, {}, {}) deathType={}",
                    player.getGameProfile().getName(), player.getUUID(), world.dimension().location(),
                    pos.x, pos.y, pos.z, deathSource.getMsgId());
        }
    }
}
