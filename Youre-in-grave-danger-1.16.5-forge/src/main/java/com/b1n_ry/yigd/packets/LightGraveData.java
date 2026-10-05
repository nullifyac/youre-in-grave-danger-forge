package com.b1n_ry.yigd.packets;

import com.b1n_ry.yigd.data.GraveStatus;
import com.b1n_ry.yigd.data.TranslatableDeathMessage;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import java.util.UUID;

/**
 * This class is used to carry a representation from an overhaul of what's in a grave, not a detailed description
 * @param itemCount Amount of items in total on the player
 * @param pos {@link BlockPos} where the player died
 * @param xpPoints How much XP the player had
 * @param registryKey {@link RegistryKey<World>} of the world player died in
 * @param deathMessage {@link TranslatableDeathMessage} of the player's death message
 * @param id The grave {@link UUID}
 * @param status The availability status of the grave
 */
public class LightGraveData {
    private final int itemCount;
    private final BlockPos pos;
    private final int xpPoints;
    private final RegistryKey<World> registryKey;
    private final TranslatableDeathMessage deathMessage;
    private final UUID id;
    private final GraveStatus status;

    public LightGraveData(int itemCount, BlockPos pos, int xpPoints, RegistryKey<World> registryKey,
                          TranslatableDeathMessage deathMessage, UUID id, GraveStatus status) {
        this.itemCount = itemCount;
        this.pos = pos;
        this.xpPoints = xpPoints;
        this.registryKey = registryKey;
        this.deathMessage = deathMessage;
        this.id = id;
        this.status = status;
    }

    public int itemCount() {
        return this.itemCount;
    }

    public BlockPos pos() {
        return this.pos;
    }

    public int xpPoints() {
        return this.xpPoints;
    }

    public RegistryKey<World> registryKey() {
        return this.registryKey;
    }

    public TranslatableDeathMessage deathMessage() {
        return this.deathMessage;
    }

    public UUID id() {
        return this.id;
    }

    public GraveStatus status() {
        return this.status;
    }

    public static LightGraveData fromNbt(CompoundNBT nbt) {
        int itemCount = nbt.getInt("itemCount");
        BlockPos pos = NBTUtil.readBlockPos(nbt.getCompound("pos"));
        int xpPoints = nbt.getInt("xpPoints");
        RegistryKey<World> registryKey = getResourceKeyFromNbt(nbt.getCompound("worldKey"));
        TranslatableDeathMessage deathMessage = TranslatableDeathMessage.fromNbt(nbt.getCompound("deathMessage"));
        UUID id = nbt.getUUID("id");
        GraveStatus status = GraveStatus.valueOf(nbt.getString("status"));

        return new LightGraveData(itemCount, pos, xpPoints, registryKey, deathMessage, id, status);
    }

    public CompoundNBT toNbt() {
        CompoundNBT nbt = new CompoundNBT();
        nbt.putInt("itemCount", this.itemCount);
        nbt.put("pos", NBTUtil.writeBlockPos(this.pos));
        nbt.putInt("xpPoints", this.xpPoints);
        nbt.put("worldKey", this.getWorldResourceKeyNbt(this.registryKey));
        nbt.put("deathMessage", this.deathMessage.toNbt());
        nbt.putUUID("id", this.id);
        nbt.putString("status", this.status.toString());

        return nbt;
    }

    private CompoundNBT getWorldResourceKeyNbt(RegistryKey<?> key) {
        CompoundNBT nbt = new CompoundNBT();
        nbt.putString("registry", key.getRegistryName().toString());
        nbt.putString("value", key.location().toString());

        return nbt;
    }

    private static RegistryKey<World> getResourceKeyFromNbt(CompoundNBT nbt) {
        String registry = nbt.getString("registry");
        String value = nbt.getString("value");

        RegistryKey<Registry<World>> r = RegistryKey.createRegistryKey(new ResourceLocation(registry));
        return RegistryKey.create(r, new ResourceLocation(value));
    }
}
