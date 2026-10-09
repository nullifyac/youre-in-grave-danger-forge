package com.b1n_ry.yigd.util;

import com.b1n_ry.yigd.config.YigdConfig;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.common.conditions.ICondition;

/** Keeps optional enchantments out of the registry when their config toggle is disabled. */
public record FeatureEnabledCondition(String feature) implements ICondition {
    public static final MapCodec<FeatureEnabledCondition> CODEC = Codec.STRING.fieldOf("feature").xmap(FeatureEnabledCondition::new, FeatureEnabledCondition::feature);

    @Override
    public boolean test(IContext context) {
        var config = YigdConfig.getConfig().extraFeatures;
        return switch (feature) {
            case "soulbound" -> config.enableSoulbound;
            case "death_sight" -> config.deathSightEnchant.enabled;
            default -> false;
        };
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }
}
