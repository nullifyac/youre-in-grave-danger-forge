package com.b1n_ry.yigd.block.entity;

import com.b1n_ry.yigd.util.TextCompat;
import com.b1n_ry.yigd.Yigd;
import com.b1n_ry.yigd.components.GraveComponent;
import com.b1n_ry.yigd.config.GraveConfig;
import com.b1n_ry.yigd.config.YigdConfig;
import com.b1n_ry.yigd.data.DeathInfoManager;
import com.b1n_ry.yigd.data.GraveStatus;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

public class GraveBlockEntity extends BlockEntity {
    @Nullable
    private GraveComponent component;
    @Nullable
    private UUID graveId;
    @Nullable
    private GameProfile graveSkull;
    @Nullable
    private Component graveText;
    @Nullable
    private BlockState previousState;

    private boolean claimed = true;

    private static YigdConfig cachedConfig = YigdConfig.getConfig();

    public GraveBlockEntity(BlockPos pos, BlockState state) {
        super(Yigd.GRAVE_BLOCK_ENTITY.get(), pos, state);
    }

    public void setComponent(GraveComponent component) {
        this.component = component;
        this.setClaimed(component.getStatus() == GraveStatus.CLAIMED);
        this.graveSkull = component.getOwner();
        this.graveId = component.getGraveId();
        if (this.graveSkull != null) {
            this.graveText = Component.nullToEmpty(this.graveSkull.getName());
        }
        this.setChanged();
    }

    public void setPreviousState(@Nullable BlockState previousState) {
        this.previousState = previousState;
    }

    public void setGraveText(@Nullable Component text) {
        this.graveText = text;
    }

    public @Nullable UUID getGraveId() {
        return this.graveId;
    }

    public @Nullable GameProfile getGraveSkull() {
        return this.graveSkull;
    }

    public void setGraveSkull(@Nullable GameProfile skull) {
        this.graveSkull = skull;
    }

    public @Nullable GraveComponent getComponent() {
        return this.component;
    }

    public @Nullable BlockState getPreviousState() {
        return this.previousState;
    }

    public boolean isUnclaimed() {
        return !this.claimed;
    }

    public void setClaimed(boolean claimed) {
        this.claimed = claimed;
    }

    public @Nullable Component getGraveText() {
        return this.graveText;
    }

    public void onBroken() {
        if (this.level == null || this.level.isClientSide || this.graveId == null) {
            return;
        }

        UUID removedGraveId = this.graveId;
        Yigd.END_OF_TICK.add(() -> {
            Optional<GraveComponent> component = DeathInfoManager.INSTANCE.getGrave(removedGraveId);
            component.ifPresent(grave -> {
                if (grave.getStatus() != GraveStatus.UNCLAIMED) {
                    return;
                }
                ServerLevel graveWorld = grave.getWorld();
                if (graveWorld == null || !graveWorld.hasChunkAt(grave.getPos())) {
                    return;
                }
                // Mining may restore this grave, or another mod may have relocated it during this tick.
                if (graveWorld.getBlockEntity(grave.getPos()) instanceof GraveBlockEntity current
                        && removedGraveId.equals(current.getGraveId())) {
                    return;
                }
                grave.onDestroyed();
            });
        });
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        this.writeSyncedData(tag);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        this.writeSyncedData(tag);
        if (this.graveId != null) {
            tag.putUUID("graveId", this.graveId);
        }
        if (this.previousState != null) {
            tag.put("previousState", NbtUtils.writeBlockState(this.previousState));
        }
    }

    private void writeSyncedData(CompoundTag tag) {
        tag.putBoolean("claimed", this.claimed);
        if (this.graveText != null) {
            tag.putString("text", Component.Serializer.toJson(this.graveText));
        }
        if (this.graveSkull != null) {
            tag.put("skull", NbtUtils.writeGameProfile(new CompoundTag(), this.graveSkull));
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("skull", Tag.TAG_COMPOUND)) {
            this.graveSkull = NbtUtils.readGameProfile(tag.getCompound("skull"));
        }
        if (tag.contains("text")) {
            this.graveText = Component.Serializer.fromJson(tag.getString("text"));
        }
        this.claimed = tag.getBoolean("claimed");

        if (tag.contains("graveId")) {
            this.graveId = tag.getUUID("graveId");
            if (this.component == null && this.level != null && !this.level.isClientSide) {
                DeathInfoManager.INSTANCE.getGrave(this.graveId).ifPresent(this::setComponent);
            }
        }

        if (tag.contains("previousState", Tag.TAG_COMPOUND)) {
            this.previousState = NbtUtils.readBlockState(tag.getCompound("previousState"));
        }
    }

    public static void tick(Level world, BlockPos pos, BlockState ignoredState, GraveBlockEntity be) {
        if (world.isClientSide) {
            return;
        }

        if (be.component == null) {
            if (be.graveId == null) {
                return;
            }
            DeathInfoManager.INSTANCE.getGrave(be.graveId).ifPresent(be::setComponent);
            if (be.component == null) {
                return;
            }
        }

        if (world.getGameTime() % 2400 == 0) {
            cachedConfig = YigdConfig.getConfig();
        }

        GraveConfig.GraveTimeout timeoutConfig = cachedConfig.graveConfig.graveTimeout;

        if (!pos.equals(be.component.getPos()) || !be.component.getWorldResourceKey().equals(world.dimension())) {
            be.updatePosition((ServerLevel) world, pos);
        }

        if (!timeoutConfig.enabled || be.component.getStatus() != GraveStatus.UNCLAIMED) {
            return;
        }

        long timePassed = world.getGameTime() - be.component.getCreationTime().getTime();
        int ticksPerSecond = 20;
        if (timeoutConfig.timeUnit.toSeconds(timeoutConfig.afterTime) * ticksPerSecond <= timePassed) {
            be.component.setStatus(GraveStatus.DESTROYED);

            BlockState newState = Blocks.AIR.defaultBlockState();
            BlockState previousState = be.getPreviousState();
            if (YigdConfig.getConfig().graveConfig.replaceOldWhenClaimed && previousState != null) {
                newState = previousState;
            }
            be.component.replaceWithOld(newState, false);

            if (timeoutConfig.dropContentsOnTimeout) {
                be.component.dropAllGraveItems();
            }
        }
    }

    private void updatePosition(ServerLevel world, BlockPos pos) {
        if (this.component == null) {
            return;
        }

        this.component.setPos(pos);
        this.component.setWorld(world);
        if (this.component.getStatus() == GraveStatus.DESTROYED || !this.claimed) {
            this.component.setStatus(GraveStatus.UNCLAIMED);
            PlayerList playerManager = world.getServer().getPlayerList();
            GameProfile owner = this.component.getOwner();
            ServerPlayer player = owner.getId() != null ? playerManager.getPlayer(owner.getId()) : playerManager.getPlayerByName(owner.getName());
            if (player != null) {
                Yigd.LOGGER.info("Grave belonging to {} resurfaced at X: {} / Y: {} / Z: {} / {}",
                        this.component.getOwner().getName(), this.worldPosition.getX(), this.worldPosition.getY(), this.worldPosition.getZ(),
                        this.component.getWorldResourceKey().location());
                player.displayClientMessage(TextCompat.translatable("text.yigd.message.grave_relocated", pos.getX(), pos.getY(), pos.getZ(),
                        world.dimension().location().toString()), false);
            }
        }
    }
}
