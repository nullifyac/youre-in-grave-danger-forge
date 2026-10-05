package com.b1n_ry.yigd.components;

import com.b1n_ry.yigd.Yigd;
import com.b1n_ry.yigd.config.ExtraFeaturesConfig;
import com.b1n_ry.yigd.config.RespawnConfig;
import com.b1n_ry.yigd.config.YigdConfig;
import com.b1n_ry.yigd.data.DeathInfoManager;
import com.b1n_ry.yigd.util.GraveCompassHelper;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class RespawnComponent {
    @Nullable
    private InventoryComponent soulboundInventory;
    @Nullable
    private ExpComponent soulboundExp;
    private final EffectComponent respawnEffects;

    private boolean graveGenerated = false;

    public RespawnComponent(ServerPlayer player) {
        this.respawnEffects = new EffectComponent(player);
    }
    private RespawnComponent(@Nullable InventoryComponent soulboundInventory, @Nullable ExpComponent expComponent, EffectComponent effectComponent) {
        this.soulboundInventory = soulboundInventory;
        this.respawnEffects = effectComponent;
        this.soulboundExp = expComponent;
    }

    public void setSoulboundInventory(@NotNull InventoryComponent component) {
        this.soulboundInventory = component;
    }
    public void setSoulboundExp(@NotNull ExpComponent component) {
        this.soulboundExp = component;
    }
    public @Nullable ExpComponent getSoulboundExp() {
        return this.soulboundExp;
    }

    public void setGraveGenerated(boolean graveGenerated) {
        this.graveGenerated = graveGenerated;
    }
    public boolean wasGraveGenerated() {
        return this.graveGenerated;
    }

    public void primeForRespawn(GameProfile profile) {
        DeathInfoManager.INSTANCE.addRespawnComponent(profile, this);
        DeathInfoManager.INSTANCE.setDirty();
    }

    public boolean isEmpty() {
        return (this.soulboundInventory == null || this.soulboundInventory.isEmpty())
                && (this.soulboundExp == null || this.soulboundExp.isEmpty());
    }

    public void apply(ServerPlayer player) {
        if (this.soulboundInventory != null) {
            NonNullList<ItemStack> extraItems = NonNullList.create();
            if (YigdConfig.getConfig().respawnConfig.treatBindingCurse) {
                extraItems.addAll(this.soulboundInventory.pullBindingCurseItems(player));
            }
            extraItems.addAll(this.soulboundInventory.applyToPlayer(player));

            double x = player.getX();
            double y = player.getY();
            double z = player.getZ();
            ServerLevel world = (ServerLevel) player.level;
            for (ItemStack stack : extraItems) {
                InventoryComponent.dropItemIfToBeDropped(stack, x, y, z, world);
            }
        }

        YigdConfig config = YigdConfig.getConfig();
        ExtraFeaturesConfig extraFeaturesConfig = config.extraFeatures;
        if (extraFeaturesConfig.deathScroll.enabled && extraFeaturesConfig.deathScroll.receiveOnRespawn) {
            ItemStack scroll = Yigd.DEATH_SCROLL_ITEM.getDefaultInstance();
            boolean turned = Yigd.DEATH_SCROLL_ITEM.bindStackToLatestDeath(player, scroll);
            if (turned)
                player.addItem(scroll);
        }
        if (extraFeaturesConfig.graveKeys.enabled && extraFeaturesConfig.graveKeys.receiveOnRespawn) {
            ItemStack key = Yigd.GRAVE_KEY_ITEM.getDefaultInstance();
            boolean turned = Yigd.GRAVE_KEY_ITEM.bindStackToLatestGrave(player, key);
            if (turned)
                player.addItem(key);
        }
        if (extraFeaturesConfig.graveCompass.receiveOnRespawn) {
            List<GraveComponent> playerGraves = DeathInfoManager.INSTANCE.getBackupData(player.getGameProfile());
            if (!playerGraves.isEmpty()) {
                GraveComponent latestGrave = playerGraves.get(playerGraves.size() - 1);

                GraveCompassHelper.giveCompass(player, latestGrave.getGraveId(), latestGrave.getPos(), latestGrave.getWorldResourceKey());
            }
        }

        for (RespawnConfig.ExtraItemDrop extraItemDrop : config.respawnConfig.extraItemDrops) {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(extraItemDrop.itemId));
            if (item == null) {
                Yigd.LOGGER.warn("Failed to grant respawn item {}, item id not found", extraItemDrop.itemId);
                continue;
            }
            ItemStack stack = new ItemStack(item, extraItemDrop.count);
            try {
                if (!extraItemDrop.itemNbt.isEmpty())
                    stack.setTag(TagParser.parseTag(extraItemDrop.itemNbt));
            }
            catch (CommandSyntaxException e) {
                Yigd.LOGGER.error("Could not give an item with NBT to player on respawn. Invalid NBT. Falling back to item without NBT");
            }
            player.addItem(stack);
        }

        if (this.soulboundExp != null)
            this.soulboundExp.applyToPlayer(player);

        this.respawnEffects.applyToPlayer(player);

        // If there is an issue, items don't get duped
        DeathInfoManager.INSTANCE.removeRespawnComponent(player.getGameProfile());
        DeathInfoManager.INSTANCE.setDirty();
    }

    public CompoundTag toNbt() {
        CompoundTag nbt = new CompoundTag();

        if (this.soulboundInventory != null) nbt.put("inventory", this.soulboundInventory.toNbt());
        if (this.soulboundExp != null) nbt.put("exp", this.soulboundExp.toNbt());
        nbt.put("effects", this.respawnEffects.toNbt());

        return nbt;
    }

    public static RespawnComponent fromNbt(CompoundTag nbt) {
        InventoryComponent soulboundInventory = null;
        if (nbt.contains("inventory")) {
            CompoundTag inventoryNbt = nbt.getCompound("inventory");
            soulboundInventory = InventoryComponent.fromNbt(inventoryNbt);
        }

        ExpComponent expComponent = null;
        if (nbt.contains("exp")) {
            CompoundTag expNbt = nbt.getCompound("exp");
            expComponent = ExpComponent.fromNbt(expNbt);
        }

        CompoundTag effectsNbt = nbt.getCompound("effects");
        EffectComponent effectComponent = EffectComponent.fromNbt(effectsNbt);

        return new RespawnComponent(soulboundInventory, expComponent, effectComponent);
    }
}
