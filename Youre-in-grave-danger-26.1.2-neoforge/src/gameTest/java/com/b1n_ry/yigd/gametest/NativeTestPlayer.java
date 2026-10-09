package com.b1n_ry.yigd.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelData;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** A genuine ServerPlayer and native packet listener, with an in-memory transport and no network port. */
public final class NativeTestPlayer implements AutoCloseable {
    private static final Set<NativeTestPlayer> ACTIVE = Collections.newSetFromMap(new IdentityHashMap<>());
    private ServerPlayer player;
    private final RecordingConnection listener;
    private final EmbeddedChannel channel;
    private boolean closed;

    private NativeTestPlayer(GameTestHelper helper, GameProfile profile) {
        MinecraftServer server = helper.getLevel().getServer();
        if (!(server instanceof GameTestServer)) {
            throw new IllegalStateException("Native test players are restricted to the isolated GameTest server");
        }
        player = new ServerPlayer(server, helper.getLevel(), profile, ClientInformation.createDefault());
        Connection transport = new Connection(PacketFlow.SERVERBOUND);
        channel = new EmbeddedChannel(transport);
        listener = new RecordingConnection(server, transport, player);
        player.connection = listener;
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        player.setInvulnerable(false);
        player.getAbilities().invulnerable = false;
        player.getAbilities().instabuild = false;
        player.setNoGravity(true);
        BlockPos position = helper.absolutePos(new BlockPos(2, 2, 2));
        player.setPos(position.getX() + 0.5, position.getY(), position.getZ() + 0.5);
        player.setRespawnPosition(new ServerPlayer.RespawnConfig(
                LevelData.RespawnData.of(helper.getLevel().dimension(), position, 0, 0), true), false);
        player.setHealth(player.getMaxHealth());
        try {
            indexPlayers(server.getPlayerList()).add(player);
            if (indexUuids(server.getPlayerList()).putIfAbsent(profile.id(), player) != null) {
                throw new IllegalStateException("A native test profile collided with an existing player UUID");
            }
            helper.getLevel().addNewPlayer(player);
            listener.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
            if (!listener.hasClientLoaded()) throw new IllegalStateException("Native player-ready packet was not accepted");
            ACTIVE.add(this);
        } catch (Throwable failure) {
            close();
            throw YigdGameTestsMod.propagate(failure);
        }
    }

    public static NativeTestPlayer create(GameTestHelper helper, GameProfile profile) {
        return new NativeTestPlayer(helper, profile);
    }

    public ServerPlayer player() {
        return player;
    }

    public List<Packet<?>> packets() {
        return List.copyOf(listener.packets);
    }

    /** Uses the vanilla respawn path, including native player clone and respawn events. */
    public ServerPlayer respawn() {
        if (!player.isDeadOrDying()) throw new IllegalStateException("Cannot respawn a living native test player");
        player = player.level().getServer().getPlayerList().respawn(player, false, Entity.RemovalReason.KILLED);
        listener.player = player;
        player.setNoGravity(true);
        listener.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        return player;
    }

    static void closeAll(MinecraftServer server) {
        RuntimeException failure = null;
        for (NativeTestPlayer fixture : List.copyOf(ACTIVE)) {
            if (fixture.player.level().getServer() != server) continue;
            try {
                fixture.close();
            } catch (RuntimeException exception) {
                if (failure == null) failure = exception;
                else failure.addSuppressed(exception);
            }
        }
        if (failure != null) throw failure;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        ACTIVE.remove(this);
        try {
            PlayerList playerList = player.level().getServer().getPlayerList();
            indexPlayers(playerList).remove(player);
            indexUuids(playerList).remove(player.getUUID(), player);
            player.level().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
            player.getAdvancements().stopListening();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Native test-player cleanup failed", exception);
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @SuppressWarnings("unchecked")
    private static List<ServerPlayer> indexPlayers(PlayerList playerList) throws ReflectiveOperationException {
        Field field = PlayerList.class.getDeclaredField("players");
        field.setAccessible(true);
        return (List<ServerPlayer>) field.get(playerList);
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, ServerPlayer> indexUuids(PlayerList playerList) throws ReflectiveOperationException {
        Field field = PlayerList.class.getDeclaredField("playersByUUID");
        field.setAccessible(true);
        return (Map<UUID, ServerPlayer>) field.get(playerList);
    }

    private static final class RecordingConnection extends ServerGamePacketListenerImpl {
        private final List<Packet<?>> packets = new ArrayList<>();

        RecordingConnection(MinecraftServer server, Connection transport, ServerPlayer player) {
            super(server, transport, player, CommonListenerCookie.createInitial(player.getGameProfile(), false));
        }

        @Override public void tick() { }
        @Override public void onDisconnect(DisconnectionDetails details) { }
        @Override public void send(Packet<?> packet) { packets.add(packet); }
        @Override public void send(Packet<?> packet, ChannelFutureListener listener) { packets.add(packet); }
    }
}
