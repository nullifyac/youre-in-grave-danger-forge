package com.b1n_ry.yigd.events;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;

@Cancelable
public class DropItemEvent extends Event {
    private final ItemStack stack;
    private final double x;
    private final double y;
    private final double z;
    private final ServerLevel level;
    private boolean shouldDrop = true;

    public DropItemEvent(ItemStack stack, double x, double y, double z, ServerLevel level) {
        this.stack = stack;
        this.x = x;
        this.y = y;
        this.z = z;
        this.level = level;
    }

    public ItemStack getStack() {
        return this.stack;
    }

    public double getX() {
        return this.x;
    }

    public double getY() {
        return this.y;
    }

    public double getZ() {
        return this.z;
    }

    public ServerLevel getLevel() {
        return this.level;
    }

    public boolean shouldDrop() {
        return this.shouldDrop;
    }

    public void setShouldDrop(boolean shouldDrop) {
        this.shouldDrop = shouldDrop;
    }
}
