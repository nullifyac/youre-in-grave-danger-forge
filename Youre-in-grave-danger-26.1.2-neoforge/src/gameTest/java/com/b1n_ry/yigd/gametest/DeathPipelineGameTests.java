package com.b1n_ry.yigd.gametest;

import com.b1n_ry.yigd.Yigd;
import com.b1n_ry.yigd.block.entity.GraveBlockEntity;
import com.b1n_ry.yigd.components.GraveComponent;
import com.b1n_ry.yigd.data.DeathInfoManager;
import com.b1n_ry.yigd.data.GraveStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;

import java.util.List;

/** Exercises actual damage, death, drop, clone and claim handlers instead of calling DeathHandler directly. */
public final class DeathPipelineGameTests {
    private static InventoryTestFixture active;
    private static boolean previousKeepInventory;

    private DeathPipelineGameTests() { }

    public static Object setup(ServerLevel level, String environment) {
        previousKeepInventory = level.getGameRules().get(GameRules.KEEP_INVENTORY);
        level.getGameRules().set(GameRules.KEEP_INVENTORY, false, level.getServer());
        return null;
    }

    public static void teardown(ServerLevel level, Object ignored) {
        try {
            if (active != null) {
                Yigd.UNFINISHED_DEATHS.remove(active.marker);
                active.close();
            }
        } finally {
            active = null;
            level.getGameRules().set(GameRules.KEEP_INVENTORY, previousKeepInventory, level.getServer());
        }
    }

    @GameTest(timeoutTicks = 160)
    public static void realDeathRespawnAndOwnerClaimRecoverNamedStackExactlyOnce(GameTestHelper helper) {
        active = new InventoryTestFixture(helper);
        InventoryTestFixture fixture = active;
        for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) {
            helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 0, z)), Blocks.STONE.defaultBlockState(), 3);
        }
        ItemStack expected = fixture.stack(Items.DIAMOND, 17, "native-death-pipeline");
        ItemStack body = fixture.stack(Items.DIAMOND_HORSE_ARMOR, 1, "native-death-body-41");
        ItemStack saddle = fixture.stack(Items.SADDLE, 1, "native-death-saddle-42");
        List<ItemStack> expectedStacks = List.of(expected, body, saddle);
        fixture.player.getInventory().setItem(0, expected.copy());
        fixture.player.getInventory().setItem(41, body.copy());
        fixture.player.getInventory().setItem(42, saddle.copy());
        fixture.player.setHealth(1);
        fixture.player.setAbsorptionAmount(0);
        fixture.player.invulnerableTime = 0;
        fixture.player.hurtServer(helper.getLevel(), fixture.player.damageSources().generic(), 1000);
        helper.assertTrue(fixture.player.isDeadOrDying(), "Native ServerPlayer did not die through actual damage");
        helper.assertTrue(fixture.player.getInventory().isEmpty(), "Actual death did not clear the captured inventory");
        helper.assertTrue(fixture.player.getInventory().getItem(41).isEmpty()
                && fixture.player.getInventory().getItem(42).isEmpty(), "Native BODY/SADDLE equipment was not captured and cleared");
        helper.assertTrue(!Yigd.UNFINISHED_DEATHS.containsKey(fixture.marker), "LivingDrops did not finalize the pending death");

        helper.runAfterDelay(3, () -> {
            var profile = ResolvableProfile.createResolved(fixture.player.getGameProfile());
            List<GraveComponent> backups = DeathInfoManager.INSTANCE.getBackupData(profile);
            helper.assertValueEqual(backups.size(), 1, "actual native death backup count");
            GraveComponent grave = backups.getFirst();
            fixture.graves.add(grave);
            helper.assertTrue(grave.getStatus() == GraveStatus.UNCLAIMED, "Native death did not leave an unclaimed grave");
            helper.assertTrue(helper.getLevel().getBlockEntity(grave.getPos()) instanceof GraveBlockEntity,
                    "Native death pipeline did not create a physical grave block entity");
            GraveBlockEntity blockEntity = (GraveBlockEntity) helper.getLevel().getBlockEntity(grave.getPos());
            helper.assertTrue(grave.getGraveId().equals(blockEntity.getGraveId()), "Generated block entity has a different grave UUID");
            helper.assertTrue(fixture.droppedItems().isEmpty(), "Captured named stack leaked into native item drops");

            ServerPlayer respawned = fixture.playerFixture.respawn();
            helper.assertTrue(!respawned.isDeadOrDying(), "Vanilla PlayerList respawn did not produce a living player");
            helper.assertTrue(respawned.getInventory().isEmpty(), "Respawn duplicated the captured stack");
            grave.claim(respawned, helper.getLevel(), blockEntity.getPreviousState(), grave.getPos(), ItemStack.EMPTY);
            helper.assertTrue(grave.getStatus() == GraveStatus.CLAIMED, "Owner claim was rejected after native respawn");
            assertNamedStacks(helper, fixture, respawned, expectedStacks);
            grave.claim(respawned, helper.getLevel(), Blocks.AIR.defaultBlockState(), grave.getPos(), ItemStack.EMPTY);
            assertNamedStacks(helper, fixture, respawned, expectedStacks);
            helper.runAfterDelay(3, () -> {
                assertNamedStacks(helper, fixture, respawned, expectedStacks);
                helper.assertTrue(fixture.droppedItems().isEmpty(), "Deferred grave removal dropped a second copy");
                helper.succeed();
            });
        });
    }

    private static void assertNamedStacks(GameTestHelper helper, InventoryTestFixture fixture,
                                         ServerPlayer player, List<ItemStack> expectedStacks) {
        for (ItemStack expected : expectedStacks) {
            List<ItemStack> matches = java.util.stream.IntStream.range(0, player.getInventory().getContainerSize())
                    .mapToObj(player.getInventory()::getItem)
                    .filter(stack -> ItemStack.isSameItemSameComponents(stack, expected)).toList();
            helper.assertValueEqual(matches.size(), 1, "named recovered stack copies: " + expected.getHoverName().getString());
            fixture.assertStack(matches.getFirst(), expected, "native recovered named stack");
        }
        helper.assertTrue(ItemStack.isSameItemSameComponents(player.getInventory().getItem(41), expectedStacks.get(1)),
                "Native claim moved BODY equipment away from slot 41");
        helper.assertTrue(ItemStack.isSameItemSameComponents(player.getInventory().getItem(42), expectedStacks.get(2)),
                "Native claim moved SADDLE equipment away from slot 42");
    }
}
