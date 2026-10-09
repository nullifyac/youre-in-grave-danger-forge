package com.b1n_ry.yigd.networking;

import com.b1n_ry.yigd.Yigd;
import com.b1n_ry.yigd.components.GraveComponent;
import com.b1n_ry.yigd.config.ClaimPriority;
import com.b1n_ry.yigd.config.CommandConfig;
import com.b1n_ry.yigd.config.YigdConfig;
import com.b1n_ry.yigd.data.DeathInfoManager;
import com.b1n_ry.yigd.data.GraveStatus;
import com.b1n_ry.yigd.networking.packets.*;
import com.b1n_ry.yigd.util.GraveCompassHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ServerPacketHandler {
    public static boolean canViewGrave(Player player, GraveComponent grave) {
        var config = YigdConfig.getConfig().commandConfig;
        boolean owner = player.getUUID().equals(grave.getOwner().partialProfile().id());
        return com.b1n_ry.yigd.util.YigdPermissions.has(player,
                owner ? config.viewSelfPermissionLevel : config.viewUserPermissionLevel);
    }

    private static boolean requireView(Player player, GraveComponent grave) {
        if (canViewGrave(player, grave)) return true;
        player.sendSystemMessage(Component.translatable("text.yigd.command.permission_fail"));
        return false;
    }

    public static void deleteGraveRequest(DeleteGraveC2SPacket payload, IPayloadContext context) {
        YigdConfig config = YigdConfig.getConfig();
        Player player = context.player();
        if (!com.b1n_ry.yigd.util.YigdPermissions.has(player, config.commandConfig.deletePermissionLevel)) {
            player.sendSystemMessage(Component.translatable("text.yigd.command.permission_fail"));
            return;
        }

        UUID graveId = payload.graveId();
        MinecraftServer server = ((ServerPlayer) player).level().getServer();

        if (server != null) server.execute(() -> {
            InteractionResult deleted = DeathInfoManager.INSTANCE.delete(graveId);
            DeathInfoManager.INSTANCE.setDirty();

            String translatable = deleted == InteractionResult.SUCCESS ? "text.yigd.command.delete.success" :
                    deleted == InteractionResult.PASS ? "text.yigd.command.delete.pass" : "text.yigd.command.delete.fail";
            player.sendSystemMessage(Component.translatable(translatable));
        });
    }
    public static void graveOverviewRequest(GraveOverviewRequestC2SPacket payload, IPayloadContext context) {
        YigdConfig config = YigdConfig.getConfig();
        Player player = context.player();
        CommandConfig commandConfig = config.commandConfig;

        UUID graveId = payload.graveId();
        Optional<GraveComponent> component = DeathInfoManager.INSTANCE.getGrave(graveId);
        component.ifPresentOrElse(grave -> { if (!requireView(player, grave)) return; PacketDistributor.sendToPlayer((ServerPlayer) player, new GraveOverviewS2CPacket(grave,
                    com.b1n_ry.yigd.util.YigdPermissions.has(player, commandConfig.restorePermissionLevel),
                    com.b1n_ry.yigd.util.YigdPermissions.has(player, commandConfig.robPermissionLevel),
                    com.b1n_ry.yigd.util.YigdPermissions.has(player, commandConfig.deletePermissionLevel),
                    com.b1n_ry.yigd.util.YigdPermissions.has(player, commandConfig.unlockPermissionLevel) && config.graveConfig.unlockable,
                    config.extraFeatures.graveKeys.enabled && config.extraFeatures.graveKeys.obtainableFromGui,
                    config.extraFeatures.graveCompass.cloneRecoveryCompassWithGUI && player.getInventory().countItem(Items.RECOVERY_COMPASS) > 0)); },
                () -> player.sendSystemMessage(Component.translatable("text.yigd.command.view_self.fail")));
    }
    public static void graveSelectionRequest(GraveSelectionRequestC2SPacket payload, IPayloadContext context) {
        YigdConfig config = YigdConfig.getConfig();
        Player player = context.player();
        ResolvableProfile profile = payload.profile();
        boolean owner = player.getUUID().equals(profile.partialProfile().id());
        if (!com.b1n_ry.yigd.util.YigdPermissions.has(player,
                owner ? config.commandConfig.viewSelfPermissionLevel : config.commandConfig.viewUserPermissionLevel)) {
            player.sendSystemMessage(Component.translatable("text.yigd.command.permission_fail"));
            return;
        }

        List<GraveComponent> components = DeathInfoManager.INSTANCE.getBackupData(profile);

        List<LightGraveData> lightGraveData = new ArrayList<>();
        for (GraveComponent component : components) {
            lightGraveData.add(component.toLightData());
        }

        PacketDistributor.sendToPlayer((ServerPlayer) player, new GraveSelectionS2CPacket(lightGraveData, profile));
    }
    public static void lockGrave(LockGraveC2SPacket payload, IPayloadContext context) {
        YigdConfig config = YigdConfig.getConfig();
        Player player = context.player();
        if (!config.graveConfig.unlockable || !com.b1n_ry.yigd.util.YigdPermissions.has(player, config.commandConfig.unlockPermissionLevel)) {
            player.sendSystemMessage(Component.translatable("text.yigd.command.permission_fail"));
            return;
        }

        UUID graveId = payload.graveId();
        boolean lockState = payload.locked();
        MinecraftServer server = ((ServerPlayer) player).level().getServer();
        if (server != null) server.execute(() -> {
            Optional<GraveComponent> component = DeathInfoManager.INSTANCE.getGrave(graveId);
            component.ifPresentOrElse(grave -> { if (requireView(player, grave)) grave.setLocked(lockState); },
                    () -> player.sendSystemMessage(Component.translatable("text.yigd.command.lock.fail")));
        });
    }
    public static void requestCompass(RequestCompassC2SPacket payload, IPayloadContext context) {
        YigdConfig config = YigdConfig.getConfig();
        Player player = context.player();
        if (!config.extraFeatures.graveCompass.cloneRecoveryCompassWithGUI) {
            player.sendSystemMessage(Component.translatable("text.yigd.command.permission_fail"));
            return;
        }

        UUID graveId = payload.graveId();
        MinecraftServer server = ((ServerPlayer) player).level().getServer();
        if (server != null) server.execute(() -> {
            Optional<GraveComponent> component = DeathInfoManager.INSTANCE.getGrave(graveId);
            component.ifPresentOrElse(grave -> {
                if (!requireView(player, grave)) return;
                for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                    ItemStack stack = player.getInventory().getItem(slot);
                    if (!stack.is(Items.RECOVERY_COMPASS)) continue;
                    stack.shrink(1);
                    GraveCompassHelper.giveCompass((ServerPlayer) player, graveId, grave.getPos(), grave.getWorldRegistryKey());
                    return;
                }
            },
                    () -> player.sendSystemMessage(Component.translatable("text.yigd.command.obtain_compass.fail")));
        });
    }
    public static void requestKey(RequestKeyC2SPacket payload, IPayloadContext context) {
        YigdConfig config = YigdConfig.getConfig();
        Player player = context.player();
        if (!config.extraFeatures.graveKeys.enabled || !config.extraFeatures.graveKeys.obtainableFromGui) {
            player.sendSystemMessage(Component.translatable("text.yigd.command.permission_fail"));
            return;
        }

        UUID graveId = payload.graveId();
        MinecraftServer server = ((ServerPlayer) player).level().getServer();
        if (server != null) server.execute(() -> {
            Optional<GraveComponent> component = DeathInfoManager.INSTANCE.getGrave(graveId);
            component.ifPresentOrElse(grave -> {
                if (!requireView(player, grave)) return;
                ItemStack key = new ItemStack(Yigd.GRAVE_KEY_ITEM.get());
                Yigd.GRAVE_KEY_ITEM.get().bindStackToGrave(graveId, grave.getOwner(), key);
                if (!player.addItem(key)) player.drop(key, false);
            }, () -> player.sendSystemMessage(Component.translatable("text.yigd.command.obtain_key.fail")));
        });
    }
    public static void restoreGrave(RestoreGraveC2SPacket payload, IPayloadContext context) {
        YigdConfig config = YigdConfig.getConfig();
        Player player = context.player();
        if (!com.b1n_ry.yigd.util.YigdPermissions.has(player, config.commandConfig.restorePermissionLevel)) {
            player.sendSystemMessage(Component.translatable("text.yigd.command.permission_fail"));
            return;
        }

        UUID graveId = payload.graveId();
        boolean itemsInGrave = payload.itemsInGrave();
        boolean itemsDeleted = payload.itemsDeleted();
        boolean itemsKept = payload.itemsKept();
        boolean itemsDropped = payload.itemsDropped();

        MinecraftServer server = ((ServerPlayer) player).level().getServer();
        if (server != null) server.execute(() -> {
            Optional<GraveComponent> maybeComponent = DeathInfoManager.INSTANCE.getGrave(graveId);
            maybeComponent.ifPresentOrElse(component -> {
                ResolvableProfile owner = component.getOwner();
                UUID uuid = owner.partialProfile().id();
                String playerName = owner.name().orElse(null);
                ServerPlayer restoringPlayer = uuid != null ?
                        server.getPlayerList().getPlayer(uuid) : playerName != null ?
                        server.getPlayerList().getPlayerByName(playerName) : null;

                if (restoringPlayer == null) {
                    player.sendSystemMessage(Component.translatable("text.yigd.command.restore.fail.offline_player"));
                    return;
                }

                component.applyToPlayer(restoringPlayer, restoringPlayer.level(), restoringPlayer.position(), true, dropRule -> switch (dropRule) {
                    case KEEP -> itemsKept;
                    case DESTROY -> itemsDeleted;
                    case DROP -> itemsDropped;
                    case PUT_IN_GRAVE -> itemsInGrave;
                });
                if (itemsInGrave) {
                    component.setStatus(GraveStatus.CLAIMED);

                    component.removeGraveBlock();
                }

                player.sendSystemMessage(Component.translatable("text.yigd.command.restore.success"));
            }, () -> player.sendSystemMessage(Component.translatable("text.yigd.command.restore.fail")));
        });
    }
    public static void robGrave(RobGraveC2SPacket payload, IPayloadContext context) {
        YigdConfig config = YigdConfig.getConfig();
        Player player = context.player();
        if (!com.b1n_ry.yigd.util.YigdPermissions.has(player, config.commandConfig.robPermissionLevel)) {
            player.sendSystemMessage(Component.translatable("text.yigd.command.permission_fail"));
            return;
        }

        UUID graveId = payload.graveId();
        boolean itemsInGrave = payload.itemsInGrave();
        boolean itemsDeleted = payload.itemsDeleted();
        boolean itemsKept = payload.itemsKept();
        boolean itemsDropped = payload.itemsDropped();

        MinecraftServer server = ((ServerPlayer) player).level().getServer();

        if (server != null) server.execute(() -> {
            Optional<GraveComponent> maybeComponent = DeathInfoManager.INSTANCE.getGrave(graveId);
            maybeComponent.ifPresentOrElse(component -> {
                component.applyToPlayer((ServerPlayer) player, (ServerLevel) player.level(), player.position(), false, dropRule -> switch (dropRule) {
                    case KEEP -> itemsKept;
                    case DESTROY -> itemsDeleted;
                    case DROP -> itemsDropped;
                    case PUT_IN_GRAVE -> itemsInGrave;
                });

                if (itemsInGrave) {
                    component.setStatus(GraveStatus.CLAIMED);

                    component.removeGraveBlock();
                }

                player.sendSystemMessage(Component.translatable("text.yigd.command.rob.success"));
            }, () -> player.sendSystemMessage(Component.translatable("text.yigd.command.rob.fail")));
        });
    }
    public static void updateConfig(UpdateConfigC2SPacket payload, IPayloadContext context) {
        Player player = context.player();

        ClaimPriority claimPriority = payload.claiming();
        ClaimPriority robPriority = payload.robbing();

        UUID playerId = player.getUUID();
        Yigd.CLAIM_PRIORITIES.put(playerId, claimPriority);
        Yigd.ROB_PRIORITIES.put(playerId, robPriority);

        Yigd.LOGGER.info("Priority overwritten for player {}. Claiming: {} / Robbing: {}", player.getGameProfile().name(), claimPriority.name(), robPriority.name());
    }
}
