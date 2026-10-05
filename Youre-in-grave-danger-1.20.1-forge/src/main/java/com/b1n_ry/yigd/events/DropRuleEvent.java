package com.b1n_ry.yigd.events;

import com.b1n_ry.yigd.data.DeathContext;
import com.b1n_ry.yigd.util.DropRule;
import com.b1n_ry.yigd.util.GraveOverrideAreas;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;
import org.jetbrains.annotations.Nullable;

@Cancelable
public class DropRuleEvent extends Event {
    private final ItemStack stack;
    private final int slot;
    @Nullable
    private final DeathContext deathContext;
    private final boolean modify;
    private DropRule dropRule = GraveOverrideAreas.INSTANCE.defaultDropRule;

    public DropRuleEvent(ItemStack stack, int slot, @Nullable DeathContext deathContext, boolean modify) {
        this.stack = stack;
        this.slot = slot;
        this.deathContext = deathContext;
        this.modify = modify;
    }

    public ItemStack getStack() {
        return this.stack;
    }

    public int getSlot() {
        return this.slot;
    }

    @Nullable
    public DeathContext getDeathContext() {
        return this.deathContext;
    }

    public boolean isModify() {
        return this.modify;
    }

    public DropRule getDropRule() {
        return this.dropRule;
    }

    public void setDropRule(DropRule dropRule) {
        this.dropRule = dropRule;
    }
}
