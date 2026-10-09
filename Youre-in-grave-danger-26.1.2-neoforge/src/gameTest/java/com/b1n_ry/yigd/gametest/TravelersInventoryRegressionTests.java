package com.b1n_ry.yigd.gametest;

import com.b1n_ry.yigd.compat.InvModCompat;
import com.b1n_ry.yigd.compat.TravelersBackpackCompat;
import com.b1n_ry.yigd.components.ExpComponent;
import com.b1n_ry.yigd.components.GraveComponent;
import com.b1n_ry.yigd.components.InventoryComponent;
import com.tiviacz.travelersbackpack.attachment.AttachmentUtils;
import com.tiviacz.travelersbackpack.init.ModDataComponents;
import com.tiviacz.travelersbackpack.init.ModItems;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

/** Loaded only in a profile containing the actual optional Traveler's Backpack mod. */
public final class TravelersInventoryRegressionTests {
    private TravelersInventoryRegressionTests() { }

    @GameTest
    public static void equippedBackpackAndNestedContentsRoundTrip(GameTestHelper helper) {
        try (InventoryTestFixture fixture = new InventoryTestFixture(helper)) {
            helper.assertFalse(TravelersBackpackCompat.isIntegrationEnabled(), "This profile must test the standalone equipped-backpack attachment");
            InvModCompat.invCompatMods.add(new TravelersBackpackCompat());
            ItemStack inner = fixture.stack(Items.DIAMOND, 7, "backpack-contents");
            ItemStack backpack = fixture.stack(ModItems.STANDARD_TRAVELERS_BACKPACK.get(), 1, "equipped-backpack");
            backpack.set(ModDataComponents.BACKPACK_CONTAINER.get(), ItemContainerContents.fromItems(List.of(inner)));
            AttachmentUtils.equipBackpack(fixture.player, backpack);
            helper.assertTrue(AttachmentUtils.isWearingBackpack(fixture.player), "Backpack fixture was not equipped");
            helper.assertTrue(AttachmentUtils.getBackpackWrapper(fixture.player) != null, "Equipped backpack wrapper did not initialize");
            ItemStack expected = AttachmentUtils.getWearingBackpack(fixture.player).copy();
            helper.assertValueEqual(expected.get(ModDataComponents.BACKPACK_CONTAINER.get()), ItemContainerContents.fromItems(List.of(inner)), "initial nested backpack contents");
            InventoryComponent captured = new InventoryComponent(fixture.player);
            captured.onDeath(fixture.deathContext());
            InventoryComponent inventory = fixture.roundTrip(captured);
            helper.assertValueEqual(inventory.getAllExtraItems(true).size(), 1, "captured equipped backpack count");
            InventoryComponent.clearPlayer(fixture.player);
            helper.assertFalse(AttachmentUtils.isWearingBackpack(fixture.player), "Equipped backpack did not clear");
            helper.assertTrue(inventory.applyToPlayer(fixture.player).stream().allMatch(ItemStack::isEmpty), "Equipped backpack restoration overflowed");
            helper.assertTrue(AttachmentUtils.isWearingBackpack(fixture.player), "Recovered backpack was not equipped");
            fixture.assertStack(AttachmentUtils.getWearingBackpack(fixture.player), expected, "restored equipped backpack");
            helper.assertTrue(fixture.player.getInventory().isEmpty(), "Backpack was duplicated into vanilla inventory");
            helper.assertTrue(fixture.droppedItems().isEmpty(), "Backpack was dropped or duplicated during restoration");
            helper.succeed();
        }
    }

    @GameTest
    public static void equippedBackpackGraveClaimRestoresOnlyOnce(GameTestHelper helper) {
        try (InventoryTestFixture fixture = new InventoryTestFixture(helper)) {
            helper.assertFalse(TravelersBackpackCompat.isIntegrationEnabled(), "This profile must test the standalone equipped-backpack attachment");
            InvModCompat.invCompatMods.add(new TravelersBackpackCompat());
            ItemStack inner = fixture.stack(Items.EMERALD, 9, "claimed-backpack-contents");
            ItemStack backpack = fixture.stack(ModItems.STANDARD_TRAVELERS_BACKPACK.get(), 1, "claimed-backpack");
            backpack.set(ModDataComponents.BACKPACK_CONTAINER.get(), ItemContainerContents.fromItems(List.of(inner)));
            AttachmentUtils.equipBackpack(fixture.player, backpack);
            helper.assertTrue(AttachmentUtils.isWearingBackpack(fixture.player), "Backpack fixture was not equipped");
            ItemStack expected = AttachmentUtils.getWearingBackpack(fixture.player).copy();
            fixture.player.getInventory().setItem(0, fixture.stack(Items.GOLD_INGOT, 4, "vanilla-with-backpack"));
            fixture.player.giveExperiencePoints(315);
            var expectedVanilla = fixture.snapshot();
            InventoryComponent captured = new InventoryComponent(fixture.player);
            captured.onDeath(fixture.deathContext());
            InventoryComponent inventory = fixture.roundTrip(captured);
            ExpComponent exp = ExpComponent.fromNbt(new ExpComponent(fixture.player).toNbt());
            InventoryComponent.clearPlayer(fixture.player);
            ExpComponent.clearXp(fixture.player);
            helper.assertFalse(AttachmentUtils.isWearingBackpack(fixture.player), "Backpack remained equipped after death clear");
            GraveComponent grave = fixture.grave(inventory, exp);
            helper.assertValueEqual(grave.claim(fixture.player, helper.getLevel(), Blocks.AIR.defaultBlockState(), grave.getPos(), ItemStack.EMPTY), InteractionResult.SUCCESS, "first equipped-backpack grave claim");
            fixture.assertInventory(expectedVanilla);
            fixture.assertStack(AttachmentUtils.getWearingBackpack(fixture.player), expected, "claimed equipped backpack");
            helper.assertValueEqual(fixture.player.totalExperience, 315, "XP after equipped-backpack claim");
            helper.assertValueEqual(grave.claim(fixture.player, helper.getLevel(), Blocks.AIR.defaultBlockState(), grave.getPos(), ItemStack.EMPTY), InteractionResult.FAIL, "second equipped-backpack claim rejected");
            fixture.assertInventory(expectedVanilla);
            fixture.assertStack(AttachmentUtils.getWearingBackpack(fixture.player), expected, "backpack after second claim");
            helper.assertValueEqual(fixture.player.totalExperience, 315, "second backpack claim duplicated XP");
            helper.assertTrue(fixture.droppedItems().isEmpty(), "Backpack grave claim dropped or duplicated items");
            helper.succeed();
        }
    }
}
