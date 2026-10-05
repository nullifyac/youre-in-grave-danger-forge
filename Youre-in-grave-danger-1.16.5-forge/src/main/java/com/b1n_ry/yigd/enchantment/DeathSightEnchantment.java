package com.b1n_ry.yigd.enchantment;

import com.b1n_ry.yigd.config.YigdConfig;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentType;
import net.minecraft.inventory.EquipmentSlotType;

public class DeathSightEnchantment extends Enchantment {
    public DeathSightEnchantment(Rarity weight, EquipmentSlotType... slotTypes) {
        super(weight, EnchantmentType.ARMOR_HEAD, slotTypes);
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
