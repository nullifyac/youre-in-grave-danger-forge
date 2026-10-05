package com.b1n_ry.yigd.enchantment;

import com.b1n_ry.yigd.config.YigdConfig;
import com.b1n_ry.yigd.util.YigdTags;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentType;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.ItemStack;

public class SoulboundEnchantment extends Enchantment {
    public SoulboundEnchantment(Rarity weight, EquipmentSlotType... slotTypes) {
        super(weight, EnchantmentType.BREAKABLE, slotTypes);
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
        return !stack.getItem().is(YigdTags.SOULBOUND_BLACKLIST);
    }
}
