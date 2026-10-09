package com.b1n_ry.yigd.gametest;

import com.b1n_ry.yigd.Yigd;
import com.b1n_ry.yigd.client.gui.GraveOverviewScreen;
import com.b1n_ry.yigd.client.gui.GraveSelectionScreen;
import com.b1n_ry.yigd.components.GraveComponent;
import com.b1n_ry.yigd.config.YigdConfig;
import com.b1n_ry.yigd.networking.packets.GraveOverviewRequestC2SPacket;
import com.b1n_ry.yigd.networking.packets.GraveSelectionRequestC2SPacket;
import me.shedaniel.autoconfig.AutoConfigClient;
import me.shedaniel.clothconfig2.gui.AbstractConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;

import java.nio.file.Files;
import java.util.Properties;
import java.util.UUID;

/** Explicit fixture for a marked disposable client and localhost server; no existing profile/world is selected. */
public final class YigdClientSmoke {
    private static String phase;
    private static int state;
    private static int ticks;
    private static int entered;
    private static boolean finished;
    private static int finishedAt;
    private static BlockPos gravePosition;
    private static Direction graveFacing;
    private static UUID graveId;

    public static void install() {
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, YigdClientSmoke::tick);
    }

    private static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ticks++;
        if (finished) {
            awaitServerSave(minecraft);
            return;
        }
        try {
            if (phase == null) {
                SmokeSupport.requireIsolatedDirectory(minecraft.gameDirectory.toPath());
                phase = SmokeSupport.phase();
            }
            SmokeSupport.check(ticks <= 3600, "Client fixture timed out at state " + state + ", screen=" + minecraft.screen);
            Properties serverState = SmokeSupport.readState();
            if (state >= 4 && gravePosition == null && "grave_ready".equals(serverState.getProperty("stage"))) {
                graveId = UUID.fromString(serverState.getProperty("grave"));
                gravePosition = new BlockPos(Integer.parseInt(serverState.getProperty("x")),
                        Integer.parseInt(serverState.getProperty("y")), Integer.parseInt(serverState.getProperty("z")));
            }
            if (state >= 4 && minecraft.player != null) pointCamera(minecraft);
            if (state == 0 && minecraft.screen instanceof TitleScreen && minecraft.getOverlay() == null) {
                String address = "127.0.0.1:" + Integer.getInteger("yigd.smokePort", 25578);
                ConnectScreen.startConnecting(minecraft.screen, minecraft, ServerAddress.parseString(address),
                        new ServerData("Isolated YiGD 26.1.2 verification", address, ServerData.Type.OTHER), false, null);
                advance();
            } else if (state == 1 && minecraft.player != null && minecraft.level != null && ticks - entered > 30) {
                if (phase.equals("write") && "seeded".equals(serverState.getProperty("stage"))) {
                    assertRecovered(minecraft);
                    minecraft.setScreen(new InventoryScreen(minecraft.player));
                    advance();
                } else if (phase.equals("read") && "grave_ready".equals(serverState.getProperty("stage"))) {
                    SmokeSupport.check(expectedCount(minecraft) == 0, "Saved player inventory duplicates unclaimed grave contents");
                    state = 4;
                    entered = ticks;
                }
            } else if (state == 2 && ticks - entered > 10) {
                capture(minecraft, "seeded-inventory");
                minecraft.setScreen(null);
                advance();
            } else if (state == 3 && minecraft.screen instanceof DeathScreen && ticks - entered > 20) {
                SmokeSupport.check(expectedCount(minecraft) == 0, "Real death left a duplicated client inventory copy");
                capture(minecraft, "actual-death");
                minecraft.player.respawn();
                advance();
            } else if (state == 4 && minecraft.player != null && !minecraft.player.isDeadOrDying()
                    && "grave_ready".equals(serverState.getProperty("stage")) && ticks - entered > 40) {
                SmokeSupport.check(expectedCount(minecraft) == 0, "Unclaimed grave also exists in live client inventory");
                graveId = UUID.fromString(serverState.getProperty("grave"));
                gravePosition = new BlockPos(Integer.parseInt(serverState.getProperty("x")),
                        Integer.parseInt(serverState.getProperty("y")), Integer.parseInt(serverState.getProperty("z")));
                SmokeSupport.check(minecraft.level.getBlockEntity(gravePosition) instanceof com.b1n_ry.yigd.block.entity.GraveBlockEntity,
                        "Server-created saved grave BE did not synchronize to the real client");
                graveFacing = minecraft.level.getBlockState(gravePosition).getValue(BlockStateProperties.HORIZONTAL_FACING);
                capture(minecraft, "unclaimed-grave-render");
                ClientPacketDistributor.sendToServer(new GraveSelectionRequestC2SPacket(
                        ResolvableProfile.createResolved(minecraft.player.getGameProfile())));
                advance();
            } else if (state == 5 && minecraft.screen instanceof GraveSelectionScreen && ticks - entered > 20) {
                capture(minecraft, "grave-selection");
                ClientPacketDistributor.sendToServer(new GraveOverviewRequestC2SPacket(graveId));
                advance();
            } else if (state == 6 && minecraft.screen instanceof GraveOverviewScreen screen && ticks - entered > 20) {
                var field = GraveOverviewScreen.class.getDeclaredField("graveComponent");
                field.setAccessible(true);
                GraveComponent component = (GraveComponent) field.get(screen);
                int contents = component.getInventoryComponent().getItems().stream().map(item -> item.stack)
                        .filter(SmokeSupport::isExpected).mapToInt(ItemStack::getCount).sum();
                SmokeSupport.check(contents == 17, "Real overview payload lost named stack data/count");
                capture(minecraft, "grave-overview");
                minecraft.setScreen(AutoConfigClient.getConfigScreen(YigdConfig.class, null).get());
                advance();
            } else if (state == 7 && minecraft.screen instanceof AbstractConfigScreen screen && ticks - entered > 30) {
                capture(minecraft, "cloth-config-screen");
                screen.save();
                minecraft.setScreen(null);
                advance();
            } else if (state == 8 && ticks - entered > 30) {
                capture(minecraft, "grave-render-before-claim");
                if (phase.equals("write")) {
                    finish("PASS write: real logged-in death, respawn, named stack captured once, client BE/render, selection/overview packets and native Cloth config open/save; saved unclaimed for process restart");
                } else {
                    clickGrave(minecraft);
                    advance();
                }
            } else if (state == 9 && minecraft.player != null && expectedCount(minecraft) == 17 && ticks - entered > 30) {
                assertRecovered(minecraft);
                clickGrave(minecraft);
                minecraft.setScreen(new InventoryScreen(minecraft.player));
                advance();
            } else if (state == 10 && ticks - entered > 20) {
                assertRecovered(minecraft);
                capture(minecraft, "recovered-inventory");
                minecraft.setScreen(null);
                finish("PASS read: new client/server processes loaded the saved grave and empty player inventory, real GUI packets/render, native Cloth config open/save and owner right-click recovered the exact named 17-stack once");
            }
        } catch (Throwable failure) {
            Yigd.LOGGER.error("YiGD isolated client verification failed", failure);
            finish("FAIL " + phase + ": " + failure);
        }
    }

    private static void clickGrave(Minecraft minecraft) {
        SmokeSupport.check(minecraft.gameMode != null && minecraft.player != null, "Real game mode/player missing for owner interaction");
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(gravePosition).add(
                graveFacing.getStepX() * 0.5, 0, graveFacing.getStepZ() * 0.5), graveFacing, gravePosition, false);
        minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit);
    }

    private static void pointCamera(Minecraft minecraft) {
        minecraft.mouseHandler.releaseMouse();
        float yaw = 0;
        if (gravePosition != null) {
            double dx = gravePosition.getX() + 0.5 - minecraft.player.getX();
            double dz = gravePosition.getZ() + 0.5 - minecraft.player.getZ();
            yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        }
        minecraft.player.setYRot(yaw);
        minecraft.player.setXRot(20);
        minecraft.player.yRotO = yaw;
        minecraft.player.xRotO = 20;
    }

    private static int expectedCount(Minecraft minecraft) {
        if (minecraft.player == null) return 0;
        return minecraft.player.getInventory().getNonEquipmentItems().stream().filter(SmokeSupport::isExpected).mapToInt(ItemStack::getCount).sum();
    }

    private static void assertRecovered(Minecraft minecraft) {
        var matches = minecraft.player.getInventory().getNonEquipmentItems().stream().filter(SmokeSupport::isExpected).toList();
        SmokeSupport.check(matches.size() == 1 && matches.getFirst().getCount() == 17,
                "Native client lost/duplicated the exact named stack or its custom data components");
    }

    private static void capture(Minecraft minecraft, String label) {
        Screenshot.grab(minecraft.gameDirectory, "yigd-26.1.2-" + phase + "-" + label + ".png",
                minecraft.getMainRenderTarget(), 1, message -> Yigd.LOGGER.info("YiGD fixture screenshot: {}", message.getString()));
    }

    private static void advance() {
        state++;
        entered = ticks;
        Yigd.LOGGER.info("YiGD isolated client stage {} {}", phase, state);
    }

    private static void finish(String result) {
        if (finished) return;
        finished = true;
        finishedAt = ticks;
        try {
            Files.writeString(SmokeSupport.root().resolve("client-result-" + (phase == null ? "unknown" : phase) + ".txt"), result + "\n");
        } catch (Throwable failure) {
            Yigd.LOGGER.error("YiGD fixture result could not be saved", failure);
        }
        Yigd.LOGGER.info("YiGD isolated client result: {}", result);
    }

    private static void awaitServerSave(Minecraft minecraft) {
        try {
            if (Files.isRegularFile(SmokeSupport.root().resolve("server-result-" + phase + ".txt"))) minecraft.stop();
            else if (ticks - finishedAt > 400) {
                Files.writeString(SmokeSupport.root().resolve("client-result-" + phase + ".txt"), "FAIL: server save acknowledgement timed out\n");
                minecraft.stop();
            }
        } catch (Throwable failure) {
            Yigd.LOGGER.error("YiGD fixture save acknowledgement failed", failure);
            minecraft.stop();
        }
    }

    private YigdClientSmoke() { }
}
