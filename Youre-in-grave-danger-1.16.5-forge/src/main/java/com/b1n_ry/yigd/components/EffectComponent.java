package com.b1n_ry.yigd.components;

import com.b1n_ry.yigd.config.RespawnConfig;
import com.b1n_ry.yigd.config.YigdConfig;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.util.ResourceLocation;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.potion.Effect;
import net.minecraft.potion.EffectInstance;
import net.minecraft.util.FoodStats;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

public class EffectComponent {
    private final List<EffectInstance> effects;
    private final int resetHp;
    private final int resetHunger;
    private final float resetSaturation;


    public EffectComponent(ServerPlayerEntity player) {
        YigdConfig config = YigdConfig.getConfig();
        RespawnConfig rConfig = config.respawnConfig;

        this.effects = new ArrayList<>();
        this.loadEffectsFromConfig(rConfig);

        this.resetHp = rConfig.respawnHealth;

        FoodStats hungerManager = player.getFoodData();
        if (!rConfig.resetHunger) {
            this.resetHunger = hungerManager.getFoodLevel();
        } else {
            this.resetHunger = rConfig.respawnHunger;
        }
        if (!rConfig.resetSaturation) {
            this.resetSaturation = hungerManager.getSaturationLevel();
        } else {
            this.resetSaturation = rConfig.respawnSaturation;
        }
    }

    public EffectComponent(List<EffectInstance> effects, int resetHp, int resetHunger, float resetSaturation) {
        this.effects = effects;
        this.resetHp = resetHp;
        this.resetHunger = resetHunger;
        this.resetSaturation = resetSaturation;
    }

    public void applyToPlayer(ServerPlayerEntity player) {
        if (this.resetHp > 0)
            player.setHealth(this.resetHp);

        FoodStats hungerManager = player.getFoodData();
        if (this.resetHunger >= 0)
            hungerManager.setFoodLevel(this.resetHunger);
        if (this.resetSaturation >= 0)
            hungerManager.setSaturation(this.resetSaturation);

        for (EffectInstance effect : this.effects) {
            player.addEffect(effect);
        }
    }

    public CompoundNBT toNbt() {
        CompoundNBT nbtCompound = new CompoundNBT();
        nbtCompound.putInt("hp", this.resetHp);
        nbtCompound.putInt("hunger", this.resetHunger);
        nbtCompound.putFloat("saturation", this.resetSaturation);

        ListNBT nbtEffects = new ListNBT();
        for (EffectInstance instance : this.effects) {
            nbtEffects.add(instance.save(new CompoundNBT()));
        }
        nbtCompound.put("effects", nbtEffects);

        return nbtCompound;
    }

    private void loadEffectsFromConfig(RespawnConfig rConfig) {
        for (RespawnConfig.EffectConfig effect : rConfig.respawnEffects) {
            Effect statusEffect = ForgeRegistries.POTIONS.getValue(new ResourceLocation(effect.effectName));
            if (statusEffect == null) continue;

            EffectInstance effectInstance = new EffectInstance(statusEffect, effect.effectTime, effect.effectLevel - 1, false, effect.showBubbles);
            this.effects.add(effectInstance);
        }
    }

    public static EffectComponent fromNbt(CompoundNBT nbt) {
        int resetHp = nbt.getInt("hp");
        int resetHunger = nbt.getInt("hunger");
        float resetSaturation = nbt.getFloat("saturation");

        List<EffectInstance> effects = new ArrayList<>();
        ListNBT effectsNbt = nbt.getList("effects", Constants.NBT.TAG_COMPOUND);
        for (INBT e : effectsNbt) {
            CompoundNBT compound = (CompoundNBT) e;
            EffectInstance instance = EffectInstance.load(compound);
            effects.add(instance);
        }

        return new EffectComponent(effects, resetHp, resetHunger, resetSaturation);
    }
}
