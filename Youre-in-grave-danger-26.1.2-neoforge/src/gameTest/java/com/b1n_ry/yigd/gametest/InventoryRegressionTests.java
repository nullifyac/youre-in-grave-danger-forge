package com.b1n_ry.yigd.gametest;

import com.b1n_ry.yigd.components.ExpComponent;
import com.b1n_ry.yigd.components.GraveComponent;
import com.b1n_ry.yigd.components.InventoryComponent;
import com.b1n_ry.yigd.config.YigdConfig;
import com.b1n_ry.yigd.util.DropRule;
import com.b1n_ry.yigd.data.GraveStatus;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

public final class InventoryRegressionTests {
    private InventoryRegressionTests() { }

    @GameTest(timeoutTicks = 300)
    public static void legacy1201ItemStackMigratesWithoutChangingSource(GameTestHelper helper) {
        try (InventoryTestFixture fixture = new InventoryTestFixture(helper)) {
            CompoundTag legacy = new CompoundTag();
            legacy.putString("id", "minecraft:diamond_sword");
            legacy.putByte("Count", (byte) 1);
            legacy.putString("dropRule", DropRule.KEEP.name());
            CompoundTag itemTag = fixture.stack(Items.DIAMOND_SWORD, 1, "legacy").get(DataComponents.CUSTOM_DATA).copyTag();
            itemTag.putInt("Damage", 17);
            CompoundTag display = new CompoundTag();
            display.putString("Name", "{\"text\":\"YiGD test legacy\"}");
            itemTag.put("display", display);
            ListTag enchantments = new ListTag();
            CompoundTag unbreaking = new CompoundTag();
            unbreaking.putString("id", "minecraft:unbreaking");
            unbreaking.putShort("lvl", (short) 3);
            enchantments.add(unbreaking);
            itemTag.put("Enchantments", enchantments);
            legacy.put("tag", itemTag);
            CompoundTag original = legacy.copy();
            ItemStack migrated = InventoryComponent.parseItemStack(legacy, helper.getLevel().registryAccess());
            ItemStack expected = fixture.stack(Items.DIAMOND_SWORD, 1, "legacy");
            expected.setDamageValue(17);
            expected.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING), 3);
            fixture.assertStack(migrated, expected, "legacy stack data conversion");
            helper.assertValueEqual(legacy, original, "legacy source NBT remains unchanged");
            fixture.assertStack(InventoryComponent.parseItemStack(InventoryComponent.saveItemStack(migrated, helper.getLevel().registryAccess()), helper.getLevel().registryAccess()), expected, "migrated stack saves in current format");
            helper.succeed();
        }
    }

    @GameTest
    public static void unavailableIntegrationDataSurvivesSaveAndFiltering(GameTestHelper helper) {
        try (InventoryTestFixture fixture = new InventoryTestFixture(helper)) {
            CompoundTag saved = new InventoryComponent(fixture.player).toNbt(helper.getLevel().registryAccess());
            CompoundTag integration = new CompoundTag();
            integration.putString("future_schema", "opaque-layout");
            integration.putInt("token", 2612);
            CompoundTag payload = new CompoundTag();
            payload.putString("slot", "do-not-guess-this-layout");
            payload.put("saved_item", InventoryComponent.saveItemStack(fixture.stack(Items.DIAMOND, 7, "unavailable"), helper.getLevel().registryAccess()));
            integration.put("payload", payload);
            saved.getCompoundOrEmpty("mods").put("not_installed_regression", integration);
            InventoryComponent loaded = InventoryComponent.fromNbt(saved, helper.getLevel().registryAccess());
            helper.assertTrue(loaded.hasUnloadedModInventories(), "Unavailable integration was not retained");
            helper.assertFalse(loaded.isEmpty(), "Unavailable inventory was classified as empty");
            helper.assertFalse(loaded.isGraveEmpty(), "Unavailable inventory was classified as an empty grave");
            CompoundTag stored = loaded.toNbt(helper.getLevel().registryAccess());
            helper.assertValueEqual(stored.getCompoundOrEmpty("mods").getCompoundOrEmpty("not_installed_regression"), integration, "opaque integration data preserved");
            CompoundTag filtered = loaded.filteredInv(rule -> rule == DropRule.PUT_IN_GRAVE).toNbt(helper.getLevel().registryAccess());
            helper.assertValueEqual(filtered.getCompoundOrEmpty("mods").getCompoundOrEmpty("not_installed_regression"), integration, "filtering retains unavailable inventory");
            helper.assertTrue(loaded.applyToPlayer(fixture.player).stream().allMatch(ItemStack::isEmpty), "Opaque data was guessed into a player slot");
            helper.assertTrue(fixture.player.getInventory().isEmpty(), "Unavailable integration created phantom vanilla items");
            InventoryComponent merged = new InventoryComponent(fixture.player);
            helper.assertTrue(merged.merge(loaded, fixture.player).stream().allMatch(ItemStack::isEmpty), "Unavailable data created merge overflow");
            helper.assertValueEqual(merged.toNbt(helper.getLevel().registryAccess()).getCompoundOrEmpty("mods").getCompoundOrEmpty("not_installed_regression"), integration, "merging retains unavailable recovery data");
            loaded.clear();
            helper.assertTrue(loaded.hasUnloadedModInventories(), "Clearing supported inventory destroyed unavailable recovery data");
            helper.assertValueEqual(loaded.toNbt(helper.getLevel().registryAccess()).getCompoundOrEmpty("mods").getCompoundOrEmpty("not_installed_regression"), integration, "unsupported data survives clearing loaded slots");
            helper.succeed();
        }
    }

    @GameTest
    public static void inventoryComponentsAndEquipmentRoundTrip(GameTestHelper helper) {
        try (InventoryTestFixture fixture = new InventoryTestFixture(helper)) {
            helper.assertValueEqual(fixture.player.getInventory().getContainerSize(), 43, "26.1.2 vanilla inventory size");
            fixture.player.getInventory().setItem(0, fixture.stack(Items.DIAMOND, 3, "main"));
            ItemStack sword = fixture.stack(Items.DIAMOND_SWORD, 1, "enchanted");
            sword.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING), 3);
            sword.setDamageValue(17);
            fixture.player.getInventory().setItem(9, sword);
            fixture.player.getInventory().setItem(36, fixture.stack(Items.DIAMOND_BOOTS, 1, "feet"));
            fixture.player.getInventory().setItem(37, fixture.stack(Items.DIAMOND_LEGGINGS, 1, "legs"));
            fixture.player.getInventory().setItem(38, fixture.stack(Items.DIAMOND_CHESTPLATE, 1, "chest"));
            fixture.player.getInventory().setItem(39, fixture.stack(Items.DIAMOND_HELMET, 1, "head"));
            fixture.player.getInventory().setItem(40, fixture.stack(Items.EMERALD, 5, "offhand"));
            fixture.player.getInventory().setItem(41, fixture.stack(Items.IRON_HORSE_ARMOR, 1, "body"));
            fixture.player.getInventory().setItem(42, fixture.stack(Items.SADDLE, 1, "saddle"));
            List<ItemStack> expected = fixture.snapshot();
            InventoryComponent captured = new InventoryComponent(fixture.player);
            captured.onDeath(fixture.deathContext());
            InventoryComponent restored = fixture.roundTrip(captured);
            helper.assertTrue(restored.getItems().get(9).stack.has(DataComponents.ENCHANTMENTS), "Enchantment component did not survive NBT");
            InventoryComponent.clearPlayer(fixture.player);
            helper.assertTrue(fixture.player.getInventory().isEmpty(), "Inventory clear missed equipment slots");
            helper.assertTrue(restored.applyToPlayer(fixture.player).stream().allMatch(ItemStack::isEmpty), "Known vanilla slots overflowed");
            fixture.assertInventory(expected);
            helper.assertTrue(fixture.droppedItems().isEmpty(), "Round trip dropped or duplicated items");
            helper.succeed();
        }
    }

    @GameTest
    public static void experienceRoundTripAndSoulboundPercentage(GameTestHelper helper) {
        try (InventoryTestFixture fixture = new InventoryTestFixture(helper)) {
            fixture.player.giveExperiencePoints(315); // Exactly level 15; avoids float progress rounding.
            helper.assertValueEqual(fixture.player.experienceLevel, 15, "initial XP level");
            ExpComponent captured = new ExpComponent(fixture.player);
            helper.assertValueEqual(captured.getStoredXp(), 315, "captured XP");
            helper.assertValueEqual(captured.getOriginalXp(), 315D, "original XP");
            ExpComponent restored = ExpComponent.fromNbt(captured.toNbt());
            helper.assertValueEqual(restored.getXpLevel(), 15, "serialized XP level");
            ExpComponent.clearXp(fixture.player);
            helper.assertValueEqual(fixture.player.totalExperience, 0, "cleared XP total");
            helper.assertValueEqual(fixture.player.experienceLevel, 0, "cleared XP level");
            helper.assertValueEqual(fixture.player.experienceProgress, 0F, "cleared XP progress");
            restored.applyToPlayer(fixture.player);
            helper.assertValueEqual(fixture.player.totalExperience, 315, "restored XP total");
            helper.assertValueEqual(fixture.player.experienceLevel, 15, "restored XP level");
            helper.assertValueEqual(fixture.player.experienceProgress, 0F, "restored XP progress");
            YigdConfig.getConfig().expConfig.keepPercentage = 25;
            ExpComponent soulbound = ExpComponent.fromNbt(restored.getSoulboundExp().toNbt());
            helper.assertValueEqual(soulbound.getStoredXp(), 78, "soulbound XP rounds down");
            helper.assertValueEqual(soulbound.getOriginalXp(), 315D, "soulbound original XP");
            helper.succeed();
        }
    }

    @GameTest
    public static void soulboundGroundAndVanishingRulesSurviveDeath(GameTestHelper helper) {
        try (InventoryTestFixture fixture = new InventoryTestFixture(helper)) {
            YigdConfig.getConfig().inventoryConfig.soulboundSlots.add(0);
            YigdConfig.getConfig().inventoryConfig.dropOnGroundSlots.add(1);
            YigdConfig.getConfig().inventoryConfig.vanishingSlots.add(2);
            fixture.player.getInventory().setItem(0, fixture.stack(Items.DIAMOND, 2, "keep"));
            fixture.player.getInventory().setItem(1, fixture.stack(Items.EMERALD, 3, "ground"));
            fixture.player.getInventory().setItem(2, fixture.stack(Items.GOLD_INGOT, 4, "destroy"));
            fixture.player.getInventory().setItem(3, fixture.stack(Items.IRON_INGOT, 5, "grave"));
            List<ItemStack> expected = fixture.snapshot();
            expected.set(1, ItemStack.EMPTY);
            expected.set(2, ItemStack.EMPTY);
            InventoryComponent captured = new InventoryComponent(fixture.player);
            captured.onDeath(fixture.deathContext());
            InventoryComponent serialized = fixture.roundTrip(captured);
            DropRule[] rules = {DropRule.KEEP, DropRule.DROP, DropRule.DESTROY, DropRule.PUT_IN_GRAVE};
            for (int slot = 0; slot < rules.length; slot++) {
                helper.assertValueEqual(serialized.getItems().get(slot).dropRule, rules[slot], "serialized drop rule " + slot);
            }
            helper.assertValueEqual(fixture.droppedItems().size(), 1, "ground-drop entity count");
            fixture.assertStack(fixture.droppedItems().getFirst().getItem(), fixture.stack(Items.EMERALD, 3, "ground"), "ground drop");
            InventoryComponent.clearPlayer(fixture.player);
            serialized.filteredInv(rule -> rule == DropRule.KEEP).applyToPlayer(fixture.player);
            helper.assertValueEqual(fixture.player.getInventory().getItem(0).getCount(), 2, "soulbound item kept after death");
            helper.assertTrue(fixture.player.getInventory().getItem(3).isEmpty(), "Stored grave item restored too early");
            GraveComponent grave = fixture.grave(serialized, new ExpComponent(fixture.player));
            helper.assertValueEqual(grave.claim(fixture.player, helper.getLevel(), Blocks.AIR.defaultBlockState(), grave.getPos(), ItemStack.EMPTY), InteractionResult.SUCCESS, "grave claim");
            fixture.assertInventory(expected);
            helper.assertValueEqual(fixture.droppedItems().size(), 1, "grave claim duplicated ground drop");
            helper.succeed();
        }
    }

    @GameTest
    public static void graveClaimRestoresInventoryAndXpOnlyOnce(GameTestHelper helper) {
        try (InventoryTestFixture fixture = new InventoryTestFixture(helper)) {
            fixture.player.getInventory().setItem(0, fixture.stack(Items.DIAMOND, 4, "grave-main"));
            fixture.player.getInventory().setItem(41, fixture.stack(Items.IRON_HORSE_ARMOR, 1, "grave-body"));
            fixture.player.getInventory().setItem(42, fixture.stack(Items.SADDLE, 1, "grave-saddle"));
            fixture.player.giveExperiencePoints(315);
            List<ItemStack> expected = fixture.snapshot();
            InventoryComponent captured = new InventoryComponent(fixture.player);
            captured.onDeath(fixture.deathContext());
            InventoryComponent inventory = fixture.roundTrip(captured);
            ExpComponent exp = ExpComponent.fromNbt(new ExpComponent(fixture.player).toNbt());
            InventoryComponent.clearPlayer(fixture.player);
            ExpComponent.clearXp(fixture.player);
            GraveComponent grave = fixture.grave(inventory, exp);
            helper.assertValueEqual(grave.claim(fixture.player, helper.getLevel(), Blocks.AIR.defaultBlockState(), grave.getPos(), ItemStack.EMPTY), InteractionResult.SUCCESS, "first claim");
            helper.assertValueEqual(grave.getStatus(), GraveStatus.CLAIMED, "grave status after recovery");
            fixture.assertInventory(expected);
            helper.assertValueEqual(fixture.player.totalExperience, 315, "XP after first claim");
            helper.assertValueEqual(grave.claim(fixture.player, helper.getLevel(), Blocks.AIR.defaultBlockState(), grave.getPos(), ItemStack.EMPTY), InteractionResult.FAIL, "second claim rejected");
            fixture.assertInventory(expected);
            helper.assertValueEqual(fixture.player.totalExperience, 315, "second claim duplicated XP");
            helper.assertTrue(fixture.droppedItems().isEmpty(), "Claim dropped or duplicated inventory");
            helper.succeed();
        }
    }
}
