package com.b1n_ry.yigd.util;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.entity.player.Player;

/** Converts the existing numeric permission settings to Minecraft's permission sets. */
public final class YigdPermissions {
    private YigdPermissions() {}

    public static boolean has(Player player, int level) {
        return level <= 0 || player instanceof ServerPlayer serverPlayer && has(serverPlayer.permissions(), level);
    }

    public static boolean has(CommandSourceStack source, int level) {
        return has(source.permissions(), level);
    }

    private static boolean has(PermissionSet permissions, int level) {
        return level <= 0 || level <= 4 && permissions.hasPermission(new Permission.HasCommandLevel(PermissionLevel.byId(level)));
    }
}
