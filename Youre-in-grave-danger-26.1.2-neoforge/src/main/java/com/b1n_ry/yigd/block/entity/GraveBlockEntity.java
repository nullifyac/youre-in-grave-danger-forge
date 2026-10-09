package com.b1n_ry.yigd.block.entity;

import com.b1n_ry.yigd.Yigd;
import com.b1n_ry.yigd.components.GraveComponent;
import com.b1n_ry.yigd.config.GraveConfig;
import com.b1n_ry.yigd.config.YigdConfig;
import com.b1n_ry.yigd.data.DeathInfoManager;
import com.b1n_ry.yigd.data.GraveStatus;
import com.b1n_ry.yigd.data.GraveNbt;
import com.b1n_ry.yigd.events.YigdEvents;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

public class GraveBlockEntity extends BlockEntity {
    @Nullable
    private GraveComponent component = null;
    @Nullable
    private UUID graveId = null;
    @Nullable
    private ResolvableProfile graveSkull = null;
    @Nullable
    private Component graveText = null;
    @Nullable
    private BlockState previousState = null;

    private boolean claimed = true;

    private static YigdConfig cachedConfig = YigdConfig.getConfig();

    public GraveBlockEntity(BlockPos pos, BlockState blockState) {
        super(Yigd.GRAVE_BLOCK_ENTITY.get(), pos, blockState);
    }

    @Override
    protected void collectImplicitComponents(@NotNull DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(DataComponents.PROFILE, this.graveSkull);
        components.set(DataComponents.CUSTOM_NAME, this.graveText);
        components.set(Yigd.GRAVE_ID, this.graveId);
    }

    @Override
    protected void applyImplicitComponents(@NotNull DataComponentGetter componentInput) {
        super.applyImplicitComponents(componentInput);
        this.setGraveSkull(componentInput.get(DataComponents.PROFILE));
        this.setGraveText(componentInput.get(DataComponents.CUSTOM_NAME));
        this.graveId = componentInput.get(Yigd.GRAVE_ID);
    }

    public void setComponent(GraveComponent component) {
        this.component = component;
        this.setClaimed(component.getStatus() == GraveStatus.CLAIMED);
        this.graveSkull = component.getOwner();
        this.graveId = component.getGraveId();
        // noinspection ConstantConditions
        this.graveSkull.name().ifPresent(name -> GraveBlockEntity.this.graveText = Component.nullToEmpty(name));
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
    public @Nullable ResolvableProfile getGraveSkull() {
        return this.graveSkull;
    }
    public void setGraveSkull(@Nullable ResolvableProfile skull) {
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
        if (this.level == null || this.level.isClientSide() || this.graveId == null) return;

        UUID removedGraveId = this.graveId;
        Yigd.END_OF_TICK.add(() -> {
            Optional<GraveComponent> component = DeathInfoManager.INSTANCE.getGrave(removedGraveId);
            component.ifPresent(grave -> {
                if (grave.getStatus() != GraveStatus.UNCLAIMED) return;
                ServerLevel graveWorld = grave.getWorld();
                if (graveWorld == null || !graveWorld.hasChunkAt(grave.getPos())) return;
                // Mining can restore the same grave, and another mod can relocate it during this tick.
                if (graveWorld.getBlockEntity(grave.getPos()) instanceof GraveBlockEntity current
                        && removedGraveId.equals(current.getGraveId())) return;
                grave.onDestroyed();
            });
        });
    }

    @Override
    public void preRemoveSideEffects(@NotNull BlockPos pos, @NotNull BlockState state) {
        this.onBroken();
        super.preRemoveSideEffects(pos, state);
    }

    @Override
    public @NotNull CompoundTag getUpdateTag(@NotNull HolderLookup.Provider registries) {
        return this.saveWithoutMetadata(registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(@NotNull ValueOutput output) {
        super.saveAdditional(output);
        output.putBoolean("claimed", this.claimed);
        output.storeNullable("text", ComponentSerialization.CODEC, this.graveText);
        output.storeNullable("skull", ResolvableProfile.CODEC, this.graveSkull);
        output.storeNullable("graveId", UUIDUtil.CODEC, this.graveId);
        output.storeNullable("previousState", BlockState.CODEC, this.previousState);
    }

    @Override
    protected void loadAdditional(@NotNull ValueInput input) {
        super.loadAdditional(input);
        this.graveSkull = GraveNbt.readProfile(input, "skull").orElse(null);
        this.graveText = GraveNbt.readComponent(input, "text").orElse(null);
        this.claimed = input.getBooleanOr("claimed", true);
        this.graveId = input.read("graveId", UUIDUtil.CODEC).orElse(null);
        this.previousState = input.read("previousState", BlockState.CODEC).orElse(null);
        this.component = null;
        if (this.graveId != null && this.level != null && !this.level.isClientSide()) {
            DeathInfoManager.INSTANCE.getGrave(this.graveId).ifPresent(this::setComponent);
        }
    }

    @Override
    public boolean hasCustomOutlineRendering(@NotNull Player player) {
        if (player.level().isClientSide()) {
            YigdEvents.RenderGlowingGraveEvent event = NeoForge.EVENT_BUS.post(new YigdEvents.RenderGlowingGraveEvent(this, (LocalPlayer) player));
            if (event.isRenderGlowing()) return true;
        }
        return super.hasCustomOutlineRendering(player);
    }

    public static void tick(Level world, BlockPos pos, BlockState ignoredState, GraveBlockEntity be) {
        if (world.isClientSide()) return;

        if (be.component == null) {
            if (be.graveId == null) return;
            DeathInfoManager.INSTANCE.getGrave(be.graveId).ifPresent(be::setComponent);
            if (be.component == null) return;
        }
        if (world.getGameTime() % 2400 == 0) cachedConfig = YigdConfig.getConfig();  // Reloads the config every 60 seconds

        GraveConfig.GraveTimeout timeoutConfig = cachedConfig.graveConfig.graveTimeout;

        if (!pos.equals(be.component.getPos())
                || (!be.component.getWorldRegistryKey().equals(world.dimension()))) {
            be.updatePosition((ServerLevel) world, pos);
        }

        if (!timeoutConfig.enabled || be.component.getStatus() != GraveStatus.UNCLAIMED) return;

        long timePassed = world.getGameTime() - be.component.getCreationTime().getTime();
        final int ticksPerSecond = 20;
        if (timeoutConfig.timeUnit.toSeconds(timeoutConfig.afterTime) * ticksPerSecond <= timePassed) {
            // Not technically destroyed, but a status has to be set to not trigger the "onDestroyed" grave component method
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
        if (this.component == null) return;

        this.component.setPos(pos);
        this.component.setWorld(world);
        if (this.component.getStatus() == GraveStatus.DESTROYED || !this.claimed) {
            this.component.setStatus(GraveStatus.UNCLAIMED);
            PlayerList playerManager = world.getServer().getPlayerList();
            ResolvableProfile owner = this.component.getOwner();
            ServerPlayer player = playerManager.getPlayer(owner.partialProfile().id());
            if (player != null) {
                Yigd.LOGGER.info("Grave belonging to {} resurfaced at X: {} / Y: {} / Z: {} / {}", this.component.getOwner().name().orElse("PLAYER_NOT_FOUND"),
                        this.worldPosition.getX(), this.worldPosition.getY(), this.worldPosition.getZ(), this.component.getWorldRegistryKey().identifier());
                player.sendSystemMessage(Component.translatable("text.yigd.message.grave_relocated", pos.getX(), pos.getY(), pos.getZ(), world.dimension().identifier().toString()));
            }
        }
    }
}
