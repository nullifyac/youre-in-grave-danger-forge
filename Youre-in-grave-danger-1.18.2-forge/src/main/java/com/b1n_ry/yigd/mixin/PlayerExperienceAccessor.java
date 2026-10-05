package com.b1n_ry.yigd.mixin;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Player.class)
public interface PlayerExperienceAccessor {
    @Invoker("getExperienceReward")
    int yigd$getExperienceReward(Player player);
}
