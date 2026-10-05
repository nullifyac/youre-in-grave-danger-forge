package com.b1n_ry.yigd.packets;

import com.b1n_ry.yigd.Yigd;
import com.b1n_ry.yigd.config.ClaimPriority;
import com.mojang.authlib.GameProfile;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.fml.network.NetworkDirection;
import net.minecraftforge.fml.network.NetworkEvent;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.PacketDistributor;
import net.minecraftforge.fml.network.simple.SimpleChannel;

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
                .consumer(GraveRestoreRequestPacket::handle)
                .add();

        CHANNEL.messageBuilder(GraveRobRequestPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(GraveRobRequestPacket::encode)
                .decoder(GraveRobRequestPacket::decode)
                .consumer(GraveRobRequestPacket::handle)
                .add();

        CHANNEL.messageBuilder(GraveDeleteRequestPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(GraveDeleteRequestPacket::encode)
                .decoder(GraveDeleteRequestPacket::decode)
                .consumer(GraveDeleteRequestPacket::handle)
                .add();

        CHANNEL.messageBuilder(GraveLockRequestPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(GraveLockRequestPacket::encode)
                .decoder(GraveLockRequestPacket::decode)
                .consumer(GraveLockRequestPacket::handle)
                .add();

        CHANNEL.messageBuilder(ObtainKeyRequestPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(ObtainKeyRequestPacket::encode)
                .decoder(ObtainKeyRequestPacket::decode)
                .consumer(ObtainKeyRequestPacket::handle)
                .add();

        CHANNEL.messageBuilder(ObtainCompassRequestPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(ObtainCompassRequestPacket::encode)
                .decoder(ObtainCompassRequestPacket::decode)
                .consumer(ObtainCompassRequestPacket::handle)
                .add();

        CHANNEL.messageBuilder(GraveOverviewRequestPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(GraveOverviewRequestPacket::encode)
                .decoder(GraveOverviewRequestPacket::decode)
                .consumer(GraveOverviewRequestPacket::handle)
                .add();

        CHANNEL.messageBuilder(GraveSelectRequestPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(GraveSelectRequestPacket::encode)
                .decoder(GraveSelectRequestPacket::decode)
                .consumer(GraveSelectRequestPacket::handle)
                .add();

        CHANNEL.messageBuilder(ConfigUpdatePacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(ConfigUpdatePacket::encode)
                .decoder(ConfigUpdatePacket::decode)
                .consumer(ConfigUpdatePacket::handle)
                .add();
    }

    private static void registerClientboundPackets() {
        CHANNEL.messageBuilder(GraveOverviewPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
                .encoder(GraveOverviewPacket::encode)
                .decoder(GraveOverviewPacket::decode)
                .consumer(GraveOverviewPacket::handle)
                .add();

        CHANNEL.messageBuilder(GraveSelectionPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
                .encoder(GraveSelectionPacket::encode)
                .decoder(GraveSelectionPacket::decode)
                .consumer(GraveSelectionPacket::handle)
                .add();

        CHANNEL.messageBuilder(PlayerSelectionPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PlayerSelectionPacket::encode)
                .decoder(PlayerSelectionPacket::decode)
                .consumer(PlayerSelectionPacket::handle)
                .add();

        CHANNEL.messageBuilder(ConfigSyncPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ConfigSyncPacket::encode)
                .decoder(ConfigSyncPacket::decode)
                .consumer(ConfigSyncPacket::handle)
                .add();
    }

    public static void sendToPlayer(ServerPlayerEntity player, Object message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    public static void sendToServer(Object message) {
        CHANNEL.sendToServer(message);
    }

    private static CompoundNBT writeProfile(GameProfile profile) {
        CompoundNBT tag = new CompoundNBT();
        NBTUtil.writeGameProfile(tag, profile);
        return tag;
    }

    private static GameProfile readProfile(PacketBuffer buf) {
        CompoundNBT tag = Objects.requireNonNull(buf.readNbt());
        return NBTUtil.readGameProfile(tag);
    }

    // === Serverbound packets ===

    public static class GraveRestoreRequestPacket {
        private final UUID graveId;
        private final boolean itemsInGrave;
        private final boolean itemsDeleted;
        private final boolean itemsKept;
        private final boolean itemsDropped;

        public GraveRestoreRequestPacket(UUID graveId, boolean itemsInGrave, boolean itemsDeleted,
                                         boolean itemsKept, boolean itemsDropped) {
            this.graveId = graveId;
            this.itemsInGrave = itemsInGrave;
            this.itemsDeleted = itemsDeleted;
            this.itemsKept = itemsKept;
            this.itemsDropped = itemsDropped;
        }

        public UUID graveId() {
            return this.graveId;
        }

        public boolean itemsInGrave() {
            return this.itemsInGrave;
        }

        public boolean itemsDeleted() {
            return this.itemsDeleted;
        }

        public boolean itemsKept() {
            return this.itemsKept;
        }

        public boolean itemsDropped() {
            return this.itemsDropped;
        }

        public static void encode(GraveRestoreRequestPacket msg, PacketBuffer buf) {
            buf.writeUUID(msg.graveId());
            buf.writeBoolean(msg.itemsInGrave());
            buf.writeBoolean(msg.itemsDeleted());
            buf.writeBoolean(msg.itemsKept());
            buf.writeBoolean(msg.itemsDropped());
        }

        public static GraveRestoreRequestPacket decode(PacketBuffer buf) {
            return new GraveRestoreRequestPacket(buf.readUUID(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean());
        }

        public static void handle(GraveRestoreRequestPacket msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> {
                ServerPlayerEntity player = context.getSender();
                if (player != null) {
                    ServerPacketHandler.handleGraveRestoreRequest(player, msg);
                }
            });
            context.setPacketHandled(true);
        }
    }

    public static class GraveRobRequestPacket {
        private final UUID graveId;
        private final boolean itemsInGrave;
        private final boolean itemsDeleted;
        private final boolean itemsKept;
        private final boolean itemsDropped;

        public GraveRobRequestPacket(UUID graveId, boolean itemsInGrave, boolean itemsDeleted,
                                     boolean itemsKept, boolean itemsDropped) {
            this.graveId = graveId;
            this.itemsInGrave = itemsInGrave;
            this.itemsDeleted = itemsDeleted;
            this.itemsKept = itemsKept;
            this.itemsDropped = itemsDropped;
        }

        public UUID graveId() {
            return this.graveId;
        }

        public boolean itemsInGrave() {
            return this.itemsInGrave;
        }

        public boolean itemsDeleted() {
            return this.itemsDeleted;
        }

        public boolean itemsKept() {
            return this.itemsKept;
        }

        public boolean itemsDropped() {
            return this.itemsDropped;
        }

        public static void encode(GraveRobRequestPacket msg, PacketBuffer buf) {
            buf.writeUUID(msg.graveId());
            buf.writeBoolean(msg.itemsInGrave());
            buf.writeBoolean(msg.itemsDeleted());
            buf.writeBoolean(msg.itemsKept());
            buf.writeBoolean(msg.itemsDropped());
        }

        public static GraveRobRequestPacket decode(PacketBuffer buf) {
            return new GraveRobRequestPacket(buf.readUUID(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean());
        }

        public static void handle(GraveRobRequestPacket msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> {
                ServerPlayerEntity player = context.getSender();
                if (player != null) {
                    ServerPacketHandler.handleGraveRobRequest(player, msg);
                }
            });
            context.setPacketHandled(true);
        }
    }

    public static class GraveDeleteRequestPacket {
        private final UUID graveId;

        public GraveDeleteRequestPacket(UUID graveId) {
            this.graveId = graveId;
        }

        public UUID graveId() {
            return this.graveId;
        }

        public static void encode(GraveDeleteRequestPacket msg, PacketBuffer buf) {
            buf.writeUUID(msg.graveId());
        }

        public static GraveDeleteRequestPacket decode(PacketBuffer buf) {
            return new GraveDeleteRequestPacket(buf.readUUID());
        }

        public static void handle(GraveDeleteRequestPacket msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> {
                ServerPlayerEntity player = context.getSender();
                if (player != null) {
                    ServerPacketHandler.handleGraveDeleteRequest(player, msg);
                }
            });
            context.setPacketHandled(true);
        }
    }

    public static class GraveLockRequestPacket {
        private final UUID graveId;
        private final boolean locked;

        public GraveLockRequestPacket(UUID graveId, boolean locked) {
            this.graveId = graveId;
            this.locked = locked;
        }

        public UUID graveId() {
            return this.graveId;
        }

        public boolean locked() {
            return this.locked;
        }

        public static void encode(GraveLockRequestPacket msg, PacketBuffer buf) {
            buf.writeUUID(msg.graveId());
            buf.writeBoolean(msg.locked());
        }

        public static GraveLockRequestPacket decode(PacketBuffer buf) {
            return new GraveLockRequestPacket(buf.readUUID(), buf.readBoolean());
        }

        public static void handle(GraveLockRequestPacket msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> {
                ServerPlayerEntity player = context.getSender();
                if (player != null) {
                    ServerPacketHandler.handleGraveLockRequest(player, msg);
                }
            });
            context.setPacketHandled(true);
        }
    }

    public static class ObtainKeyRequestPacket {
        private final UUID graveId;

        public ObtainKeyRequestPacket(UUID graveId) {
            this.graveId = graveId;
        }

        public UUID graveId() {
            return this.graveId;
        }

        public static void encode(ObtainKeyRequestPacket msg, PacketBuffer buf) {
            buf.writeUUID(msg.graveId());
        }

        public static ObtainKeyRequestPacket decode(PacketBuffer buf) {
            return new ObtainKeyRequestPacket(buf.readUUID());
        }

        public static void handle(ObtainKeyRequestPacket msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> {
                ServerPlayerEntity player = context.getSender();
                if (player != null) {
                    ServerPacketHandler.handleObtainKeyRequest(player, msg);
                }
            });
            context.setPacketHandled(true);
        }
    }

    public static class ObtainCompassRequestPacket {
        private final UUID graveId;

        public ObtainCompassRequestPacket(UUID graveId) {
            this.graveId = graveId;
        }

        public UUID graveId() {
            return this.graveId;
        }

        public static void encode(ObtainCompassRequestPacket msg, PacketBuffer buf) {
            buf.writeUUID(msg.graveId());
        }

        public static ObtainCompassRequestPacket decode(PacketBuffer buf) {
            return new ObtainCompassRequestPacket(buf.readUUID());
        }

        public static void handle(ObtainCompassRequestPacket msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> {
                ServerPlayerEntity player = context.getSender();
                if (player != null) {
                    ServerPacketHandler.handleObtainCompassRequest(player, msg);
                }
            });
            context.setPacketHandled(true);
        }
    }

    public static class GraveOverviewRequestPacket {
        private final UUID graveId;

        public GraveOverviewRequestPacket(UUID graveId) {
            this.graveId = graveId;
        }

        public UUID graveId() {
            return this.graveId;
        }

        public static void encode(GraveOverviewRequestPacket msg, PacketBuffer buf) {
            buf.writeUUID(msg.graveId());
        }

        public static GraveOverviewRequestPacket decode(PacketBuffer buf) {
            return new GraveOverviewRequestPacket(buf.readUUID());
        }

        public static void handle(GraveOverviewRequestPacket msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> {
                ServerPlayerEntity player = context.getSender();
                if (player != null) {
                    ServerPacketHandler.handleGraveOverviewRequest(player, msg);
                }
            });
            context.setPacketHandled(true);
        }
    }

    public static class GraveSelectRequestPacket {
        private final GameProfile profile;

        public GraveSelectRequestPacket(GameProfile profile) {
            this.profile = profile;
        }

        public GameProfile profile() {
            return this.profile;
        }

        public static void encode(GraveSelectRequestPacket msg, PacketBuffer buf) {
            buf.writeNbt(writeProfile(msg.profile()));
        }

        public static GraveSelectRequestPacket decode(PacketBuffer buf) {
            return new GraveSelectRequestPacket(readProfile(buf));
        }

        public static void handle(GraveSelectRequestPacket msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> {
                ServerPlayerEntity player = context.getSender();
                if (player != null) {
                    ServerPacketHandler.handleGraveSelectRequest(player, msg);
                }
            });
            context.setPacketHandled(true);
        }
    }

    public static class ConfigUpdatePacket {
        private final ClaimPriority claimPriority;
        private final ClaimPriority robPriority;

        public ConfigUpdatePacket(ClaimPriority claimPriority, ClaimPriority robPriority) {
            this.claimPriority = claimPriority;
            this.robPriority = robPriority;
        }

        public ClaimPriority claimPriority() {
            return this.claimPriority;
        }

        public ClaimPriority robPriority() {
            return this.robPriority;
        }

        public static void encode(ConfigUpdatePacket msg, PacketBuffer buf) {
            buf.writeEnum(msg.claimPriority());
            buf.writeEnum(msg.robPriority());
        }

        public static ConfigUpdatePacket decode(PacketBuffer buf) {
            return new ConfigUpdatePacket(buf.readEnum(ClaimPriority.class), buf.readEnum(ClaimPriority.class));
        }

        public static void handle(ConfigUpdatePacket msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> {
                ServerPlayerEntity player = context.getSender();
                if (player != null) {
                    ServerPacketHandler.handleConfigUpdate(player, msg);
                }
            });
            context.setPacketHandled(true);
        }
    }

    // === Clientbound packets ===

    public static class GraveOverviewPacket {
        private final CompoundNBT graveNbt;
        private final boolean canRestore;
        private final boolean canRob;
        private final boolean canDelete;
        private final boolean canUnlock;
        private final boolean obtainableKeys;
        private final boolean obtainableCompass;

        public GraveOverviewPacket(CompoundNBT graveNbt, boolean canRestore, boolean canRob,
                                   boolean canDelete, boolean canUnlock,
                                   boolean obtainableKeys, boolean obtainableCompass) {
            this.graveNbt = graveNbt;
            this.canRestore = canRestore;
            this.canRob = canRob;
            this.canDelete = canDelete;
            this.canUnlock = canUnlock;
            this.obtainableKeys = obtainableKeys;
            this.obtainableCompass = obtainableCompass;
        }

        public CompoundNBT graveNbt() {
            return this.graveNbt;
        }

        public boolean canRestore() {
            return this.canRestore;
        }

        public boolean canRob() {
            return this.canRob;
        }

        public boolean canDelete() {
            return this.canDelete;
        }

        public boolean canUnlock() {
            return this.canUnlock;
        }

        public boolean obtainableKeys() {
            return this.obtainableKeys;
        }

        public boolean obtainableCompass() {
            return this.obtainableCompass;
        }

        public static void encode(GraveOverviewPacket msg, PacketBuffer buf) {
            buf.writeNbt(msg.graveNbt());
            buf.writeBoolean(msg.canRestore());
            buf.writeBoolean(msg.canRob());
            buf.writeBoolean(msg.canDelete());
            buf.writeBoolean(msg.canUnlock());
            buf.writeBoolean(msg.obtainableKeys());
            buf.writeBoolean(msg.obtainableCompass());
        }

        public static GraveOverviewPacket decode(PacketBuffer buf) {
            CompoundNBT nbt = Objects.requireNonNull(buf.readNbt());
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

    public static class GraveSelectionPacket {
        private final List<LightGraveData> graves;
        private final GameProfile profile;

        public GraveSelectionPacket(List<LightGraveData> graves, GameProfile profile) {
            this.graves = graves;
            this.profile = profile;
        }

        public List<LightGraveData> graves() {
            return this.graves;
        }

        public GameProfile profile() {
            return this.profile;
        }

        public static void encode(GraveSelectionPacket msg, PacketBuffer buf) {
            buf.writeInt(msg.graves().size());
            for (LightGraveData data : msg.graves()) {
                buf.writeNbt(data.toNbt());
            }
            buf.writeNbt(writeProfile(msg.profile()));
        }

        public static GraveSelectionPacket decode(PacketBuffer buf) {
            int size = buf.readInt();
            List<LightGraveData> graves = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                CompoundNBT tag = Objects.requireNonNull(buf.readNbt());
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

    public static class PlayerSelectionPacket {
        private final List<LightPlayerData> players;

        public PlayerSelectionPacket(List<LightPlayerData> players) {
            this.players = players;
        }

        public List<LightPlayerData> players() {
            return this.players;
        }

        public static void encode(PlayerSelectionPacket msg, PacketBuffer buf) {
            buf.writeInt(msg.players().size());
            for (LightPlayerData data : msg.players()) {
                buf.writeNbt(data.toNbt());
            }
        }

        public static PlayerSelectionPacket decode(PacketBuffer buf) {
            int size = buf.readInt();
            List<LightPlayerData> players = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                CompoundNBT tag = Objects.requireNonNull(buf.readNbt());
                players.add(LightPlayerData.fromNbt(tag));
            }
            return new PlayerSelectionPacket(players);
        }

        public static void handle(PlayerSelectionPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> ClientPacketHandler.handlePlayerSelection(msg));
            ctx.get().setPacketHandled(true);
        }
    }

    public static class ConfigSyncPacket {
        private final boolean breakableGraves;
        private final boolean glowingGraves;
        private final int glowingDistance;
        private final double deathSightRange;

        public ConfigSyncPacket(boolean breakableGraves, boolean glowingGraves,
                                int glowingDistance, double deathSightRange) {
            this.breakableGraves = breakableGraves;
            this.glowingGraves = glowingGraves;
            this.glowingDistance = glowingDistance;
            this.deathSightRange = deathSightRange;
        }

        public boolean breakableGraves() {
            return this.breakableGraves;
        }

        public boolean glowingGraves() {
            return this.glowingGraves;
        }

        public int glowingDistance() {
            return this.glowingDistance;
        }

        public double deathSightRange() {
            return this.deathSightRange;
        }

        public static void encode(ConfigSyncPacket msg, PacketBuffer buf) {
            buf.writeBoolean(msg.breakableGraves());
            buf.writeBoolean(msg.glowingGraves());
            buf.writeInt(msg.glowingDistance());
            buf.writeDouble(msg.deathSightRange());
        }

        public static ConfigSyncPacket decode(PacketBuffer buf) {
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
