package com.b1n_ry.yigd.enchantment;

import com.b1n_ry.yigd.config.YigdConfig;
import com.b1n_ry.yigd.util.YigdTags;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

public class SoulboundEnchantment extends Enchantment {
    public SoulboundEnchantment(Rarity weight, EquipmentSlot... slotTypes) {
        super(weight, EnchantmentCategory.BREAKABLE, slotTypes);
    }

    @Override
    public boolean isTreasureOnly() {
        return YigdConfig.getConfig().extraFeatures.soulboundEnchant.isTreasure;
    }

    @Override
    public boolean isTradeable() {
        return YigdConfig.getConfig().extraFeatures.soulboundEnchant.isAvailableForEnchantedBookOffer;
    }

    @Override
    public boolean isDiscoverable() {
        return YigdConfig.getConfig().extraFeatures.soulboundEnchant.isAvailableForRandomSelection;
    }

    @Override
    public boolean canEnchant(ItemStack stack) {
        return !stack.is(YigdTags.SOULBOUND_BLACKLIST);
    }
}
