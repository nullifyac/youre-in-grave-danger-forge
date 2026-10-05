package com.b1n_ry.yigd.events;

import com.b1n_ry.yigd.components.GraveComponent;
import com.b1n_ry.yigd.data.DeathContext;
import net.minecraftforge.eventbus.api.Event;

public class AllowGraveGenerationEvent extends Event {
    private final DeathContext deathContext;
    private final GraveComponent grave;
    private boolean allowGeneration = true;

    public AllowGraveGenerationEvent(DeathContext deathContext, GraveComponent grave) {
        this.deathContext = deathContext;
        this.grave = grave;
    }

    public DeathContext getDeathContext() {
        return this.deathContext;
    }

    public GraveComponent getGrave() {
        return this.grave;
    }

    public boolean isGenerationAllowed() {
        return this.allowGeneration;
    }

    public void setAllowGeneration(boolean allowGeneration) {
        this.allowGeneration = allowGeneration;
    }
}
