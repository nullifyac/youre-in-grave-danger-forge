package com.b1n_ry.yigd.item;

import com.b1n_ry.yigd.Yigd;
import com.b1n_ry.yigd.components.GraveComponent;
import com.b1n_ry.yigd.config.CommandConfig;
import com.b1n_ry.yigd.config.ExtraFeaturesConfig;
import com.b1n_ry.yigd.config.YigdConfig;
import com.b1n_ry.yigd.config.ExtraFeaturesConfig.ScrollConfig;
import com.b1n_ry.yigd.data.DeathInfoManager;
import com.b1n_ry.yigd.data.GraveStatus;
import com.b1n_ry.yigd.networking.packets.GraveOverviewS2CPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class DeathScrollItem extends Item {
    public DeathScrollItem(Properties properties) {
        super(properties);
    }

    private static final int USE_TIME_MARGIN = 3;

    @Override
    public void onCraftedBy(@NotNull ItemStack stack, @NotNull Player player) {
        if (!player.level().isClientSide()) {
            this.bindStackToLatestDeath((ServerPlayer) player, stack);
        }
        super.onCraftedBy(stack, player);
    }

    @Override
    public boolean isEnabled(@NotNull FeatureFlagSet enabledFeatures) {
        return YigdConfig.getConfig().extraFeatures.deathScroll.enabled;
    }

    @Override
    public int getUseDuration(@NotNull ItemStack ignoredStack, @NotNull LivingEntity ignoredEntity) {
        return YigdConfig.getConfig().extraFeatures.deathScroll.useTime + USE_TIME_MARGIN;
    }

    @Override
    public @NotNull ItemUseAnimation getUseAnimation(@NotNull ItemStack stack) {
        return ItemUseAnimation.BOW;
    }

    @Override
    public void onUseTick(@NotNull Level level, @NotNull LivingEntity livingEntity, @NotNull ItemStack stack, int remainingUseDuration) {
        if (remainingUseDuration < USE_TIME_MARGIN) {
            livingEntity.releaseUsingItem();
        }
    }

    @Override
    public boolean releaseUsing(@NotNull ItemStack stack, @NotNull Level level, @NotNull LivingEntity livingEntity, int timeCharged) {
        float f = (float) (this.getUseDuration(stack, livingEntity) - timeCharged) / (float) (this.getUseDuration(stack, livingEntity) - USE_TIME_MARGIN);
        Yigd.LOGGER.debug("{}", f);
        if (f >= 1.0F && livingEntity instanceof Player player) {
            InteractionHand hand = player.getItemInHand(InteractionHand.MAIN_HAND).equals(stack) ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
            this.useAction(level, player, hand);
            return true;
        }
        return super.releaseUsing(stack, level, livingEntity, timeCharged);
    }

    @Override
    public @NotNull InteractionResult use(Level world, @NotNull Player user, @NotNull InteractionHand hand) {
        if (world.isClientSide()) return super.use(world, user, hand);

        ExtraFeaturesConfig.ScrollConfig scrollConfig = YigdConfig.getConfig().extraFeatures.deathScroll;
        ServerPlayer player = (ServerPlayer) user;
        ItemStack scroll = player.getItemInHand(hand);
        CustomData scrollNbtComponent = scroll.get(DataComponents.CUSTOM_DATA);
        CompoundTag scrollNbt = scrollNbtComponent != null ? scrollNbtComponent.copyTag() : null;

        // Rebind if the player is sneaking (and it can be rebound), or if the scroll is unbound
        if ((scrollConfig.rebindable && player.isShiftKeyDown()) || scrollNbt == null || scrollNbt.read("grave", net.minecraft.core.UUIDUtil.CODEC).isEmpty()) {
            if (this.bindStackToLatestDeath(player, scroll))
                return InteractionResult.SUCCESS_SERVER;
        }

        if (player.getCooldowns().isOnCooldown(scroll)) return InteractionResult.FAIL;

        if (YigdConfig.getConfig().extraFeatures.deathScroll.useTime > 0) {
            user.startUsingItem(hand);
        } else {
            return this.useAction(world, user, hand);
        }
        return InteractionResult.CONSUME;
    }

    private InteractionResult useAction(Level world, @NotNull Player user, @NotNull InteractionHand hand) {
        if (world.isClientSide()) return super.use(world, user, hand);

        ScrollConfig scrollConfig = YigdConfig.getConfig().extraFeatures.deathScroll;

        ServerPlayer player = (ServerPlayer) user;
        ItemStack scroll = player.getItemInHand(hand);
        CustomData scrollNbtComponent = scroll.get(DataComponents.CUSTOM_DATA);
        CompoundTag scrollNbt = scrollNbtComponent != null ? scrollNbtComponent.copyTag() : null;

        ScrollConfig.ClickFunction clickFunction = scrollConfig.clickFunction;
        if (scrollNbt != null && scrollNbt.contains("clickFunction") && !scrollNbt.getStringOr("clickFunction", "").equals("default")) {
            clickFunction = ScrollConfig.ClickFunction.valueOf(scrollNbt.getStringOr("clickFunction", ""));
        }

        InteractionResult res = switch (clickFunction) {
            case VIEW_CONTENTS -> this.viewContent(scroll, player);
            case RESTORE_CONTENTS -> this.restoreContent(scroll, player);
            case TELEPORT_TO_LOCATION -> this.teleport(scroll, player);
        };
        if (res != InteractionResult.PASS) {  // If the action was successful/failed or something other than 'standard'
            if (YigdConfig.getConfig().extraFeatures.deathScroll.consumeOnUse && res != InteractionResult.CONSUME)
                scroll.shrink(1);
        }
        player.getCooldowns().addCooldown(scroll, scrollConfig.useCooldown);
        return res;
    }

    public boolean bindStackToLatestDeath(ServerPlayer player, ItemStack scroll) {
        if (player == null) return false;  // Idk how some mods do auto-crafting, but this could fix some issues if they just pass null

        ResolvableProfile playerProfile = ResolvableProfile.createResolved(player.getGameProfile());
        List<GraveComponent> graves = new ArrayList<>(DeathInfoManager.INSTANCE.getBackupData(playerProfile));
        graves.removeIf(component -> component.getStatus() != GraveStatus.UNCLAIMED);

        int size = graves.size();
        if (size >= 1) {
            GraveComponent component = graves.get(size - 1);
            scroll.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY, comp -> comp.update(nbtCompound -> {
                nbtCompound.store("grave", net.minecraft.core.UUIDUtil.CODEC, component.getGraveId());
                nbtCompound.putString("clickFunction", "default");
            }));
            return true;
        }
        return false;
    }

    private InteractionResult viewContent(ItemStack scroll, ServerPlayer player) {
        CustomData scrollNbtComponent = scroll.get(DataComponents.CUSTOM_DATA);
        if (scrollNbtComponent == null) return InteractionResult.PASS;

        CompoundTag scrollNbt = scrollNbtComponent.copyTag();

        UUID graveId = scrollNbt.read("grave", net.minecraft.core.UUIDUtil.CODEC).orElse(null);
        Optional<GraveComponent> optional = DeathInfoManager.INSTANCE.getGrave(graveId);
        if (optional.isPresent()) {
            GraveComponent component = optional.get();
            YigdConfig config = YigdConfig.getConfig();
            CommandConfig commandConfig = config.commandConfig;
            PacketDistributor.sendToPlayer(player, new GraveOverviewS2CPacket(component,
                    com.b1n_ry.yigd.util.YigdPermissions.has(player, commandConfig.restorePermissionLevel),
                    com.b1n_ry.yigd.util.YigdPermissions.has(player, commandConfig.robPermissionLevel),
                    com.b1n_ry.yigd.util.YigdPermissions.has(player, commandConfig.deletePermissionLevel),
                    com.b1n_ry.yigd.util.YigdPermissions.has(player, commandConfig.unlockPermissionLevel) && config.graveConfig.unlockable,
                    config.extraFeatures.graveKeys.enabled && config.extraFeatures.graveKeys.obtainableFromGui,
                    config.extraFeatures.graveCompass.cloneRecoveryCompassWithGUI && player.getInventory().countItem(Items.RECOVERY_COMPASS) > 0));
        }

        return InteractionResult.SUCCESS_SERVER;
    }
    private InteractionResult restoreContent(ItemStack scroll, ServerPlayer player) {
        CustomData scrollNbtComponent = scroll.get(DataComponents.CUSTOM_DATA);
        if (scrollNbtComponent == null) return InteractionResult.PASS;

        CompoundTag scrollNbt = scrollNbtComponent.copyTag();

        UUID graveId = scrollNbt.read("grave", net.minecraft.core.UUIDUtil.CODEC).orElse(null);
        Optional<GraveComponent> optional = DeathInfoManager.INSTANCE.getGrave(graveId);
        if (optional.isPresent()) {
            GraveComponent component = optional.get();
            InteractionResult res = component.claim(player, player.level(), null, component.getPos(), scroll);
            return res;
        }
        return InteractionResult.PASS;
    }
    private InteractionResult teleport(ItemStack scroll, ServerPlayer player) {
        CustomData scrollNbtComponent = scroll.get(DataComponents.CUSTOM_DATA);
        if (scrollNbtComponent == null) return InteractionResult.PASS;

        CompoundTag scrollNbt = scrollNbtComponent.copyTag();

        UUID graveId = scrollNbt.read("grave", net.minecraft.core.UUIDUtil.CODEC).orElse(null);
        Optional<GraveComponent> optional = DeathInfoManager.INSTANCE.getGrave(graveId);
        if (optional.isPresent()) {
            GraveComponent component = optional.get();
            BlockPos gravePos = component.getPos();
            if (component.getWorld() != null)
                player.teleportTo(component.getWorld(), gravePos.getX(), gravePos.getY(), gravePos.getZ(), java.util.Set.of(), player.getYRot(), player.getXRot(), false);
        }

        return InteractionResult.SUCCESS_SERVER;
    }
}
