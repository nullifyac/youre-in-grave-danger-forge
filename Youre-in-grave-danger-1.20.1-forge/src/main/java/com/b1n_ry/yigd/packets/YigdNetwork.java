package com.b1n_ry.yigd.packets;

import com.b1n_ry.yigd.Yigd;
import com.b1n_ry.yigd.config.ClaimPriority;
import com.mojang.authlib.GameProfile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Central network channel for all YIGD packets.
 */
public final class YigdNetwork {
    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(Yigd.MOD_ID, "main"))
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .clientAcceptedVersions(PROTOCOL_VERSION::equals)
            .serverAcceptedVersions(PROTOCOL_VERSION::equals)
            .simpleChannel();

    private static int packetId = 0;

    private YigdNetwork() { }

    private static int nextId() {
        return packetId++;
    }

    public static void register() {
        registerServerboundPackets();
        registerClientboundPackets();
    }

    private static void registerServerboundPackets() {
        CHANNEL.messageBuilder(GraveRestoreRequestPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(GraveRestoreRequestPacket::encode)
                .decoder(GraveRestoreRequestPacket::decode)
                .consumerMainThread(GraveRestoreRequestPacket::handle)
                .add();

        CHANNEL.messageBuilder(GraveRobRequestPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(GraveRobRequestPacket::encode)
                .decoder(GraveRobRequestPacket::decode)
                .consumerMainThread(GraveRobRequestPacket::handle)
                .add();

        CHANNEL.messageBuilder(GraveDeleteRequestPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(GraveDeleteRequestPacket::encode)
                .decoder(GraveDeleteRequestPacket::decode)
                .consumerMainThread(GraveDeleteRequestPacket::handle)
                .add();

        CHANNEL.messageBuilder(GraveLockRequestPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(GraveLockRequestPacket::encode)
                .decoder(GraveLockRequestPacket::decode)
                .consumerMainThread(GraveLockRequestPacket::handle)
                .add();

        CHANNEL.messageBuilder(ObtainKeyRequestPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(ObtainKeyRequestPacket::encode)
                .decoder(ObtainKeyRequestPacket::decode)
                .consumerMainThread(ObtainKeyRequestPacket::handle)
                .add();

        CHANNEL.messageBuilder(ObtainCompassRequestPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(ObtainCompassRequestPacket::encode)
                .decoder(ObtainCompassRequestPacket::decode)
                .consumerMainThread(ObtainCompassRequestPacket::handle)
                .add();

        CHANNEL.messageBuilder(GraveOverviewRequestPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(GraveOverviewRequestPacket::encode)
                .decoder(GraveOverviewRequestPacket::decode)
                .consumerMainThread(GraveOverviewRequestPacket::handle)
                .add();

        CHANNEL.messageBuilder(GraveSelectRequestPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(GraveSelectRequestPacket::encode)
                .decoder(GraveSelectRequestPacket::decode)
                .consumerMainThread(GraveSelectRequestPacket::handle)
                .add();

        CHANNEL.messageBuilder(ConfigUpdatePacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(ConfigUpdatePacket::encode)
                .decoder(ConfigUpdatePacket::decode)
                .consumerMainThread(ConfigUpdatePacket::handle)
                .add();
    }

    private static void registerClientboundPackets() {
        CHANNEL.messageBuilder(GraveOverviewPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
                .encoder(GraveOverviewPacket::encode)
                .decoder(GraveOverviewPacket::decode)
                .consumerMainThread(GraveOverviewPacket::handle)
                .add();

        CHANNEL.messageBuilder(GraveSelectionPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
                .encoder(GraveSelectionPacket::encode)
                .decoder(GraveSelectionPacket::decode)
                .consumerMainThread(GraveSelectionPacket::handle)
                .add();

        CHANNEL.messageBuilder(PlayerSelectionPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PlayerSelectionPacket::encode)
                .decoder(PlayerSelectionPacket::decode)
                .consumerMainThread(PlayerSelectionPacket::handle)
                .add();

        CHANNEL.messageBuilder(ConfigSyncPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ConfigSyncPacket::encode)
                .decoder(ConfigSyncPacket::decode)
                .consumerMainThread(ConfigSyncPacket::handle)
                .add();
    }

    public static void sendToPlayer(ServerPlayer player, Object message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    public static void sendToServer(Object message) {
        CHANNEL.sendToServer(message);
    }

    private static CompoundTag writeProfile(GameProfile profile) {
        CompoundTag tag = new CompoundTag();
        NbtUtils.writeGameProfile(tag, profile);
        return tag;
    }

    private static GameProfile readProfile(FriendlyByteBuf buf) {
        CompoundTag tag = Objects.requireNonNull(buf.readNbt());
        return NbtUtils.readGameProfile(tag);
    }

    // === Serverbound packets ===

    public record GraveRestoreRequestPacket(UUID graveId, boolean itemsInGrave, boolean itemsDeleted,
                                            boolean itemsKept, boolean itemsDropped) {
        public static void encode(GraveRestoreRequestPacket msg, FriendlyByteBuf buf) {
            buf.writeUUID(msg.graveId);
            buf.writeBoolean(msg.itemsInGrave);
            buf.writeBoolean(msg.itemsDeleted);
            buf.writeBoolean(msg.itemsKept);
            buf.writeBoolean(msg.itemsDropped);
        }

        public static GraveRestoreRequestPacket decode(FriendlyByteBuf buf) {
            return new GraveRestoreRequestPacket(buf.readUUID(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean());
        }

        public static void handle(GraveRestoreRequestPacket msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player != null) {
                    ServerPacketHandler.handleGraveRestoreRequest(player, msg);
                }
            });
            context.setPacketHandled(true);
        }
    }

    public record GraveRobRequestPacket(UUID graveId, boolean itemsInGrave, boolean itemsDeleted,
                                        boolean itemsKept, boolean itemsDropped) {
        public static void encode(GraveRobRequestPacket msg, FriendlyByteBuf buf) {
            buf.writeUUID(msg.graveId);
            buf.writeBoolean(msg.itemsInGrave);
            buf.writeBoolean(msg.itemsDeleted);
            buf.writeBoolean(msg.itemsKept);
            buf.writeBoolean(msg.itemsDropped);
        }

        public static GraveRobRequestPacket decode(FriendlyByteBuf buf) {
            return new GraveRobRequestPacket(buf.readUUID(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean());
        }

        public static void handle(GraveRobRequestPacket msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player != null) {
                    ServerPacketHandler.handleGraveRobRequest(player, msg);
                }
            });
            context.setPacketHandled(true);
        }
    }

    public record GraveDeleteRequestPacket(UUID graveId) {
        public static void encode(GraveDeleteRequestPacket msg, FriendlyByteBuf buf) {
            buf.writeUUID(msg.graveId);
        }

        public static GraveDeleteRequestPacket decode(FriendlyByteBuf buf) {
            return new GraveDeleteRequestPacket(buf.readUUID());
        }

        public static void handle(GraveDeleteRequestPacket msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player != null) {
                    ServerPacketHandler.handleGraveDeleteRequest(player, msg);
                }
            });
            context.setPacketHandled(true);
        }
    }

    public record GraveLockRequestPacket(UUID graveId, boolean locked) {
        public static void encode(GraveLockRequestPacket msg, FriendlyByteBuf buf) {
            buf.writeUUID(msg.graveId);
            buf.writeBoolean(msg.locked);
        }

        public static GraveLockRequestPacket decode(FriendlyByteBuf buf) {
            return new GraveLockRequestPacket(buf.readUUID(), buf.readBoolean());
        }

        public static void handle(GraveLockRequestPacket msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player != null) {
                    ServerPacketHandler.handleGraveLockRequest(player, msg);
                }
            });
            context.setPacketHandled(true);
        }
    }

    public record ObtainKeyRequestPacket(UUID graveId) {
        public static void encode(ObtainKeyRequestPacket msg, FriendlyByteBuf buf) {
            buf.writeUUID(msg.graveId);
        }

        public static ObtainKeyRequestPacket decode(FriendlyByteBuf buf) {
            return new ObtainKeyRequestPacket(buf.readUUID());
        }

        public static void handle(ObtainKeyRequestPacket msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player != null) {
                    ServerPacketHandler.handleObtainKeyRequest(player, msg);
                }
            });
            context.setPacketHandled(true);
        }
    }

    public record ObtainCompassRequestPacket(UUID graveId) {
        public static void encode(ObtainCompassRequestPacket msg, FriendlyByteBuf buf) {
            buf.writeUUID(msg.graveId);
        }

        public static ObtainCompassRequestPacket decode(FriendlyByteBuf buf) {
            return new ObtainCompassRequestPacket(buf.readUUID());
        }

        public static void handle(ObtainCompassRequestPacket msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player != null) {
                    ServerPacketHandler.handleObtainCompassRequest(player, msg);
                }
            });
            context.setPacketHandled(true);
        }
    }

    public record GraveOverviewRequestPacket(UUID graveId) {
        public static void encode(GraveOverviewRequestPacket msg, FriendlyByteBuf buf) {
            buf.writeUUID(msg.graveId);
        }

        public static GraveOverviewRequestPacket decode(FriendlyByteBuf buf) {
            return new GraveOverviewRequestPacket(buf.readUUID());
        }

        public static void handle(GraveOverviewRequestPacket msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player != null) {
                    ServerPacketHandler.handleGraveOverviewRequest(player, msg);
                }
            });
            context.setPacketHandled(true);
        }
    }

    public record GraveSelectRequestPacket(GameProfile profile) {
        public static void encode(GraveSelectRequestPacket msg, FriendlyByteBuf buf) {
            buf.writeNbt(writeProfile(msg.profile));
        }

        public static GraveSelectRequestPacket decode(FriendlyByteBuf buf) {
            return new GraveSelectRequestPacket(readProfile(buf));
        }

        public static void handle(GraveSelectRequestPacket msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player != null) {
                    ServerPacketHandler.handleGraveSelectRequest(player, msg);
                }
            });
            context.setPacketHandled(true);
        }
    }

    public record ConfigUpdatePacket(ClaimPriority claimPriority, ClaimPriority robPriority) {
        public static void encode(ConfigUpdatePacket msg, FriendlyByteBuf buf) {
            buf.writeEnum(msg.claimPriority);
            buf.writeEnum(msg.robPriority);
        }

        public static ConfigUpdatePacket decode(FriendlyByteBuf buf) {
            return new ConfigUpdatePacket(buf.readEnum(ClaimPriority.class), buf.readEnum(ClaimPriority.class));
        }

        public static void handle(ConfigUpdatePacket msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player != null) {
                    ServerPacketHandler.handleConfigUpdate(player, msg);
                }
            });
            context.setPacketHandled(true);
        }
    }

    // === Clientbound packets ===

    public record GraveOverviewPacket(CompoundTag graveNbt, boolean canRestore, boolean canRob,
                                      boolean canDelete, boolean canUnlock,
                                      boolean obtainableKeys, boolean obtainableCompass) {
        public static void encode(GraveOverviewPacket msg, FriendlyByteBuf buf) {
            buf.writeNbt(msg.graveNbt);
            buf.writeBoolean(msg.canRestore);
            buf.writeBoolean(msg.canRob);
            buf.writeBoolean(msg.canDelete);
            buf.writeBoolean(msg.canUnlock);
            buf.writeBoolean(msg.obtainableKeys);
            buf.writeBoolean(msg.obtainableCompass);
        }

        public static GraveOverviewPacket decode(FriendlyByteBuf buf) {
            CompoundTag nbt = Objects.requireNonNull(buf.readNbt());
            boolean canRestore = buf.readBoolean();
            boolean canRob = buf.readBoolean();
            boolean canDelete = buf.readBoolean();
            boolean canUnlock = buf.readBoolean();
            boolean keys = buf.readBoolean();
            boolean compass = buf.readBoolean();
            return new GraveOverviewPacket(nbt, canRestore, canRob, canDelete, canUnlock, keys, compass);
        }

        public static void handle(GraveOverviewPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> ClientPacketHandler.handleGraveOverview(msg));
            ctx.get().setPacketHandled(true);
        }
    }

    public record GraveSelectionPacket(List<LightGraveData> graves, GameProfile profile) {
        public static void encode(GraveSelectionPacket msg, FriendlyByteBuf buf) {
            buf.writeInt(msg.graves.size());
            for (LightGraveData data : msg.graves) {
                buf.writeNbt(data.toNbt());
            }
            buf.writeNbt(writeProfile(msg.profile));
        }

        public static GraveSelectionPacket decode(FriendlyByteBuf buf) {
            int size = buf.readInt();
            List<LightGraveData> graves = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                CompoundTag tag = Objects.requireNonNull(buf.readNbt());
                graves.add(LightGraveData.fromNbt(tag));
            }
            GameProfile profile = readProfile(buf);
            return new GraveSelectionPacket(graves, profile);
        }

        public static void handle(GraveSelectionPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> ClientPacketHandler.handleGraveSelection(msg));
            ctx.get().setPacketHandled(true);
        }
    }

    public record PlayerSelectionPacket(List<LightPlayerData> players) {
        public static void encode(PlayerSelectionPacket msg, FriendlyByteBuf buf) {
            buf.writeInt(msg.players.size());
            for (LightPlayerData data : msg.players) {
                buf.writeNbt(data.toNbt());
            }
        }

        public static PlayerSelectionPacket decode(FriendlyByteBuf buf) {
            int size = buf.readInt();
            List<LightPlayerData> players = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                CompoundTag tag = Objects.requireNonNull(buf.readNbt());
                players.add(LightPlayerData.fromNbt(tag));
            }
            return new PlayerSelectionPacket(players);
        }

        public static void handle(PlayerSelectionPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> ClientPacketHandler.handlePlayerSelection(msg));
            ctx.get().setPacketHandled(true);
        }
    }

    public record ConfigSyncPacket(boolean breakableGraves, boolean glowingGraves,
                                   int glowingDistance, double deathSightRange) {
        public static void encode(ConfigSyncPacket msg, FriendlyByteBuf buf) {
            buf.writeBoolean(msg.breakableGraves);
            buf.writeBoolean(msg.glowingGraves);
            buf.writeInt(msg.glowingDistance);
            buf.writeDouble(msg.deathSightRange);
        }

        public static ConfigSyncPacket decode(FriendlyByteBuf buf) {
            boolean breakable = buf.readBoolean();
            boolean glowing = buf.readBoolean();
            int range = buf.readInt();
            double sight = buf.readDouble();
            return new ConfigSyncPacket(breakable, glowing, range, sight);
        }

        public static void handle(ConfigSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> ClientPacketHandler.handleConfigSync(msg));
            ctx.get().setPacketHandled(true);
        }
    }
}
