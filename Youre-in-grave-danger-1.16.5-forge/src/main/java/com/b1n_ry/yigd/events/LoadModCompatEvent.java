package com.b1n_ry.yigd.events;

import com.b1n_ry.yigd.compat.InvModCompat;
import net.minecraftforge.eventbus.api.Event;

import java.util.List;

public class LoadModCompatEvent extends Event {
    private final List<InvModCompat<?>> compatMods;

    public LoadModCompatEvent(List<InvModCompat<?>> compatMods) {
        this.compatMods = compatMods;
    }

    public void addModCompat(InvModCompat<?> invModCompat) {
        this.compatMods.add(invModCompat);
    }
}
