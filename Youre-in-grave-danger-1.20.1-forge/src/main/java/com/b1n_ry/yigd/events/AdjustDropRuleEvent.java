package com.b1n_ry.yigd.events;

import com.b1n_ry.yigd.components.InventoryComponent;
import com.b1n_ry.yigd.data.DeathContext;
import net.minecraftforge.eventbus.api.Event;
import org.jetbrains.annotations.Nullable;

public class AdjustDropRuleEvent extends Event {
    private final InventoryComponent inventoryComponent;
    @Nullable
    private final DeathContext deathContext;

    public AdjustDropRuleEvent(InventoryComponent inventoryComponent, @Nullable DeathContext deathContext) {
        this.inventoryComponent = inventoryComponent;
        this.deathContext = deathContext;
    }

    public InventoryComponent getInventoryComponent() {
        return this.inventoryComponent;
    }

    @Nullable
    public DeathContext getDeathContext() {
        return this.deathContext;
    }
}
