package com.b1n_ry.yigd.util;

import com.b1n_ry.yigd.Yigd;
import com.b1n_ry.yigd.config.YigdConfig;
import com.b1n_ry.yigd.data.DeathInfoManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;

/** Migrates the pre-26 saved-data path without modifying the original recovery file. */
public final class LegacyGraveDataMigration {
    private static final ThreadLocal<Integer> SOURCE_VERSION = ThreadLocal.withInitial(() -> -1);

    private LegacyGraveDataMigration() {}

    public static int sourceDataVersion() {
        return SOURCE_VERSION.get();
    }

    public static DeathInfoManager load(MinecraftServer server) {
        var storage = server.overworld().getDataStorage();
        var type = DeathInfoManager.getPersistentStateType(server);
        Path directory = server.getWorldPath(LevelResource.DATA);
        Path current = directory.resolve("yigd/yigd_data.dat");
        if (Files.exists(current)) {
            var existing = storage.get(type);
            if (existing == null) {
                throw new IllegalStateException("YiGD could not read " + current + "; refusing to overwrite grave recovery data");
            }
            return existing;
        }

        Path legacy = directory.resolve("yigd_data.dat");
        if (!Files.exists(legacy)) return storage.computeIfAbsent(type);

        try {
            CompoundTag wrapper = NbtIo.readCompressed(legacy, NbtAccounter.unlimitedHeap());
            var manager = loadLegacyNbt(wrapper, server);
            storage.set(type, manager);
            Yigd.LOGGER.info("Migrated YiGD recovery records to {}. Original file retained at {}", current, legacy);
            return manager;
        } catch (Exception failure) {
            throw new IllegalStateException("YiGD could not migrate " + legacy + "; original grave recovery data is retained", failure);
        }
    }

    public static DeathInfoManager loadLegacyNbt(CompoundTag wrapper, MinecraftServer server) {
        int oldLimit = YigdConfig.getConfig().graveConfig.maxBackupsPerPerson;
        try {
            CompoundTag data = wrapper.getCompound("data").orElseThrow(() -> new IllegalArgumentException("Missing grave data compound"));
            SOURCE_VERSION.set(wrapper.getIntOr("DataVersion", 3465));
            // Loading existing records must not trigger history-cap drops before migration is complete.
            YigdConfig.getConfig().graveConfig.maxBackupsPerPerson = Integer.MAX_VALUE;
            return DeathInfoManager.load(data, server.registryAccess(), server);
        } finally {
            SOURCE_VERSION.remove();
            YigdConfig.getConfig().graveConfig.maxBackupsPerPerson = oldLimit;
        }
    }
}
