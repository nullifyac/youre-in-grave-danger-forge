package com.b1n_ry.yigd.events;

import com.b1n_ry.yigd.Yigd;
import com.b1n_ry.yigd.components.GraveComponent;
import com.b1n_ry.yigd.components.RespawnComponent;
import com.b1n_ry.yigd.config.GraveConfig;
import com.b1n_ry.yigd.config.YigdConfig;
import com.b1n_ry.yigd.data.DeathInfoManager;
import com.b1n_ry.yigd.data.GraveStatus;
import com.b1n_ry.yigd.packets.ServerPacketHandler;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = Yigd.MOD_ID)
public final class ServerEventHandler {
    private ServerEventHandler() {}

    @SubscribeEvent
    public static void handleServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        DeathInfoManager.INSTANCE.clear();

        ServerLevel overworld = server.overworld();
        DeathInfoManager.INSTANCE = overworld.getDataStorage().computeIfAbsent(
                tag -> DeathInfoManager.fromNbt(tag, server),
                DeathInfoManager::new,
                "yigd_data");
        DeathInfoManager.INSTANCE.setDirty();
    }

    @SubscribeEvent
    public static void handlePlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) return;
        if (!(event.getEntity() instanceof ServerPlayer newPlayer) || !(event.getOriginal() instanceof ServerPlayer oldPlayer)) return;
        if (newPlayer.level().isClientSide || oldPlayer.level().isClientSide) return;

        MinecraftForge.EVENT_BUS.post(new BeforeSoulboundEvent(oldPlayer, newPlayer));

        GameProfile newProfile = newPlayer.getGameProfile();
        Optional<RespawnComponent> respawnComponent = DeathInfoManager.INSTANCE.getRespawnComponent(newProfile);
        respawnComponent.ifPresent(component -> component.apply(newPlayer));

        boolean shouldInform = YigdConfig.getConfig().graveConfig.informGraveLocation
                && respawnComponent.map(RespawnComponent::wasGraveGenerated).orElse(false);
        if (shouldInform) {
            List<GraveComponent> graves = new ArrayList<>(DeathInfoManager.INSTANCE.getBackupData(newProfile));
            graves.removeIf(grave -> grave.getStatus() != GraveStatus.UNCLAIMED);
            if (!graves.isEmpty()) {
                GraveComponent latest = graves.get(graves.size() - 1);
                BlockPos gravePos = latest.getPos();
                newPlayer.sendSystemMessage(Component.translatable("text.yigd.message.grave_location",
                        gravePos.getX(), gravePos.getY(), gravePos.getZ(),
                        latest.getWorldResourceKey().location().toString()));
            }
        }
    }

    @SubscribeEvent
    public static void handleServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        List<Runnable> tickFunctions = new ArrayList<>(Yigd.END_OF_TICK);
        Yigd.END_OF_TICK.clear();
        for (Runnable function : tickFunctions) {
            function.run();
        }
    }

    @SubscribeEvent
    public static void handlePlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        YigdConfig config = YigdConfig.getConfig();
        if (!config.graveConfig.sellOutOfflinePeople) return;

        MinecraftServer server = player.getServer();
        if (server == null) return;

        GameProfile loggedOffProfile = player.getGameProfile();
        List<GraveComponent> loggedOffGraves = DeathInfoManager.INSTANCE.getBackupData(loggedOffProfile);
        List<GraveComponent> loggedOffUnclaimed = new ArrayList<>(loggedOffGraves);
        loggedOffUnclaimed.removeIf(grave -> grave.getStatus() != GraveStatus.UNCLAIMED);
        if (!loggedOffUnclaimed.isEmpty()) {
            GraveComponent component = loggedOffUnclaimed.get(0);
            BlockPos lastGravePos = component.getPos();
            server.sendSystemMessage(Component.translatable("text.yigd.message.sellout_player",
                    loggedOffProfile.getName(), lastGravePos.getX(), lastGravePos.getY(), lastGravePos.getZ(),
                    component.getWorldResourceKey().location().toString()));
        }
    }

    @SubscribeEvent
    public static void handlePlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        GraveConfig.GraveRobbing robConfig = YigdConfig.getConfig().graveConfig.graveRobbing;
        UUID joiningId = player.getUUID();

        ServerPacketHandler.sendConfigSyncPacket(player);

        if (!Yigd.NOT_NOTIFIED_ROBBERIES.containsKey(joiningId)) return;

        if (robConfig.tellWhoRobbed) {
            List<String> robbedBy = Yigd.NOT_NOTIFIED_ROBBERIES.remove(joiningId);
            if (robbedBy != null) {
                for (String robber : robbedBy) {
                    player.sendSystemMessage(Component.translatable("text.yigd.message.inform_robbery.with_details", robber));
                }
            }
        } else {
            Yigd.NOT_NOTIFIED_ROBBERIES.remove(joiningId);
            player.sendSystemMessage(Component.translatable("text.yigd.message.inform_robbery"));
        }
    }

    /**
     * @deprecated Fabric-only registration helper retained for backwards compatibility while port completes.
     */
    @Deprecated
    public static void registerEvents() {
        // No-op; Forge uses automatic event bus subscription.
    }
}
