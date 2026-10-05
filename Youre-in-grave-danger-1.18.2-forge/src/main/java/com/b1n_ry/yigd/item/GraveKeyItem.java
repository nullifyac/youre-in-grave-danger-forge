package com.b1n_ry.yigd.item;

import com.b1n_ry.yigd.components.GraveComponent;
import com.b1n_ry.yigd.config.YigdConfig;
import com.b1n_ry.yigd.data.DeathInfoManager;
import com.b1n_ry.yigd.data.GraveStatus;
import com.mojang.authlib.GameProfile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class GraveKeyItem extends Item {
    public GraveKeyItem(Properties properties) {
        super(properties);
    }

    @Override
    public void onCraftedBy(ItemStack stack, Level level, Player player) {
        if (!level.isClientSide) {
            this.bindStackToLatestGrave(player, stack);
        }
        super.onCraftedBy(stack, level, player);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) {
            return super.use(level, player, hand);
        }

        YigdConfig config = YigdConfig.getConfig();
        if (config.extraFeatures.graveKeys.rebindable && player.isShiftKeyDown()) {
            ItemStack key = player.getItemInHand(hand);
            if (this.bindStackToLatestGrave(player, key)) {
                return InteractionResultHolder.sidedSuccess(key, level.isClientSide);
            }
        }

        return super.use(level, player, hand);
    }

    public boolean bindStackToLatestGrave(Player player, ItemStack key) {
        GameProfile profile = player.getGameProfile();
        List<GraveComponent> graves = new ArrayList<>(DeathInfoManager.INSTANCE.getBackupData(profile));
        graves.removeIf(component -> component.getStatus() != GraveStatus.UNCLAIMED);

        if (!graves.isEmpty()) {
            GraveComponent component = graves.get(graves.size() - 1);
            this.bindStackToGrave(component.getGraveId(), profile, key);
            return true;
        }
        return false;
    }

    public void bindStackToGrave(UUID graveId, GameProfile profile, ItemStack key) {
        CompoundTag tag = key.getOrCreateTag();
        tag.putUUID("grave", graveId);
        tag.put("user", NbtUtils.writeGameProfile(new CompoundTag(), profile));
    }
}
