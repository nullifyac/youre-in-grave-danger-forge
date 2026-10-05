package com.b1n_ry.yigd.packets;

import com.mojang.authlib.GameProfile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;

public record LightPlayerData(int graveCount, int unclaimedCount, int destroyedCount, GameProfile playerProfile) {
    public static LightPlayerData fromNbt(CompoundTag nbt) {
        int graveCount = nbt.getInt("graveCount");
        int unclaimedCount = nbt.getInt("unclaimedCount");
        int destroyedCount = nbt.getInt("destroyedCount");
        GameProfile profile = NbtUtils.readGameProfile(nbt.getCompound("playerProfile"));

        return new LightPlayerData(graveCount, unclaimedCount, destroyedCount, profile);
    }

    public CompoundTag toNbt() {
        CompoundTag nbt = new CompoundTag();
        nbt.putInt("graveCount", this.graveCount);
        nbt.putInt("unclaimedCount", this.unclaimedCount);
        nbt.putInt("destroyedCount", this.destroyedCount);
        nbt.put("playerProfile", NbtUtils.writeGameProfile(new CompoundTag(), this.playerProfile));

        return nbt;
    }
}
