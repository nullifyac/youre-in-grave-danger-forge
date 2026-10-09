package com.b1n_ry.yigd.gametest;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Explicit opt-in boundary: fixtures require a marker in their own disposable game directory. */
final class SmokeSupport {
    static final String MARKER = "YIGD_ISOLATED_RUNTIME_26_1_2";
    static final String ITEM_NAME = "YiGD 26.1.2 native restart diamonds";
    static final String ITEM_TOKEN = "yigd-26.1.2-restart-probe";

    static Path root() {
        String configured = System.getProperty("yigd.smokeRoot");
        if (configured == null || configured.isBlank()) throw new IllegalStateException("yigd.smokeRoot is required");
        return Path.of(configured).toAbsolutePath().normalize();
    }

    static String phase() {
        String phase = System.getProperty("yigd.smokePhase");
        if (!"write".equals(phase) && !"read".equals(phase)) {
            throw new IllegalStateException("yigd.smokePhase must explicitly be write or read");
        }
        return phase;
    }

    static void requireIsolatedDirectory(Path directory) throws IOException {
        Path actual = directory.toAbsolutePath().normalize();
        Path marker = actual.resolve(".yigd-isolated-runtime");
        if (!Files.isRegularFile(marker) || !Files.readString(marker).trim().equals(MARKER)) {
            throw new IllegalStateException("Runtime fixture requires its marker in a disposable game directory: " + actual);
        }
        String clientDirectory = System.getProperty("yigd.smokeClientDirectory");
        boolean dedicatedClient = Boolean.getBoolean("yigd.smokeClient") && clientDirectory != null
                && actual.equals(Path.of(clientDirectory).toAbsolutePath().normalize());
        if (!actual.startsWith(root()) && !dedicatedClient) {
            throw new IllegalStateException("Runtime fixture directory is outside yigd.smokeRoot and its explicit dedicated client directory");
        }
    }

    static void requireServerWorld(MinecraftServer server) throws IOException {
        requireIsolatedDirectory(Path.of(""));
        if (!server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().startsWith(Path.of("").toAbsolutePath().normalize())) {
            throw new IllegalStateException("Runtime fixture world must be inside its marked game directory");
        }
    }

    static ItemStack expectedStack() {
        ItemStack stack = new ItemStack(Items.DIAMOND, 17);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(ITEM_NAME));
        CompoundTag payload = new CompoundTag();
        payload.putString("yigd_smoke", ITEM_TOKEN);
        payload.putInt("payload", 2612);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(payload));
        return stack;
    }

    static boolean isExpected(ItemStack stack) {
        return ItemStack.isSameItemSameComponents(stack, expectedStack());
    }

    static Properties readState() throws IOException {
        Properties state = new Properties();
        Path file = root().resolve("server-state.properties");
        if (Files.exists(file)) try (var reader = Files.newBufferedReader(file)) { state.load(reader); }
        return state;
    }

    static void writeState(Properties state) throws IOException {
        try (var writer = Files.newBufferedWriter(root().resolve("server-state.properties"))) { state.store(writer, "Sanitized YiGD fixture state"); }
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private SmokeSupport() { }
}
