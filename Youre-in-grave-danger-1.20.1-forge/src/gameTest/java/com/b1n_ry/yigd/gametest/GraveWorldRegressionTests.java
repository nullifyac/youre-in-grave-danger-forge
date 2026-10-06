package com.b1n_ry.yigd.gametest;

import com.b1n_ry.yigd.Yigd;
import com.b1n_ry.yigd.block.entity.GraveBlockEntity;
import com.b1n_ry.yigd.components.ExpComponent;
import com.b1n_ry.yigd.components.GraveComponent;
import com.b1n_ry.yigd.components.InventoryComponent;
import com.b1n_ry.yigd.components.RespawnComponent;
import com.b1n_ry.yigd.config.ExtraFeaturesConfig;
import com.b1n_ry.yigd.config.GraveConfig;
import com.b1n_ry.yigd.config.InventoryConfig;
import com.b1n_ry.yigd.config.MapEntryConfig;
import com.b1n_ry.yigd.config.YigdConfig;
import com.b1n_ry.yigd.data.DeathContext;
import com.b1n_ry.yigd.data.DeathInfoManager;
import com.b1n_ry.yigd.data.GraveItem;
import com.b1n_ry.yigd.data.GraveStatus;
import com.b1n_ry.yigd.data.TranslatableDeathMessage;
import com.b1n_ry.yigd.util.DropRule;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;

/** Exercises generation, native boss removal and saved-data recovery in a disposable server. */
@GameTestHolder(Yigd.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GraveWorldRegressionTests {
    private static final String ENABLED = "yigd_void_enabled";
    private static final String DISABLED = "yigd_void_disabled";
    private static final String PROTECTION = "yigd_boss_and_recovery";
    private static final String PERSISTENCE = "yigd_saved_data_reload";
    private static final String TEMPLATE = "grave_regression_empty";
    private static final String TOKEN = "yigdWorldRegressionToken";
    private static final BlockPos GRAVE_POS = new BlockPos(2, 1, 2);
    private static final BlockPos CONTROL_POS = new BlockPos(3, 1, 2);
    private static final BlockPos SECOND_POS = new BlockPos(4, 1, 4);

    private static DeathInfoManager savedManager;
    private static YigdConfig config;
    private static GraveConfig savedGraves;
    private static ExtraFeaturesConfig savedExtras;
    private static InventoryConfig savedInventory;
    private static boolean savedMobGriefing;

    private GraveWorldRegressionTests() {}

    @BeforeBatch(batch = ENABLED)
    public static void beforeEnabled(ServerLevel level) { isolate(level, true); }

    @BeforeBatch(batch = DISABLED)
    public static void beforeDisabled(ServerLevel level) { isolate(level, false); }

    @BeforeBatch(batch = PROTECTION)
    public static void beforeProtection(ServerLevel level) { isolate(level, true); }

    @BeforeBatch(batch = PERSISTENCE)
    public static void beforePersistence(ServerLevel level) { isolate(level, true); }

    @AfterBatch(batch = ENABLED)
    public static void afterEnabled(ServerLevel level) { restore(level); }

    @AfterBatch(batch = DISABLED)
    public static void afterDisabled(ServerLevel level) { restore(level); }

    @AfterBatch(batch = PROTECTION)
    public static void afterProtection(ServerLevel level) { restore(level); }

    @AfterBatch(batch = PERSISTENCE)
    public static void afterPersistence(ServerLevel level) { restore(level); }

    private static void isolate(ServerLevel level, boolean allowVoid) {
        check(level.getServer() instanceof GameTestServer, "Use runGameTestServer; these tests require its disposable world");
        check(savedManager == null, "Another regression batch still owns the isolated state");
        savedManager = DeathInfoManager.INSTANCE;
        config = YigdConfig.getConfig();
        savedGraves = config.graveConfig;
        savedExtras = config.extraFeatures;
        savedInventory = config.inventoryConfig;
        savedMobGriefing = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        DeathInfoManager.INSTANCE = new DeathInfoManager();
        config.graveConfig = new GraveConfig();
        config.graveConfig.generateGraveInVoid = allowVoid;
        // The default false value is intentional: removal should preserve recoverable backup contents.
        config.extraFeatures = new ExtraFeaturesConfig();
        config.inventoryConfig = new InventoryConfig();
        level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true, level.getServer());
    }

    private static void restore(ServerLevel level) {
        if (savedManager == null) return;
        config.graveConfig = savedGraves;
        config.extraFeatures = savedExtras;
        config.inventoryConfig = savedInventory;
        level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(savedMobGriefing, level.getServer());
        DeathInfoManager.INSTANCE = savedManager;
        savedManager = null;
        config = null;
        savedGraves = null;
        savedExtras = null;
        savedInventory = null;
    }

    @GameTest(batch = ENABLED, template = TEMPLATE, timeoutTicks = 30)
    public static void voidGenerationCreatesLinkedGraveAtConfiguredMinimum(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos reference = helper.absolutePos(GRAVE_POS);
        int minimum = minimumY(level);
        BlockPos target = new BlockPos(reference.getX(), minimum, reference.getZ());
        BlockState previous = level.getBlockState(target);
        BlockState previousSupport = level.getBlockState(target.below());
        check(level.getBlockEntity(target) == null, "Void fixture target must not replace a block entity");
        level.setBlockAndUpdate(target, Blocks.AIR.defaultBlockState());
        Vec3 deathPos = new Vec3(reference.getX() + .5, level.getMinBuildHeight() - 80, reference.getZ() + .5);
        Fixture fixture = component(level, deathPos, 7);
        ServerPlayer player = player(fixture);
        RespawnComponent respawn = new RespawnComponent(player);
        fixture.grave.generateOrDrop(Direction.NORTH,
                new DeathContext(player, level, deathPos, level.damageSources().fellOutOfWorld()), respawn);
        helper.runAfterDelay(2, () -> {
            check(respawn.wasGraveGenerated(), "Void death did not generate a grave with the option enabled");
            check(fixture.grave.getPos().equals(target), "Void grave was not raised to the configured minimum Y");
            assertLinked(fixture.grave);
            check(fixture.grave.getInventoryComponent().getItems().get(0).dropRule == DropRule.PUT_IN_GRAVE,
                    "Generated grave contents were incorrectly marked dropped");
            assertNoDrops(fixture, target);
            fixture.grave.setStatus(GraveStatus.CLAIMED);
            level.setBlockAndUpdate(target, previous);
            level.setBlockAndUpdate(target.below(), previousSupport);
            helper.succeed();
        });
    }

    @GameTest(batch = DISABLED, template = TEMPLATE, timeoutTicks = 30)
    public static void disabledVoidGenerationDropsButRetainsRecoverableBackup(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos reference = helper.absolutePos(GRAVE_POS);
        Vec3 deathPos = new Vec3(reference.getX() + .5, level.getMinBuildHeight() - 80, reference.getZ() + .5);
        Fixture fixture = component(level, deathPos, 7);
        ServerPlayer player = player(fixture);
        RespawnComponent respawn = new RespawnComponent(player);
        fixture.grave.generateOrDrop(Direction.NORTH,
                new DeathContext(player, level, deathPos, level.damageSources().fellOutOfWorld()), respawn);
        helper.runAfterDelay(2, () -> {
            check(!respawn.wasGraveGenerated(), "Disabled void generation unexpectedly created a grave");
            check(level.getBlockEntity(fixture.grave.getPos()) == null, "A grave block exists outside build height");
            check(fixture.grave.getInventoryComponent().getItems().get(0).dropRule == DropRule.DROP,
                    "Denied generation did not mark the original inventory as dropped");
            check(DeathInfoManager.INSTANCE.getGrave(fixture.grave.getGraveId()).isPresent(),
                    "Void drops deleted the death backup");
            assertNoDrops(fixture, fixture.grave.getPos());
            // GUI restoration can include DROP items even after their entities are lost in the void.
            fixture.grave.applyToPlayer(player, level, Vec3.atCenterOf(reference), true, rule -> rule == DropRule.DROP);
            fixture.grave.setStatus(GraveStatus.CLAIMED);
            assertRecovered(fixture, player);
            helper.succeed();
        });
    }

    @GameTest(batch = PROTECTION, template = TEMPLATE, timeoutTicks = 30)
    public static void dragonWallRemovalDestroysControlButPreservesGrave(GameTestHelper helper) {
        Fixture fixture = placed(helper, GRAVE_POS, 0);
        BlockPos control = helper.absolutePos(CONTROL_POS);
        fixture.level.setBlockAndUpdate(control, Blocks.COBBLESTONE.defaultBlockState());
        EnderDragon dragon = EntityType.ENDER_DRAGON.create(fixture.level);
        check(dragon != null, "Could not create a real Ender Dragon");
        dragon.setPos(Vec3.atCenterOf(fixture.grave.getPos()));
        invokeDragonWalls(dragon, new AABB(fixture.grave.getPos()).expandTowards(1, 0, 0));
        helper.runAfterDelay(2, () -> {
            check(fixture.level.getBlockState(control).isAir(), "Dragon removal routine did not destroy the breakable control");
            assertLinked(fixture.grave);
            check(fixture.grave.getStatus() == GraveStatus.UNCLAIMED, "Dragon destroyed the protected grave");
            assertNoDrops(fixture, fixture.grave.getPos());
            helper.succeed();
        });
    }

    @GameTest(batch = PROTECTION, template = TEMPLATE, timeoutTicks = 40)
    public static void witherBodyRemovalDestroysControlButPreservesGrave(GameTestHelper helper) {
        Fixture fixture = placed(helper, GRAVE_POS, 0);
        BlockPos control = helper.absolutePos(CONTROL_POS);
        fixture.level.setBlockAndUpdate(control, Blocks.COBBLESTONE.defaultBlockState());
        BodyBreakingWither wither = new BodyBreakingWither(fixture.level);
        wither.setPos(Vec3.atCenterOf(fixture.grave.getPos()));
        wither.setHealth(wither.getMaxHealth());
        check(wither.hurt(fixture.level.damageSources().generic(), 1), "Could not arm the wither's damage-triggered destruction");
        int[] aiSteps = {0};
        // Keep position deterministic while executing the actual native AI destruction countdown on server ticks.
        helper.onEachTick(() -> {
            if (aiSteps[0]++ < 20) wither.advanceBodyBreakingAi();
        });
        helper.runAfterDelay(22, () -> {
            check(fixture.level.getBlockState(control).isAir(), "Wither body destruction did not remove the breakable control");
            assertLinked(fixture.grave);
            check(fixture.grave.getStatus() == GraveStatus.UNCLAIMED, "Wither destroyed the protected grave");
            assertNoDrops(fixture, fixture.grave.getPos());
            helper.succeed();
        });
    }

    @GameTest(batch = PROTECTION, template = TEMPLATE, timeoutTicks = 30)
    public static void defaultDestructionPreservesInventoryForRecovery(GameTestHelper helper) {
        Fixture fixture = placed(helper, GRAVE_POS, 7);
        BlockPos originalPos = fixture.grave.getPos();
        fixture.level.setBlockAndUpdate(originalPos, Blocks.AIR.defaultBlockState());
        helper.runAfterDelay(2, () -> {
            check(!config.graveConfig.dropItemsIfDestroyed, "Recovery fixture must use the default no-drop setting");
            check(fixture.grave.getStatus() == GraveStatus.DESTROYED, "Removal was not detected");
            assertNoDrops(fixture, originalPos);
            GraveComponent retained = DeathInfoManager.INSTANCE.getGrave(fixture.grave.getGraveId())
                    .orElseThrow(() -> new GameTestAssertException("Destroyed grave lost its backup"));
            ServerPlayer player = player(fixture);
            retained.applyToPlayer(player, fixture.level, Vec3.atCenterOf(originalPos), true);
            retained.setStatus(GraveStatus.CLAIMED);
            retained.removeGraveBlock();
            helper.runAfterDelay(2, () -> {
                assertRecovered(fixture, player);
                assertNoDrops(fixture, originalPos);
                helper.succeed();
            });
        });
    }

    @GameTest(batch = PERSISTENCE, template = TEMPLATE, timeoutTicks = 30)
    public static void compressedSavedDataReloadRestoresBackupAndRelinksGrave(GameTestHelper helper) {
        Fixture destroyed = placed(helper, GRAVE_POS, 7);
        Fixture surviving = placed(helper, SECOND_POS, 0);
        destroyed.level.setBlockAndUpdate(destroyed.grave.getPos(), Blocks.AIR.defaultBlockState());
        helper.runAfterDelay(2, () -> {
            check(destroyed.grave.getStatus() == GraveStatus.DESTROYED, "Persistence fixture removal was not detected");
            CompoundTag checkpoint = diskRoundTrip(destroyed.level, DeathInfoManager.INSTANCE.save(new CompoundTag()));
            BlockPos survivingPos = surviving.grave.getPos();
            CompoundTag blockCheckpoint = blockEntity(surviving.grave).saveWithFullMetadata();
            surviving.level.setBlockAndUpdate(survivingPos, Blocks.AIR.defaultBlockState());
            surviving.level.setBlockAndUpdate(survivingPos, Yigd.GRAVE_BLOCK.defaultBlockState());
            GraveBlockEntity reloadedBlock = (GraveBlockEntity) surviving.level.getBlockEntity(survivingPos);
            check(reloadedBlock != null, "Could not recreate the saved grave block entity");
            reloadedBlock.load(blockCheckpoint);

            // A fresh manager removes in-memory component identity, as a new server process would.
            DeathInfoManager.INSTANCE = new DeathInfoManager();
            DeathInfoManager restored = DeathInfoManager.fromNbt(checkpoint, destroyed.level.getServer());
            GraveComponent recovered = restored.getGrave(destroyed.grave.getGraveId())
                    .orElseThrow(() -> new GameTestAssertException("Destroyed backup was not reloaded"));
            GraveComponent linked = restored.getGrave(surviving.grave.getGraveId())
                    .orElseThrow(() -> new GameTestAssertException("Surviving grave was not reloaded"));
            check(recovered != destroyed.grave, "Reload reused the old in-memory backup component");
            check(recovered.getStatus() == GraveStatus.DESTROYED, "Reload changed destroyed backup status");
            check(recovered.getWorld() == destroyed.level, "Reload lost the grave's dimension binding");
            check(recovered.getPos().equals(destroyed.grave.getPos()), "Reload lost the grave coordinates");
            check(reloadedBlock.getComponent() == linked, "Reload did not relink the block entity to the new backup component");
            ServerPlayer player = player(destroyed);
            recovered.applyToPlayer(player, destroyed.level, Vec3.atCenterOf(destroyed.grave.getPos()), true);
            recovered.setStatus(GraveStatus.CLAIMED);
            helper.runAfterDelay(2, () -> {
                assertRecovered(destroyed, player);
                check(linked.getStatus() == GraveStatus.UNCLAIMED, "Deferred removal destroyed the reloaded same-UUID grave");
                assertLinked(linked);
                assertNoDrops(destroyed, destroyed.grave.getPos());
                helper.succeed();
            });
        });
    }

    private static void invokeDragonWalls(EnderDragon dragon, AABB bounds) {
        // Vanilla keeps wall removal private; signature lookup avoids hardcoding a mapped method name.
        Method[] matches = Arrays.stream(EnderDragon.class.getDeclaredMethods())
                .filter(method -> method.getReturnType() == boolean.class
                        && Arrays.equals(method.getParameterTypes(), new Class<?>[]{AABB.class}))
                .toArray(Method[]::new);
        check(matches.length == 1, "Could not uniquely identify the native dragon wall-removal routine");
        try {
            matches[0].setAccessible(true);
            matches[0].invoke(dragon, bounds);
        } catch (ReflectiveOperationException exception) {
            throw new GameTestAssertException("Native dragon wall removal failed: " + exception);
        }
    }

    private static CompoundTag diskRoundTrip(ServerLevel level, CompoundTag checkpoint) {
        Path temporary = null;
        try {
            temporary = Files.createTempFile(level.getServer().getWorldPath(LevelResource.ROOT), "yigd-regression-", ".dat");
            NbtIo.writeCompressed(checkpoint, temporary.toFile());
            return NbtIo.readCompressed(temporary.toFile());
        } catch (IOException exception) {
            throw new GameTestAssertException("Saved-data disk round trip failed: " + exception);
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); }
                catch (IOException exception) { throw new GameTestAssertException("Could not remove temporary checkpoint: " + exception); }
            }
        }
    }

    private static int minimumY(ServerLevel level) {
        String dimension = level.dimension().location().toString();
        int fallback = level.getMinBuildHeight();
        for (MapEntryConfig.IntType entry : config.graveConfig.minimumGraveYLevel) {
            if (entry.key.equals(dimension)) return entry.value;
            if (entry.key.equals("misc")) fallback = entry.value;
        }
        return fallback;
    }

    private static Fixture placed(GameTestHelper helper, BlockPos relativePos, int xp) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(relativePos);
        Fixture fixture = component(level, Vec3.atCenterOf(pos), xp);
        level.setBlockAndUpdate(pos, Yigd.GRAVE_BLOCK.defaultBlockState());
        GraveBlockEntity be = blockEntity(fixture.grave);
        be.setPreviousState(Blocks.AIR.defaultBlockState());
        be.setComponent(fixture.grave);
        return fixture;
    }

    private static Fixture component(ServerLevel level, Vec3 pos, int xp) {
        UUID token = UUID.randomUUID();
        GameProfile owner = new GameProfile(UUID.randomUUID(), "YigdWorldTest");
        CompoundTag experience = new CompoundTag();
        experience.putInt("value", xp);
        experience.putDouble("original", xp);
        GraveComponent grave = new GraveComponent(owner, inventory(token), ExpComponent.fromNbt(experience), level,
                pos, new TranslatableDeathMessage("outOfWorld", owner.getName(), null, null, null, null), null);
        grave.backUp();
        return new Fixture(level, grave, token, xp);
    }

    private static InventoryComponent inventory(UUID token) {
        ItemStack stack = new ItemStack(Items.DIAMOND, 3);
        stack.getOrCreateTag().putUUID(TOKEN, token);
        NonNullList<GraveItem> items = NonNullList.withSize(41, InventoryComponent.EMPTY_GRAVE_ITEM);
        items.set(0, new GraveItem(stack, DropRule.PUT_IN_GRAVE));
        CompoundTag vanilla = InventoryComponent.listToNbt(items, item -> {
            CompoundTag nbt = item.stack.save(new CompoundTag());
            nbt.putString("dropRule", item.dropRule.name());
            return nbt;
        }, item -> item.stack.isEmpty());
        vanilla.putInt("mainSize", 36);
        vanilla.putInt("armorSize", 4);
        vanilla.putInt("offHandSize", 1);
        CompoundTag nbt = new CompoundTag();
        nbt.put("vanilla", vanilla);
        nbt.put("mods", new CompoundTag());
        return InventoryComponent.fromNbt(nbt);
    }

    private static ServerPlayer player(Fixture fixture) {
        ServerPlayer player = new ServerPlayer(fixture.level.getServer(), fixture.level, fixture.grave.getOwner());
        player.setHealth(player.getMaxHealth());
        return player;
    }

    private static GraveBlockEntity blockEntity(GraveComponent grave) {
        check(grave.getWorld() != null && grave.getWorld().getBlockEntity(grave.getPos()) instanceof GraveBlockEntity,
                "Missing physical grave block entity");
        return (GraveBlockEntity) grave.getWorld().getBlockEntity(grave.getPos());
    }

    private static void assertLinked(GraveComponent grave) {
        check(grave.getGraveId().equals(blockEntity(grave).getGraveId()), "Grave block UUID does not match its backup");
        check(DeathInfoManager.INSTANCE.getGrave(grave.getGraveId()).isPresent(), "Missing recovery backup");
    }

    private static void assertRecovered(Fixture fixture, ServerPlayer player) {
        ItemStack stack = player.getInventory().getItem(0);
        check(matches(stack, fixture.token) && stack.getCount() == 3, "Could not recover the exact saved inventory");
        check(player.totalExperience == fixture.xp, "Could not recover the exact saved experience");
    }

    private static void assertNoDrops(Fixture fixture, BlockPos pos) {
        check(fixture.level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3),
                entity -> matches(entity.getItem(), fixture.token)).isEmpty(), "Protected or backed-up items unexpectedly dropped");
    }

    private static boolean matches(ItemStack stack, UUID token) {
        return stack.hasTag() && stack.getTag().hasUUID(TOKEN) && token.equals(stack.getTag().getUUID(TOKEN));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }

    private static final class BodyBreakingWither extends WitherBoss {
        private BodyBreakingWither(ServerLevel level) { super(EntityType.WITHER, level); }
        private void advanceBodyBreakingAi() { super.customServerAiStep(); }
    }

    private record Fixture(ServerLevel level, GraveComponent grave, UUID token, int xp) {}
}
