package com.b1n_ry.yigd.compat;

import com.b1n_ry.yigd.compat.misc_compat_mods.TwilightCompat;
import com.b1n_ry.yigd.config.CompatConfig;
import com.b1n_ry.yigd.config.YigdConfig;
import com.b1n_ry.yigd.events.LoadModCompatEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.common.MinecraftForge;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.entity.player.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;

public interface InvModCompat<T> {
    List<InvModCompat<?>> invCompatMods = new ArrayList<>();
    static void reloadModCompat() {
        invCompatMods.clear();
        ModList modList = ModList.get();
        CompatConfig compatConfig = YigdConfig.getConfig().compatConfig;

        boolean curiosLoaded = compatConfig.enableCuriosCompat && modList.isLoaded("curios");
        boolean numismaticLoaded = compatConfig.enableNumismaticOverhaulCompat && modList.isLoaded("numismatic-overhaul");

        if (curiosLoaded)
            invCompatMods.add(new CuriosCompat());
        if (numismaticLoaded)
            invCompatMods.add(new NumismaticOverhaulCompat());
        if (modList.isLoaded("twilightforest"))
            TwilightCompat.init();

        MinecraftForge.EVENT_BUS.post(new LoadModCompatEvent(invCompatMods));
    }

    String getModName();
    void clear(ServerPlayerEntity player);
    CompatComponent<T> load(CompoundNBT nbt);

    CompatComponent<T> getNewComponent(ServerPlayerEntity player);
}
