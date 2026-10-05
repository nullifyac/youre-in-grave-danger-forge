package com.b1n_ry.yigd.block.entity;

import com.b1n_ry.yigd.Yigd;
import com.b1n_ry.yigd.components.GraveComponent;
import com.b1n_ry.yigd.config.GraveConfig;
import com.b1n_ry.yigd.config.YigdConfig;
import com.b1n_ry.yigd.data.DeathInfoManager;
import com.b1n_ry.yigd.data.GraveStatus;
import com.mojang.authlib.GameProfile;
import net.minecraft.util.math.BlockPos;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.nbt.INBT;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.network.play.server.SUpdateTileEntityPacket;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.management.PlayerList;
import net.minecraft.world.World;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.block.BlockState;
import net.minecraft.util.Util;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.common.util.Constants;
import javax.annotation.Nullable;

import java.util.Optional;
import java.util.UUID;

public class GraveBlockEntity extends TileEntity implements ITickableTileEntity {
    @Nullable
    private GraveComponent component;
    @Nullable
    private UUID graveId;
    @Nullable
    private GameProfile graveSkull;
    @Nullable
    private ITextComponent graveText;
    @Nullable
    private BlockState previousState;

    private boolean claimed = true;

    private static YigdConfig cachedConfig = YigdConfig.getConfig();

    public GraveBlockEntity() {
        super(Yigd.GRAVE_BLOCK_ENTITY.get());
    }

    public void setComponent(GraveComponent component) {
        this.component = component;
        this.setClaimed(component.getStatus() == GraveStatus.CLAIMED);
        this.graveSkull = component.getOwner();
        this.graveId = component.getGraveId();
        if (this.graveSkull != null) {
            this.graveText = ITextComponent.nullToEmpty(this.graveSkull.getName());
        }
        this.setChanged();
    }

    public void setPreviousState(@Nullable BlockState previousState) {
        this.previousState = previousState;
    }

    public void setGraveText(@Nullable ITextComponent text) {
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

    public @Nullable ITextComponent getGraveText() {
        return this.graveText;
    }

    public void onBroken() {
        if (this.level == null || this.level.isClientSide) {
            return;
        }

        Yigd.END_OF_TICK.add(() -> {
            Optional<GraveComponent> component = DeathInfoManager.INSTANCE.getGrave(this.graveId);
            component.ifPresent(grave -> {
                if (grave.getStatus() == GraveStatus.UNCLAIMED) {
                    grave.onDestroyed();
                }
            });
        });
    }

    @Override
    public CompoundNBT getUpdateTag() {
        CompoundNBT tag = super.getUpdateTag();
        this.writeSyncedData(tag);
        return tag;
    }

    @Override
    public CompoundNBT save(CompoundNBT tag) {
        super.save(tag);
        this.writeSyncedData(tag);
        if (this.graveId != null) {
            tag.putUUID("graveId", this.graveId);
        }
        if (this.previousState != null) {
            tag.put("previousState", NBTUtil.writeBlockState(this.previousState));
        }
        return tag;
    }

    private void writeSyncedData(CompoundNBT tag) {
        tag.putBoolean("claimed", this.claimed);
        if (this.graveText != null) {
            tag.putString("text", ITextComponent.Serializer.toJson(this.graveText));
        }
        if (this.graveSkull != null) {
            tag.put("skull", NBTUtil.writeGameProfile(new CompoundNBT(), this.graveSkull));
        }
    }

    @Nullable
    @Override
    public SUpdateTileEntityPacket getUpdatePacket() {
        return new SUpdateTileEntityPacket(this.worldPosition, 1, this.getUpdateTag());
    }

    @Override
    public void load(BlockState state, CompoundNBT tag) {
        super.load(state, tag);
        if (tag.contains("skull", Constants.NBT.TAG_COMPOUND)) {
            this.graveSkull = NBTUtil.readGameProfile(tag.getCompound("skull"));
        }
        if (tag.contains("text")) {
            this.graveText = ITextComponent.Serializer.fromJson(tag.getString("text"));
        }
        this.claimed = tag.getBoolean("claimed");

        if (tag.contains("graveId")) {
            this.graveId = tag.getUUID("graveId");
            if (this.component == null && this.level != null && !this.level.isClientSide) {
                DeathInfoManager.INSTANCE.getGrave(this.graveId).ifPresent(this::setComponent);
            }
        }

        if (tag.contains("previousState", Constants.NBT.TAG_COMPOUND)) {
            this.previousState = NBTUtil.readBlockState(tag.getCompound("previousState"));
        }
    }

    @Override
    public void tick() {
        World world = this.level;
        if (world == null) {
            return;
        }
        if (world.isClientSide) {
            return;
        }

        if (this.component == null) {
            if (this.graveId == null) {
                return;
            }
            DeathInfoManager.INSTANCE.getGrave(this.graveId).ifPresent(this::setComponent);
            if (this.component == null) {
                return;
            }
        }

        if (world.getGameTime() % 2400 == 0) {
            cachedConfig = YigdConfig.getConfig();
        }

        GraveConfig.GraveTimeout timeoutConfig = cachedConfig.graveConfig.graveTimeout;

        if (!this.worldPosition.equals(this.component.getPos()) || !this.component.getWorldResourceKey().equals(world.dimension())) {
            this.updatePosition((ServerWorld) world, this.worldPosition);
        }

        if (!timeoutConfig.enabled || this.component.getStatus() != GraveStatus.UNCLAIMED) {
            return;
        }

        long timePassed = world.getGameTime() - this.component.getCreationTime().getTime();
        int ticksPerSecond = 20;
        if (timeoutConfig.timeUnit.toSeconds(timeoutConfig.afterTime) * ticksPerSecond <= timePassed) {
            this.component.setStatus(GraveStatus.DESTROYED);

            BlockState newState = Blocks.AIR.defaultBlockState();
            BlockState previousState = this.getPreviousState();
            if (YigdConfig.getConfig().graveConfig.replaceOldWhenClaimed && previousState != null) {
                newState = previousState;
            }
            this.component.replaceWithOld(newState, false);

            if (timeoutConfig.dropContentsOnTimeout) {
                this.component.dropAllGraveItems();
            }
        }
    }

    private void updatePosition(ServerWorld world, BlockPos pos) {
        if (this.component == null) {
            return;
        }

        this.component.setPos(pos);
        this.component.setWorld(world);
        if (this.component.getStatus() == GraveStatus.DESTROYED || !this.claimed) {
            this.component.setStatus(GraveStatus.UNCLAIMED);
            PlayerList playerManager = world.getServer().getPlayerList();
            GameProfile owner = this.component.getOwner();
            ServerPlayerEntity player = owner.getId() != null ? playerManager.getPlayer(owner.getId()) : playerManager.getPlayerByName(owner.getName());
            if (player != null) {
                Yigd.LOGGER.info("Grave belonging to {} resurfaced at X: {} / Y: {} / Z: {} / {}",
                        this.component.getOwner().getName(), this.worldPosition.getX(), this.worldPosition.getY(), this.worldPosition.getZ(),
                        this.component.getWorldResourceKey().location());
                player.sendMessage(new TranslationTextComponent("text.yigd.message.grave_relocated", pos.getX(), pos.getY(), pos.getZ(),
                        world.dimension().location().toString()), Util.NIL_UUID);
            }
        }
    }
}
