package com.b1n_ry.yigd.packets;

import com.b1n_ry.yigd.Yigd;
import com.b1n_ry.yigd.components.GraveComponent;
import com.b1n_ry.yigd.config.CommandConfig;
import com.b1n_ry.yigd.config.YigdConfig;
import com.b1n_ry.yigd.data.DeathInfoManager;
import com.b1n_ry.yigd.data.GraveStatus;
import com.b1n_ry.yigd.util.DropRule;
import com.b1n_ry.yigd.util.GraveCompassHelper;
import com.mojang.authlib.GameProfile;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.ActionResultType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ServerPacketHandler {
    private ServerPacketHandler() { }

    public static void handleGraveRestoreRequest(ServerPlayerEntity player, YigdNetwork.GraveRestoreRequestPacket msg) {
        YigdConfig config = YigdConfig.getConfig();
        if (!hasPermission(player, config.commandConfig.restorePermissionLevel)) {
            player.sendMessage(new TranslationTextComponent("text.yigd.command.permission_fail"), Util.NIL_UUID);
            return;
        }

        Optional<GraveComponent> maybeComponent = DeathInfoManager.INSTANCE.getGrave(msg.graveId());
        if (maybeComponent.isPresent()) {
            GraveComponent component = maybeComponent.get();
            GameProfile owner = component.getOwner();
            ServerPlayerEntity restoringPlayer = player.server.getPlayerList().getPlayer(owner.getId());
            if (restoringPlayer == null) {
                player.sendMessage(new TranslationTextComponent("text.yigd.command.restore.fail.offline_player"), Util.NIL_UUID);
                return;
            }

            component.applyToPlayer(restoringPlayer, (ServerWorld) restoringPlayer.level, restoringPlayer.position(), true,
                    dropRule -> shouldTransfer(dropRule, msg.itemsInGrave(), msg.itemsDeleted(), msg.itemsKept(), msg.itemsDropped()));
            if (msg.itemsInGrave()) {
                component.setStatus(GraveStatus.CLAIMED);
                component.removeGraveBlock();
            }

            player.sendMessage(new TranslationTextComponent("text.yigd.command.restore.success"), Util.NIL_UUID);
        } else {
            player.sendMessage(new TranslationTextComponent("text.yigd.command.restore.fail"), Util.NIL_UUID);
        }
    }

    public static void handleGraveRobRequest(ServerPlayerEntity player, YigdNetwork.GraveRobRequestPacket msg) {
        YigdConfig config = YigdConfig.getConfig();
        if (!hasPermission(player, config.commandConfig.robPermissionLevel)) {
            player.sendMessage(new TranslationTextComponent("text.yigd.command.permission_fail"), Util.NIL_UUID);
            return;
        }

        Optional<GraveComponent> maybeComponent = DeathInfoManager.INSTANCE.getGrave(msg.graveId());
        if (maybeComponent.isPresent()) {
            GraveComponent component = maybeComponent.get();
            component.applyToPlayer(player, (ServerWorld) player.level, player.position(), false,
                    dropRule -> shouldTransfer(dropRule, msg.itemsInGrave(), msg.itemsDeleted(), msg.itemsKept(), msg.itemsDropped()));

            if (msg.itemsInGrave()) {
                component.setStatus(GraveStatus.CLAIMED);
                component.removeGraveBlock();
            }

            player.sendMessage(new TranslationTextComponent("text.yigd.command.rob.success"), Util.NIL_UUID);
        } else {
            player.sendMessage(new TranslationTextComponent("text.yigd.command.rob.fail"), Util.NIL_UUID);
        }
    }

    public static void handleGraveDeleteRequest(ServerPlayerEntity player, YigdNetwork.GraveDeleteRequestPacket msg) {
        YigdConfig config = YigdConfig.getConfig();
        if (!hasPermission(player, config.commandConfig.deletePermissionLevel)) {
            player.sendMessage(new TranslationTextComponent("text.yigd.command.permission_fail"), Util.NIL_UUID);
            return;
        }

        ActionResultType deleted = DeathInfoManager.INSTANCE.delete(msg.graveId());
        DeathInfoManager.INSTANCE.setDirty();

        String translationKey;
        switch (deleted) {
            case SUCCESS:
                translationKey = "text.yigd.command.delete.success";
                break;
            case PASS:
                translationKey = "text.yigd.command.delete.pass";
                break;
            case FAIL:
                translationKey = "text.yigd.command.delete.fail";
                break;
            default:
                translationKey = null;
                break;
        }

        ITextComponent response = translationKey == null
                ? new StringTextComponent("If you see this, congratulations. You've broken YIGD")
                : new TranslationTextComponent(translationKey);
        player.sendMessage(response, Util.NIL_UUID);
    }

    public static void handleGraveLockRequest(ServerPlayerEntity player, YigdNetwork.GraveLockRequestPacket msg) {
        YigdConfig config = YigdConfig.getConfig();
        if (!hasPermission(player, config.commandConfig.unlockPermissionLevel)) {
            player.sendMessage(new TranslationTextComponent("text.yigd.command.permission_fail"), Util.NIL_UUID);
            return;
        }

        Optional<GraveComponent> component = DeathInfoManager.INSTANCE.getGrave(msg.graveId());
        if (component.isPresent()) {
            component.get().setLocked(msg.locked());
        } else {
            player.sendMessage(new TranslationTextComponent("text.yigd.command.lock.fail"), Util.NIL_UUID);
        }
    }

    public static void handleObtainKeyRequest(ServerPlayerEntity player, YigdNetwork.ObtainKeyRequestPacket msg) {
        YigdConfig config = YigdConfig.getConfig();
        if (!config.extraFeatures.graveKeys.enabled || !config.extraFeatures.graveKeys.obtainableFromGui) {
            player.sendMessage(new TranslationTextComponent("text.yigd.command.permission_fail"), Util.NIL_UUID);
            return;
        }

        Optional<GraveComponent> component = DeathInfoManager.INSTANCE.getGrave(msg.graveId());
        if (component.isPresent()) {
            GraveComponent grave = component.get();
            ItemStack key = new ItemStack(Yigd.GRAVE_KEY_ITEM);
            Yigd.GRAVE_KEY_ITEM.bindStackToGrave(msg.graveId(), grave.getOwner(), key);
            player.addItem(key);
        } else {
            player.sendMessage(new TranslationTextComponent("text.yigd.command.obtain_key.fail"), Util.NIL_UUID);
        }
    }

    public static void handleObtainCompassRequest(ServerPlayerEntity player, YigdNetwork.ObtainCompassRequestPacket msg) {
        YigdConfig config = YigdConfig.getConfig();
        if (!config.extraFeatures.graveCompass.cloneRecoveryCompassWithGUI) {
            player.sendMessage(new TranslationTextComponent("text.yigd.command.permission_fail"), Util.NIL_UUID);
            return;
        }

        Optional<GraveComponent> component = DeathInfoManager.INSTANCE.getGrave(msg.graveId());
        if (component.isPresent()) {
            GraveComponent grave = component.get();
            GraveCompassHelper.giveCompass(player, msg.graveId(), grave.getPos(), grave.getWorldResourceKey());
        } else {
            player.sendMessage(new TranslationTextComponent("text.yigd.command.obtain_compass.fail"), Util.NIL_UUID);
        }
    }

    public static void handleGraveOverviewRequest(ServerPlayerEntity player, YigdNetwork.GraveOverviewRequestPacket msg) {
        YigdConfig config = YigdConfig.getConfig();
        if (!hasPermission(player, config.commandConfig.viewSelfPermissionLevel)) {
            player.sendMessage(new TranslationTextComponent("text.yigd.command.permission_fail"), Util.NIL_UUID);
            return;
        }

        Optional<GraveComponent> component = DeathInfoManager.INSTANCE.getGrave(msg.graveId());
        if (component.isPresent()) {
            sendGraveOverviewPacket(player, component.get());
        } else {
            player.sendMessage(new TranslationTextComponent("text.yigd.command.view_self.fail"), Util.NIL_UUID);
        }
    }

    public static void handleGraveSelectRequest(ServerPlayerEntity player, YigdNetwork.GraveSelectRequestPacket msg) {
        YigdConfig config = YigdConfig.getConfig();
        if (!hasPermission(player, config.commandConfig.viewUserPermissionLevel)) {
            player.sendMessage(new TranslationTextComponent("text.yigd.command.permission_fail"), Util.NIL_UUID);
            return;
        }

        List<GraveComponent> components = DeathInfoManager.INSTANCE.getBackupData(msg.profile());
        List<LightGraveData> lightGraveData = new ArrayList<>();
        for (GraveComponent component : components) {
            lightGraveData.add(component.toLightData());
        }

        sendGraveSelectionPacket(player, msg.profile(), lightGraveData);
    }

    public static void handleConfigUpdate(ServerPlayerEntity player, YigdNetwork.ConfigUpdatePacket msg) {
        UUID playerId = player.getUUID();
        Yigd.CLAIM_PRIORITIES.put(playerId, msg.claimPriority());
        Yigd.ROB_PRIORITIES.put(playerId, msg.robPriority());

        Yigd.LOGGER.info("Priority overwritten for player {}. Claiming: {} / Robbing: {}",
                player.getGameProfile().getName(), msg.claimPriority().name(), msg.robPriority().name());
    }

    public static void sendGraveOverviewPacket(ServerPlayerEntity player, GraveComponent component) {
        YigdConfig config = YigdConfig.getConfig();
        CommandConfig commandConfig = config.commandConfig;
        CompoundNBT graveNbt = component.toNbt();

        boolean canRestore = hasPermission(player, commandConfig.restorePermissionLevel);
        boolean canRob = hasPermission(player, commandConfig.robPermissionLevel);
        boolean canDelete = hasPermission(player, commandConfig.deletePermissionLevel);
        boolean canUnlock = hasPermission(player, commandConfig.unlockPermissionLevel);
        boolean obtainableKeys = config.extraFeatures.graveKeys.enabled && config.extraFeatures.graveKeys.obtainableFromGui;
        boolean obtainableCompass = config.extraFeatures.graveCompass.cloneRecoveryCompassWithGUI
                && player.inventory.contains(new ItemStack(Items.COMPASS));

        YigdNetwork.sendToPlayer(player, new YigdNetwork.GraveOverviewPacket(graveNbt, canRestore, canRob,
                canDelete, canUnlock, obtainableKeys, obtainableCompass));
    }

    public static void sendGraveSelectionPacket(ServerPlayerEntity player, GameProfile ofUser, List<LightGraveData> data) {
        YigdNetwork.sendToPlayer(player, new YigdNetwork.GraveSelectionPacket(data, ofUser));
    }

    public static void sendPlayerSelectionPacket(ServerPlayerEntity player, List<LightPlayerData> data) {
        YigdNetwork.sendToPlayer(player, new YigdNetwork.PlayerSelectionPacket(data));
    }

    public static void sendConfigSyncPacket(ServerPlayerEntity player) {
        YigdConfig config = YigdConfig.getConfig();
        YigdNetwork.sendToPlayer(player, new YigdNetwork.ConfigSyncPacket(
                config.graveConfig.retrieveMethods.onBreak,
                config.graveRendering.useGlowingEffect,
                config.graveRendering.glowingDistance,
                config.extraFeatures.deathSightEnchant.range));
    }

    private static boolean hasPermission(ServerPlayerEntity player, int requiredLevel) {
        return player.server.getProfilePermissions(player.getGameProfile()) >= requiredLevel;
    }

    private static boolean shouldTransfer(DropRule dropRule, boolean itemsInGrave, boolean itemsDeleted,
                                          boolean itemsKept, boolean itemsDropped) {
        switch (dropRule) {
            case KEEP:
                return itemsKept;
            case DESTROY:
                return itemsDeleted;
            case DROP:
                return itemsDropped;
            case PUT_IN_GRAVE:
                return itemsInGrave;
            default:
                return false;
        }
    }
}
