package com.b1n_ry.yigd.gametest;

import com.b1n_ry.yigd.Yigd;
import com.b1n_ry.yigd.block.entity.GraveBlockEntity;
import com.b1n_ry.yigd.components.ExpComponent;
import com.b1n_ry.yigd.components.GraveComponent;
import com.b1n_ry.yigd.components.InventoryComponent;
import com.b1n_ry.yigd.config.YigdConfig;
import com.b1n_ry.yigd.data.DeathInfoManager;
import com.b1n_ry.yigd.data.GraveStatus;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

/** Separately packaged, opt-in server fixture; ordinary released YiGD contains none of this code. */
public final class YigdSmokeServer {
    private static final SavedDataType<Probe> PROBE_TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(YigdGameTestsMod.MOD_ID, "restart_probe"),
            () -> new Probe(new CompoundTag()), CompoundTag.CODEC.xmap(Probe::new, probe -> probe.data));
    private static MinecraftServer server;
    private static String phase;
    private static String mode;
    private static UUID playerId;
    private static GraveComponent grave;
    private static Probe probe;
    private static int ticks;
    private static int loginAt;
    private static boolean deathStarted;
    private static boolean stopping;

    public static void install() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ServerStartedEvent.class, YigdSmokeServer::started);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, PlayerEvent.PlayerLoggedInEvent.class, YigdSmokeServer::login);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ServerTickEvent.Post.class, YigdSmokeServer::tick);
    }

    private static void started(ServerStartedEvent event) {
        server = event.getServer();
        try {
            SmokeSupport.requireServerWorld(server);
            phase = SmokeSupport.phase();
            mode = System.getProperty("yigd.smokeMode", "client");
            SmokeSupport.check(mode.equals("client") || mode.equals("persistence"), "Unknown yigd.smokeMode");
            probe = server.overworld().getDataStorage().get(PROBE_TYPE);
            if (phase.equals("write")) {
                SmokeSupport.check(probe == null, "Write phase must start in a fresh disposable world");
                probe = new Probe(new CompoundTag());
                server.overworld().getDataStorage().set(PROBE_TYPE, probe);
            } else {
                SmokeSupport.check(probe != null, "Namespaced restart probe was not loaded from disk");
                playerId = UUID.fromString(probe.data.getStringOr("owner", ""));
                grave = DeathInfoManager.INSTANCE.getGrave(UUID.fromString(probe.data.getStringOr("grave", "")))
                        .orElseThrow(() -> new AssertionError("Actual YiGD SavedData lost the unclaimed grave after process restart"));
                assertGrave();
            }
            if (mode.equals("persistence") && phase.equals("write")) seedHeadlessGrave();
            updateState("ready");
            Yigd.LOGGER.info("YiGD isolated {} {} fixture ready", mode, phase);
        } catch (Throwable failure) {
            finish("FAIL " + failure);
        }
    }

    private static void seedHeadlessGrave() {
        playerId = UUID.randomUUID();
        ServerPlayer fixture = new ServerPlayer(server, server.overworld(), new GameProfile(playerId, "yigd-restart"), ClientInformation.createDefault());
        fixture.getInventory().setItem(0, SmokeSupport.expectedStack());
        BlockPos position = new BlockPos(8, 100, 8);
        preparePlatform(position);
        grave = new GraveComponent(ResolvableProfile.createResolved(fixture.getGameProfile()),
                new InventoryComponent(fixture), new ExpComponent(fixture), server.overworld(), position.getCenter(),
                Component.literal("YiGD isolated process-restart fixture"), null);
        grave.backUp();
        server.overworld().setBlock(position, Yigd.GRAVE.get().defaultBlockState(), 3);
        GraveBlockEntity blockEntity = (GraveBlockEntity) server.overworld().getBlockEntity(position);
        SmokeSupport.check(blockEntity != null, "Headless fixture did not create a real grave BE");
        blockEntity.setComponent(grave);
        blockEntity.setPreviousState(Blocks.AIR.defaultBlockState());
        storeProbe();
    }

    private static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (stopping || !mode.equals("client")) return;
        try {
            ServerPlayer player = (ServerPlayer) event.getEntity();
            SmokeSupport.check(server.getPlayerList().getPlayers().size() == 1, "Fixture permits only its one test client");
            if (phase.equals("write")) {
                playerId = player.getUUID();
                SmokeSupport.check(player.getInventory().isEmpty(), "Fresh fixture player already has inventory contents");
                player.getInventory().setItem(0, SmokeSupport.expectedStack());
                preparePlatform(new BlockPos(8, 100, 8));
                movePlayer(player, new BlockPos(8, 100, 8));
                player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                player.setInvulnerable(false);
                player.getAbilities().invulnerable = false;
                player.setHealth(player.getMaxHealth());
                server.overworld().getGameRules().set(GameRules.KEEP_INVENTORY, false, server);
                player.inventoryMenu.broadcastChanges();
                updateState("seeded");
            } else {
                SmokeSupport.check(playerId.equals(player.getUUID()), "Restart client used a different owner UUID");
                SmokeSupport.check(player.getInventory().isEmpty(), "Restart recovered a duplicate inventory copy before grave claim");
                assertGrave();
                movePlayerToGrave(player);
                updateState("grave_ready");
            }
            loginAt = ticks;
        } catch (Throwable failure) {
            finish("FAIL " + failure);
        }
    }

    private static void tick(ServerTickEvent.Post event) {
        if (server == null || stopping) return;
        ticks++;
        try {
            if (mode.equals("persistence") && ticks >= 40) {
                assertGrave();
                finish("PASS " + phase + ": actual namespaced SavedData and physical grave block entity survive process restart");
                return;
            }
            ServerPlayer player = playerId == null ? null : server.getPlayerList().getPlayer(playerId);
            if (phase.equals("write") && mode.equals("client") && player != null && ticks - loginAt >= 80 && !deathStarted) {
                deathStarted = true;
                player.setHealth(1);
                player.invulnerableTime = 0;
                player.hurtServer(player.level(), player.damageSources().generic(), 1000);
                SmokeSupport.check(player.isDeadOrDying(), "Real logged-in server player did not die");
                SmokeSupport.check(player.getInventory().isEmpty(), "Native player death did not capture inventory");
                updateState("dead");
            }
            if (phase.equals("write") && deathStarted && grave == null) {
                List<GraveComponent> backups = DeathInfoManager.INSTANCE.getBackupData(ResolvableProfile.createResolved(player.getGameProfile()));
                if (!backups.isEmpty()) {
                    SmokeSupport.check(backups.size() == 1, "Native death generated duplicate backups");
                    grave = backups.getFirst();
                    if (server.overworld().getBlockEntity(grave.getPos()) instanceof GraveBlockEntity) {
                        storeProbe();
                    } else grave = null;
                }
            }
            if (phase.equals("write") && grave != null && player != null && !player.isDeadOrDying() && player.connection.hasClientLoaded()) {
                Properties current = SmokeSupport.readState();
                if (!"grave_ready".equals(current.getProperty("stage"))) {
                    movePlayerToGrave(player);
                    assertGrave();
                    updateState("grave_ready");
                }
            }
            Path resultFile = SmokeSupport.root().resolve("client-result-" + phase + ".txt");
            if (Files.isRegularFile(resultFile)) {
                String result = Files.readString(resultFile).trim();
                SmokeSupport.check(result.startsWith("PASS"), result);
                SmokeSupport.check(player != null, "No real player logged in");
                if (phase.equals("write")) {
                    assertGrave();
                    SmokeSupport.check(player.getInventory().isEmpty(), "Player retained a pre-restart duplicate");
                } else {
                    SmokeSupport.check(grave.getStatus() == GraveStatus.CLAIMED, "Client did not claim the persisted grave");
                    int count = player.getInventory().getNonEquipmentItems().stream().filter(SmokeSupport::isExpected).mapToInt(net.minecraft.world.item.ItemStack::getCount).sum();
                    SmokeSupport.check(count == 17, "Claimed server inventory count differs: " + count);
                }
                finish(result);
            } else if (ticks > 3000) finish("FAIL: client timeout after 3000 server ticks");
        } catch (Throwable failure) {
            finish("FAIL " + failure);
        }
    }

    private static void assertGrave() {
        SmokeSupport.check(grave != null && grave.getStatus() == GraveStatus.UNCLAIMED, "Saved grave is absent or already claimed");
        int count = grave.getInventoryComponent().getItems().stream().map(item -> item.stack).filter(SmokeSupport::isExpected)
                .mapToInt(net.minecraft.world.item.ItemStack::getCount).sum();
        SmokeSupport.check(count == 17, "Saved named grave contents differ: " + count);
        SmokeSupport.check(server.overworld().getBlockEntity(grave.getPos()) instanceof GraveBlockEntity, "Saved grave BE missing");
        GraveBlockEntity blockEntity = (GraveBlockEntity) server.overworld().getBlockEntity(grave.getPos());
        SmokeSupport.check(grave.getGraveId().equals(blockEntity.getGraveId()) && blockEntity.isUnclaimed(), "Saved BE UUID/unclaimed state differs");
    }

    private static void storeProbe() {
        probe.data.putString("owner", playerId.toString());
        probe.data.putString("grave", grave.getGraveId().toString());
        probe.data.putString("name", SmokeSupport.ITEM_NAME);
        probe.setDirty();
        DeathInfoManager.INSTANCE.setDirty();
    }

    private static void preparePlatform(BlockPos center) {
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
            server.overworld().setBlock(center.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
        }
    }

    private static void movePlayer(ServerPlayer player, BlockPos position) {
        player.setNoGravity(true);
        player.connection.teleport(position.getX() + 0.5, position.getY(), position.getZ() + 0.5, 0, 0);
        player.setRespawnPosition(new ServerPlayer.RespawnConfig(
                net.minecraft.world.level.storage.LevelData.RespawnData.of(player.level().dimension(), position, 0, 0), true), false);
    }

    private static void movePlayerToGrave(ServerPlayer player) {
        var facing = server.overworld().getBlockState(grave.getPos()).getValue(BlockStateProperties.HORIZONTAL_FACING);
        movePlayer(player, grave.getPos().relative(facing, 3));
    }

    private static void updateState(String stage) throws java.io.IOException {
        Properties state = new Properties();
        state.setProperty("phase", phase);
        state.setProperty("stage", stage);
        if (grave != null) {
            state.setProperty("grave", grave.getGraveId().toString());
            state.setProperty("x", Integer.toString(grave.getPos().getX()));
            state.setProperty("y", Integer.toString(grave.getPos().getY()));
            state.setProperty("z", Integer.toString(grave.getPos().getZ()));
            state.setProperty("status", grave.getStatus().name());
        }
        SmokeSupport.writeState(state);
        Yigd.LOGGER.info("YiGD isolated smoke stage {} {}", phase, stage);
    }

    private static void finish(String result) {
        if (stopping) return;
        stopping = true;
        try {
            server.getPlayerList().saveAll();
            server.saveEverything(false, true, true);
            Files.writeString(SmokeSupport.root().resolve("server-result-" + (phase == null ? "unknown" : phase) + ".txt"), result + "\n");
        } catch (Throwable failure) {
            Yigd.LOGGER.error("YiGD isolated server save/result failed", failure);
        }
        Yigd.LOGGER.info("YiGD isolated server result: {}", result);
        server.halt(false);
    }

    private static final class Probe extends SavedData {
        private final CompoundTag data;
        private Probe(CompoundTag data) { this.data = data; }
    }

    private YigdSmokeServer() { }
}
