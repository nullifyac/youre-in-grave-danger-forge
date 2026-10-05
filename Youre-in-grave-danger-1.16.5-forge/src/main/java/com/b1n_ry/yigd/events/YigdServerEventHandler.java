package com.b1n_ry.yigd.events;

import com.b1n_ry.yigd.Yigd;
import com.b1n_ry.yigd.config.ExtraFeaturesConfig;
import com.b1n_ry.yigd.config.ExtraFeaturesConfig.GraveKeyConfig;
import com.b1n_ry.yigd.config.GraveConfig;
import com.b1n_ry.yigd.config.YigdConfig;
import com.b1n_ry.yigd.components.GraveComponent;
import com.b1n_ry.yigd.data.DeathContext;
import com.b1n_ry.yigd.data.DeathInfoManager;
import com.b1n_ry.yigd.data.ListMode;
import com.b1n_ry.yigd.util.DropRule;
import com.b1n_ry.yigd.util.GraveCompassHelper;
import com.b1n_ry.yigd.util.GraveOverrideAreas;
import com.b1n_ry.yigd.util.YigdTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.nbt.INBT;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.potion.Effect;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.ShovelItem;
import net.minecraft.block.BlockState;
import net.minecraft.util.text.TranslationTextComponent;
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
        ResourceLocation dim = context.world().dimension().location();
        String deathType = context.deathSource().getMsgId();

        // Keep this single-line for easier grepping in latest.log.
        Yigd.LOGGER.warn("Grave generation denied: reason={} player={} uuid={} dim={} deathPos=({}, {}, {}) deathType={}",
                reason, playerName, playerId, dim, context.deathPos().x, context.deathPos().y, context.deathPos().z, deathType);
    }

    @SubscribeEvent
    public static void handleDropRule(DropRuleEvent event) {
        ItemStack item = event.getStack();
        int slot = event.getSlot();
        DeathContext context = event.getDeathContext();
        boolean modify = event.isModify();

        YigdConfig config = YigdConfig.getConfig();

        Effect statusEffect = ForgeRegistries.POTIONS.getValue(new ResourceLocation("amethyst_imbuement", "soulbinding"));
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

        if (item.getItem().is(YigdTags.NATURAL_SOULBOUND)) {
            event.setDropRule(DropRule.KEEP);
            event.setCanceled(true);
            return;
        }
        if (item.getItem().is(YigdTags.NATURAL_VANISHING)) {
            event.setDropRule(DropRule.DESTROY);
            event.setCanceled(true);
            return;
        }
        if (item.getItem().is(YigdTags.GRAVE_INCOMPATIBLE)) {
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
            CompoundNBT tag = item.getTag();
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

        ListNBT enchantments = item.getEnchantmentTags();
        Set<CompoundNBT> toRemove = new HashSet<>();
        for (INBT element : enchantments) {
            if (!(element instanceof CompoundNBT)) {
                continue;
            }
            CompoundNBT enchantTag = (CompoundNBT) element;

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
        ServerPlayerEntity player = event.getPlayer();
        ServerWorld level = event.getLevel();
        GraveComponent grave = event.getGrave();
        ItemStack tool = event.getTool();

        if (player.isDeadOrDying()) {
            event.setCanClaim(false);
            return;
        }

        YigdConfig config = YigdConfig.getConfig();

        if (config.extraFeatures.graveCompass.consumeOnUse
                || config.extraFeatures.graveCompass.pointToClosest != ExtraFeaturesConfig.GraveCompassConfig.CompassGraveTarget.DISABLED) {
            PlayerInventory inventory = player.inventory;
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack stack = inventory.getItem(i);
                if (stack.getItem() != Items.COMPASS) continue;

                if (config.extraFeatures.graveCompass.consumeOnUse) {
                    CompoundNBT compassTag = stack.getTag();
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
            if (tool.getItem() == Yigd.GRAVE_KEY_ITEM) {
                CompoundNBT tag = tool.getOrCreateTag();
                CompoundNBT userTag = tag.getCompound("user");
                INBT graveTag = tag.get("grave");
                GraveKeyConfig.KeyTargeting targeting = config.extraFeatures.graveKeys.targeting;
                switch (targeting) {
                    case ANY_GRAVE:
                        tool.shrink(1);
                        event.setCanClaim(true);
                        return;
                    case PLAYER_GRAVE:
                        if (Objects.equals(NBTUtil.readGameProfile(userTag), grave.getOwner())) {
                            tool.shrink(1);
                            event.setCanClaim(true);
                            return;
                        }
                        break;
                    case SPECIFIC_GRAVE:
                        if (graveTag != null && Objects.equals(NBTUtil.loadUUID(graveTag), grave.getGraveId())) {
                            tool.shrink(1);
                            event.setCanClaim(true);
                            return;
                        }
                        break;
                }
            }

            if (config.extraFeatures.graveKeys.required) {
                player.displayClientMessage(new TranslationTextComponent("text.yigd.message.missing_key"), true);
                event.setCanClaim(false);
                return;
            }
        }

        if (config.graveConfig.requireShovelToLoot && !(tool.getItem() instanceof ShovelItem)) {
            player.displayClientMessage(new TranslationTextComponent("text.yigd.message.no_shovel"), true);
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
            player.displayClientMessage(new TranslationTextComponent("text.yigd.message.rob.too_early", grave.getTimeUntilRobbable()), true);
            event.setCanClaim(false);
            return;
        }

        event.setCanClaim(true);
    }

    @SubscribeEvent
    public static void handleAllowGraveGeneration(AllowGraveGenerationEvent event) {
        DeathContext context = event.getDeathContext();
        GraveComponent grave = event.getGrave();
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

        if (!graveConfig.generateGraveInVoid && context.world().isOutsideBuildHeight(grave.getPos())) {
            logGraveGenDenied(context, "generateGraveInVoid=false gravePosOutsideBuildHeight gravePos=("
                    + grave.getPos().getX() + ", " + grave.getPos().getY() + ", " + grave.getPos().getZ() + ")");
            event.setAllowGeneration(false);
            return;
        }

        if (graveConfig.requireItem) {
            Item required = ForgeRegistries.ITEMS.getValue(new ResourceLocation(graveConfig.requiredItem));
            if (required == null || !grave.getInventoryComponent().removeItem(stack -> stack.getItem() == required, graveConfig.requiredItemCount)) {
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
        ServerWorld level = event.getLevel();
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
            case 0:
                if (!config.useSoftBlockWhitelist || !state.is(YigdTags.REPLACE_SOFT_WHITELIST)) {
                    event.setCanGenerate(false);
                    return;
                }
                break;
            case 1:
                if (!config.useStrictBlockBlacklist || state.is(YigdTags.KEEP_STRICT_BLACKLIST)) {
                    event.setCanGenerate(false);
                    return;
                }
                break;
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
