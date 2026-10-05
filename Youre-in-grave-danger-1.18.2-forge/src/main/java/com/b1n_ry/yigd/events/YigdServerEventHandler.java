package com.b1n_ry.yigd.events;

import com.b1n_ry.yigd.util.TextCompat;
import com.b1n_ry.yigd.Yigd;
import com.b1n_ry.yigd.config.ExtraFeaturesConfig;
import com.b1n_ry.yigd.config.ExtraFeaturesConfig.GraveKeyConfig;
import com.b1n_ry.yigd.config.GraveConfig;
import com.b1n_ry.yigd.config.YigdConfig;
import com.b1n_ry.yigd.data.DeathContext;
import com.b1n_ry.yigd.data.DeathInfoManager;
import com.b1n_ry.yigd.data.ListMode;
import com.b1n_ry.yigd.util.DropRule;
import com.b1n_ry.yigd.util.GraveCompassHelper;
import com.b1n_ry.yigd.util.GraveOverrideAreas;
import com.b1n_ry.yigd.util.YigdTags;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = Yigd.MOD_ID)
public final class YigdServerEventHandler {
    private YigdServerEventHandler() {}

    private static void logGraveGenDenied(DeathContext context, String reason) {
        GraveConfig graveConfig = YigdConfig.getConfig().graveConfig;
        if (!graveConfig.logGraveGenerationFailures) return;

        String playerName = context.player().getGameProfile().getName();
        UUID playerId = context.player().getUUID();
        var dim = context.world().dimension().location();
        var d = context.deathPos();
        String deathType = context.deathSource().getMsgId();

        // Keep this single-line for easier grepping in latest.log.
        Yigd.LOGGER.warn("Grave generation denied: reason={} player={} uuid={} dim={} deathPos=({}, {}, {}) deathType={}",
                reason, playerName, playerId, dim, d.x, d.y, d.z, deathType);
    }

    @SubscribeEvent
    public static void handleDropRule(DropRuleEvent event) {
        ItemStack item = event.getStack();
        int slot = event.getSlot();
        var context = event.getDeathContext();
        boolean modify = event.isModify();

        YigdConfig config = YigdConfig.getConfig();

        MobEffect statusEffect = ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation("amethyst_imbuement", "soulbinding"));
        if (config.inventoryConfig.soulboundSlots.contains(slot)) {
            event.setDropRule(DropRule.KEEP);
            event.setCanceled(true);
            return;
        }
        if (config.inventoryConfig.vanishingSlots.contains(slot)) {
            event.setDropRule(DropRule.DESTROY);
            event.setCanceled(true);
            return;
        }
        if (config.inventoryConfig.dropOnGroundSlots.contains(slot)) {
            event.setDropRule(DropRule.DROP);
            event.setCanceled(true);
            return;
        }

        if (item.is(YigdTags.NATURAL_SOULBOUND)) {
            event.setDropRule(DropRule.KEEP);
            event.setCanceled(true);
            return;
        }
        if (item.is(YigdTags.NATURAL_VANISHING)) {
            event.setDropRule(DropRule.DESTROY);
            event.setCanceled(true);
            return;
        }
        if (item.is(YigdTags.GRAVE_INCOMPATIBLE)) {
            event.setDropRule(DropRule.DROP);
            event.setCanceled(true);
            return;
        }

        if (statusEffect != null && context != null && context.player().hasEffect(statusEffect)) {
            event.setDropRule(DropRule.KEEP);
            event.setCanceled(true);
            return;
        }

        if (!item.isEmpty() && item.hasTag()) {
            CompoundTag tag = item.getTag();
            if (tag != null && tag.contains("Botania_keepIvy") && tag.getBoolean("Botania_keepIvy")) {
                if (modify) {
                    item.removeTagKey("Botania_keepIvy");
                }
                event.setDropRule(DropRule.KEEP);
                event.setCanceled(true);
                return;
            }
        }

        DropRule dropRule = context != null
                ? GraveOverrideAreas.INSTANCE.getDropRuleFromArea(new BlockPos(context.deathPos().x, context.deathPos().y, context.deathPos().z), context.world())
                : GraveOverrideAreas.INSTANCE.defaultDropRule;

        ListTag enchantments = item.getEnchantmentTags();
        Set<CompoundTag> toRemove = new HashSet<>();
        for (var element : enchantments) {
            if (!(element instanceof CompoundTag enchantTag)) continue;

            String id = enchantTag.getString("id");
            if (config.inventoryConfig.vanishingEnchantments.contains(id)) {
                event.setDropRule(DropRule.DESTROY);
                event.setCanceled(true);
                return;
            }
            if (!config.inventoryConfig.soulboundEnchantments.contains(id)) continue;

            int level = enchantTag.getInt("lvl");
            if (config.inventoryConfig.loseSoulboundLevelOnDeath && modify) {
                if (level <= 1) {
                    toRemove.add(enchantTag);
                } else {
                    enchantTag.putInt("lvl", level - 1);
                }
            }
            dropRule = DropRule.KEEP;
            break;
        }
        enchantments.removeAll(toRemove);

        event.setDropRule(dropRule);
    }

    @SubscribeEvent
    public static void handleGraveClaim(GraveClaimEvent event) {
        var player = event.getPlayer();
        var level = event.getLevel();
        var grave = event.getGrave();
        ItemStack tool = event.getTool();

        if (player.isDeadOrDying()) {
            event.setCanClaim(false);
            return;
        }

        YigdConfig config = YigdConfig.getConfig();

        if (config.extraFeatures.graveCompass.consumeOnUse
                || config.extraFeatures.graveCompass.pointToClosest != ExtraFeaturesConfig.GraveCompassConfig.CompassGraveTarget.DISABLED) {
            Inventory inventory = player.getInventory();
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack stack = inventory.getItem(i);
                if (!stack.is(Items.COMPASS)) continue;

                if (config.extraFeatures.graveCompass.consumeOnUse) {
                    CompoundTag compassTag = stack.getTag();
                    if (compassTag != null && compassTag.contains("linked_grave")) {
                        UUID graveId = compassTag.getUUID("linked_grave");
                        if (graveId.equals(grave.getGraveId())) {
                            stack.shrink(1);
                            break;
                        }
                    }
                } else {
                    GraveCompassHelper.updateClosestNbt(level.dimension(), player.blockPosition(), player.getUUID(), stack);
                }
            }
        }

        if (config.extraFeatures.graveKeys.enabled) {
            if (tool.is(Yigd.GRAVE_KEY_ITEM)) {
                CompoundTag tag = tool.getOrCreateTag();
                CompoundTag userTag = tag.getCompound("user");
                Tag graveTag = tag.get("grave");
                GraveKeyConfig.KeyTargeting targeting = config.extraFeatures.graveKeys.targeting;
                switch (targeting) {
                    case ANY_GRAVE -> {
                        tool.shrink(1);
                        event.setCanClaim(true);
                        return;
                    }
                    case PLAYER_GRAVE -> {
                        if (Objects.equals(NbtUtils.readGameProfile(userTag), grave.getOwner())) {
                            tool.shrink(1);
                            event.setCanClaim(true);
                            return;
                        }
                    }
                    case SPECIFIC_GRAVE -> {
                        if (graveTag != null && Objects.equals(NbtUtils.loadUUID(graveTag), grave.getGraveId())) {
                            tool.shrink(1);
                            event.setCanClaim(true);
                            return;
                        }
                    }
                }
            }

            if (config.extraFeatures.graveKeys.required) {
                player.displayClientMessage(TextCompat.translatable("text.yigd.message.missing_key"), true);
                event.setCanClaim(false);
                return;
            }
        }

        if (config.graveConfig.requireShovelToLoot && !(tool.getItem() instanceof ShovelItem)) {
            player.displayClientMessage(TextCompat.translatable("text.yigd.message.no_shovel"), true);
            event.setCanClaim(false);
            return;
        }

        if (player.getUUID().equals(grave.getOwner().getId())) {
            event.setCanClaim(true);
            return;
        }
        if (!grave.isLocked()) {
            event.setCanClaim(true);
            return;
        }

        GraveConfig.GraveRobbing robConfig = config.graveConfig.graveRobbing;
        if (!robConfig.enabled) {
            event.setCanClaim(false);
            return;
        }

        if (robConfig.killerSkipWaitTime && player.getUUID().equals(grave.getKillerId())) {
            event.setCanClaim(true);
            return;
        }

        int tps = 20;
        if (!grave.hasExistedTicks(robConfig.timeUnit.toSeconds(robConfig.afterTime) * tps)) {
            player.displayClientMessage(TextCompat.translatable("text.yigd.message.rob.too_early", grave.getTimeUntilRobbable()), true);
            event.setCanClaim(false);
            return;
        }

        event.setCanClaim(true);
    }

    @SubscribeEvent
    public static void handleAllowGraveGeneration(AllowGraveGenerationEvent event) {
        var context = event.getDeathContext();
        var grave = event.getGrave();
        GraveConfig graveConfig = YigdConfig.getConfig().graveConfig;

        if (!graveConfig.enabled) {
            logGraveGenDenied(context, "graveConfig.enabled=false");
            event.setAllowGeneration(false);
            return;
        }

        ListMode listMode = DeathInfoManager.INSTANCE.getGraveListMode();
        boolean inList = DeathInfoManager.INSTANCE.isInList(context.player().getGameProfile());
        if ((listMode == ListMode.WHITELIST && !inList) || (listMode == ListMode.BLACKLIST && inList)) {
            logGraveGenDenied(context, "player_list_mode=" + listMode.name() + " inList=" + inList);
            event.setAllowGeneration(false);
            return;
        }

        if (!graveConfig.generateEmptyGraves && grave.isGraveEmpty()) {
            int storedXp = grave.getExpComponent().getStoredXp();
            int graveSlots = grave.getInventoryComponent().graveSize();
            logGraveGenDenied(context, "grave_empty generateEmptyGraves=false graveSlots=" + graveSlots + " storedXp=" + storedXp);
            event.setAllowGeneration(false);
            return;
        }

        if (graveConfig.dimensionBlacklist.contains(grave.getWorldResourceKey().location().toString())) {
            logGraveGenDenied(context, "dimension_blacklisted dim=" + grave.getWorldResourceKey().location());
            event.setAllowGeneration(false);
            return;
        }

        if (!graveConfig.generateGraveInVoid && grave.getPos().getY() < context.world().getMinBuildHeight()) {
            logGraveGenDenied(context, "generateGraveInVoid=false graveY=" + grave.getPos().getY() + " worldMinY=" + context.world().getMinBuildHeight());
            event.setAllowGeneration(false);
            return;
        }

        if (graveConfig.requireItem) {
            Item required = ForgeRegistries.ITEMS.getValue(new ResourceLocation(graveConfig.requiredItem));
            if (required == null || !grave.getInventoryComponent().removeItem(stack -> stack.is(required), graveConfig.requiredItemCount)) {
                logGraveGenDenied(context, "missing_required_item item=" + graveConfig.requiredItem + " count=" + graveConfig.requiredItemCount);
                event.setAllowGeneration(false);
                return;
            }
        }

        if (graveConfig.ignoredDeathTypes.contains(context.deathSource().getMsgId())) {
            logGraveGenDenied(context, "ignored_death_type type=" + context.deathSource().getMsgId());
            event.setAllowGeneration(false);
        }
    }

    @SubscribeEvent
    public static void handleBlockUnderPlacement(AllowBlockUnderGraveGenerationEvent event) {
        boolean allow = YigdConfig.getConfig().graveConfig.blockUnderGrave.enabled
                && event.getBlockUnder().is(YigdTags.REPLACE_SOFT_WHITELIST);
        event.setAllowPlacement(allow);
    }

    @SubscribeEvent
    public static void handleGraveGeneration(GraveGenerationEvent event) {
        var level = event.getLevel();
        BlockPos pos = event.getPos();
        int nthTry = event.getNthTry();

        if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)) {
            event.setCanGenerate(false);
            return;
        }

        BlockState state = level.getBlockState(pos);
        if (level.getBlockEntity(pos) != null) {
            event.setCanGenerate(false);
            return;
        }

        GraveConfig config = YigdConfig.getConfig().graveConfig;
        switch (nthTry) {
            case 0 -> {
                if (!config.useSoftBlockWhitelist || !state.is(YigdTags.REPLACE_SOFT_WHITELIST)) {
                    event.setCanGenerate(false);
                    return;
                }
            }
            case 1 -> {
                if (!config.useStrictBlockBlacklist || state.is(YigdTags.KEEP_STRICT_BLACKLIST)) {
                    event.setCanGenerate(false);
                    return;
                }
            }
        }

        event.setCanGenerate(true);
    }

    @SubscribeEvent
    public static void handleDropItem(DropItemEvent event) {
        if (event.getStack().isEmpty()) {
            event.setShouldDrop(false);
        }
    }

    /**
     * @deprecated Forge now uses automatic event bus subscription; retained to avoid Fabric-only call sites.
     */
    @Deprecated
    public static void registerEventCallbacks() {
        // No-op on Forge.
    }
}
