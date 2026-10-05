package com.b1n_ry.yigd.packets;

import com.b1n_ry.yigd.util.TextCompat;
import com.b1n_ry.yigd.Yigd;
import com.b1n_ry.yigd.components.GraveComponent;
import com.b1n_ry.yigd.config.CommandConfig;
import com.b1n_ry.yigd.config.YigdConfig;
import com.b1n_ry.yigd.data.DeathInfoManager;
import com.b1n_ry.yigd.data.GraveStatus;
import com.b1n_ry.yigd.util.DropRule;
import com.b1n_ry.yigd.util.GraveCompassHelper;
import com.mojang.authlib.GameProfile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ServerPacketHandler {
    private ServerPacketHandler() { }

    public static void handleGraveRestoreRequest(ServerPlayer player, YigdNetwork.GraveRestoreRequestPacket msg) {
        YigdConfig config = YigdConfig.getConfig();
        if (!hasPermission(player, config.commandConfig.restorePermissionLevel)) {
            player.displayClientMessage(TextCompat.translatable("text.yigd.command.permission_fail"), false);
            return;
        }

        Optional<GraveComponent> maybeComponent = DeathInfoManager.INSTANCE.getGrave(msg.graveId());
        maybeComponent.ifPresentOrElse(component -> {
            GameProfile owner = component.getOwner();
            ServerPlayer restoringPlayer = player.server.getPlayerList().getPlayer(owner.getId());
            if (restoringPlayer == null) {
                player.displayClientMessage(TextCompat.translatable("text.yigd.command.restore.fail.offline_player"), false);
                return;
            }

            component.applyToPlayer(restoringPlayer, (ServerLevel) restoringPlayer.level, restoringPlayer.position(), true,
                    dropRule -> shouldTransfer(dropRule, msg.itemsInGrave(), msg.itemsDeleted(), msg.itemsKept(), msg.itemsDropped()));
            if (msg.itemsInGrave()) {
                component.setStatus(GraveStatus.CLAIMED);
                component.removeGraveBlock();
            }

            player.displayClientMessage(TextCompat.translatable("text.yigd.command.restore.success"), false);
        }, () -> player.displayClientMessage(TextCompat.translatable("text.yigd.command.restore.fail"), false));
    }

    public static void handleGraveRobRequest(ServerPlayer player, YigdNetwork.GraveRobRequestPacket msg) {
        YigdConfig config = YigdConfig.getConfig();
        if (!hasPermission(player, config.commandConfig.robPermissionLevel)) {
            player.displayClientMessage(TextCompat.translatable("text.yigd.command.permission_fail"), false);
            return;
        }

        Optional<GraveComponent> maybeComponent = DeathInfoManager.INSTANCE.getGrave(msg.graveId());
        maybeComponent.ifPresentOrElse(component -> {
            component.applyToPlayer(player, (ServerLevel) player.level, player.position(), false,
                    dropRule -> shouldTransfer(dropRule, msg.itemsInGrave(), msg.itemsDeleted(), msg.itemsKept(), msg.itemsDropped()));

            if (msg.itemsInGrave()) {
                component.setStatus(GraveStatus.CLAIMED);
                component.removeGraveBlock();
            }

            player.displayClientMessage(TextCompat.translatable("text.yigd.command.rob.success"), false);
        }, () -> player.displayClientMessage(TextCompat.translatable("text.yigd.command.rob.fail"), false));
    }

    public static void handleGraveDeleteRequest(ServerPlayer player, YigdNetwork.GraveDeleteRequestPacket msg) {
        YigdConfig config = YigdConfig.getConfig();
        if (!hasPermission(player, config.commandConfig.deletePermissionLevel)) {
            player.displayClientMessage(TextCompat.translatable("text.yigd.command.permission_fail"), false);
            return;
        }

        InteractionResult deleted = DeathInfoManager.INSTANCE.delete(msg.graveId());
        DeathInfoManager.INSTANCE.setDirty();

        String translationKey = switch (deleted) {
            case SUCCESS -> "text.yigd.command.delete.success";
            case PASS -> "text.yigd.command.delete.pass";
            case FAIL -> "text.yigd.command.delete.fail";
            default -> null;
        };

        Component response = translationKey == null
                ? TextCompat.literal("If you see this, congratulations. You've broken YIGD")
                : TextCompat.translatable(translationKey);
        player.displayClientMessage(response, false);
    }

    public static void handleGraveLockRequest(ServerPlayer player, YigdNetwork.GraveLockRequestPacket msg) {
        YigdConfig config = YigdConfig.getConfig();
        if (!hasPermission(player, config.commandConfig.unlockPermissionLevel)) {
            player.displayClientMessage(TextCompat.translatable("text.yigd.command.permission_fail"), false);
            return;
        }

        Optional<GraveComponent> component = DeathInfoManager.INSTANCE.getGrave(msg.graveId());
        component.ifPresentOrElse(grave -> grave.setLocked(msg.locked()),
                () -> player.displayClientMessage(TextCompat.translatable("text.yigd.command.lock.fail"), false));
    }

    public static void handleObtainKeyRequest(ServerPlayer player, YigdNetwork.ObtainKeyRequestPacket msg) {
        YigdConfig config = YigdConfig.getConfig();
        if (!config.extraFeatures.graveKeys.enabled || !config.extraFeatures.graveKeys.obtainableFromGui) {
            player.displayClientMessage(TextCompat.translatable("text.yigd.command.permission_fail"), false);
            return;
        }

        Optional<GraveComponent> component = DeathInfoManager.INSTANCE.getGrave(msg.graveId());
        component.ifPresentOrElse(grave -> {
            ItemStack key = new ItemStack(Yigd.GRAVE_KEY_ITEM);
            Yigd.GRAVE_KEY_ITEM.bindStackToGrave(msg.graveId(), grave.getOwner(), key);
            player.addItem(key);
        }, () -> player.displayClientMessage(TextCompat.translatable("text.yigd.command.obtain_key.fail"), false));
    }

    public static void handleObtainCompassRequest(ServerPlayer player, YigdNetwork.ObtainCompassRequestPacket msg) {
        YigdConfig config = YigdConfig.getConfig();
        if (!config.extraFeatures.graveCompass.cloneRecoveryCompassWithGUI) {
            player.displayClientMessage(TextCompat.translatable("text.yigd.command.permission_fail"), false);
            return;
        }

        Optional<GraveComponent> component = DeathInfoManager.INSTANCE.getGrave(msg.graveId());
        component.ifPresentOrElse(grave ->
                        GraveCompassHelper.giveCompass(player, msg.graveId(), grave.getPos(), grave.getWorldResourceKey()),
                () -> player.displayClientMessage(TextCompat.translatable("text.yigd.command.obtain_compass.fail"), false));
    }

    public static void handleGraveOverviewRequest(ServerPlayer player, YigdNetwork.GraveOverviewRequestPacket msg) {
        YigdConfig config = YigdConfig.getConfig();
        if (!hasPermission(player, config.commandConfig.viewSelfPermissionLevel)) {
            player.displayClientMessage(TextCompat.translatable("text.yigd.command.permission_fail"), false);
            return;
        }

        Optional<GraveComponent> component = DeathInfoManager.INSTANCE.getGrave(msg.graveId());
        component.ifPresentOrElse(grave -> sendGraveOverviewPacket(player, grave),
                () -> player.displayClientMessage(TextCompat.translatable("text.yigd.command.view_self.fail"), false));
    }

    public static void handleGraveSelectRequest(ServerPlayer player, YigdNetwork.GraveSelectRequestPacket msg) {
        YigdConfig config = YigdConfig.getConfig();
        if (!hasPermission(player, config.commandConfig.viewUserPermissionLevel)) {
            player.displayClientMessage(TextCompat.translatable("text.yigd.command.permission_fail"), false);
            return;
        }

        List<GraveComponent> components = DeathInfoManager.INSTANCE.getBackupData(msg.profile());
        List<LightGraveData> lightGraveData = new ArrayList<>();
        for (GraveComponent component : components) {
            lightGraveData.add(component.toLightData());
        }

        sendGraveSelectionPacket(player, msg.profile(), lightGraveData);
    }

    public static void handleConfigUpdate(ServerPlayer player, YigdNetwork.ConfigUpdatePacket msg) {
        UUID playerId = player.getUUID();
        Yigd.CLAIM_PRIORITIES.put(playerId, msg.claimPriority());
        Yigd.ROB_PRIORITIES.put(playerId, msg.robPriority());

        Yigd.LOGGER.info("Priority overwritten for player {}. Claiming: {} / Robbing: {}",
                player.getGameProfile().getName(), msg.claimPriority().name(), msg.robPriority().name());
    }

    public static void sendGraveOverviewPacket(ServerPlayer player, GraveComponent component) {
        YigdConfig config = YigdConfig.getConfig();
        CommandConfig commandConfig = config.commandConfig;
        CompoundTag graveNbt = component.toNbt();

        boolean canRestore = hasPermission(player, commandConfig.restorePermissionLevel);
        boolean canRob = hasPermission(player, commandConfig.robPermissionLevel);
        boolean canDelete = hasPermission(player, commandConfig.deletePermissionLevel);
        boolean canUnlock = hasPermission(player, commandConfig.unlockPermissionLevel);
        boolean obtainableKeys = config.extraFeatures.graveKeys.enabled && config.extraFeatures.graveKeys.obtainableFromGui;
        boolean obtainableCompass = config.extraFeatures.graveCompass.cloneRecoveryCompassWithGUI
                && player.getInventory().contains(new ItemStack(Items.COMPASS));

        YigdNetwork.sendToPlayer(player, new YigdNetwork.GraveOverviewPacket(graveNbt, canRestore, canRob,
                canDelete, canUnlock, obtainableKeys, obtainableCompass));
    }

    public static void sendGraveSelectionPacket(ServerPlayer player, GameProfile ofUser, List<LightGraveData> data) {
        YigdNetwork.sendToPlayer(player, new YigdNetwork.GraveSelectionPacket(data, ofUser));
    }

    public static void sendPlayerSelectionPacket(ServerPlayer player, List<LightPlayerData> data) {
        YigdNetwork.sendToPlayer(player, new YigdNetwork.PlayerSelectionPacket(data));
    }

    public static void sendConfigSyncPacket(ServerPlayer player) {
        YigdConfig config = YigdConfig.getConfig();
        YigdNetwork.sendToPlayer(player, new YigdNetwork.ConfigSyncPacket(
                config.graveConfig.retrieveMethods.onBreak,
                config.graveRendering.useGlowingEffect,
                config.graveRendering.glowingDistance,
                config.extraFeatures.deathSightEnchant.range));
    }

    private static boolean hasPermission(ServerPlayer player, int requiredLevel) {
        return player.hasPermissions(requiredLevel);
    }

    private static boolean shouldTransfer(DropRule dropRule, boolean itemsInGrave, boolean itemsDeleted,
                                          boolean itemsKept, boolean itemsDropped) {
        return switch (dropRule) {
            case KEEP -> itemsKept;
            case DESTROY -> itemsDeleted;
            case DROP -> itemsDropped;
            case PUT_IN_GRAVE -> itemsInGrave;
        };
    }
}
