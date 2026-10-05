package com.b1n_ry.yigd.events;

import com.b1n_ry.yigd.components.GraveComponent;
import net.minecraft.block.BlockState;
import net.minecraftforge.eventbus.api.Event;

public class AllowBlockUnderGraveGenerationEvent extends Event {
    private final GraveComponent grave;
    private final BlockState blockUnder;
    private boolean allowPlacement = true;

    public AllowBlockUnderGraveGenerationEvent(GraveComponent grave, BlockState blockUnder) {
        this.grave = grave;
        this.blockUnder = blockUnder;
    }

    public GraveComponent getGrave() {
        return this.grave;
    }

    public BlockState getBlockUnder() {
        return this.blockUnder;
    }

    public boolean isPlacementAllowed() {
        return this.allowPlacement;
    }

    public void setAllowPlacement(boolean allowPlacement) {
        this.allowPlacement = allowPlacement;
    }
}
