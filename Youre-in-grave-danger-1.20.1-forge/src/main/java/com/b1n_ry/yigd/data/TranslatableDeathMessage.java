package com.b1n_ry.yigd.data;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public class TranslatableDeathMessage {
    private final String damageTypeId;
    private final String killedDisplayName;
    @Nullable private final String sourceDisplayName;
    @Nullable private final String attackerDisplayName;
    @Nullable private String itemDisplayName;
    @Nullable private final String primeAdversaryDisplayName;

    public TranslatableDeathMessage(DamageSource deathSource, LivingEntity killed) {
        this.damageTypeId = deathSource.getMsgId();
        this.killedDisplayName = killed.getDisplayName().getString();
        Entity source = deathSource.getDirectEntity();
        Entity attacker = deathSource.getEntity();
        this.sourceDisplayName = source != null ? source.getDisplayName().getString() : null;
        this.attackerDisplayName = attacker != null ? attacker.getDisplayName().getString() : null;
        this.itemDisplayName = null;
        if (attacker instanceof LivingEntity livingAttacker) {
            ItemStack killingWeapon = livingAttacker.getMainHandItem();
            if (!killingWeapon.isEmpty() && killingWeapon.hasCustomHoverName()) {
                this.itemDisplayName = killingWeapon.getDisplayName().getString();
            }
        }
        LivingEntity primeAdversary = killed.getKillCredit();
        this.primeAdversaryDisplayName = primeAdversary != null ? primeAdversary.getDisplayName().getString() : null;
    }
    public TranslatableDeathMessage(String damageTypeId, String killedDisplayName, @Nullable String sourceDisplayName, @Nullable String attackerDisplayName, @Nullable String itemDisplayName, @Nullable String primeAdversaryDisplayName) {
        this.damageTypeId = damageTypeId;
        this.killedDisplayName = killedDisplayName;
        this.sourceDisplayName = sourceDisplayName;
        this.attackerDisplayName = attackerDisplayName;
        this.itemDisplayName = itemDisplayName;
        this.primeAdversaryDisplayName = primeAdversaryDisplayName;
    }

    public Component getDeathMessage() {
        String string = "death.attack." + this.damageTypeId;
        if (this.attackerDisplayName != null || this.sourceDisplayName != null) {
            String killedBy = this.attackerDisplayName == null ? this.sourceDisplayName : this.attackerDisplayName;
            if (this.itemDisplayName != null) {
                return Component.translatable(string + ".item", this.killedDisplayName, killedBy, this.itemDisplayName);
            }
            return Component.translatable(string, this.killedDisplayName, killedBy);
        }
        if (this.primeAdversaryDisplayName != null) {
            return Component.translatable(string + ".player", this.killedDisplayName, this.primeAdversaryDisplayName);
        }
        return Component.translatable(string, this.killedDisplayName);
    }

    public CompoundTag toNbt() {
        CompoundTag nbt = new CompoundTag();
        nbt.putString("damageTypeId", this.damageTypeId);
        nbt.putString("killedDisplayName", this.killedDisplayName);
        if (this.sourceDisplayName != null) nbt.putString("sourceDisplayName", this.sourceDisplayName);
        if (this.attackerDisplayName != null) nbt.putString("attackerDisplayName", this.attackerDisplayName);
        if (this.itemDisplayName != null) nbt.putString("itemDisplayName", this.itemDisplayName);
        if (this.primeAdversaryDisplayName != null) nbt.putString("primeAdversaryDisplayName", this.primeAdversaryDisplayName);

        return nbt;
    }
    public static TranslatableDeathMessage fromNbt(CompoundTag nbt) {
        String damageTypeId = nbt.getString("damageTypeId");
        String killedDisplayName = nbt.getString("killedDisplayName");
        String sourceDisplayName = nbt.contains("sourceDisplayName") ? nbt.getString("sourceDisplayName") : null;
        String attackerDisplayName = nbt.contains("attackerDisplayName") ? nbt.getString("attackerDisplayName") : null;
        String itemDisplayName = nbt.contains("itemDisplayName") ? nbt.getString("itemDisplayName") : null;
        String primeAdversaryDisplayName = nbt.contains("primeAdversaryDisplayName") ? nbt.getString("primeAdversaryDisplayName") : null;

        return new TranslatableDeathMessage(damageTypeId, killedDisplayName, sourceDisplayName, attackerDisplayName, itemDisplayName, primeAdversaryDisplayName);
    }
}
