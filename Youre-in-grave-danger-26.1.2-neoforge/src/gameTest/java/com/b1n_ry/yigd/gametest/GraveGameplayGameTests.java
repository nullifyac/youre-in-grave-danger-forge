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
import com.b1n_ry.yigd.data.ListMode;
import com.b1n_ry.yigd.data.TimePoint;
import com.b1n_ry.yigd.util.DropRule;
import com.b1n_ry.yigd.util.LegacyGraveDataMigration;
import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Real graves, native boss routines and server ticks in the disposable GameTest server. */
public final class GraveGameplayGameTests {
    private static final BlockPos GRAVE_POS = new BlockPos(2, 1, 2);
    private static final BlockPos SECOND_POS = new BlockPos(5, 1, 5);
    private static final BlockPos CONTROL_POS = new BlockPos(3, 1, 2);
    private static final String TOKEN = "yigdRegressionToken";
    private static final int ITEM_COUNT = 3;

    private GraveGameplayGameTests() {}

    /** Native environments run sequentially, and always restore the original manager/config objects. */
    public static Object setup(ServerLevel level, String environment) {
        check(level.getServer() instanceof GameTestServer, "Run these tests in runGameTestServer's disposable world");
        YigdConfig config = YigdConfig.getConfig();
        Isolation saved = new Isolation(DeathInfoManager.INSTANCE, config, config.graveConfig,
                config.extraFeatures, config.inventoryConfig, level.getGameRules().get(GameRules.MOB_GRIEFING));
        DeathInfoManager.INSTANCE = new DeathInfoManager();
        config.graveConfig = new GraveConfig();
        config.graveConfig.dropItemsIfDestroyed = environment.equals("grave_drops");
        config.graveConfig.maxBackupsPerPerson = environment.equals("grave_drops") ? 1 : 100;
        config.graveConfig.dropFromOldestWhenDeleted = true;
        config.graveConfig.generateGraveInVoid = !environment.equals("grave_void_off");
        config.extraFeatures = new ExtraFeaturesConfig();
        config.inventoryConfig = new InventoryConfig();
        level.getGameRules().set(GameRules.MOB_GRIEFING, true, level.getServer());
        return saved;
    }

    public static void teardown(ServerLevel level, Object state) {
        Isolation saved = (Isolation) state;
        saved.config.graveConfig = saved.graves;
        saved.config.extraFeatures = saved.extras;
        saved.config.inventoryConfig = saved.inventory;
        level.getGameRules().set(GameRules.MOB_GRIEFING, saved.mobGriefing, level.getServer());
        DeathInfoManager.INSTANCE = saved.manager;
    }

    @GameTest(environment = "grave_drops")
    public static void entityDestructionProtectsOnlyLinkedUnclaimedGraves(GameTestHelper helper) {
        Fixture fixture = placed(helper, owner(), GRAVE_POS, 0);
        BlockState state = Yigd.GRAVE.get().defaultBlockState();
        for (EntityType<?> type : List.of(EntityType.ENDER_DRAGON, EntityType.WITHER, EntityType.WARDEN, EntityType.ZOMBIE)) {
            Entity attacker = type.create(fixture.level, EntitySpawnReason.STRUCTURE);
            check(attacker != null, "Could not create real entity " + type);
            check(!Yigd.GRAVE.get().canEntityDestroy(state, fixture.level, fixture.pos, attacker),
                    "An entity can destroy a linked, unclaimed player's grave: " + type);
        }
        Entity ordinary = EntityType.ZOMBIE.create(fixture.level, EntitySpawnReason.STRUCTURE);
        fixture.grave.setStatus(GraveStatus.CLAIMED);
        blockEntity(fixture.grave).setClaimed(true);
        check(Yigd.GRAVE.get().canEntityDestroy(state, fixture.level, fixture.pos, ordinary),
                "Claimed grave changed inherited entity behavior");
        BlockPos decorativePos = helper.absolutePos(SECOND_POS);
        fixture.level.setBlockAndUpdate(decorativePos, state);
        check(Yigd.GRAVE.get().canEntityDestroy(state, fixture.level, decorativePos, ordinary),
                "Decorative grave changed inherited entity behavior");
        helper.runAfterDelay(2, helper::succeed);
    }

    @GameTest(environment = "grave_backup")
    public static void nativeImmunityTagsAreLoaded(GameTestHelper helper) {
        BlockState state = Yigd.GRAVE.get().defaultBlockState();
        check(state.is(BlockTags.DRAGON_IMMUNE), "Grave dragon_immune tag is missing");
        check(state.is(BlockTags.WITHER_IMMUNE), "Grave wither_immune tag is missing");
        helper.succeed();
    }

    @GameTest(environment = "grave_drops")
    public static void offlineRemovalAndRepeatedNotificationsDropExactlyOnce(GameTestHelper helper) {
        Fixture fixture = placed(helper, owner(), GRAVE_POS, 0);
        check(fixture.level.getServer().getPlayerList().getPlayer(fixture.grave.getOwner().partialProfile().id()) == null,
                "Fixture owner must be offline");
        GraveBlockEntity removed = blockEntity(fixture.grave);
        fixture.level.setBlockAndUpdate(fixture.pos, Blocks.AIR.defaultBlockState());
        removed.onBroken();
        removed.onBroken();
        helper.runAfterDelay(2, () -> {
            check(fixture.grave.getStatus() == GraveStatus.DESTROYED, "Native block removal was not detected");
            check(DeathInfoManager.INSTANCE.getGrave(fixture.grave.getGraveId()).isPresent(), "Removal deleted the backup");
            assertDroppedOnce(fixture);
            fixture.grave.onDestroyed();
            helper.runAfterDelay(2, () -> {
                assertDroppedOnce(fixture);
                helper.succeed();
            });
        });
    }

    @GameTest(environment = "grave_drops")
    public static void directBlockTypeReplacementPreservesDestroyedBackup(GameTestHelper helper) {
        Fixture fixture = placed(helper, owner(), GRAVE_POS, 0);
        fixture.level.setBlockAndUpdate(fixture.pos, Blocks.STONE.defaultBlockState());
        helper.runAfterDelay(2, () -> {
            check(fixture.level.getBlockState(fixture.pos).is(Blocks.STONE), "Replacement did not remain in the world");
            check(fixture.grave.getStatus() == GraveStatus.DESTROYED, "Direct replacement bypassed removal detection");
            check(DeathInfoManager.INSTANCE.getGrave(fixture.grave.getGraveId()).isPresent(), "Replacement deleted the backup");
            assertDroppedOnce(fixture);
            helper.succeed();
        });
    }

    @GameTest(environment = "grave_drops")
    public static void sameUuidRestorationDoesNotDestroyOrDrop(GameTestHelper helper) {
        Fixture fixture = placed(helper, owner(), GRAVE_POS, 0);
        fixture.level.setBlockAndUpdate(fixture.pos, Blocks.AIR.defaultBlockState());
        placeGrave(fixture.level, fixture.pos, fixture.grave);
        helper.runAfterDelay(2, () -> {
            assertUnclaimedAndLinked(fixture.grave);
            assertNoDrops(fixture, fixture.pos);
            helper.succeed();
        });
    }

    @GameTest(environment = "grave_drops")
    public static void relocatedLiveUuidDoesNotDrop(GameTestHelper helper) {
        Fixture fixture = placed(helper, owner(), GRAVE_POS, 0);
        fixture.level.setBlockAndUpdate(fixture.pos, Blocks.AIR.defaultBlockState());
        BlockPos relocated = helper.absolutePos(SECOND_POS);
        fixture.grave.setPos(relocated);
        placeGrave(fixture.level, relocated, fixture.grave);
        helper.runAfterDelay(2, () -> {
            assertUnclaimedAndLinked(fixture.grave);
            assertNoDrops(fixture, fixture.pos);
            assertNoDrops(fixture, relocated);
            helper.succeed();
        });
    }

    @GameTest(environment = "grave_drops")
    public static void propertyUpdateDoesNotRemoveGrave(GameTestHelper helper) {
        Fixture fixture = placed(helper, owner(), GRAVE_POS, 0);
        fixture.level.setBlockAndUpdate(fixture.pos, fixture.level.getBlockState(fixture.pos)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
        helper.runAfterDelay(2, () -> {
            assertUnclaimedAndLinked(fixture.grave);
            assertNoDrops(fixture, fixture.pos);
            helper.succeed();
        });
    }

    @GameTest(environment = "grave_drops")
    public static void successfulOwnerClaimTransfersInventoryAndXpOnce(GameTestHelper helper) {
        Fixture fixture = placed(helper, owner(), GRAVE_POS, 7);
        ServerPlayer player = player(helper, fixture);
        check(fixture.grave.claim(player, fixture.level, Blocks.AIR.defaultBlockState(), fixture.pos, ItemStack.EMPTY)
                == InteractionResult.SUCCESS, "Owner could not claim their grave");
        check(fixture.grave.claim(player, fixture.level, Blocks.AIR.defaultBlockState(), fixture.pos, ItemStack.EMPTY)
                == InteractionResult.FAIL, "Repeated claim accepted an already claimed grave");
        helper.runAfterDelay(2, () -> {
            assertClaimedAndRecovered(fixture, player);
            assertNoDrops(fixture, fixture.pos);
            helper.succeed();
        });
    }

    @GameTest(environment = "grave_drops")
    public static void commandStyleRestoreDoesNotDropAgain(GameTestHelper helper) {
        Fixture fixture = placed(helper, owner(), GRAVE_POS, 7);
        ServerPlayer player = player(helper, fixture);
        fixture.grave.applyToPlayer(player, fixture.level, Vec3.atCenterOf(fixture.pos), true);
        fixture.grave.setStatus(GraveStatus.CLAIMED);
        check(fixture.grave.removeGraveBlock(), "Restore did not remove the grave");
        helper.runAfterDelay(2, () -> {
            assertClaimedAndRecovered(fixture, player);
            assertNoDrops(fixture, fixture.pos);
            helper.succeed();
        });
    }

    @GameTest(environment = "grave_drops")
    public static void refusedMiningRestoresLinkedGrave(GameTestHelper helper) {
        Fixture fixture = placed(helper, owner(), GRAVE_POS, 0);
        ServerPlayer player = player(helper, fixture);
        GraveBlockEntity removed = blockEntity(fixture.grave);
        BlockState state = fixture.level.getBlockState(fixture.pos);
        fixture.level.setBlockAndUpdate(fixture.pos, Blocks.AIR.defaultBlockState());
        Yigd.GRAVE.get().playerDestroy(fixture.level, player, fixture.pos, state, removed, ItemStack.EMPTY);
        helper.runAfterDelay(2, () -> {
            assertUnclaimedAndLinked(fixture.grave);
            check(player.getInventory().isEmpty(), "Refused mining transferred grave contents");
            assertNoDrops(fixture, fixture.pos);
            helper.succeed();
        });
    }

    @GameTest(environment = "grave_drops")
    public static void historyCapDropsOldUnclaimedContentsExactlyOnce(GameTestHelper helper) {
        GameProfile owner = owner();
        Fixture oldest = placed(helper, owner, GRAVE_POS, 0);
        Fixture newest = placed(helper, owner, SECOND_POS, 0);
        helper.runAfterDelay(2, () -> {
            check(DeathInfoManager.INSTANCE.getGrave(oldest.grave.getGraveId()).isEmpty(), "History limit did not evict oldest record");
            check(oldest.grave.getStatus() == GraveStatus.DESTROYED && oldest.level.getBlockState(oldest.pos).isAir(),
                    "Eviction did not remove oldest unclaimed block");
            assertDroppedOnce(oldest);
            assertUnclaimedAndLinked(newest.grave);
            assertNoDrops(newest, newest.pos);
            check(DeathInfoManager.INSTANCE.getBackupData(newest.grave.getOwner()).size() == 1, "History limit was not enforced");
            helper.succeed();
        });
    }

    @GameTest(environment = "grave_drops")
    public static void staleHistoryRecordCannotRemoveForeignGrave(GameTestHelper helper) {
        GameProfile oldOwner = owner();
        Fixture oldest = placed(helper, oldOwner, GRAVE_POS, 0);
        oldest.level.setBlockAndUpdate(oldest.pos, Blocks.STONE.defaultBlockState());
        // A different grave takes this position before the first removal's EOT notification runs.
        Fixture foreign = placed(helper, owner(), GRAVE_POS, 5);
        GraveBlockEntity foreignBlock = blockEntity(foreign.grave);
        foreignBlock.setPreviousState(Blocks.STONE.defaultBlockState());
        CompoundTag foreignContents = foreign.grave.getInventoryComponent().toNbt(foreign.level.registryAccess());
        CompoundTag foreignExperience = foreign.grave.getExpComponent().toNbt();
        Fixture latest = placed(helper, oldOwner, SECOND_POS, 0);
        helper.runAfterDelay(2, () -> {
            check(DeathInfoManager.INSTANCE.getGrave(oldest.grave.getGraveId()).isEmpty(), "Stale oldest record was not evicted");
            check(oldest.grave.getStatus() == GraveStatus.DESTROYED, "Stale oldest record remained unclaimed");
            assertDroppedOnce(oldest);
            assertUnclaimedAndLinked(foreign.grave);
            check(foreign.level.getBlockEntity(foreign.pos) == foreignBlock, "Eviction replaced the foreign grave block entity");
            check(foreignBlock.getPreviousState().is(Blocks.STONE), "Eviction changed the foreign grave's saved world state");
            check(foreign.grave.getInventoryComponent().toNbt(foreign.level.registryAccess()).equals(foreignContents),
                    "Eviction changed the foreign grave's inventory");
            check(foreign.grave.getExpComponent().toNbt().equals(foreignExperience), "Eviction changed the foreign grave's XP");
            assertNoDrops(foreign, foreign.pos);
            assertUnclaimedAndLinked(latest.grave);
            assertNoDrops(latest, latest.pos);
            helper.succeed();
        });
    }

    @GameTest(environment = "grave_drops")
    public static void missingDimensionHistoryEvictionDoesNotDropIntoAnotherWorld(GameTestHelper helper) {
        GameProfile owner = owner();
        UUID token = UUID.randomUUID();
        GraveComponent missing = new GraveComponent(ResolvableProfile.createResolved(owner), inventory(helper.getLevel(), token), experience(0),
                ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(Yigd.MOD_ID, "missing_test_dimension")),
                helper.absolutePos(GRAVE_POS), Component.literal("test death"), UUID.randomUUID(), GraveStatus.UNCLAIMED,
                true, new TimePoint(helper.getLevel()), null);
        missing.backUp();
        Fixture latest = placed(helper, owner, SECOND_POS, 0);
        helper.runAfterDelay(2, () -> {
            check(missing.getWorld() == null, "Fixture dimension unexpectedly exists");
            check(DeathInfoManager.INSTANCE.getGrave(missing.getGraveId()).isEmpty(), "Missing-dimension history was not capped");
            check(drops(latest.level, helper.absolutePos(GRAVE_POS), token).isEmpty(), "Absent-dimension items dropped into this world");
            assertUnclaimedAndLinked(latest.grave);
            helper.succeed();
        });
    }

    @GameTest(environment = "grave_backup")
    public static void dragonWallRemovalBreaksControlAndPreservesGrave(GameTestHelper helper) {
        Fixture fixture = placed(helper, owner(), GRAVE_POS, 0);
        BlockPos control = helper.absolutePos(CONTROL_POS);
        fixture.level.setBlockAndUpdate(control, Blocks.COBBLESTONE.defaultBlockState());
        EnderDragon dragon = EntityType.ENDER_DRAGON.create(fixture.level, EntitySpawnReason.STRUCTURE);
        check(dragon != null, "Could not create real Ender Dragon");
        dragon.snapTo(Vec3.atCenterOf(fixture.pos));
        Method[] matches = Arrays.stream(EnderDragon.class.getDeclaredMethods())
                .filter(method -> method.getReturnType() == boolean.class
                        && Arrays.equals(method.getParameterTypes(), new Class<?>[]{ServerLevel.class, AABB.class}))
                .toArray(Method[]::new);
        check(matches.length == 1, "Could not uniquely identify native dragon wall removal");
        try {
            matches[0].setAccessible(true);
            matches[0].invoke(dragon, fixture.level, new AABB(fixture.pos).expandTowards(1, 0, 0));
        } catch (ReflectiveOperationException exception) {
            throw failure("Native dragon wall removal failed: " + exception);
        }
        helper.runAfterDelay(2, () -> {
            check(fixture.level.getBlockState(control).isAir(), "Native dragon routine did not destroy breakable control");
            assertUnclaimedAndLinked(fixture.grave);
            assertNoDrops(fixture, fixture.pos);
            helper.succeed();
        });
    }

    @GameTest(environment = "grave_drops")
    public static void uuidIdentitySurvivesNameAndSkinPropertyChanges(GameTestHelper helper) {
        GameProfile original = owner();
        GameProfile changed = new GameProfile(original.id(), "YigdChanged", new PropertyMap(
                ImmutableMultimap.of("textures", new Property("textures", "eyJ0ZXh0dXJlcyI6e319"))));
        check(!original.equals(changed), "Identity fixture must change actual GameProfile equality");
        ResolvableProfile firstProfile = ResolvableProfile.createResolved(original);
        ResolvableProfile changedProfile = ResolvableProfile.createResolved(changed);
        Fixture oldest = placed(helper, original, GRAVE_POS, 0);
        DeathInfoManager manager = DeathInfoManager.INSTANCE;
        check(manager.getBackupData(changedProfile).getFirst() == oldest.grave, "Changed name/skin lost grave history");
        ServerPlayer originalPlayer = player(helper, oldest);
        RespawnComponent firstRespawn = new RespawnComponent(originalPlayer);
        manager.addRespawnComponent(firstProfile, firstRespawn);
        manager.addRespawnComponent(changedProfile, new RespawnComponent(originalPlayer));
        check(manager.getRespawnComponent(changedProfile).orElseThrow() == firstRespawn, "Changed profile duplicated or lost respawn data");
        manager.addToList(firstProfile);
        check(manager.isInList(changedProfile), "Changed profile lost whitelist membership");
        Fixture latest = placed(helper, changed, SECOND_POS, 0);
        check(manager.getPlayerGraves().size() == 1 && manager.getBackupData(firstProfile).size() == 1,
                "Changed profile created a second player history or bypassed history cap");
        check(latest.grave.getOwner().partialProfile().properties().equals(changed.properties()), "UUID identity normalization discarded render properties");
        check(manager.removeFromList(changedProfile) && !manager.isInList(firstProfile), "Changed profile could not remove whitelist membership");
        manager.removeRespawnComponent(changedProfile);
        check(manager.getRespawnComponent(firstProfile).isEmpty(), "Changed profile could not remove respawn data");
        helper.runAfterDelay(2, () -> {
            assertDroppedOnce(oldest);
            assertUnclaimedAndLinked(latest.grave);
            helper.succeed();
        });
    }

    @GameTest(environment = "grave_drops")
    public static void zeroHistoryLimitKeepsNewestBackupBeforeGeneration(GameTestHelper helper) {
        YigdConfig.getConfig().graveConfig.maxBackupsPerPerson = 0;
        Fixture fixture = component(helper.getLevel(), owner(), Vec3.atCenterOf(helper.absolutePos(GRAVE_POS)), 7);
        check(fixture.grave.getStatus() == GraveStatus.UNCLAIMED && DeathInfoManager.INSTANCE.getGrave(fixture.grave.getGraveId()).isPresent(),
                "Zero limit evicted the new death before its grave could generate");
        placeGrave(fixture.level, fixture.pos, fixture.grave);
        helper.runAfterDelay(2, () -> {
            assertUnclaimedAndLinked(fixture.grave);
            assertNoDrops(fixture, fixture.pos);
            helper.succeed();
        });
    }

    @GameTest(environment = "grave_reload")
    public static void malformedLoadKeepsExistingManagerAndPhysicalGraves(GameTestHelper helper) {
        GameProfile owner = owner();
        Fixture first = placed(helper, owner, GRAVE_POS, 0);
        Fixture second = placed(helper, owner, SECOND_POS, 0);
        DeathInfoManager original = DeathInfoManager.INSTANCE;
        CompoundTag checkpoint = original.save(new CompoundTag(), first.level.registryAccess());
        CompoundTag playerRecord = (CompoundTag) checkpoint.getListOrEmpty("graves").get(0);
        CompoundTag invalid = (CompoundTag) playerRecord.getListOrEmpty("graves").get(1);
        invalid.putString("graveId", "invalid UUID");
        YigdConfig.getConfig().graveConfig.maxBackupsPerPerson = 1;
        boolean rejected = false;
        try { DeathInfoManager.load(checkpoint, first.level.registryAccess(), first.level.getServer()); }
        catch (RuntimeException expected) { rejected = true; }
        check(rejected, "Malformed record was accepted");
        check(DeathInfoManager.INSTANCE == original, "Failed load replaced or cleared the active manager");
        helper.runAfterDelay(2, () -> {
            assertUnclaimedAndLinked(first.grave);
            assertUnclaimedAndLinked(second.grave);
            check(blockEntity(first.grave).getComponent() == first.grave && blockEntity(second.grave).getComponent() == second.grave,
                    "Failed load relinked a physical grave to partially parsed data");
            assertNoDrops(first, first.pos);
            assertNoDrops(second, second.pos);
            helper.succeed();
        });
    }

    @GameTest(environment = "grave_backup", timeoutTicks = 40)
    public static void witherBodyRemovalBreaksControlAndPreservesGrave(GameTestHelper helper) {
        Fixture fixture = placed(helper, owner(), GRAVE_POS, 0);
        BlockPos control = helper.absolutePos(CONTROL_POS);
        fixture.level.setBlockAndUpdate(control, Blocks.COBBLESTONE.defaultBlockState());
        BodyBreakingWither wither = new BodyBreakingWither(fixture.level);
        wither.snapTo(Vec3.atCenterOf(fixture.pos));
        check(wither.hurtServer(fixture.level, fixture.level.damageSources().generic(), 1), "Could not arm native wither destruction");
        for (int tick = 1; tick <= 20; tick++) {
            helper.runAfterDelay(tick, () -> wither.advanceBodyBreakingAi(fixture.level));
        }
        helper.runAfterDelay(22, () -> {
            check(fixture.level.getBlockState(control).isAir(), "Native wither routine did not destroy breakable control");
            assertUnclaimedAndLinked(fixture.grave);
            assertNoDrops(fixture, fixture.pos);
            helper.succeed();
        });
    }

    @GameTest(environment = "grave_backup")
    public static void defaultDestructionKeepsRecoverableInventoryAndXp(GameTestHelper helper) {
        Fixture fixture = placed(helper, owner(), GRAVE_POS, 7);
        fixture.level.setBlockAndUpdate(fixture.pos, Blocks.AIR.defaultBlockState());
        helper.runAfterDelay(2, () -> {
            check(!YigdConfig.getConfig().graveConfig.dropItemsIfDestroyed, "Expected default no-drop policy");
            check(fixture.grave.getStatus() == GraveStatus.DESTROYED, "Destruction was not recorded");
            assertNoDrops(fixture, fixture.pos);
            GraveComponent retained = DeathInfoManager.INSTANCE.getGrave(fixture.grave.getGraveId())
                    .orElseThrow(() -> failure("Destroyed grave lost its recovery backup"));
            ServerPlayer player = player(helper, fixture);
            retained.applyToPlayer(player, fixture.level, Vec3.atCenterOf(fixture.pos), true);
            retained.setStatus(GraveStatus.CLAIMED);
            retained.removeGraveBlock();
            check(retained.claim(player, fixture.level, Blocks.AIR.defaultBlockState(), fixture.pos, ItemStack.EMPTY)
                    == InteractionResult.FAIL, "Claim accepted already restored backup");
            helper.runAfterDelay(2, () -> {
                assertClaimedAndRecovered(fixture, player);
                assertNoDrops(fixture, fixture.pos);
                helper.succeed();
            });
        });
    }

    @GameTest(environment = "grave_void_on")
    public static void voidGenerationRaisesLinkedGraveToConfiguredMinimum(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos reference = helper.absolutePos(GRAVE_POS);
        BlockPos target = new BlockPos(reference.getX(), minimumY(level), reference.getZ());
        BlockState previous = level.getBlockState(target);
        BlockState previousSupport = level.getBlockState(target.below());
        check(level.getBlockEntity(target) == null, "Void target must not overwrite a block entity");
        level.setBlockAndUpdate(target, Blocks.AIR.defaultBlockState());
        Vec3 deathPos = new Vec3(reference.getX() + .5, level.getMinY() - 80, reference.getZ() + .5);
        Fixture fixture = component(level, owner(), deathPos, 7);
        ServerPlayer player = player(helper, fixture);
        RespawnComponent respawn = new RespawnComponent(player);
        fixture.grave.generateOrDrop(Direction.NORTH, new DeathContext(player, level, deathPos, level.damageSources().fellOutOfWorld()), respawn);
        helper.runAfterDelay(2, () -> {
            check(respawn.wasGraveGenerated(), "Enabled void policy did not generate a grave");
            check(fixture.grave.getPos().equals(target), "Void grave did not use configured minimum Y");
            assertUnclaimedAndLinked(fixture.grave);
            check(fixture.grave.getInventoryComponent().getItems().getFirst().dropRule == DropRule.PUT_IN_GRAVE, "Void contents were marked dropped");
            assertNoDrops(fixture, target);
            fixture.grave.setStatus(GraveStatus.CLAIMED);
            level.setBlockAndUpdate(target, previous);
            level.setBlockAndUpdate(target.below(), previousSupport);
            helper.runAfterDelay(2, helper::succeed);
        });
    }

    @GameTest(environment = "grave_void_off")
    public static void disabledVoidGenerationRetainsDroppedInventoryForRecovery(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos reference = helper.absolutePos(GRAVE_POS);
        Vec3 deathPos = new Vec3(reference.getX() + .5, level.getMinY() - 80, reference.getZ() + .5);
        Fixture fixture = component(level, owner(), deathPos, 7);
        ServerPlayer player = player(helper, fixture);
        RespawnComponent respawn = new RespawnComponent(player);
        fixture.grave.generateOrDrop(Direction.NORTH, new DeathContext(player, level, deathPos, level.damageSources().fellOutOfWorld()), respawn);
        helper.runAfterDelay(2, () -> {
            check(!respawn.wasGraveGenerated(), "Disabled void policy generated a grave");
            check(level.getBlockEntity(fixture.grave.getPos()) == null, "A grave exists outside build height");
            check(fixture.grave.getInventoryComponent().getItems().getFirst().dropRule == DropRule.DROP, "Denied grave did not mark contents dropped");
            check(DeathInfoManager.INSTANCE.getGrave(fixture.grave.getGraveId()).isPresent(), "Denied void generation erased backup");
            assertNoDrops(fixture, fixture.grave.getPos());
            fixture.grave.applyToPlayer(player, level, Vec3.atCenterOf(reference), true, rule -> rule == DropRule.DROP);
            fixture.grave.setStatus(GraveStatus.CLAIMED);
            assertRecovered(fixture, player);
            helper.succeed();
        });
    }

    @GameTest(environment = "grave_reload")
    public static void compressedReloadPreservesDestroyedBackupAndRelinksLiveGrave(GameTestHelper helper) {
        Fixture destroyed = placed(helper, owner(), GRAVE_POS, 7);
        Fixture surviving = placed(helper, owner(), SECOND_POS, 0);
        DeathInfoManager.INSTANCE.setGraveListMode(ListMode.WHITELIST);
        DeathInfoManager.INSTANCE.addToList(surviving.grave.getOwner());
        destroyed.level.setBlockAndUpdate(destroyed.pos, Blocks.AIR.defaultBlockState());
        helper.runAfterDelay(2, () -> {
            check(destroyed.grave.getStatus() == GraveStatus.DESTROYED, "Persistence fixture removal was not detected");
            CompoundTag checkpoint = diskRoundTrip(destroyed.level,
                    DeathInfoManager.INSTANCE.save(new CompoundTag(), destroyed.level.registryAccess()));
            CompoundTag blockCheckpoint = blockEntity(surviving.grave).saveWithFullMetadata(surviving.level.registryAccess());
            surviving.level.setBlockAndUpdate(surviving.pos, Blocks.AIR.defaultBlockState());
            surviving.level.setBlockAndUpdate(surviving.pos, Yigd.GRAVE.get().defaultBlockState());
            GraveBlockEntity reloadedBlock = (GraveBlockEntity) surviving.level.getBlockEntity(surviving.pos);
            check(reloadedBlock != null, "Could not recreate saved block entity");
            reloadedBlock.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, surviving.level.registryAccess(), blockCheckpoint));
            DeathInfoManager.INSTANCE = new DeathInfoManager();
            DeathInfoManager restored = DeathInfoManager.load(checkpoint, destroyed.level.registryAccess(), destroyed.level.getServer());
            GraveComponent recovered = restored.getGrave(destroyed.grave.getGraveId()).orElseThrow(() -> failure("Destroyed backup did not reload"));
            GraveComponent linked = restored.getGrave(surviving.grave.getGraveId()).orElseThrow(() -> failure("Live backup did not reload"));
            check(recovered != destroyed.grave, "Reload reused old in-memory component");
            check(recovered.getStatus() == GraveStatus.DESTROYED && recovered.getWorld() == destroyed.level && recovered.getPos().equals(destroyed.pos),
                    "Reload lost status, dimension or position");
            // Vanilla canonicalizes a literal Component argument into a String during codec decode.
            // Compare both displayed text and the canonical serialized translation/style data.
            check(recovered.getDeathMessage().getString().equals(destroyed.grave.getDeathMessage().getString())
                            && recovered.toNbt(destroyed.level.registryAccess()).get("deathMessage")
                            .equals(destroyed.grave.toNbt(destroyed.level.registryAccess()).get("deathMessage")),
                    "Reload changed death message text, translation or formatting");
            check(reloadedBlock.getComponent() == linked, "Reload did not relink to fresh backup component");
            check(restored.getGraveListMode() == ListMode.WHITELIST && restored.isInList(surviving.grave.getOwner()),
                    "Reload lost whitelist mode or membership");
            ServerPlayer player = player(helper, destroyed);
            recovered.applyToPlayer(player, destroyed.level, Vec3.atCenterOf(destroyed.pos), true);
            recovered.setStatus(GraveStatus.CLAIMED);
            helper.runAfterDelay(2, () -> {
                assertRecovered(destroyed, player);
                assertUnclaimedAndLinked(linked);
                assertNoDrops(destroyed, destroyed.pos);
                helper.succeed();
            });
        });
    }

    @GameTest(environment = "grave_reload")
    public static void legacy121JsonMetadataAndInventoryMigrateWithoutHistoryDrops(GameTestHelper helper) {
        assertLegacyMigration(helper, false);
    }

    @GameTest(environment = "grave_reload")
    public static void legacy120ProfilesPositionsDeathMessageAndItemsMigrate(GameTestHelper helper) {
        assertLegacyMigration(helper, true);
    }

    @GameTest(environment = "grave_backup")
    public static void defaultRandomSpawnWearsOwnerHeadAndMigratesKnownDefaults(GameTestHelper helper) {
        GraveConfig.RandomSpawn defaults = new GraveConfig.RandomSpawn();
        defaults.spawnNbt = GraveConfig.RandomSpawn.LEGACY_121_SPAWN_NBT;
        check(defaults.migrateDefaultNbt() && defaults.spawnNbt.equals(GraveConfig.RandomSpawn.DEFAULT_SPAWN_NBT), "1.21 random-spawn default did not migrate");
        defaults.spawnNbt = GraveConfig.RandomSpawn.LEGACY_120_SPAWN_NBT;
        check(defaults.migrateDefaultNbt(), "1.20 random-spawn default did not migrate");
        defaults.spawnNbt = "{CustomPayload:1}";
        check(!defaults.migrateDefaultNbt() && defaults.spawnNbt.equals("{CustomPayload:1}"), "Migration rewrote custom random-spawn settings");
        GameProfile owner = new GameProfile(UUID.randomUUID(), "YigdGraveTest", new PropertyMap(
                ImmutableMultimap.of("textures", new Property("textures", "eyJ0ZXh0dXJlcyI6e319"))));
        Fixture fixture = placed(helper, owner, GRAVE_POS, 7);
        YigdConfig.getConfig().graveConfig.randomSpawn.percentSpawnChance = 100;
        ServerPlayer player = player(helper, fixture);
        check(fixture.grave.claim(player, fixture.level, Blocks.AIR.defaultBlockState(), fixture.pos, ItemStack.EMPTY)
                == InteractionResult.SUCCESS, "Default random spawn prevented grave claim");
        helper.runAfterDelay(2, () -> {
            List<Zombie> zombies = fixture.level.getEntitiesOfClass(Zombie.class, new AABB(fixture.pos).inflate(4),
                    zombie -> zombie.getItemBySlot(EquipmentSlot.HEAD).is(Items.PLAYER_HEAD));
            check(zombies.size() == 1, "Default random spawn did not create one zombie wearing an owner head");
            ResolvableProfile skull = zombies.getFirst().getItemBySlot(EquipmentSlot.HEAD).get(DataComponents.PROFILE);
            check(skull != null && skull.partialProfile().equals(owner), "Spawned head lost owner UUID, name or skin properties");
            assertClaimedAndRecovered(fixture, player);
            assertNoDrops(fixture, fixture.pos);
            zombies.getFirst().discard();
            helper.succeed();
        });
    }

    @GameTest(environment = "grave_backup")
    public static void randomSpawnConsumesItemWithLiteralDollarAndBackslashMetadata(GameTestHelper helper) {
        Fixture fixture = placed(helper, owner(), GRAVE_POS, 7);
        ItemStack saved = fixture.grave.getInventoryComponent().getItems().getFirst().stack;
        saved.set(DataComponents.CUSTOM_NAME, Component.literal("Cost $5 \\ keep metadata"));
        ItemStack expected = saved.copy();
        GraveConfig.RandomSpawn config = YigdConfig.getConfig().graveConfig.randomSpawn;
        config.percentSpawnChance = 100;
        config.spawnNbt = "{equipment:{mainhand:${!item[0]}},NoAI:1b,Silent:1b}";
        ServerPlayer player = player(helper, fixture);
        check(fixture.grave.claim(player, fixture.level, Blocks.AIR.defaultBlockState(), fixture.pos, ItemStack.EMPTY)
                == InteractionResult.SUCCESS, "Metadata interpolation prevented grave claim");
        helper.runAfterDelay(2, () -> {
            List<Zombie> zombies = fixture.level.getEntitiesOfClass(Zombie.class, new AABB(fixture.pos).inflate(4),
                    zombie -> matches(zombie.getItemBySlot(EquipmentSlot.MAINHAND), fixture.token));
            check(zombies.size() == 1, "Item metadata prevented the configured entity spawn");
            ItemStack equipped = zombies.getFirst().getItemBySlot(EquipmentSlot.MAINHAND);
            check(ItemStack.isSameItemSameComponents(equipped, expected) && equipped.getCount() == ITEM_COUNT,
                    "Spawn interpolation changed item name, custom data or count");
            check(player.getInventory().countItem(Items.DIAMOND) == 0, "Successfully consumed spawn item also reached player inventory");
            check(player.totalExperience == fixture.xp, "Spawn consumption changed recovered experience");
            check(fixture.grave.getStatus() == GraveStatus.CLAIMED, "Spawn consumption did not complete grave claim");
            assertNoDrops(fixture, fixture.pos);
            zombies.getFirst().discard();
            helper.succeed();
        });
    }

    @GameTest(environment = "grave_backup")
    public static void randomSpawnTreatsInsertedPlaceholdersAsLiteralMetadata(GameTestHelper helper) {
        Fixture fixture = placed(helper, owner(), GRAVE_POS, 7);
        NonNullList<GraveItem> items = fixture.grave.getInventoryComponent().getItems();
        items.getFirst().stack.set(DataComponents.CUSTOM_NAME, Component.literal("Literal ${item[0]} and ${!item[1]} $5 \\"));
        ItemStack mainhand = items.getFirst().stack.copy();
        ItemStack offhand = new ItemStack(Items.EMERALD, 2);
        offhand.set(DataComponents.CUSTOM_DATA, mainhand.get(DataComponents.CUSTOM_DATA));
        offhand.set(DataComponents.CUSTOM_NAME, Component.literal("Keep ${owner.name} and ${looter.uuid}"));
        items.set(1, new GraveItem(offhand, DropRule.PUT_IN_GRAVE));
        ItemStack expectedOffhand = offhand.copy();
        GraveConfig.RandomSpawn config = YigdConfig.getConfig().graveConfig.randomSpawn;
        config.percentSpawnChance = 100;
        config.spawnNbt = "{equipment:{mainhand:${!item[0]},offhand:${!item[1]}},NoAI:1b,Silent:1b}";
        ServerPlayer player = player(helper, fixture);
        check(fixture.grave.claim(player, fixture.level, Blocks.AIR.defaultBlockState(), fixture.pos, ItemStack.EMPTY)
                == InteractionResult.SUCCESS, "Literal item metadata prevented grave claim");
        helper.runAfterDelay(2, () -> {
            List<Zombie> zombies = fixture.level.getEntitiesOfClass(Zombie.class, new AABB(fixture.pos).inflate(4),
                    zombie -> matches(zombie.getItemBySlot(EquipmentSlot.MAINHAND), fixture.token));
            check(zombies.size() == 1, "Multiple placeholders prevented the configured entity spawn");
            Zombie zombie = zombies.getFirst();
            ItemStack equippedMainhand = zombie.getItemBySlot(EquipmentSlot.MAINHAND);
            ItemStack equippedOffhand = zombie.getItemBySlot(EquipmentSlot.OFFHAND);
            check(ItemStack.isSameItemSameComponents(equippedMainhand, mainhand) && equippedMainhand.getCount() == ITEM_COUNT,
                    "Inserted item placeholder was interpreted instead of preserving literal metadata");
            check(ItemStack.isSameItemSameComponents(equippedOffhand, expectedOffhand) && equippedOffhand.getCount() == 2,
                    "Inserted owner or looter placeholder changed literal metadata");
            check(player.getInventory().countItem(Items.DIAMOND) == 0 && player.getInventory().countItem(Items.EMERALD) == 0,
                    "Consumed spawn items were also recovered into player inventory");
            check(player.totalExperience == fixture.xp && fixture.grave.getStatus() == GraveStatus.CLAIMED,
                    "Literal metadata prevented complete item/XP claim");
            assertNoDrops(fixture, fixture.pos);
            zombie.discard();
            helper.succeed();
        });
    }

    @GameTest(environment = "grave_backup")
    public static void oversizedRandomSpawnIndexPreservesPendingConsumedLoot(GameTestHelper helper) {
        Fixture fixture = placed(helper, owner(), GRAVE_POS, 7);
        GraveConfig.RandomSpawn config = YigdConfig.getConfig().graveConfig.randomSpawn;
        config.percentSpawnChance = 100;
        config.spawnNbt = "{equipment:{mainhand:${!item[0]},offhand:${item[999999999999999999999999999999999999]}},NoAI:1b}";
        ServerPlayer player = player(helper, fixture);
        check(fixture.grave.claim(player, fixture.level, Blocks.AIR.defaultBlockState(), fixture.pos, ItemStack.EMPTY)
                == InteractionResult.SUCCESS, "Oversized configured item index aborted grave recovery");
        helper.runAfterDelay(2, () -> {
            assertClaimedAndRecovered(fixture, player);
            assertNoDrops(fixture, fixture.pos);
            check(fixture.level.getEntitiesOfClass(Zombie.class, new AABB(fixture.pos).inflate(4),
                    zombie -> matches(zombie.getItemBySlot(EquipmentSlot.MAINHAND), fixture.token)).isEmpty(),
                    "Rejected oversized index also consumed loot into an entity");
            helper.succeed();
        });
    }

    @GameTest(environment = "grave_backup")
    public static void failedRandomSpawnPreservesPendingConsumedLoot(GameTestHelper helper) {
        Fixture fixture = placed(helper, owner(), GRAVE_POS, 7);
        GraveConfig.RandomSpawn config = YigdConfig.getConfig().graveConfig.randomSpawn;
        config.percentSpawnChance = 100;
        config.spawnNbt = "{equipment:{mainhand:${!item[0]}},broken:";
        ServerPlayer player = player(helper, fixture);
        check(fixture.grave.claim(player, fixture.level, Blocks.AIR.defaultBlockState(), fixture.pos, ItemStack.EMPTY)
                == InteractionResult.SUCCESS, "Malformed spawn settings prevented recovery claim");
        helper.runAfterDelay(2, () -> {
            assertClaimedAndRecovered(fixture, player);
            assertNoDrops(fixture, fixture.pos);
            check(fixture.level.getEntitiesOfClass(Zombie.class, new AABB(fixture.pos).inflate(4),
                    zombie -> matches(zombie.getItemBySlot(EquipmentSlot.MAINHAND), fixture.token)).isEmpty(),
                    "Invalid spawn also equipped the recovered item on an entity");
            helper.succeed();
        });
    }

    private static void assertLegacyMigration(GameTestHelper helper, boolean forge120) {
        GameProfile owner = owner();
        Fixture fixture = placed(helper, owner, GRAVE_POS, 7);
        Fixture second = placed(helper, owner, SECOND_POS, 0);
        CompoundTag wrapper = legacyCheckpoint(fixture, second, forge120);
        CompoundTag blockCheckpoint = blockEntity(fixture.grave).saveWithFullMetadata(fixture.level.registryAccess());
        CompoundTag legacyProfile = legacyProfile(owner, forge120);
        blockCheckpoint.put("skull", legacyProfile.copy());
        blockCheckpoint.putString("text", "{\"text\":\"Legacy grave\",\"color\":\"gold\"}");
        fixture.level.setBlockAndUpdate(fixture.pos, Blocks.AIR.defaultBlockState());
        fixture.level.setBlockAndUpdate(fixture.pos, Yigd.GRAVE.get().defaultBlockState());
        GraveBlockEntity loadedBlock = (GraveBlockEntity) fixture.level.getBlockEntity(fixture.pos);
        check(loadedBlock != null, "Missing legacy block fixture");
        // Keep the pending EOT callback, so migration must retain the live grave's identity.
        DeathInfoManager.INSTANCE = new DeathInfoManager();
        loadedBlock.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, fixture.level.registryAccess(), blockCheckpoint));
        check(loadedBlock.getGraveSkull().partialProfile().id().equals(owner.id()), "Legacy skull profile lost owner UUID");
        check(loadedBlock.getGraveText().getString().equals("Legacy grave"), "Legacy block JSON text was treated as literal JSON");
        YigdConfig.getConfig().graveConfig.maxBackupsPerPerson = 1;
        DeathInfoManager restored = LegacyGraveDataMigration.loadLegacyNbt(wrapper, fixture.level.getServer());
        check(YigdConfig.getConfig().graveConfig.maxBackupsPerPerson == 1, "Migration did not restore configured history limit");
        check(LegacyGraveDataMigration.sourceDataVersion() == -1, "Migration leaked source data version context");
        check(restored.getBackupData(ResolvableProfile.createResolved(owner)).size() == 2, "Migration evicted existing recovery history");
        GraveComponent recovered = restored.getGrave(fixture.grave.getGraveId()).orElseThrow(() -> failure("Legacy grave did not migrate"));
        GraveComponent retained = restored.getGrave(second.grave.getGraveId()).orElseThrow(() -> failure("Legacy history was capped during migration"));
        check(recovered.getOwner().partialProfile().id().equals(owner.id()), "Legacy owner UUID changed");
        check(recovered.getPos().equals(fixture.pos), "Legacy position was lost");
        check(recovered.getDeathMessage().getString().equals(fixture.grave.getDeathMessage().getString()), "Legacy death message did not reconstruct");
        check(restored.getGraveListMode() == ListMode.WHITELIST && restored.isInList(ResolvableProfile.createResolved(owner)),
                "Legacy whitelist did not migrate");
        ServerPlayer player = player(helper, fixture);
        recovered.applyToPlayer(player, fixture.level, Vec3.atCenterOf(fixture.pos), true);
        recovered.setStatus(GraveStatus.CLAIMED);
        recovered.removeGraveBlock();
        helper.runAfterDelay(2, () -> {
            assertRecovered(fixture, player);
            assertUnclaimedAndLinked(retained);
            assertNoDrops(fixture, fixture.pos);
            assertNoDrops(second, second.pos);
            helper.succeed();
        });
    }

    private static CompoundTag legacyCheckpoint(Fixture first, Fixture second, boolean forge120) {
        CompoundTag profile = legacyProfile(first.grave.getOwner().partialProfile(), forge120);
        ListTag components = new ListTag();
        for (Fixture fixture : List.of(first, second)) {
            CompoundTag grave = fixture.grave.toNbt(fixture.level.registryAccess());
            grave.put("owner", profile.copy());
            if (forge120) {
                CompoundTag pos = new CompoundTag();
                pos.putInt("X", fixture.pos.getX());
                pos.putInt("Y", fixture.pos.getY());
                pos.putInt("Z", fixture.pos.getZ());
                grave.put("pos", pos);
                CompoundTag death = new CompoundTag();
                death.putString("damageTypeId", "generic");
                death.putString("killedDisplayName", fixture.grave.getOwner().partialProfile().name());
                grave.put("deathMessage", death);
            } else {
                grave.putString("deathMessage", "{\"translate\":\"death.attack.generic\",\"with\":[{\"text\":\"YigdGraveTest\"}]}");
            }
            CompoundTag stack = new CompoundTag();
            stack.putString("id", "minecraft:diamond");
            CompoundTag marker = new CompoundTag();
            marker.putString(TOKEN, fixture.token.toString());
            if (forge120) {
                stack.putByte("Count", (byte) ITEM_COUNT);
                stack.put("tag", marker);
            } else {
                stack.putInt("count", ITEM_COUNT);
                CompoundTag itemComponents = new CompoundTag();
                itemComponents.put("minecraft:custom_data", marker);
                stack.put("components", itemComponents);
            }
            stack.putInt("Slot", 0);
            stack.putString("dropRule", DropRule.PUT_IN_GRAVE.name());
            ListTag items = new ListTag();
            items.add(stack);
            CompoundTag vanilla = new CompoundTag();
            vanilla.putInt("size", 41);
            vanilla.putInt("mainSize", 36);
            vanilla.putInt("armorSize", 4);
            vanilla.putInt("offHandSize", 1);
            vanilla.put("Items", items);
            CompoundTag inventory = new CompoundTag();
            inventory.put("vanilla", vanilla);
            inventory.put("mods", new CompoundTag());
            grave.put("inventory", inventory);
            components.add(grave);
        }
        CompoundTag player = new CompoundTag();
        player.put("user", profile.copy());
        player.put("graves", components);
        ListTag graves = new ListTag();
        graves.add(player);
        ListTag affected = new ListTag();
        affected.add(profile.copy());
        CompoundTag whitelist = new CompoundTag();
        whitelist.putString("listMode", ListMode.WHITELIST.name());
        whitelist.put("affectedPlayers", affected);
        CompoundTag data = new CompoundTag();
        data.put("graves", graves);
        data.put("respawns", new ListTag());
        data.put("whitelist", whitelist);
        CompoundTag wrapper = new CompoundTag();
        wrapper.putInt("DataVersion", forge120 ? 3465 : 3955);
        wrapper.put("data", data);
        return wrapper;
    }

    private static CompoundTag legacyProfile(GameProfile profile, boolean forge120) {
        CompoundTag legacy = new CompoundTag();
        legacy.putString(forge120 ? "Name" : "name", profile.name());
        legacy.store(forge120 ? "Id" : "id", UUIDUtil.CODEC, profile.id());
        return legacy;
    }

    private static Fixture placed(GameTestHelper helper, GameProfile owner, BlockPos relativePos, int xp) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(relativePos);
        Fixture fixture = component(level, owner, Vec3.atCenterOf(pos), xp);
        placeGrave(level, pos, fixture.grave);
        return fixture;
    }

    private static Fixture component(ServerLevel level, GameProfile owner, Vec3 pos, int xp) {
        UUID token = UUID.randomUUID();
        GraveComponent grave = new GraveComponent(ResolvableProfile.createResolved(owner), inventory(level, token), experience(xp), level,
                pos, Component.translatable("death.attack.generic", Component.literal(owner.name())), null);
        grave.backUp();
        return new Fixture(level, BlockPos.containing(pos), grave, token, xp);
    }

    private static InventoryComponent inventory(ServerLevel level, UUID token) {
        ItemStack stack = new ItemStack(Items.DIAMOND, ITEM_COUNT);
        CompoundTag marker = new CompoundTag();
        marker.putString(TOKEN, token.toString());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(marker));
        NonNullList<GraveItem> items = NonNullList.withSize(43, InventoryComponent.EMPTY_GRAVE_ITEM);
        items.set(0, new GraveItem(stack, DropRule.PUT_IN_GRAVE));
        CompoundTag vanilla = InventoryComponent.listToNbt(items, item -> {
            CompoundTag nbt = InventoryComponent.saveItemStack(item.stack, level.registryAccess());
            nbt.putString("dropRule", item.dropRule.name());
            return nbt;
        }, item -> item.stack.isEmpty());
        vanilla.putInt("mainSize", 36);
        vanilla.putInt("armorSize", 4);
        vanilla.putInt("offHandSize", 1);
        CompoundTag inventory = new CompoundTag();
        inventory.put("vanilla", vanilla);
        inventory.put("mods", new CompoundTag());
        return InventoryComponent.fromNbt(inventory, level.registryAccess());
    }

    private static ExpComponent experience(int xp) {
        CompoundTag nbt = new CompoundTag();
        nbt.putInt("value", xp);
        nbt.putDouble("original", xp);
        return ExpComponent.fromNbt(nbt);
    }

    private static GameProfile owner() { return new GameProfile(UUID.randomUUID(), "YigdGraveTest"); }

    private static ServerPlayer player(GameTestHelper helper, Fixture fixture) {
        ServerPlayer player = NativeTestPlayer.create(helper, fixture.grave.getOwner().partialProfile()).player();
        player.setHealth(player.getMaxHealth());
        return player;
    }

    private static void placeGrave(ServerLevel level, BlockPos pos, GraveComponent grave) {
        check(level.setBlockAndUpdate(pos, Yigd.GRAVE.get().defaultBlockState()), "Could not place fixture grave");
        GraveBlockEntity blockEntity = blockEntity(grave);
        blockEntity.setPreviousState(Blocks.AIR.defaultBlockState());
        blockEntity.setComponent(grave);
    }

    private static GraveBlockEntity blockEntity(GraveComponent grave) {
        check(grave.getWorld() != null && grave.getWorld().getBlockEntity(grave.getPos()) instanceof GraveBlockEntity, "Missing physical grave block entity");
        return (GraveBlockEntity) grave.getWorld().getBlockEntity(grave.getPos());
    }

    private static void assertUnclaimedAndLinked(GraveComponent grave) {
        check(grave.getStatus() == GraveStatus.UNCLAIMED, "Live grave was marked destroyed or claimed");
        check(grave.getGraveId().equals(blockEntity(grave).getGraveId()), "Grave block lost its backup UUID");
        check(DeathInfoManager.INSTANCE.getGrave(grave.getGraveId()).isPresent(), "Missing recovery backup");
    }

    private static void assertClaimedAndRecovered(Fixture fixture, ServerPlayer player) {
        check(fixture.grave.getStatus() == GraveStatus.CLAIMED, "Deferred removal changed claimed grave status");
        check(fixture.level.getBlockState(fixture.pos).isAir(), "Claimed grave block remains");
        assertRecovered(fixture, player);
    }

    private static void assertRecovered(Fixture fixture, ServerPlayer player) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (matches(stack, fixture.token)) count += stack.getCount();
        }
        check(count == ITEM_COUNT, "Saved inventory was not transferred exactly once; item count=" + count);
        check(player.totalExperience == fixture.xp, "Saved experience was not transferred exactly once");
    }

    private static void assertDroppedOnce(Fixture fixture) {
        List<ItemEntity> entities = drops(fixture.level, fixture.pos, fixture.token);
        check(entities.size() == 1, "Expected one dropped item entity, found " + entities.size());
        check(entities.getFirst().getItem().getCount() == ITEM_COUNT, "Dropped contents are missing or duplicated");
    }

    private static void assertNoDrops(Fixture fixture, BlockPos pos) {
        check(drops(fixture.level, pos, fixture.token).isEmpty(), "Grave unexpectedly dropped inventory");
    }

    private static List<ItemEntity> drops(ServerLevel level, BlockPos pos, UUID token) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3), item -> matches(item.getItem(), token));
    }

    private static boolean matches(ItemStack stack, UUID token) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getStringOr(TOKEN, "").equals(token.toString());
    }

    private static int minimumY(ServerLevel level) {
        int fallback = level.getMinY();
        for (MapEntryConfig.IntType entry : YigdConfig.getConfig().graveConfig.minimumGraveYLevel) {
            if (entry.key.equals(level.dimension().identifier().toString())) return entry.value;
            if (entry.key.equals("misc")) fallback = entry.value;
        }
        return fallback;
    }

    private static CompoundTag diskRoundTrip(ServerLevel level, CompoundTag checkpoint) {
        Path temporary = null;
        try {
            temporary = Files.createTempFile(level.getServer().getWorldPath(LevelResource.ROOT), "yigd-regression-", ".dat");
            NbtIo.writeCompressed(checkpoint, temporary);
            return NbtIo.readCompressed(temporary, NbtAccounter.unlimitedHeap());
        } catch (IOException exception) {
            throw failure("Saved-data disk round trip failed: " + exception);
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); }
                catch (IOException exception) { throw failure("Could not remove temporary checkpoint: " + exception); }
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw failure(message);
    }

    private static GameTestAssertException failure(String message) {
        return new GameTestAssertException(Component.literal(message), 0);
    }

    private static final class BodyBreakingWither extends WitherBoss {
        private BodyBreakingWither(ServerLevel level) { super(EntityType.WITHER, level); }
        private void advanceBodyBreakingAi(ServerLevel level) { super.customServerAiStep(level); }
    }

    private record Fixture(ServerLevel level, BlockPos pos, GraveComponent grave, UUID token, int xp) {}
    private record Isolation(DeathInfoManager manager, YigdConfig config, GraveConfig graves,
                             ExtraFeaturesConfig extras, InventoryConfig inventory, boolean mobGriefing) {}
}
