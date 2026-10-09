package com.b1n_ry.yigd.gametest;

import com.b1n_ry.yigd.Yigd;
import com.b1n_ry.yigd.components.ExpComponent;
import com.b1n_ry.yigd.components.GraveComponent;
import com.b1n_ry.yigd.components.InventoryComponent;
import com.b1n_ry.yigd.config.YigdConfig;
import com.b1n_ry.yigd.config.CommandConfig;
import com.b1n_ry.yigd.data.DeathInfoManager;
import com.b1n_ry.yigd.networking.ServerPacketHandler;
import com.b1n_ry.yigd.networking.packets.*;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.lang.reflect.Proxy;
import java.util.UUID;

/** Native players and payload codecs exercise access checks and paid compass creation. */
public final class NetworkingGameTests {
    private NetworkingGameTests() {}

    public static Object setup(ServerLevel level, String environment) {
        Object state = GraveGameplayGameTests.setup(level, environment);
        var config = YigdConfig.getConfig();
        CommandConfig commands = config.commandConfig;
        config.commandConfig = new CommandConfig();
        config.extraFeatures.graveKeys.enabled = true;
        config.extraFeatures.graveKeys.obtainableFromGui = true;
        config.extraFeatures.graveCompass.cloneRecoveryCompassWithGUI = true;
        return new Isolation(state, commands);
    }

    public static void teardown(ServerLevel level, Object state) {
        var saved = (Isolation) state;
        YigdConfig.getConfig().commandConfig = saved.commands();
        GraveGameplayGameTests.teardown(level, saved.graves());
    }

    private record Isolation(Object graves, CommandConfig commands) {}

    @GameTest
    public static void selfAndForeignViewsUseTheirConfiguredPermissionLevels(GameTestHelper helper) {
        NativeTestPlayer owner = player(helper, "yigd-owner");
        NativeTestPlayer visitor = player(helper, "yigd-visitor");
        GraveComponent grave = grave(owner.player());
        var profileRequest = new GraveSelectionRequestC2SPacket(grave.getOwner());
        ServerPacketHandler.graveSelectionRequest(profileRequest, context(owner.player()));
        helper.assertTrue(owner.packets().stream().anyMatch(packet -> packet instanceof ClientboundCustomPayloadPacket custom
                && custom.payload() instanceof GraveSelectionS2CPacket selection && selection.data().size() == 1),
                "Self list incorrectly required permission to view other players");
        var config = YigdConfig.getConfig();
        config.commandConfig.viewSelfPermissionLevel = 4;
        config.commandConfig.viewUserPermissionLevel = 0;
        config.graveConfig.unlockable = false;
        ServerPacketHandler.graveOverviewRequest(new GraveOverviewRequestC2SPacket(grave.getGraveId()), context(visitor.player()));
        ServerPacketHandler.graveSelectionRequest(profileRequest, context(visitor.player()));
        ServerPacketHandler.graveOverviewRequest(new GraveOverviewRequestC2SPacket(grave.getGraveId()), context(owner.player()));
        ServerPacketHandler.lockGrave(new LockGraveC2SPacket(grave.getGraveId(), false), context(visitor.player()));
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(visitor.packets().stream().anyMatch(packet -> packet instanceof ClientboundCustomPayloadPacket custom
                    && custom.payload() instanceof GraveOverviewS2CPacket), "Foreign view incorrectly required self permission");
            helper.assertTrue(visitor.packets().stream().anyMatch(packet -> packet instanceof ClientboundCustomPayloadPacket custom
                    && custom.payload() instanceof GraveSelectionS2CPacket selection && selection.data().size() == 1),
                    "Allowed foreign list was not sent");
            helper.assertTrue(owner.packets().stream().noneMatch(packet -> packet instanceof ClientboundCustomPayloadPacket custom
                    && custom.payload() instanceof GraveOverviewS2CPacket), "Disabled self preview was sent");
            helper.assertTrue(grave.isLocked(), "Disabled unlocking was bypassed by a payload");
            helper.succeed();
        });
    }

    @GameTest
    public static void foreignGraveRequestsCannotRevealUnlockOrGenerateItems(GameTestHelper helper) {
        NativeTestPlayer owner = player(helper, "yigd-owner");
        NativeTestPlayer visitor = player(helper, "yigd-visitor");
        GraveComponent grave = grave(owner.player());
        helper.assertTrue(!ServerPacketHandler.canViewGrave(visitor.player(), grave), "Unprivileged player may inspect another owner's grave");
        var context = context(visitor.player());
        ServerPacketHandler.graveOverviewRequest(new GraveOverviewRequestC2SPacket(grave.getGraveId()), context);
        ServerPacketHandler.lockGrave(new LockGraveC2SPacket(grave.getGraveId(), false), context);
        ServerPacketHandler.requestKey(new RequestKeyC2SPacket(grave.getGraveId()), context);
        visitor.player().getInventory().setItem(0, new ItemStack(Items.RECOVERY_COMPASS));
        ServerPacketHandler.requestCompass(new RequestCompassC2SPacket(grave.getGraveId()), context);
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(grave.isLocked(), "Foreign lock request modified the grave");
            helper.assertValueEqual(visitor.player().getInventory().countItem(Yigd.GRAVE_KEY_ITEM.get()), 0, "foreign key count");
            helper.assertValueEqual(visitor.player().getInventory().countItem(Items.COMPASS), 0, "foreign compass count");
            helper.assertValueEqual(visitor.player().getInventory().countItem(Items.RECOVERY_COMPASS), 1, "denied request spent a compass");
            helper.assertTrue(visitor.packets().stream().noneMatch(packet -> packet instanceof ClientboundCustomPayloadPacket custom
                    && custom.payload() instanceof GraveOverviewS2CPacket), "Foreign inventory preview was sent");
            helper.succeed();
        });
    }

    @GameTest
    public static void ownerPreviewRoundTripsAndLockRequestWorks(GameTestHelper helper) {
        NativeTestPlayer owner = player(helper, "yigd-owner");
        GraveComponent grave = grave(owner.player());
        helper.assertTrue(ServerPacketHandler.canViewGrave(owner.player(), grave), "Owner cannot inspect their grave");
        ServerPacketHandler.graveOverviewRequest(new GraveOverviewRequestC2SPacket(grave.getGraveId()), context(owner.player()));
        ServerPacketHandler.lockGrave(new LockGraveC2SPacket(grave.getGraveId(), false), context(owner.player()));
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(!grave.isLocked(), "Owner lock request did not apply");
            var packet = owner.packets().stream().filter(p -> p instanceof ClientboundCustomPayloadPacket custom
                    && custom.payload() instanceof GraveOverviewS2CPacket).map(p -> (GraveOverviewS2CPacket) ((ClientboundCustomPayloadPacket) p).payload())
                    .findFirst().orElseThrow();
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
            try {
                GraveOverviewS2CPacket.STREAM_CODEC.encode(buffer, packet);
                var decoded = GraveOverviewS2CPacket.STREAM_CODEC.decode(buffer);
                helper.assertValueEqual(decoded.component().getGraveId(), grave.getGraveId(), "preview UUID");
                helper.assertValueEqual(decoded.component().getInventoryComponent().getItems().getFirst().stack.getCount(), 3, "preview item count");
                helper.assertTrue(buffer.readableBytes() == 0, "Preview codec left unread bytes");
                helper.assertTrue(!decoded.canRestore() && !decoded.canDelete(), "Owner acquired administrator actions");
            } finally { buffer.release(); }
            helper.succeed();
        });
    }

    @GameTest
    public static void guiCompassRequiresAndConsumesOneRecoveryCompass(GameTestHelper helper) {
        NativeTestPlayer owner = player(helper, "yigd-owner");
        GraveComponent grave = grave(owner.player());
        var request = new RequestCompassC2SPacket(grave.getGraveId());
        ServerPacketHandler.requestCompass(request, context(owner.player()));
        helper.runAfterDelay(2, () -> {
            helper.assertValueEqual(owner.player().getInventory().countItem(Items.COMPASS), 0, "unpaid compass count");
            owner.player().getInventory().setItem(0, new ItemStack(Items.RECOVERY_COMPASS, 2));
            ServerPacketHandler.requestCompass(request, context(owner.player()));
        });
        helper.runAfterDelay(4, () -> {
            helper.assertValueEqual(owner.player().getInventory().countItem(Items.RECOVERY_COMPASS), 1, "recovery compass cost");
            helper.assertValueEqual(owner.player().getInventory().countItem(Items.COMPASS), 1, "created compass count");
            boolean linked = false;
            for (int i = 0; i < owner.player().getInventory().getContainerSize(); i++) {
                ItemStack stack = owner.player().getInventory().getItem(i);
                if (stack.is(Items.COMPASS)) linked = grave.getGraveId().equals(stack.get(Yigd.GRAVE_ID));
            }
            helper.assertTrue(linked, "Created compass lost its grave UUID");
            helper.succeed();
        });
    }

    private static NativeTestPlayer player(GameTestHelper helper, String name) {
        return NativeTestPlayer.create(helper, new GameProfile(UUID.randomUUID(), name));
    }

    private static GraveComponent grave(ServerPlayer player) {
        player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 3));
        var grave = new GraveComponent(ResolvableProfile.createResolved(player.getGameProfile()), new InventoryComponent(player),
                new ExpComponent(player), player.level(), player.position(), Component.literal("Network regression"), null);
        InventoryComponent.clearPlayer(player);
        DeathInfoManager.INSTANCE.addBackup(grave.getOwner(), grave);
        return grave;
    }

    private static IPayloadContext context(ServerPlayer player) {
        return (IPayloadContext) Proxy.newProxyInstance(IPayloadContext.class.getClassLoader(), new Class<?>[]{IPayloadContext.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "player" -> player;
                    case "listener" -> player.connection;
                    default -> throw new UnsupportedOperationException("Unexpected context method " + method.getName());
                });
    }
}
