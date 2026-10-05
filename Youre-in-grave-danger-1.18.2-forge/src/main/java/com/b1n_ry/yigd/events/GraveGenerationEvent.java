package com.b1n_ry.yigd.events;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.eventbus.api.Event;

public class GraveGenerationEvent extends Event {
    private final ServerLevel level;
    private final BlockPos pos;
    private final int nthTry;
    private boolean canGenerate = true;

    public GraveGenerationEvent(ServerLevel level, BlockPos pos, int nthTry) {
        this.level = level;
        this.pos = pos;
        this.nthTry = nthTry;
    }

    public ServerLevel getLevel() {
        return this.level;
    }

    public BlockPos getPos() {
        return this.pos;
    }

    public int getNthTry() {
        return this.nthTry;
    }

    public boolean canGenerate() {
        return this.canGenerate;
    }

    public void setCanGenerate(boolean canGenerate) {
        this.canGenerate = canGenerate;
    }
}
