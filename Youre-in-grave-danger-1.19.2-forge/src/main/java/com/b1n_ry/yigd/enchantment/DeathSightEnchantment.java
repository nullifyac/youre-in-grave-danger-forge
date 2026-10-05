package com.b1n_ry.yigd.enchantment;

import com.b1n_ry.yigd.config.YigdConfig;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.entity.EquipmentSlot;

public class DeathSightEnchantment extends Enchantment {
    public DeathSightEnchantment(Rarity weight, EquipmentSlot... slotTypes) {
        super(weight, EnchantmentCategory.ARMOR_HEAD, slotTypes);
    }

    @Override
    public boolean isTreasureOnly() {
        return YigdConfig.getConfig().extraFeatures.deathSightEnchant.isTreasure;
    }

    @Override
    public boolean isTradeable() {
        return YigdConfig.getConfig().extraFeatures.deathSightEnchant.isAvailableForEnchantedBookOffer;
    }

    @Override
    public boolean isDiscoverable() {
        return YigdConfig.getConfig().extraFeatures.deathSightEnchant.isAvailableForRandomSelection;
    }
}
