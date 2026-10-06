package com.b1n_ry.yigd.gametest;

import com.b1n_ry.yigd.Yigd;
import com.b1n_ry.yigd.block.entity.GraveBlockEntity;
import com.b1n_ry.yigd.components.ExpComponent;
import com.b1n_ry.yigd.components.GraveComponent;
import com.b1n_ry.yigd.components.InventoryComponent;
import com.b1n_ry.yigd.config.ExtraFeaturesConfig;
import com.b1n_ry.yigd.config.GraveConfig;
import com.b1n_ry.yigd.config.InventoryConfig;
import com.b1n_ry.yigd.config.YigdConfig;
import com.b1n_ry.yigd.data.DeathInfoManager;
import com.b1n_ry.yigd.data.GraveItem;
import com.b1n_ry.yigd.data.GraveStatus;
import com.b1n_ry.yigd.data.TimePoint;
import com.b1n_ry.yigd.data.TranslatableDeathMessage;
import com.b1n_ry.yigd.util.DropRule;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.UUID;

/** Tests real graves and server ticks; deliberately restricted to runGameTestServer. */
@GameTestHolder(Yigd.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GraveRegressionTests {
    private static final String BATCH = "yigd_grave_regressions";
    private static final String TEMPLATE = "grave_regression_empty";
    private static final String TOKEN = "yigdRegressionToken";
    private static final int ITEM_COUNT = 3;
    private static final BlockPos GRAVE_POS = new BlockPos(2, 1, 2);
    private static final BlockPos SECOND_POS = new BlockPos(4, 1, 4);

    private static DeathInfoManager savedManager;
    private static YigdConfig activeConfig;
    private static GraveConfig savedGraveConfig;
    private static ExtraFeaturesConfig savedExtraFeatures;
    private static InventoryConfig savedInventoryConfig;

    private GraveRegressionTests() {}

    @BeforeBatch(batch = BATCH)
    public static void isolateState(ServerLevel level) {
        if (!(level.getServer() instanceof GameTestServer)) {
            throw new IllegalStateException("Run grave regression tests with runGameTestServer, in its disposable world");
        }
        if (savedManager != null) {
            throw new IllegalStateException("Grave regression batch already owns the test state");
        }
        savedManager = DeathInfoManager.INSTANCE;
        activeConfig = YigdConfig.getConfig();
        savedGraveConfig = activeConfig.graveConfig;
        savedExtraFeatures = activeConfig.extraFeatures;
        savedInventoryConfig = activeConfig.inventoryConfig;

        DeathInfoManager.INSTANCE = new DeathInfoManager();
        // Keep the config object itself: GraveBlockEntity caches that object between ticks.
        activeConfig.graveConfig = new GraveConfig();
        activeConfig.graveConfig.maxBackupsPerPerson = 1;
        activeConfig.graveConfig.dropFromOldestWhenDeleted = true;
        activeConfig.graveConfig.dropItemsIfDestroyed = true;
        activeConfig.extraFeatures = new ExtraFeaturesConfig();
        activeConfig.inventoryConfig = new InventoryConfig();
    }

    @AfterBatch(batch = BATCH)
    public static void restoreState(ServerLevel level) {
        if (savedManager == null) return;
        activeConfig.graveConfig = savedGraveConfig;
        activeConfig.extraFeatures = savedExtraFeatures;
        activeConfig.inventoryConfig = savedInventoryConfig;
        DeathInfoManager.INSTANCE = savedManager;
        savedManager = null;
        activeConfig = null;
        savedGraveConfig = null;
        savedExtraFeatures = null;
        savedInventoryConfig = null;
        // Never clear real player records or manually drain the server's deferred callback queue.
    }

    @GameTest(batch = BATCH, template = TEMPLATE, timeoutTicks = 20)
    public static void entityAttacksProtectOnlyUnclaimedPlayerGraves(GameTestHelper helper) {
        Fixture fixture = createGrave(helper, owner(), GRAVE_POS, 0);
        Entity attacker = EntityType.ZOMBIE.create(helper.getLevel());
        check(attacker != null, "Could not create an entity attacker");
        BlockState state = Yigd.GRAVE_BLOCK.defaultBlockState();
        check(!Yigd.GRAVE_BLOCK.canEntityDestroy(state, fixture.level, fixture.pos, attacker),
                "A normal entity can destroy an unclaimed player's grave");

        fixture.grave.setStatus(GraveStatus.CLAIMED);
        graveEntity(fixture.level, fixture.pos).setClaimed(true);
        check(Yigd.GRAVE_BLOCK.canEntityDestroy(state, fixture.level, fixture.pos, attacker),
                "Claimed grave should retain inherited entity behavior");
        BlockPos decorativePos = helper.absolutePos(SECOND_POS);
        fixture.level.setBlockAndUpdate(decorativePos, state);
        check(Yigd.GRAVE_BLOCK.canEntityDestroy(state, fixture.level, decorativePos, attacker),
                "Decorative grave should retain inherited entity behavior");
        helper.runAfterDelay(2, helper::succeed);
    }

    @GameTest(batch = BATCH, template = TEMPLATE, timeoutTicks = 20)
    public static void graveImmunityTagsLoad(GameTestHelper helper) {
        BlockState state = Yigd.GRAVE_BLOCK.defaultBlockState();
        check(state.is(BlockTags.DRAGON_IMMUNE), "Grave is missing the loaded dragon_immune block tag");
        check(state.is(BlockTags.WITHER_IMMUNE), "Grave is missing the loaded wither_immune block tag");
        check(ForgeRegistries.ITEMS.tags().isKnownTagName(TagKey.create(Registries.ITEM,
                        new ResourceLocation(Yigd.MOD_ID, "soulbindable"))),
                "The soulbindable item tag failed to load when optional integrations were absent");
        helper.runAfterDelay(2, helper::succeed);
    }

    @GameTest(batch = BATCH, template = TEMPLATE, timeoutTicks = 20)
    public static void offlineDestructionDropsOnceAndKeepsBackup(GameTestHelper helper) {
        Fixture fixture = createGrave(helper, owner(), GRAVE_POS, 0);
        check(fixture.level.getServer().getPlayerList().getPlayer(fixture.grave.getOwner().getId()) == null,
                "Fixture owner must be offline");
        GraveBlockEntity removed = graveEntity(fixture.level, fixture.pos);
        fixture.level.setBlockAndUpdate(fixture.pos, Blocks.AIR.defaultBlockState());
        // Duplicate notifications must not duplicate loot; the actual onRemove queues another one.
        removed.onBroken();
        removed.onBroken();
        helper.runAfterDelay(2, () -> {
            check(fixture.grave.getStatus() == GraveStatus.DESTROYED, "Removal did not mark the grave destroyed");
            check(DeathInfoManager.INSTANCE.getGrave(fixture.grave.getGraveId()).isPresent(),
                    "Destruction deleted the recovery backup");
            assertDroppedOnce(fixture);
            helper.succeed();
        });
    }

    @GameTest(batch = BATCH, template = TEMPLATE, timeoutTicks = 20)
    public static void sameUuidReplacementDoesNotDestroyOrDrop(GameTestHelper helper) {
        Fixture fixture = createGrave(helper, owner(), GRAVE_POS, 0);
        fixture.level.setBlockAndUpdate(fixture.pos, Blocks.AIR.defaultBlockState());
        placeGrave(fixture.level, fixture.pos, fixture.grave);
        helper.runAfterDelay(2, () -> {
            assertUnclaimedAndPresent(fixture);
            assertNoDrops(fixture);
            helper.succeed();
        });
    }

    @GameTest(batch = BATCH, template = TEMPLATE, timeoutTicks = 20)
    public static void successfulClaimDoesNotDropAgain(GameTestHelper helper) {
        Fixture fixture = createGrave(helper, owner(), GRAVE_POS, 7);
        ServerPlayer player = new ServerPlayer(fixture.level.getServer(), fixture.level, fixture.grave.getOwner());
        InteractionResult result = fixture.grave.claim(player, fixture.level, Blocks.AIR.defaultBlockState(),
                fixture.pos, ItemStack.EMPTY);
        check(result == InteractionResult.SUCCESS, "Owner could not claim the fixture grave");
        helper.runAfterDelay(2, () -> {
            assertTransferredAndClaimed(fixture, player);
            helper.succeed();
        });
    }

    @GameTest(batch = BATCH, template = TEMPLATE, timeoutTicks = 20)
    public static void restoredClaimedRemovalDoesNotDropAgain(GameTestHelper helper) {
        Fixture fixture = createGrave(helper, owner(), GRAVE_POS, 7);
        ServerPlayer player = new ServerPlayer(fixture.level.getServer(), fixture.level, fixture.grave.getOwner());
        // Use the same transfer/status/removal operations as the restore command.
        fixture.grave.applyToPlayer(player, fixture.level, Vec3.atCenterOf(fixture.pos), true);
        fixture.grave.setStatus(GraveStatus.CLAIMED);
        check(fixture.grave.removeGraveBlock(), "Restore did not remove the grave block");
        helper.runAfterDelay(2, () -> {
            assertTransferredAndClaimed(fixture, player);
            helper.succeed();
        });
    }

    @GameTest(batch = BATCH, template = TEMPLATE, timeoutTicks = 20)
    public static void refusedMiningRestoresGraveWithoutDropping(GameTestHelper helper) {
        Fixture fixture = createGrave(helper, owner(), GRAVE_POS, 0);
        ServerPlayer player = new ServerPlayer(fixture.level.getServer(), fixture.level, fixture.grave.getOwner());
        GraveBlockEntity removed = graveEntity(fixture.level, fixture.pos);
        BlockState state = fixture.level.getBlockState(fixture.pos);
        // Forge removes the block before playerDestroy; onBreak=false must put the grave back.
        fixture.level.setBlockAndUpdate(fixture.pos, Blocks.AIR.defaultBlockState());
        Yigd.GRAVE_BLOCK.playerDestroy(fixture.level, player, fixture.pos, state, removed, ItemStack.EMPTY);
        helper.runAfterDelay(2, () -> {
            assertUnclaimedAndPresent(fixture);
            check(player.getInventory().isEmpty(), "Refused mining transferred the grave inventory");
            assertNoDrops(fixture);
            helper.succeed();
        });
    }

    @GameTest(batch = BATCH, template = TEMPLATE, timeoutTicks = 20)
    public static void facingChangeDoesNotDestroyGrave(GameTestHelper helper) {
        Fixture fixture = createGrave(helper, owner(), GRAVE_POS, 0);
        fixture.level.setBlockAndUpdate(fixture.pos, fixture.level.getBlockState(fixture.pos)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
        helper.runAfterDelay(2, () -> {
            assertUnclaimedAndPresent(fixture);
            assertNoDrops(fixture);
            helper.succeed();
        });
    }

    @GameTest(batch = BATCH, template = TEMPLATE, timeoutTicks = 20)
    public static void relocatedSameUuidGraveDoesNotDrop(GameTestHelper helper) {
        Fixture fixture = createGrave(helper, owner(), GRAVE_POS, 0);
        fixture.level.setBlockAndUpdate(fixture.pos, Blocks.AIR.defaultBlockState());
        BlockPos relocatedPos = helper.absolutePos(SECOND_POS);
        fixture.grave.setPos(relocatedPos);
        placeGrave(fixture.level, relocatedPos, fixture.grave);
        helper.runAfterDelay(2, () -> {
            check(fixture.grave.getStatus() == GraveStatus.UNCLAIMED, "Relocation was treated as destruction");
            check(fixture.grave.getGraveId().equals(graveEntity(fixture.level, relocatedPos).getGraveId()),
                    "Relocated block lost its grave UUID");
            assertNoDrops(fixture);
            helper.succeed();
        });
    }

    @GameTest(batch = BATCH, template = TEMPLATE, timeoutTicks = 20)
    public static void backupEvictionDropsOldUnclaimedInventoryOnce(GameTestHelper helper) {
        GameProfile owner = owner();
        Fixture oldest = createGrave(helper, owner, GRAVE_POS, 0);
        Fixture latest = createGrave(helper, owner, SECOND_POS, 0);
        helper.runAfterDelay(2, () -> {
            check(DeathInfoManager.INSTANCE.getGrave(oldest.grave.getGraveId()).isEmpty(),
                    "Oldest backup was not evicted at a limit of one");
            check(oldest.grave.getStatus() == GraveStatus.DESTROYED, "Eviction did not remove the old unclaimed grave");
            check(oldest.level.getBlockState(oldest.pos).isAir(), "Evicted grave block is still present");
            assertDroppedOnce(oldest);
            assertUnclaimedAndPresent(latest);
            assertNoDrops(latest);
            check(DeathInfoManager.INSTANCE.getBackupData(owner).size() == 1, "Backup limit was not enforced");
            helper.succeed();
        });
    }

    @GameTest(batch = BATCH, template = TEMPLATE, timeoutTicks = 20)
    public static void unavailableDimensionEvictionDoesNotCrash(GameTestHelper helper) {
        GameProfile owner = owner();
        UUID token = UUID.randomUUID();
        GraveComponent unavailable = new GraveComponent(owner, inventory(token), experience(0),
                ResourceKey.create(Registries.DIMENSION, new ResourceLocation(Yigd.MOD_ID, "absent_test_dimension")),
                helper.absolutePos(GRAVE_POS), deathMessage(owner), UUID.randomUUID(), GraveStatus.UNCLAIMED,
                true, new TimePoint(helper.getLevel()), null);
        unavailable.backUp();
        Fixture latest = createGrave(helper, owner, SECOND_POS, 0);
        helper.runAfterDelay(2, () -> {
            check(unavailable.getWorld() == null, "Fixture should represent a removed dimension");
            check(DeathInfoManager.INSTANCE.getGrave(unavailable.getGraveId()).isEmpty(),
                    "Unavailable-dimension backup was not evicted");
            assertUnclaimedAndPresent(latest);
            assertNoDrops(latest);
            check(drops(latest.level, helper.absolutePos(GRAVE_POS), token).isEmpty(),
                    "Absent-dimension contents should not drop into the current dimension");
            helper.succeed();
        });
    }

    private static Fixture createGrave(GameTestHelper helper, GameProfile owner, BlockPos relativePos, int xp) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(relativePos);
        UUID token = UUID.randomUUID();
        GraveComponent grave = new GraveComponent(owner, inventory(token), experience(xp), level,
                Vec3.atCenterOf(pos), deathMessage(owner), null);
        grave.backUp();
        placeGrave(level, pos, grave);
        return new Fixture(level, pos, grave, token);
    }

    private static void placeGrave(ServerLevel level, BlockPos pos, GraveComponent grave) {
        check(level.setBlockAndUpdate(pos, Yigd.GRAVE_BLOCK.defaultBlockState()), "Could not place fixture grave");
        GraveBlockEntity blockEntity = graveEntity(level, pos);
        blockEntity.setPreviousState(Blocks.AIR.defaultBlockState());
        blockEntity.setComponent(grave);
    }

    private static GraveBlockEntity graveEntity(ServerLevel level, BlockPos pos) {
        check(level.getBlockEntity(pos) instanceof GraveBlockEntity, "Missing grave block entity at " + pos);
        return (GraveBlockEntity) level.getBlockEntity(pos);
    }

    private static GameProfile owner() {
        return new GameProfile(UUID.randomUUID(), "YigdTest");
    }

    private static TranslatableDeathMessage deathMessage(GameProfile owner) {
        return new TranslatableDeathMessage("generic", owner.getName(), null, null, null, null);
    }

    private static InventoryComponent inventory(UUID token) {
        ItemStack stack = new ItemStack(Items.DIAMOND, ITEM_COUNT);
        stack.getOrCreateTag().putUUID(TOKEN, token);
        NonNullList<GraveItem> items = NonNullList.withSize(41, InventoryComponent.EMPTY_GRAVE_ITEM);
        items.set(0, new GraveItem(stack, DropRule.PUT_IN_GRAVE));
        CompoundTag vanilla = InventoryComponent.listToNbt(items, graveItem -> {
            CompoundTag item = graveItem.stack.save(new CompoundTag());
            item.putString("dropRule", graveItem.dropRule.name());
            return item;
        }, graveItem -> graveItem.stack.isEmpty());
        vanilla.putInt("mainSize", 36);
        vanilla.putInt("armorSize", 4);
        vanilla.putInt("offHandSize", 1);
        CompoundTag inventory = new CompoundTag();
        inventory.put("vanilla", vanilla);
        inventory.put("mods", new CompoundTag());
        return InventoryComponent.fromNbt(inventory);
    }

    private static ExpComponent experience(int xp) {
        CompoundTag nbt = new CompoundTag();
        nbt.putInt("value", xp);
        nbt.putDouble("original", xp);
        return ExpComponent.fromNbt(nbt);
    }

    private static void assertUnclaimedAndPresent(Fixture fixture) {
        check(fixture.grave.getStatus() == GraveStatus.UNCLAIMED, "Grave is no longer unclaimed");
        check(fixture.grave.getGraveId().equals(graveEntity(fixture.level, fixture.pos).getGraveId()),
                "Grave block no longer links to the retained backup");
        check(DeathInfoManager.INSTANCE.getGrave(fixture.grave.getGraveId()).isPresent(), "Missing grave backup");
    }

    private static void assertTransferredAndClaimed(Fixture fixture, ServerPlayer player) {
        check(fixture.grave.getStatus() == GraveStatus.CLAIMED, "Deferred removal changed a claimed grave's status");
        check(fixture.level.getBlockState(fixture.pos).isAir(), "Claimed grave block was not removed");
        ItemStack restored = player.getInventory().getItem(0);
        check(matches(restored, fixture.token) && restored.getCount() == ITEM_COUNT, "Inventory was not transferred exactly once");
        check(player.totalExperience == 7, "Experience was not transferred exactly once");
        assertNoDrops(fixture);
    }

    private static void assertDroppedOnce(Fixture fixture) {
        List<ItemEntity> entities = drops(fixture.level, fixture.pos, fixture.token);
        check(entities.size() == 1, "Expected one dropped item entity, found " + entities.size());
        check(entities.get(0).getItem().getCount() == ITEM_COUNT, "Contents were missing or dropped more than once");
    }

    private static void assertNoDrops(Fixture fixture) {
        check(drops(fixture.level, fixture.pos, fixture.token).isEmpty(), "Grave unexpectedly dropped its inventory");
    }

    private static List<ItemEntity> drops(ServerLevel level, BlockPos pos, UUID token) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3),
                entity -> matches(entity.getItem(), token));
    }

    private static boolean matches(ItemStack stack, UUID token) {
        return stack.hasTag() && stack.getTag().hasUUID(TOKEN) && token.equals(stack.getTag().getUUID(TOKEN));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }

    private record Fixture(ServerLevel level, BlockPos pos, GraveComponent grave, UUID token) {}
}
