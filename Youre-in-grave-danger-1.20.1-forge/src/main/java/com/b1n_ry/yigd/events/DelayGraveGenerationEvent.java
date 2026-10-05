package com.b1n_ry.yigd.events;

import com.b1n_ry.yigd.components.GraveComponent;
import com.b1n_ry.yigd.components.RespawnComponent;
import com.b1n_ry.yigd.data.DeathContext;
import net.minecraft.core.Direction;
import net.minecraftforge.eventbus.api.Event;

public class DelayGraveGenerationEvent extends Event {
    private final GraveComponent grave;
    private final Direction direction;
    private final DeathContext deathContext;
    private final RespawnComponent respawnComponent;
    private final String caller;
    private boolean delayGeneration = false;

    public DelayGraveGenerationEvent(GraveComponent grave, Direction direction, DeathContext deathContext,
                                     RespawnComponent respawnComponent, String caller) {
        this.grave = grave;
        this.direction = direction;
        this.deathContext = deathContext;
        this.respawnComponent = respawnComponent;
        this.caller = caller;
    }

    public GraveComponent getGrave() {
        return this.grave;
    }

    public Direction getDirection() {
        return this.direction;
    }

    public DeathContext getDeathContext() {
        return this.deathContext;
    }

    public RespawnComponent getRespawnComponent() {
        return this.respawnComponent;
    }

    public String getCaller() {
        return this.caller;
    }

    public boolean generationIsDelayed() {
        return this.delayGeneration;
    }

    public void setDelayGeneration(boolean delayGeneration) {
        this.delayGeneration = delayGeneration;
    }
}
