package com.b1n_ry.yigd.data;

import com.b1n_ry.yigd.block.entity.GraveBlockEntity;
import com.b1n_ry.yigd.components.GraveComponent;
import com.b1n_ry.yigd.components.RespawnComponent;
import com.b1n_ry.yigd.config.ExtraFeaturesConfig;
import com.b1n_ry.yigd.config.YigdConfig;
import com.b1n_ry.yigd.util.GraveCompassHelper;
import com.mojang.authlib.GameProfile;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.nbt.INBT;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.util.ActionResultType;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

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
public class DeathInfoManager extends WorldSavedData {
    public static final String DATA_NAME = "yigd_data";
    public static DeathInfoManager INSTANCE = new DeathInfoManager();

    @Nullable
    private final MinecraftServer server;
    private final Map<GameProfile, RespawnComponent> respawnEffects = new HashMap<>();
    private final Map<GameProfile, List<GraveComponent>> graveBackups = new HashMap<>();
    private final Map<UUID, GraveComponent> graveMap = new HashMap<>();

    private ListMode graveListMode = ListMode.BLACKLIST;
    private final Set<GameProfile> affectedPlayers = new HashSet<>();

    public DeathInfoManager() {
        super(DATA_NAME);
        this.server = null;
        INSTANCE = this;
    }

    public DeathInfoManager(MinecraftServer server) {
        super(DATA_NAME);
        this.server = server;
        INSTANCE = this;
    }

    public void clear() {
        this.respawnEffects.clear();
        this.graveBackups.clear();
        this.graveMap.clear();
        this.affectedPlayers.clear();
    }

    public Set<GameProfile> getAffectedPlayers() {
        return this.affectedPlayers;
    }

    public ActionResultType delete(UUID graveId) {
        GraveComponent component = this.graveMap.get(graveId);
        if (component == null) return ActionResultType.FAIL;

        GameProfile profile = component.getOwner();
        this.graveMap.remove(graveId);

        if (!this.graveBackups.containsKey(profile)) return ActionResultType.PASS;
        this.graveBackups.get(profile).remove(component);

        if (component.getStatus() != GraveStatus.UNCLAIMED) return ActionResultType.SUCCESS;

        return component.removeGraveBlock() ? ActionResultType.SUCCESS : ActionResultType.PASS;
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
            // Removing the block changes UNCLAIMED to DESTROYED, so decide before deletion.
            boolean dropContents = toBeRemoved.getStatus() == GraveStatus.UNCLAIMED
                    && config.graveConfig.dropFromOldestWhenDeleted && toBeRemoved.getWorld() != null;
            this.delete(toBeRemoved.getGraveId());
            if (dropContents) {
                toBeRemoved.dropAllGraveItems();
            }
        }

        if (config.extraFeatures.graveCompass.pointToClosest != ExtraFeaturesConfig.GraveCompassConfig.CompassGraveTarget.DISABLED
                && component.getStatus() == GraveStatus.UNCLAIMED) {
            GraveCompassHelper.addGravePosition(component.getWorldResourceKey(), component.getPos(), profile.getId());
        }
    }

    public @Nonnull List<GraveComponent> getBackupData(GameProfile profile) {
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
    public CompoundNBT save(CompoundNBT nbt) {
        ListNBT respawnNbt = new ListNBT();
        ListNBT graveNbt = new ListNBT();
        CompoundNBT graveListNbt = new CompoundNBT();

        for (Map.Entry<GameProfile, RespawnComponent> entry : this.respawnEffects.entrySet()) {
            CompoundNBT respawnCompound = new CompoundNBT();
            respawnCompound.put("user", NBTUtil.writeGameProfile(new CompoundNBT(), entry.getKey()));
            respawnCompound.put("component", entry.getValue().toNbt());
            respawnNbt.add(respawnCompound);
        }

        for (Map.Entry<GameProfile, List<GraveComponent>> entry : this.graveBackups.entrySet()) {
            CompoundNBT graveCompound = new CompoundNBT();
            graveCompound.put("user", NBTUtil.writeGameProfile(new CompoundNBT(), entry.getKey()));

            ListNBT graveNbtList = new ListNBT();
            for (GraveComponent graveComponent : entry.getValue()) {
                graveNbtList.add(graveComponent.toNbt());
            }

            graveCompound.put("graves", graveNbtList);
            graveNbt.add(graveCompound);
        }

        graveListNbt.putString("listMode", this.graveListMode.name());
        ListNBT affectedPlayersNbt = new ListNBT();
        for (GameProfile profile : this.affectedPlayers) {
            affectedPlayersNbt.add(NBTUtil.writeGameProfile(new CompoundNBT(), profile));
        }
        graveListNbt.put("affectedPlayers", affectedPlayersNbt);

        nbt.put("respawns", respawnNbt);
        nbt.put("graves", graveNbt);
        nbt.put("whitelist", graveListNbt);
        return nbt;
    }

    @Override
    public void load(CompoundNBT nbt) {
        this.clear();

        ListNBT respawnNbt = nbt.getList("respawns", Constants.NBT.TAG_COMPOUND);
        ListNBT graveNbt = nbt.getList("graves", Constants.NBT.TAG_COMPOUND);
        for (INBT respawnElement : respawnNbt) {
            CompoundNBT respawnCompound = (CompoundNBT) respawnElement;
            GameProfile profile = NBTUtil.readGameProfile(respawnCompound.getCompound("user"));
            this.addRespawnComponent(profile, RespawnComponent.fromNbt(respawnCompound.getCompound("component")));
        }
        for (INBT graveElement : graveNbt) {
            CompoundNBT graveCompound = (CompoundNBT) graveElement;
            GameProfile user = NBTUtil.readGameProfile(graveCompound.getCompound("user"));
            ListNBT gravesList = graveCompound.getList("graves", Constants.NBT.TAG_COMPOUND);
            for (INBT grave : gravesList) {
                GraveComponent component = GraveComponent.fromNbt((CompoundNBT) grave, this.server);
                this.addBackup(user, component);

                ServerWorld world = component.getWorld();
                if (world != null && world.hasChunkAt(component.getPos())
                        && world.getBlockEntity(component.getPos()) instanceof GraveBlockEntity) {
                    GraveBlockEntity be = (GraveBlockEntity) world.getBlockEntity(component.getPos());
                    if (be.getGraveId() != null
                            && be.getGraveId().equals(component.getGraveId())) {
                        be.setComponent(component);
                    }
                }
            }
        }

        CompoundNBT graveListNbt = nbt.getCompound("whitelist");
        if (graveListNbt.contains("listMode")) {
            ListMode listMode = ListMode.valueOf(graveListNbt.getString("listMode"));
            this.setGraveListMode(listMode);
        }
        ListNBT affectedPlayersNbt = graveListNbt.getList("affectedPlayers", Constants.NBT.TAG_COMPOUND);
        for (INBT e : affectedPlayersNbt) {
            GameProfile profile = NBTUtil.readGameProfile((CompoundNBT) e);
            this.addToList(profile);
        }
    }
}
