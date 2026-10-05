package com.b1n_ry.yigd.item;

import com.b1n_ry.yigd.components.GraveComponent;
import com.b1n_ry.yigd.config.ExtraFeaturesConfig.ScrollConfig;
import com.b1n_ry.yigd.config.YigdConfig;
import com.b1n_ry.yigd.data.DeathInfoManager;
import com.b1n_ry.yigd.data.GraveStatus;
import com.b1n_ry.yigd.packets.ServerPacketHandler;
import com.mojang.authlib.GameProfile;
import net.minecraft.util.math.BlockPos;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.ActionResult;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.UseAction;
import net.minecraft.world.World;
import javax.annotation.Nonnull;

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
    public void onCraftedBy(ItemStack stack, World level, PlayerEntity player) {
        if (!level.isClientSide && player instanceof ServerPlayerEntity) {
            this.bindStackToLatestDeath((ServerPlayerEntity) player, stack);
        }
        super.onCraftedBy(stack, level, player);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return YigdConfig.getConfig().extraFeatures.deathScroll.useTime + USE_TIME_MARGIN;
    }

    @Override
    public UseAction getUseAnimation(ItemStack stack) {
        return UseAction.BOW;
    }

    @Override
    public void onUseTick(World level, LivingEntity user, ItemStack stack, int remainingUseDuration) {
        if (remainingUseDuration < USE_TIME_MARGIN) {
            user.stopUsingItem();
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, World level, LivingEntity entity, int timeCharged) {
        float progress = (float) (this.getUseDuration(stack) - timeCharged)
                / (float) (this.getUseDuration(stack) - USE_TIME_MARGIN);

        if (progress >= 1.0F && entity instanceof PlayerEntity) {
            PlayerEntity player = (PlayerEntity) entity;
            Hand hand = player.getItemInHand(Hand.MAIN_HAND) == stack
                    ? Hand.MAIN_HAND
                    : Hand.OFF_HAND;
            this.useAction(level, player, hand);
            return;
        }
        super.releaseUsing(stack, level, entity, timeCharged);
    }

    @Override
    public ActionResult<ItemStack> use(World level, PlayerEntity player, Hand hand) {
        if (level.isClientSide) {
            return super.use(level, player, hand);
        }

        ScrollConfig scrollConfig = YigdConfig.getConfig().extraFeatures.deathScroll;

        ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;
        ItemStack scroll = serverPlayer.getItemInHand(hand);
        CompoundNBT scrollTag = scroll.getTag();
        if ((scrollConfig.rebindable && serverPlayer.isShiftKeyDown())
                || scrollTag == null || !scrollTag.contains("grave")) {
            if (this.bindStackToLatestDeath(serverPlayer, scroll)) {
                return ActionResult.sidedSuccess(scroll, level.isClientSide);
            }
        }

        if (serverPlayer.getCooldowns().isOnCooldown(this)) {
            return ActionResult.fail(scroll);
        }

        if (scrollConfig.useTime > 0) {
            player.startUsingItem(hand);
            return ActionResult.consume(scroll);
        }

        return this.useAction(level, player, hand);
    }

    private ActionResult<ItemStack> useAction(World level, @Nonnull PlayerEntity player, @Nonnull Hand hand) {
        if (level.isClientSide) {
            return super.use(level, player, hand);
        }

        ScrollConfig scrollConfig = YigdConfig.getConfig().extraFeatures.deathScroll;
        ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;
        ItemStack scroll = serverPlayer.getItemInHand(hand);
        CompoundNBT scrollTag = scroll.getTag();

        ScrollConfig.ClickFunction clickFunction = scrollConfig.clickFunction;
        if (scrollTag != null && scrollTag.contains("clickFunction")
                && !"default".equals(scrollTag.getString("clickFunction"))) {
            clickFunction = ScrollConfig.ClickFunction.valueOf(scrollTag.getString("clickFunction"));
        }

        ActionResult<ItemStack> result;
        switch (clickFunction) {
            case VIEW_CONTENTS:
                result = this.viewContent(scroll, serverPlayer);
                break;
            case RESTORE_CONTENTS:
                result = this.restoreContent(scroll, serverPlayer);
                break;
            case TELEPORT_TO_LOCATION:
                result = this.teleport(scroll, serverPlayer);
                break;
            default:
                result = ActionResult.pass(scroll);
                break;
        }

        if (result.getResult() != ActionResultType.PASS) {
            if (scrollConfig.consumeOnUse && result.getResult() != ActionResultType.CONSUME) {
                scroll.shrink(1);
            }
            return result;
        }

        serverPlayer.getCooldowns().addCooldown(this, scrollConfig.useCooldown);
        return result;
    }

    public boolean bindStackToLatestDeath(ServerPlayerEntity player, ItemStack scroll) {
        if (player == null) {
            return false;
        }

        GameProfile profile = player.getGameProfile();
        List<GraveComponent> graves = new ArrayList<>(DeathInfoManager.INSTANCE.getBackupData(profile));
        graves.removeIf(component -> component.getStatus() != GraveStatus.UNCLAIMED);

        if (!graves.isEmpty()) {
            GraveComponent component = graves.get(graves.size() - 1);
            CompoundNBT scrollTag = scroll.getOrCreateTag();
            scrollTag.putUUID("grave", component.getGraveId());
            scrollTag.putString("clickFunction", "default");
            return true;
        }
        return false;
    }

    private ActionResult<ItemStack> viewContent(ItemStack scroll, ServerPlayerEntity player) {
        CompoundNBT scrollTag = scroll.getTag();
        if (scrollTag == null) {
            return ActionResult.pass(scroll);
        }

        UUID graveId = scrollTag.getUUID("grave");
        Optional<GraveComponent> optional = DeathInfoManager.INSTANCE.getGrave(graveId);
        optional.ifPresent(component -> ServerPacketHandler.sendGraveOverviewPacket(player, component));

        return ActionResult.success(scroll);
    }

    private ActionResult<ItemStack> restoreContent(ItemStack scroll, ServerPlayerEntity player) {
        CompoundNBT scrollTag = scroll.getTag();
        if (scrollTag == null) {
            return ActionResult.pass(scroll);
        }

        UUID graveId = scrollTag.getUUID("grave");
        Optional<GraveComponent> optional = DeathInfoManager.INSTANCE.getGrave(graveId);
        if (optional.isPresent()) {
            GraveComponent component = optional.get();
            ActionResultType result = component.claim(player, (ServerWorld) player.level, null, component.getPos(), scroll);
            return new ActionResult<>(result, scroll);
        }

        return ActionResult.pass(scroll);
    }

    private ActionResult<ItemStack> teleport(ItemStack scroll, ServerPlayerEntity player) {
        CompoundNBT scrollTag = scroll.getTag();
        if (scrollTag == null) {
            return ActionResult.pass(scroll);
        }

        UUID graveId = scrollTag.getUUID("grave");
        Optional<GraveComponent> optional = DeathInfoManager.INSTANCE.getGrave(graveId);
        if (optional.isPresent()) {
            GraveComponent component = optional.get();
            BlockPos gravePos = component.getPos();
            player.teleportTo(component.getWorld(), gravePos.getX(), gravePos.getY(), gravePos.getZ(),
                    player.yRot, player.xRot);
        }

        return ActionResult.success(scroll);
    }
}
