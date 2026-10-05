package com.b1n_ry.yigd.item;

import com.b1n_ry.yigd.components.GraveComponent;
import com.b1n_ry.yigd.config.ExtraFeaturesConfig.ScrollConfig;
import com.b1n_ry.yigd.config.YigdConfig;
import com.b1n_ry.yigd.data.DeathInfoManager;
import com.b1n_ry.yigd.data.GraveStatus;
import com.b1n_ry.yigd.packets.ServerPacketHandler;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class DeathScrollItem extends Item {
    private static final int USE_TIME_MARGIN = 3;

    public DeathScrollItem(Properties properties) {
        super(properties);
    }

    @Override
    public void onCraftedBy(ItemStack stack, Level level, Player player) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            this.bindStackToLatestDeath(serverPlayer, stack);
        }
        super.onCraftedBy(stack, level, player);
    }

    @Override
    public boolean isEnabled(FeatureFlagSet enabledFeatures) {
        return YigdConfig.getConfig().extraFeatures.deathScroll.enabled;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return YigdConfig.getConfig().extraFeatures.deathScroll.useTime + USE_TIME_MARGIN;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remainingUseDuration) {
        if (remainingUseDuration < USE_TIME_MARGIN) {
            user.stopUsingItem();
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeCharged) {
        float progress = (float) (this.getUseDuration(stack) - timeCharged)
                / (float) (this.getUseDuration(stack) - USE_TIME_MARGIN);

        if (progress >= 1.0F && entity instanceof Player player) {
            InteractionHand hand = player.getItemInHand(InteractionHand.MAIN_HAND) == stack
                    ? InteractionHand.MAIN_HAND
                    : InteractionHand.OFF_HAND;
            this.useAction(level, player, hand);
            return;
        }
        super.releaseUsing(stack, level, entity, timeCharged);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) {
            return super.use(level, player, hand);
        }

        ScrollConfig scrollConfig = YigdConfig.getConfig().extraFeatures.deathScroll;

        ServerPlayer serverPlayer = (ServerPlayer) player;
        ItemStack scroll = serverPlayer.getItemInHand(hand);
        CompoundTag scrollTag = scroll.getTag();
        if ((scrollConfig.rebindable && serverPlayer.isShiftKeyDown())
                || scrollTag == null || !scrollTag.contains("grave")) {
            if (this.bindStackToLatestDeath(serverPlayer, scroll)) {
                return InteractionResultHolder.sidedSuccess(scroll, level.isClientSide);
            }
        }

        if (serverPlayer.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(scroll);
        }

        if (scrollConfig.useTime > 0) {
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(scroll);
        }

        return this.useAction(level, player, hand);
    }

    private InteractionResultHolder<ItemStack> useAction(Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        if (level.isClientSide) {
            return super.use(level, player, hand);
        }

        ScrollConfig scrollConfig = YigdConfig.getConfig().extraFeatures.deathScroll;
        ServerPlayer serverPlayer = (ServerPlayer) player;
        ItemStack scroll = serverPlayer.getItemInHand(hand);
        CompoundTag scrollTag = scroll.getTag();

        ScrollConfig.ClickFunction clickFunction = scrollConfig.clickFunction;
        if (scrollTag != null && scrollTag.contains("clickFunction")
                && !"default".equals(scrollTag.getString("clickFunction"))) {
            clickFunction = ScrollConfig.ClickFunction.valueOf(scrollTag.getString("clickFunction"));
        }

        InteractionResultHolder<ItemStack> result = switch (clickFunction) {
            case VIEW_CONTENTS -> this.viewContent(scroll, serverPlayer);
            case RESTORE_CONTENTS -> this.restoreContent(scroll, serverPlayer);
            case TELEPORT_TO_LOCATION -> this.teleport(scroll, serverPlayer);
        };

        if (result.getResult() != InteractionResult.PASS) {
            if (scrollConfig.consumeOnUse && result.getResult() != InteractionResult.CONSUME) {
                scroll.shrink(1);
            }
            return result;
        }

        serverPlayer.getCooldowns().addCooldown(this, scrollConfig.useCooldown);
        return result;
    }

    public boolean bindStackToLatestDeath(ServerPlayer player, ItemStack scroll) {
        if (player == null) {
            return false;
        }

        GameProfile profile = player.getGameProfile();
        List<GraveComponent> graves = new ArrayList<>(DeathInfoManager.INSTANCE.getBackupData(profile));
        graves.removeIf(component -> component.getStatus() != GraveStatus.UNCLAIMED);

        if (!graves.isEmpty()) {
            GraveComponent component = graves.get(graves.size() - 1);
            CompoundTag scrollTag = scroll.getOrCreateTag();
            scrollTag.putUUID("grave", component.getGraveId());
            scrollTag.putString("clickFunction", "default");
            return true;
        }
        return false;
    }

    private InteractionResultHolder<ItemStack> viewContent(ItemStack scroll, ServerPlayer player) {
        CompoundTag scrollTag = scroll.getTag();
        if (scrollTag == null) {
            return InteractionResultHolder.pass(scroll);
        }

        UUID graveId = scrollTag.getUUID("grave");
        Optional<GraveComponent> optional = DeathInfoManager.INSTANCE.getGrave(graveId);
        optional.ifPresent(component -> ServerPacketHandler.sendGraveOverviewPacket(player, component));

        return InteractionResultHolder.success(scroll);
    }

    private InteractionResultHolder<ItemStack> restoreContent(ItemStack scroll, ServerPlayer player) {
        CompoundTag scrollTag = scroll.getTag();
        if (scrollTag == null) {
            return InteractionResultHolder.pass(scroll);
        }

        UUID graveId = scrollTag.getUUID("grave");
        Optional<GraveComponent> optional = DeathInfoManager.INSTANCE.getGrave(graveId);
        if (optional.isPresent()) {
            GraveComponent component = optional.get();
            InteractionResult result = component.claim(player, player.serverLevel(), null, component.getPos(), scroll);
            return new InteractionResultHolder<>(result, scroll);
        }

        return InteractionResultHolder.pass(scroll);
    }

    private InteractionResultHolder<ItemStack> teleport(ItemStack scroll, ServerPlayer player) {
        CompoundTag scrollTag = scroll.getTag();
        if (scrollTag == null) {
            return InteractionResultHolder.pass(scroll);
        }

        UUID graveId = scrollTag.getUUID("grave");
        Optional<GraveComponent> optional = DeathInfoManager.INSTANCE.getGrave(graveId);
        if (optional.isPresent()) {
            GraveComponent component = optional.get();
            BlockPos gravePos = component.getPos();
            player.teleportTo(component.getWorld(), gravePos.getX(), gravePos.getY(), gravePos.getZ(),
                    player.getYRot(), player.getXRot());
        }

        return InteractionResultHolder.success(scroll);
    }
}
