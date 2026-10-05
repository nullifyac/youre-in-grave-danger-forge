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
import net.minecraft.util.math.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.Util;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.event.server.FMLServerStartedEvent;
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
    public static void handleServerStarted(FMLServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        ServerWorld overworld = server.overworld();
        DeathInfoManager.INSTANCE = overworld.getDataStorage().computeIfAbsent(
                () -> new DeathInfoManager(server),
                DeathInfoManager.DATA_NAME);
        DeathInfoManager.INSTANCE.setDirty();
    }

    @SubscribeEvent
    public static void handlePlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) return;
        if (!(event.getEntity() instanceof ServerPlayerEntity) || !(event.getOriginal() instanceof ServerPlayerEntity)) return;
        ServerPlayerEntity newPlayer = (ServerPlayerEntity) event.getEntity();
        ServerPlayerEntity oldPlayer = (ServerPlayerEntity) event.getOriginal();
        if (newPlayer.level.isClientSide || oldPlayer.level.isClientSide) return;

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
                newPlayer.sendMessage(new TranslationTextComponent("text.yigd.message.grave_location",
                        gravePos.getX(), gravePos.getY(), gravePos.getZ(),
                        latest.getWorldResourceKey().location().toString()), Util.NIL_UUID);
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
        if (!(event.getEntity() instanceof ServerPlayerEntity)) return;
        ServerPlayerEntity player = (ServerPlayerEntity) event.getEntity();

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
            server.sendMessage(new TranslationTextComponent("text.yigd.message.sellout_player",
                    loggedOffProfile.getName(), lastGravePos.getX(), lastGravePos.getY(), lastGravePos.getZ(),
                    component.getWorldResourceKey().location().toString()), Util.NIL_UUID);
        }
    }

    @SubscribeEvent
    public static void handlePlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayerEntity)) return;
        ServerPlayerEntity player = (ServerPlayerEntity) event.getEntity();

        GraveConfig.GraveRobbing robConfig = YigdConfig.getConfig().graveConfig.graveRobbing;
        UUID joiningId = player.getUUID();

        ServerPacketHandler.sendConfigSyncPacket(player);

        if (!Yigd.NOT_NOTIFIED_ROBBERIES.containsKey(joiningId)) return;

        if (robConfig.tellWhoRobbed) {
            List<String> robbedBy = Yigd.NOT_NOTIFIED_ROBBERIES.remove(joiningId);
            if (robbedBy != null) {
                for (String robber : robbedBy) {
                    player.sendMessage(new TranslationTextComponent("text.yigd.message.inform_robbery.with_details", robber), Util.NIL_UUID);
                }
            }
        } else {
            Yigd.NOT_NOTIFIED_ROBBERIES.remove(joiningId);
            player.sendMessage(new TranslationTextComponent("text.yigd.message.inform_robbery"), Util.NIL_UUID);
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
