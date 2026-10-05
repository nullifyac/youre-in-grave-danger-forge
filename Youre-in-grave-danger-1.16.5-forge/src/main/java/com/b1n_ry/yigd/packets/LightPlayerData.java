package com.b1n_ry.yigd.packets;

import com.mojang.authlib.GameProfile;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.NBTUtil;

public class LightPlayerData {
    private final int graveCount;
    private final int unclaimedCount;
    private final int destroyedCount;
    private final GameProfile playerProfile;

    public LightPlayerData(int graveCount, int unclaimedCount, int destroyedCount, GameProfile playerProfile) {
        this.graveCount = graveCount;
        this.unclaimedCount = unclaimedCount;
        this.destroyedCount = destroyedCount;
        this.playerProfile = playerProfile;
    }

    public int graveCount() {
        return this.graveCount;
    }

    public int unclaimedCount() {
        return this.unclaimedCount;
    }

    public int destroyedCount() {
        return this.destroyedCount;
    }

    public GameProfile playerProfile() {
        return this.playerProfile;
    }

    public static LightPlayerData fromNbt(CompoundNBT nbt) {
        int graveCount = nbt.getInt("graveCount");
        int unclaimedCount = nbt.getInt("unclaimedCount");
        int destroyedCount = nbt.getInt("destroyedCount");
        GameProfile profile = NBTUtil.readGameProfile(nbt.getCompound("playerProfile"));

        return new LightPlayerData(graveCount, unclaimedCount, destroyedCount, profile);
    }

    public CompoundNBT toNbt() {
        CompoundNBT nbt = new CompoundNBT();
        nbt.putInt("graveCount", this.graveCount);
        nbt.putInt("unclaimedCount", this.unclaimedCount);
        nbt.putInt("destroyedCount", this.destroyedCount);
        nbt.put("playerProfile", NBTUtil.writeGameProfile(new CompoundNBT(), this.playerProfile));

        return nbt;
    }
}
