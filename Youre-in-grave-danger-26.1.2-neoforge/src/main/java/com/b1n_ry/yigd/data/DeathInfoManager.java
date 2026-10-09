package com.b1n_ry.yigd.data;

import com.b1n_ry.yigd.block.entity.GraveBlockEntity;
import com.b1n_ry.yigd.components.GraveComponent;
import com.b1n_ry.yigd.components.RespawnComponent;
import com.b1n_ry.yigd.config.ExtraFeaturesConfig;
import com.b1n_ry.yigd.config.YigdConfig;
import com.b1n_ry.yigd.util.GraveCompassHelper;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Class that will keep track of all backed up data (graves)
 * Will also keep track of a white/blacklist that can allow/disallow certain people from generating graves
 */
public class DeathInfoManager extends SavedData {
    public static DeathInfoManager INSTANCE = new DeathInfoManager();

    private final Map<GameProfile, RespawnComponent> respawnEffects = new HashMap<>();
    private final Map<GameProfile, List<GraveComponent>> graveBackups = new HashMap<>();
    private final Map<UUID, GraveComponent> graveMap = new HashMap<>();
    private final Map<UUID, GameProfile> playerIdentities = new HashMap<>();

    private ListMode graveListMode = ListMode.BLACKLIST;
    private final Set<GameProfile> affectedPlayers = new HashSet<>();

    public void clear() {
        this.respawnEffects.clear();
        this.graveBackups.clear();
        this.graveMap.clear();
        this.playerIdentities.clear();

        this.affectedPlayers.clear();
    }

    public Set<GameProfile> getAffectedPlayers() {
        return affectedPlayers;
    }

    /** Authlib profiles include names and skin properties in equality; player identity is their UUID. */
    private GameProfile profileKey(ResolvableProfile profile) {
        GameProfile supplied = profile.partialProfile();
        return this.playerIdentities.computeIfAbsent(supplied.id(), ignored -> supplied);
    }

    public static SavedDataType<DeathInfoManager> getPersistentStateType(MinecraftServer server) {
        return new SavedDataType<>(Identifier.fromNamespaceAndPath("yigd", "yigd_data"), DeathInfoManager::new,
                CompoundTag.CODEC.xmap(nbt -> load(nbt, server.registryAccess(), server),
                        manager -> manager.save(new CompoundTag(), server.registryAccess())));
    }

    /**
     * Tries to delete a grave based on its grave ID
     * @param graveId the ID of the grave
     * @return FAIL if nothing were deleted. PASS if it wasn't completely deleted. SUCCESS if it was 100% deleted
     */
    public InteractionResult delete(UUID graveId) {
        GraveComponent component = this.graveMap.get(graveId);
        if (component == null) return InteractionResult.FAIL;

        GameProfile profile = this.profileKey(component.getOwner());

        this.graveMap.remove(graveId);
        this.setDirty();

        // Probably unnecessary, but if it would turn out it's required, people won't crash now
        if (!this.graveBackups.containsKey(profile)) return InteractionResult.PASS;  // No more of the grave was found
        this.graveBackups.get(profile).remove(component);

        if (component.getStatus() != GraveStatus.UNCLAIMED) return InteractionResult.SUCCESS;

        return component.removeGraveBlock() ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    public void addRespawnComponent(ResolvableProfile profile, RespawnComponent component) {
        // If respawn component already exists for this profile, we assume this one was added because the death
        // event fired more than once for the same death (should not happen unless other mods are involved), and
        // only the first respawn component should have anything stored, meaning that is the one we use.
        GameProfile key = this.profileKey(profile);
        if (!this.respawnEffects.containsKey(key)) {
            this.respawnEffects.put(key, component);
            this.setDirty();
        }
    }
    public Optional<RespawnComponent> getRespawnComponent(ResolvableProfile profile) {
        return Optional.ofNullable(this.respawnEffects.get(this.profileKey(profile)));
    }
    public Map<GameProfile, List<GraveComponent>> getPlayerGraves() {
        return this.graveBackups;
    }

    public void removeRespawnComponent(ResolvableProfile profile) {
        if (this.respawnEffects.remove(this.profileKey(profile)) != null) this.setDirty();
    }

    public void addBackup(ResolvableProfile p, GraveComponent component) {
        YigdConfig config = YigdConfig.getConfig();

        GameProfile profile = this.profileKey(p);
        if (!this.graveBackups.containsKey(profile)) {
            this.graveBackups.put(profile, new ArrayList<>());
        }
        List<GraveComponent> playerGraves = this.graveBackups.get(profile);
        playerGraves.add(component);
        this.graveMap.put(component.getGraveId(), component);
        this.setDirty();

        // If player have too many backed up graves
        if (playerGraves.size() > Math.max(1, config.graveConfig.maxBackupsPerPerson)) {
            GraveComponent toBeRemoved = playerGraves.getFirst();
            // Removing the block changes UNCLAIMED to DESTROYED. Capture the policy first.
            boolean dropContents = toBeRemoved.getStatus() == GraveStatus.UNCLAIMED
                    && config.graveConfig.dropFromOldestWhenDeleted && toBeRemoved.getWorld() != null;
            this.delete(toBeRemoved.getGraveId());
            if (dropContents) toBeRemoved.dropAllGraveItems();
        }

        if (config.extraFeatures.graveCompass.pointToClosest != ExtraFeaturesConfig.GraveCompassConfig.CompassGraveTarget.DISABLED
                && component.getStatus() == GraveStatus.UNCLAIMED) {
            GraveCompassHelper.addGravePosition(component.getWorldRegistryKey(), component.getPos(), profile.id());
        }
    }
    public @NotNull List<GraveComponent> getBackupData(ResolvableProfile profile) {
        return this.graveBackups.computeIfAbsent(this.profileKey(profile), k -> new ArrayList<>());
    }
    public Optional<GraveComponent> getGrave(UUID graveId) {
        return Optional.ofNullable(this.graveMap.get(graveId));
    }

    public ListMode getGraveListMode() {
        return this.graveListMode;
    }
    public void setGraveListMode(ListMode listMode) {
        this.graveListMode = listMode;
        this.setDirty();
    }
    public void addToList(ResolvableProfile profile) {
        if (this.affectedPlayers.add(this.profileKey(profile))) this.setDirty();
    }
    public boolean removeFromList(ResolvableProfile profile) {
        boolean removed = this.affectedPlayers.remove(this.profileKey(profile));
        if (removed) this.setDirty();
        return removed;
    }
    public boolean isInList(ResolvableProfile profile) {
        return this.affectedPlayers.contains(this.profileKey(profile));
    }

    public @NotNull CompoundTag save(@NotNull CompoundTag nbt, HolderLookup.@NotNull Provider registryLookup) {
        ListTag respawnNbt = new ListTag();
        ListTag graveNbt = new ListTag();
        CompoundTag graveListNbt = new CompoundTag();
        for (Map.Entry<GameProfile, RespawnComponent> entry : this.respawnEffects.entrySet()) {
            CompoundTag respawnCompound = new CompoundTag();

            ResolvableProfile.CODEC.encodeStart(NbtOps.INSTANCE, ResolvableProfile.createResolved(entry.getKey())).result()
                    .ifPresent(nbtElement -> respawnCompound.put("user", nbtElement));
            respawnCompound.put("component", entry.getValue().toNbt(registryLookup));

            respawnNbt.add(respawnCompound);
        }

        for (Map.Entry<GameProfile, List<GraveComponent>> entry : this.graveBackups.entrySet()) {
            CompoundTag graveCompound = new CompoundTag();
            ResolvableProfile.CODEC.encodeStart(NbtOps.INSTANCE, ResolvableProfile.createResolved(entry.getKey())).result()
                    .ifPresent(nbtElement -> graveCompound.put("user", nbtElement));

            ListTag graveNbtList = new ListTag();
            for (GraveComponent graveComponent : entry.getValue()) {
                graveNbtList.add(graveComponent.toNbt(registryLookup));
            }

            graveCompound.put("graves", graveNbtList);

            graveNbt.add(graveCompound);
        }

        graveListNbt.putString("listMode", this.graveListMode.name());
        ListTag affectedPlayersNbt = new ListTag();
        for (GameProfile profile : this.affectedPlayers) {
            Tag profileNbt = ResolvableProfile.CODEC.encodeStart(NbtOps.INSTANCE, ResolvableProfile.createResolved(profile)).result().orElseThrow();
            affectedPlayersNbt.add(profileNbt);
        }
        graveListNbt.put("affectedPlayers", affectedPlayersNbt);

        nbt.put("respawns", respawnNbt);
        nbt.put("graves", graveNbt);
        nbt.put("whitelist", graveListNbt);
        return nbt;
    }

    public static DeathInfoManager load(CompoundTag nbt, HolderLookup.Provider lookupRegistry, MinecraftServer server) {
        // Parse everything before committing or applying world side effects. A malformed later record
        // must not clear live recovery data, remove an earlier grave, or redirect its block entity.
        DeathInfoManager loaded = new DeathInfoManager();

        ListTag respawnNbt = nbt.getListOrEmpty("respawns");
        ListTag graveNbt = nbt.getListOrEmpty("graves");
        for (Tag respawnElement : respawnNbt) {
            CompoundTag respawnCompound = (CompoundTag) respawnElement;
            loaded.addRespawnComponent(
                    GraveNbt.readProfile(respawnCompound.get("user")),
                    RespawnComponent.fromNbt(respawnCompound.getCompoundOrEmpty("component"), lookupRegistry));
        }
        for (Tag graveElement : graveNbt) {
            CompoundTag graveCompound = (CompoundTag) graveElement;
            ResolvableProfile user = GraveNbt.readProfile(graveCompound.get("user"));
            ListTag gravesList = graveCompound.getListOrEmpty("graves");
            for (Tag grave : gravesList) {
                GraveComponent component = GraveComponent.fromNbt((CompoundTag) grave, lookupRegistry, server);
                if (loaded.graveMap.putIfAbsent(component.getGraveId(), component) != null) {
                    throw new IllegalArgumentException("Duplicate grave UUID in recovery data: " + component.getGraveId());
                }
                loaded.graveBackups.computeIfAbsent(loaded.profileKey(user), ignored -> new ArrayList<>()).add(component);
            }
        }

        CompoundTag graveListNbt = nbt.getCompoundOrEmpty("whitelist");
        ListMode listMode = ListMode.valueOf(graveListNbt.getStringOr("listMode", ListMode.BLACKLIST.name()));
        loaded.setGraveListMode(listMode);
        ListTag affectedPlayersNbt = graveListNbt.getListOrEmpty("affectedPlayers");
        for (Tag e : affectedPlayersNbt) {
            ResolvableProfile profile = GraveNbt.readProfile(e);
            loaded.addToList(profile);
        }

        INSTANCE = loaded;
        YigdConfig config = YigdConfig.getConfig();
        for (List<GraveComponent> playerGraves : loaded.graveBackups.values()) {
            while (playerGraves.size() > Math.max(1, config.graveConfig.maxBackupsPerPerson)) {
                GraveComponent oldest = playerGraves.getFirst();
                boolean dropContents = oldest.getStatus() == GraveStatus.UNCLAIMED
                        && config.graveConfig.dropFromOldestWhenDeleted && oldest.getWorld() != null;
                loaded.delete(oldest.getGraveId());
                if (dropContents) oldest.dropAllGraveItems();
            }
        }
        for (GraveComponent component : loaded.graveMap.values()) {
            if (config.extraFeatures.graveCompass.pointToClosest != ExtraFeaturesConfig.GraveCompassConfig.CompassGraveTarget.DISABLED
                    && component.getStatus() == GraveStatus.UNCLAIMED) {
                GraveCompassHelper.addGravePosition(component.getWorldRegistryKey(), component.getPos(), component.getOwner().partialProfile().id());
            }
            ServerLevel world = component.getWorld();
            if (world != null && world.areEntitiesLoaded(ChunkPos.containing(component.getPos()).pack())
                    && world.getBlockEntity(component.getPos()) instanceof GraveBlockEntity be
                    && component.getGraveId().equals(be.getGraveId())) {
                be.setComponent(component);
            }
        }
        return loaded;
    }
}
