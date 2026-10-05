package com.b1n_ry.yigd.events;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

public class BeforeSoulboundEvent extends Event {
    private final ServerPlayer oldPlayer;
    private final ServerPlayer newPlayer;

    public BeforeSoulboundEvent(ServerPlayer oldPlayer, ServerPlayer newPlayer) {
        this.oldPlayer = oldPlayer;
        this.newPlayer = newPlayer;
    }

    public ServerPlayer getOldPlayer() {
        return this.oldPlayer;
    }

    public ServerPlayer getNewPlayer() {
        return this.newPlayer;
    }
}
