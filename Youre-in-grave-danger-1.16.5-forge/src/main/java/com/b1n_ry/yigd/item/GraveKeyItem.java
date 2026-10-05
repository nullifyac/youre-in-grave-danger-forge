package com.b1n_ry.yigd.item;

import com.b1n_ry.yigd.components.GraveComponent;
import com.b1n_ry.yigd.config.YigdConfig;
import com.b1n_ry.yigd.data.DeathInfoManager;
import com.b1n_ry.yigd.data.GraveStatus;
import com.mojang.authlib.GameProfile;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.util.Hand;
import net.minecraft.util.ActionResult;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class GraveKeyItem extends Item {
    public GraveKeyItem(Properties properties) {
        super(properties);
    }

    @Override
    public void onCraftedBy(ItemStack stack, World level, PlayerEntity player) {
        if (!level.isClientSide) {
            this.bindStackToLatestGrave(player, stack);
        }
        super.onCraftedBy(stack, level, player);
    }

    @Override
    public ActionResult<ItemStack> use(World level, PlayerEntity player, Hand hand) {
        if (level.isClientSide) {
            return super.use(level, player, hand);
        }

        YigdConfig config = YigdConfig.getConfig();
        if (config.extraFeatures.graveKeys.rebindable && player.isShiftKeyDown()) {
            ItemStack key = player.getItemInHand(hand);
            if (this.bindStackToLatestGrave(player, key)) {
                return ActionResult.sidedSuccess(key, level.isClientSide);
            }
        }

        return super.use(level, player, hand);
    }

    public boolean bindStackToLatestGrave(PlayerEntity player, ItemStack key) {
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
        CompoundNBT tag = key.getOrCreateTag();
        tag.putUUID("grave", graveId);
        tag.put("user", NBTUtil.writeGameProfile(new CompoundNBT(), profile));
    }
}
