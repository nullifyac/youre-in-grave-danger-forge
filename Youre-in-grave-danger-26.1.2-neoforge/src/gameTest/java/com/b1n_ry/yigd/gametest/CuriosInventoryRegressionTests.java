package com.b1n_ry.yigd.gametest;

import com.b1n_ry.yigd.compat.CuriosCompat;
import com.b1n_ry.yigd.compat.InvModCompat;
import com.b1n_ry.yigd.components.ExpComponent;
import com.b1n_ry.yigd.components.GraveComponent;
import com.b1n_ry.yigd.components.InventoryComponent;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

/** Loaded by the test harness only when the optional Curios mod is present. */
public final class CuriosInventoryRegressionTests {
    private CuriosInventoryRegressionTests() { }

    @GameTest
    public static void curiosMergeKeepsTheSelectedItemsRenderPreference(GameTestHelper helper) {
        try (InventoryTestFixture fixture = new InventoryTestFixture(helper)) {
            InvModCompat.invCompatMods.add(new CuriosCompat());
            var optionalInventory = CuriosApi.getCuriosInventory(fixture.player);
            helper.assertTrue(optionalInventory.isPresent(), "Curios player inventory capability is missing");
            var handler = optionalInventory.orElseThrow();
            handler.reset();
            var optionalSlot = handler.getStacksHandler("yigd_regression");
            helper.assertTrue(optionalSlot.isPresent(), "Curios regression slot datapack did not load");
            ICurioStacksHandler slot = optionalSlot.orElseThrow();
            ItemStack preferredNormal = fixture.stack(Items.DIAMOND, 2, "merge-preferred-normal");
            ItemStack preferredCosmetic = fixture.stack(Items.EMERALD, 3, "merge-preferred-cosmetic");
            slot.getStacks().setStackInSlot(0, preferredNormal.copy());
            slot.getCosmeticStacks().setStackInSlot(0, preferredCosmetic.copy());
            slot.getRenders().set(0, false);
            InventoryComponent preferred = fixture.roundTrip(new InventoryComponent(fixture.player));
            slot.getStacks().setStackInSlot(0, fixture.stack(Items.GOLD_INGOT, 4, "merge-other-normal"));
            slot.getCosmeticStacks().setStackInSlot(0, fixture.stack(Items.IRON_INGOT, 5, "merge-other-cosmetic"));
            slot.getRenders().set(0, true);
            InventoryComponent other = fixture.roundTrip(new InventoryComponent(fixture.player));
            helper.assertTrue(preferred.merge(other, fixture.player).stream().allMatch(ItemStack::isEmpty), "Conflicting Curios stacks did not fit vanilla inventory");
            InventoryComponent.clearPlayer(fixture.player);
            preferred.applyToPlayer(fixture.player);
            fixture.assertStack(slot.getStacks().getStackInSlot(0), preferredNormal, "preferred merged normal item");
            fixture.assertStack(slot.getCosmeticStacks().getStackInSlot(0), preferredCosmetic, "preferred merged cosmetic item");
            helper.assertValueEqual(slot.getRenders().get(0), false, "occupied destination keeps its render preference");
            helper.assertValueEqual(fixture.player.getInventory().getItem(0).getCount() + fixture.player.getInventory().getItem(1).getCount(), 9, "conflicting Curios items recovered once into vanilla inventory");

            InventoryComponent.clearPlayer(fixture.player);
            slot.getRenders().set(0, true);
            InventoryComponent emptyDestination = new InventoryComponent(fixture.player);
            helper.assertTrue(emptyDestination.merge(preferred, fixture.player).stream().allMatch(ItemStack::isEmpty), "Empty Curios destination overflowed");
            InventoryComponent.clearPlayer(fixture.player);
            emptyDestination.applyToPlayer(fixture.player);
            fixture.assertStack(slot.getStacks().getStackInSlot(0), preferredNormal, "incoming normal item selected into empty slot");
            fixture.assertStack(slot.getCosmeticStacks().getStackInSlot(0), preferredCosmetic, "incoming cosmetic item selected into empty slot");
            helper.assertValueEqual(slot.getRenders().get(0), false, "empty destination adopts the incoming render preference");
            helper.assertValueEqual(fixture.player.getInventory().getItem(0).getCount() + fixture.player.getInventory().getItem(1).getCount(), 9, "second merge preserves exact overflow count");
            helper.assertTrue(fixture.droppedItems().isEmpty(), "Merging Curios inventories dropped or duplicated items");
            helper.succeed();
        }
    }

    @GameTest
    public static void curiosNormalCosmeticAndRenderPreferenceRoundTrip(GameTestHelper helper) {
        try (InventoryTestFixture fixture = new InventoryTestFixture(helper)) {
            InvModCompat.invCompatMods.add(new CuriosCompat());
            var optionalInventory = CuriosApi.getCuriosInventory(fixture.player);
            helper.assertTrue(optionalInventory.isPresent(), "Curios player inventory capability is missing");
            var handler = optionalInventory.orElseThrow();
            // Real login initializes Curios' attachment. The native player factory bypasses that
            // login event, so use the public reset API to load the actual entity-slot datapack.
            handler.reset();
            var optionalSlot = handler.getStacksHandler("yigd_regression");
            helper.assertTrue(optionalSlot.isPresent(), "Curios regression datapack slot missing; available slots: " + handler.getCurios().keySet());
            ICurioStacksHandler slot = optionalSlot.orElseThrow();
            helper.assertValueEqual(slot.getStacks().getSlots(), 1, "normal Curios fixture size");
            helper.assertValueEqual(slot.getCosmeticStacks().getSlots(), 1, "cosmetic Curios fixture size");
            helper.assertValueEqual(slot.getRenders().size(), 1, "Curios render preference fixture size");
            ItemStack normal = fixture.stack(Items.DIAMOND, 2, "curios-normal");
            ItemStack cosmetic = fixture.stack(Items.EMERALD, 3, "curios-cosmetic");
            slot.getStacks().setStackInSlot(0, normal.copy());
            slot.getCosmeticStacks().setStackInSlot(0, cosmetic.copy());
            slot.getRenders().set(0, false);
            fixture.player.getInventory().setItem(0, fixture.stack(Items.GOLD_INGOT, 4, "vanilla-with-curios"));
            var expectedVanilla = fixture.snapshot();
            InventoryComponent captured = new InventoryComponent(fixture.player);
            captured.onDeath(fixture.deathContext());
            InventoryComponent inventory = fixture.roundTrip(captured);
            helper.assertValueEqual(inventory.getAllExtraItems(true).size(), 2, "nonempty captured Curios stacks");
            InventoryComponent.clearPlayer(fixture.player);
            helper.assertTrue(slot.getStacks().getStackInSlot(0).isEmpty(), "Normal Curios slot did not clear");
            helper.assertTrue(slot.getCosmeticStacks().getStackInSlot(0).isEmpty(), "Cosmetic Curios slot did not clear");
            slot.getRenders().set(0, true); // A replacement player starts with the default render preference.
            helper.assertTrue(inventory.applyToPlayer(fixture.player).stream().allMatch(ItemStack::isEmpty), "Curios restoration overflowed available slots");
            fixture.assertInventory(expectedVanilla);
            fixture.assertStack(slot.getStacks().getStackInSlot(0), normal, "normal Curios slot");
            fixture.assertStack(slot.getCosmeticStacks().getStackInSlot(0), cosmetic, "cosmetic Curios slot");
            helper.assertValueEqual(slot.getRenders().get(0), false, "saved Curios render preference");
            slot.getRenders().set(0, true);
            InventoryComponent visibleCapture = new InventoryComponent(fixture.player);
            visibleCapture.onDeath(fixture.deathContext());
            InventoryComponent visibleInventory = fixture.roundTrip(visibleCapture);
            InventoryComponent.clearPlayer(fixture.player);
            slot.getRenders().set(0, false);
            helper.assertTrue(visibleInventory.applyToPlayer(fixture.player).stream().allMatch(ItemStack::isEmpty), "Visible Curios restoration overflowed");
            fixture.assertStack(slot.getStacks().getStackInSlot(0), normal, "visible normal Curios slot");
            fixture.assertStack(slot.getCosmeticStacks().getStackInSlot(0), cosmetic, "visible cosmetic Curios slot");
            helper.assertValueEqual(slot.getRenders().get(0), true, "enabled Curios render preference survives NBT");

            // A historical grave has no renders list and must use Curios' visible default.
            var oldSave = visibleInventory.toNbt(helper.getLevel().registryAccess());
            oldSave.getCompoundOrEmpty("mods").getCompoundOrEmpty("curios").getCompoundOrEmpty("yigd_regression").remove("renders");
            InventoryComponent legacyInventory = InventoryComponent.fromNbt(oldSave, helper.getLevel().registryAccess());
            InventoryComponent.clearPlayer(fixture.player);
            slot.getRenders().set(0, false);
            legacyInventory.applyToPlayer(fixture.player);
            helper.assertValueEqual(slot.getRenders().get(0), true, "historical Curios render default");
            helper.assertTrue(fixture.droppedItems().isEmpty(), "Curios round trip dropped or duplicated items");
            helper.succeed();
        }
    }

    @GameTest
    public static void curiosGraveClaimRestoresItemsAndXpOnlyOnce(GameTestHelper helper) {
        try (InventoryTestFixture fixture = new InventoryTestFixture(helper)) {
            InvModCompat.invCompatMods.add(new CuriosCompat());
            var optionalInventory = CuriosApi.getCuriosInventory(fixture.player);
            helper.assertTrue(optionalInventory.isPresent(), "Curios player inventory capability is missing");
            var handler = optionalInventory.orElseThrow();
            handler.reset();
            var optionalSlot = handler.getStacksHandler("yigd_regression");
            helper.assertTrue(optionalSlot.isPresent(), "Curios regression datapack slot missing; available slots: " + handler.getCurios().keySet());
            ICurioStacksHandler slot = optionalSlot.orElseThrow();
            helper.assertValueEqual(slot.getStacks().getSlots(), 1, "normal Curios fixture size");
            helper.assertValueEqual(slot.getCosmeticStacks().getSlots(), 1, "cosmetic Curios fixture size");
            ItemStack normal = fixture.stack(Items.DIAMOND, 2, "claim-curios-normal");
            ItemStack cosmetic = fixture.stack(Items.EMERALD, 3, "claim-curios-cosmetic");
            slot.getStacks().setStackInSlot(0, normal.copy());
            slot.getCosmeticStacks().setStackInSlot(0, cosmetic.copy());
            slot.getRenders().set(0, false);
            fixture.player.getInventory().setItem(0, fixture.stack(Items.GOLD_INGOT, 4, "claim-vanilla"));
            fixture.player.giveExperiencePoints(315);
            var expectedVanilla = fixture.snapshot();
            InventoryComponent captured = new InventoryComponent(fixture.player);
            captured.onDeath(fixture.deathContext());
            InventoryComponent inventory = fixture.roundTrip(captured);
            ExpComponent exp = ExpComponent.fromNbt(new ExpComponent(fixture.player).toNbt());
            InventoryComponent.clearPlayer(fixture.player);
            ExpComponent.clearXp(fixture.player);
            slot.getRenders().set(0, true);
            GraveComponent grave = fixture.grave(inventory, exp);
            helper.assertValueEqual(grave.claim(fixture.player, helper.getLevel(), Blocks.AIR.defaultBlockState(), grave.getPos(), ItemStack.EMPTY), InteractionResult.SUCCESS, "first Curios grave claim");
            fixture.assertInventory(expectedVanilla);
            fixture.assertStack(slot.getStacks().getStackInSlot(0), normal, "claimed normal Curios slot");
            fixture.assertStack(slot.getCosmeticStacks().getStackInSlot(0), cosmetic, "claimed cosmetic Curios slot");
            helper.assertValueEqual(slot.getRenders().get(0), false, "claimed Curios render preference");
            helper.assertValueEqual(fixture.player.totalExperience, 315, "XP after Curios claim");
            helper.assertValueEqual(grave.claim(fixture.player, helper.getLevel(), Blocks.AIR.defaultBlockState(), grave.getPos(), ItemStack.EMPTY), InteractionResult.FAIL, "second Curios grave claim rejected");
            fixture.assertInventory(expectedVanilla);
            fixture.assertStack(slot.getStacks().getStackInSlot(0), normal, "normal Curios slot after second claim");
            fixture.assertStack(slot.getCosmeticStacks().getStackInSlot(0), cosmetic, "cosmetic Curios slot after second claim");
            helper.assertValueEqual(fixture.player.totalExperience, 315, "second Curios claim duplicated XP");
            helper.assertTrue(fixture.droppedItems().isEmpty(), "Curios grave claim dropped or duplicated items");
            helper.succeed();
        }
    }
}
