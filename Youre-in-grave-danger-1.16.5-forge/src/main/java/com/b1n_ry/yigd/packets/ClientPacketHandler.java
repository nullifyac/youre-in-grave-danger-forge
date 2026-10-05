package com.b1n_ry.yigd.packets;

import com.b1n_ry.yigd.client.gui.GraveOverviewScreen;
import com.b1n_ry.yigd.client.gui.GraveSelectionScreen;
import com.b1n_ry.yigd.client.gui.PlayerSelectionScreen;
import com.b1n_ry.yigd.client.render.GraveBlockEntityRenderer;
import com.b1n_ry.yigd.components.GraveComponent;
import com.b1n_ry.yigd.config.YigdConfig;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;

import java.util.UUID;

public class ClientPacketHandler {
    private ClientPacketHandler() { }

    public static void handleGraveOverview(YigdNetwork.GraveOverviewPacket msg) {
        GraveComponent component = GraveComponent.fromNbt(msg.graveNbt(), null);
        if (component == null) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return;
        }

        Screen parent = client.screen;
        client.setScreen(new GraveOverviewScreen(component, parent,
                msg.canRestore(), msg.canRob(), msg.canDelete(), msg.canUnlock(),
                msg.obtainableKeys(), msg.obtainableCompass()));
    }

    public static void handleGraveSelection(YigdNetwork.GraveSelectionPacket msg) {
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return;
        }

        Screen parent = client.screen;
        client.setScreen(new GraveSelectionScreen(msg.graves(), msg.profile(), parent));
    }

    public static void handlePlayerSelection(YigdNetwork.PlayerSelectionPacket msg) {
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return;
        }

        Screen parent = client.screen;
        client.setScreen(new PlayerSelectionScreen(msg.players(), parent));
    }

    public static void handleConfigSync(YigdNetwork.ConfigSyncPacket msg) {
        YigdConfig.getConfig().graveConfig.retrieveMethods.onBreak = msg.breakableGraves();
        GraveBlockEntityRenderer.syncedGlowing = msg.glowingGraves();
        GraveBlockEntityRenderer.syncedGlowingMaxDistance = msg.glowingDistance();
        GraveBlockEntityRenderer.syncedDeathSightDistance = msg.deathSightRange();
    }

    public static void sendRestoreGraveRequestPacket(UUID graveId, boolean itemsInGrave, boolean itemsDeleted, boolean itemsKept, boolean itemsDropped) {
        YigdNetwork.sendToServer(new YigdNetwork.GraveRestoreRequestPacket(graveId, itemsInGrave, itemsDeleted, itemsKept, itemsDropped));
    }

    public static void sendRobGraveRequestPacket(UUID graveId, boolean itemsInGrave, boolean itemsDeleted, boolean itemsKept, boolean itemsDropped) {
        YigdNetwork.sendToServer(new YigdNetwork.GraveRobRequestPacket(graveId, itemsInGrave, itemsDeleted, itemsKept, itemsDropped));
    }

    public static void sendDeleteGraveRequestPacket(UUID graveId) {
        YigdNetwork.sendToServer(new YigdNetwork.GraveDeleteRequestPacket(graveId));
    }

    public static void sendGraveLockRequestPacket(UUID graveId, boolean locked) {
        YigdNetwork.sendToServer(new YigdNetwork.GraveLockRequestPacket(graveId, locked));
    }

    public static void sendObtainKeysRequestPacket(UUID graveId) {
        YigdNetwork.sendToServer(new YigdNetwork.ObtainKeyRequestPacket(graveId));
    }

    public static void sendObtainCompassRequestPacket(UUID graveId) {
        YigdNetwork.sendToServer(new YigdNetwork.ObtainCompassRequestPacket(graveId));
    }

    public static void sendGraveOverviewRequest(UUID graveId) {
        YigdNetwork.sendToServer(new YigdNetwork.GraveOverviewRequestPacket(graveId));
    }

    public static void sendGraveSelectionRequest(GameProfile profile) {
        YigdNetwork.sendToServer(new YigdNetwork.GraveSelectRequestPacket(profile));
    }

    public static void sendConfigUpdate(YigdConfig config) {
        YigdNetwork.sendToServer(new YigdNetwork.ConfigUpdatePacket(
                config.graveConfig.claimPriority,
                config.graveConfig.graveRobbing.robPriority));
    }
}
