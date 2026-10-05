package com.b1n_ry.yigd.data;

import com.b1n_ry.yigd.block.entity.GraveBlockEntity;
import com.b1n_ry.yigd.components.GraveComponent;
import com.b1n_ry.yigd.components.RespawnComponent;
import com.b1n_ry.yigd.config.ExtraFeaturesConfig;
import com.b1n_ry.yigd.config.YigdConfig;
import com.b1n_ry.yigd.util.GraveCompassHelper;
import com.mojang.authlib.GameProfile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps track of backed up graves and respawn data.
 */
public class DeathInfoManager extends SavedData {
    public static DeathInfoManager INSTANCE = new DeathInfoManager();

    private final Map<GameProfile, RespawnComponent> respawnEffects = new HashMap<>();
    private final Map<GameProfile, List<GraveComponent>> graveBackups = new HashMap<>();
    private final Map<UUID, GraveComponent> graveMap = new HashMap<>();

    private ListMode graveListMode = ListMode.BLACKLIST;
    private final Set<GameProfile> affectedPlayers = new HashSet<>();

    public void clear() {
        this.respawnEffects.clear();
        this.graveBackups.clear();
        this.graveMap.clear();
        this.affectedPlayers.clear();
    }

    public Set<GameProfile> getAffectedPlayers() {
        return this.affectedPlayers;
    }

    public InteractionResult delete(UUID graveId) {
        GraveComponent component = this.graveMap.get(graveId);
        if (component == null) return InteractionResult.FAIL;

        GameProfile profile = component.getOwner();
        this.graveMap.remove(graveId);

        if (!this.graveBackups.containsKey(profile)) return InteractionResult.PASS;
        this.graveBackups.get(profile).remove(component);

        if (component.getStatus() != GraveStatus.UNCLAIMED) return InteractionResult.SUCCESS;

        return component.removeGraveBlock() ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    public void addRespawnComponent(GameProfile profile, RespawnComponent component) {
        if (!this.respawnEffects.containsKey(profile)) {
            this.respawnEffects.put(profile, component);
        }
    }

    public Optional<RespawnComponent> getRespawnComponent(GameProfile profile) {
        return Optional.ofNullable(this.respawnEffects.get(profile));
    }

    public Map<GameProfile, List<GraveComponent>> getPlayerGraves() {
        return this.graveBackups;
    }

    public void removeRespawnComponent(GameProfile profile) {
        this.respawnEffects.remove(profile);
    }

    public void addBackup(GameProfile profile, GraveComponent component) {
        YigdConfig config = YigdConfig.getConfig();

        List<GraveComponent> playerGraves = this.graveBackups.computeIfAbsent(profile, k -> new ArrayList<>());
        playerGraves.add(component);
        this.graveMap.put(component.getGraveId(), component);

        if (playerGraves.size() > config.graveConfig.maxBackupsPerPerson) {
            GraveComponent toBeRemoved = playerGraves.get(0);
            this.delete(toBeRemoved.getGraveId());
            if (toBeRemoved.getStatus() == GraveStatus.UNCLAIMED && config.graveConfig.dropFromOldestWhenDeleted) {
                toBeRemoved.dropAllGraveItems();
            }
        }

        if (config.extraFeatures.graveCompass.pointToClosest != ExtraFeaturesConfig.GraveCompassConfig.CompassGraveTarget.DISABLED
                && component.getStatus() == GraveStatus.UNCLAIMED) {
            GraveCompassHelper.addGravePosition(component.getWorldResourceKey(), component.getPos(), profile.getId());
        }
    }

    public @NotNull List<GraveComponent> getBackupData(GameProfile profile) {
        return this.graveBackups.computeIfAbsent(profile, k -> new ArrayList<>());
    }

    public Optional<GraveComponent> getGrave(UUID graveId) {
        return Optional.ofNullable(this.graveMap.get(graveId));
    }

    public ListMode getGraveListMode() {
        return this.graveListMode;
    }

    public void setGraveListMode(ListMode listMode) {
        this.graveListMode = listMode;
    }

    public void addToList(GameProfile profile) {
        this.affectedPlayers.add(profile);
    }

    public boolean removeFromList(GameProfile profile) {
        return this.affectedPlayers.remove(profile);
    }

    public boolean isInList(GameProfile profile) {
        return this.affectedPlayers.contains(profile);
    }

    @Override
    public CompoundTag save(CompoundTag nbt) {
        ListTag respawnNbt = new ListTag();
        ListTag graveNbt = new ListTag();
        CompoundTag graveListNbt = new CompoundTag();

        for (Map.Entry<GameProfile, RespawnComponent> entry : this.respawnEffects.entrySet()) {
            CompoundTag respawnCompound = new CompoundTag();
            respawnCompound.put("user", NbtUtils.writeGameProfile(new CompoundTag(), entry.getKey()));
            respawnCompound.put("component", entry.getValue().toNbt());
            respawnNbt.add(respawnCompound);
        }

        for (Map.Entry<GameProfile, List<GraveComponent>> entry : this.graveBackups.entrySet()) {
            CompoundTag graveCompound = new CompoundTag();
            graveCompound.put("user", NbtUtils.writeGameProfile(new CompoundTag(), entry.getKey()));

            ListTag graveNbtList = new ListTag();
            for (GraveComponent graveComponent : entry.getValue()) {
                graveNbtList.add(graveComponent.toNbt());
            }

            graveCompound.put("graves", graveNbtList);
            graveNbt.add(graveCompound);
        }

        graveListNbt.putString("listMode", this.graveListMode.name());
        ListTag affectedPlayersNbt = new ListTag();
        for (GameProfile profile : this.affectedPlayers) {
            affectedPlayersNbt.add(NbtUtils.writeGameProfile(new CompoundTag(), profile));
        }
        graveListNbt.put("affectedPlayers", affectedPlayersNbt);

        nbt.put("respawns", respawnNbt);
        nbt.put("graves", graveNbt);
        nbt.put("whitelist", graveListNbt);
        return nbt;
    }

    public static DeathInfoManager fromNbt(CompoundTag nbt, MinecraftServer server) {
        INSTANCE.clear();

        ListTag respawnNbt = nbt.getList("respawns", Tag.TAG_COMPOUND);
        ListTag graveNbt = nbt.getList("graves", Tag.TAG_COMPOUND);
        for (Tag respawnElement : respawnNbt) {
            CompoundTag respawnCompound = (CompoundTag) respawnElement;
            GameProfile profile = NbtUtils.readGameProfile(respawnCompound.getCompound("user"));
            INSTANCE.addRespawnComponent(profile, RespawnComponent.fromNbt(respawnCompound.getCompound("component")));
        }
        for (Tag graveElement : graveNbt) {
            CompoundTag graveCompound = (CompoundTag) graveElement;
            GameProfile user = NbtUtils.readGameProfile(graveCompound.getCompound("user"));
            ListTag gravesList = graveCompound.getList("graves", Tag.TAG_COMPOUND);
            for (Tag grave : gravesList) {
                GraveComponent component = GraveComponent.fromNbt((CompoundTag) grave, server);
                INSTANCE.addBackup(user, component);

                ServerLevel world = component.getWorld();
                if (world != null && world.hasChunkAt(component.getPos())
                        && world.getBlockEntity(component.getPos()) instanceof GraveBlockEntity be
                        && be.getGraveId() != null
                        && be.getGraveId().equals(component.getGraveId())) {
                    be.setComponent(component);
                }
            }
        }

        CompoundTag graveListNbt = nbt.getCompound("whitelist");
        ListMode listMode = ListMode.valueOf(graveListNbt.getString("listMode"));
        INSTANCE.setGraveListMode(listMode);
        ListTag affectedPlayersNbt = graveListNbt.getList("affectedPlayers", Tag.TAG_LIST);
        for (Tag e : affectedPlayersNbt) {
            GameProfile profile = NbtUtils.readGameProfile((CompoundTag) e);
            INSTANCE.addToList(profile);
        }

        return INSTANCE;
    }
}
