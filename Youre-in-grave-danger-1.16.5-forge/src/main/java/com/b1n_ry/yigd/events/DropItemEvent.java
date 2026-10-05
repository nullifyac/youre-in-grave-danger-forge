package com.b1n_ry.yigd.events;

import net.minecraft.world.server.ServerWorld;
import net.minecraft.item.ItemStack;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;

@Cancelable
public class DropItemEvent extends Event {
    private final ItemStack stack;
    private final double x;
    private final double y;
    private final double z;
    private final ServerWorld level;
    private boolean shouldDrop = true;

    public DropItemEvent(ItemStack stack, double x, double y, double z, ServerWorld level) {
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

    public ServerWorld getLevel() {
        return this.level;
    }

    public boolean shouldDrop() {
        return this.shouldDrop;
    }

    public void setShouldDrop(boolean shouldDrop) {
        this.shouldDrop = shouldDrop;
    }
}
