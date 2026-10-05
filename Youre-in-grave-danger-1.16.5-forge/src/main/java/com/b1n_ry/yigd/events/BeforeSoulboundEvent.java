package com.b1n_ry.yigd.events;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.eventbus.api.Event;

public class BeforeSoulboundEvent extends Event {
    private final ServerPlayerEntity oldPlayer;
    private final ServerPlayerEntity newPlayer;

    public BeforeSoulboundEvent(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer) {
        this.oldPlayer = oldPlayer;
        this.newPlayer = newPlayer;
    }

    public ServerPlayerEntity getOldPlayer() {
        return this.oldPlayer;
    }

    public ServerPlayerEntity getNewPlayer() {
        return this.newPlayer;
    }
}
