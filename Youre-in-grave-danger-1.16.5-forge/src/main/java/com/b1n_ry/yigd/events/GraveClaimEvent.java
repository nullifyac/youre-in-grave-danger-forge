package com.b1n_ry.yigd.events;

import com.b1n_ry.yigd.components.GraveComponent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;

@Cancelable
public class GraveClaimEvent extends Event {
    private final ServerPlayerEntity player;
    private final ServerWorld level;
    private final BlockPos pos;
    private final GraveComponent grave;
    private final ItemStack tool;
    private boolean canClaim = false;

    public GraveClaimEvent(ServerPlayerEntity player, ServerWorld level, BlockPos pos, GraveComponent grave, ItemStack tool) {
        this.player = player;
        this.level = level;
        this.pos = pos;
        this.grave = grave;
        this.tool = tool;
    }

    public ServerPlayerEntity getPlayer() {
        return this.player;
    }

    public ServerWorld getLevel() {
        return this.level;
    }

    public BlockPos getPos() {
        return this.pos;
    }

    public GraveComponent getGrave() {
        return this.grave;
    }

    public ItemStack getTool() {
        return this.tool;
    }

    public boolean allowClaim() {
        return this.canClaim;
    }

    public void setCanClaim(boolean canClaim) {
        this.canClaim = canClaim;
    }
}
