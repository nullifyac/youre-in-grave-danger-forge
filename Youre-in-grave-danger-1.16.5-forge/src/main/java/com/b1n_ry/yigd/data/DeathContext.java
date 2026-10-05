package com.b1n_ry.yigd.data;

import net.minecraft.util.DamageSource;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.util.math.vector.Vector3d;
import javax.annotation.Nonnull;

public class DeathContext {
    private final ServerPlayerEntity player;
    private final ServerWorld world;
    private final Vector3d deathPos;
    private final DamageSource deathSource;

    public DeathContext(ServerPlayerEntity player, @Nonnull ServerWorld world, Vector3d deathPos, DamageSource deathSource) {
        this.player = player;
        this.world = world;
        this.deathPos = deathPos;
        this.deathSource = deathSource;
    }

    public ServerPlayerEntity player() {
        return this.player;
    }

    public @Nonnull ServerWorld world() {
        return this.world;
    }

    public Vector3d deathPos() {
        return this.deathPos;
    }

    public DamageSource deathSource() {
        return this.deathSource;
    }
}
