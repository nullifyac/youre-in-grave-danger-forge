package com.b1n_ry.yigd.util;

import com.b1n_ry.yigd.components.GraveComponent;
import com.b1n_ry.yigd.config.CommandConfig;
import com.b1n_ry.yigd.config.YigdConfig;
import com.b1n_ry.yigd.data.DeathInfoManager;
import com.b1n_ry.yigd.data.GraveStatus;
import com.b1n_ry.yigd.data.ListMode;
import com.b1n_ry.yigd.packets.LightGraveData;
import com.b1n_ry.yigd.packets.LightPlayerData;
import com.b1n_ry.yigd.packets.ServerPacketHandler;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.util.*;

public final class YigdCommands {
    private YigdCommands() {}

    public static void register(RegisterCommandsEvent event) {
        YigdConfig config = YigdConfig.getConfig();
        CommandConfig commandConfig = config.commandConfig;

        event.getDispatcher().register(Commands.literal(commandConfig.mainCommand)
                .requires(source -> hasPermission(source, commandConfig.basePermissionLevel))
                .executes(YigdCommands::baseCommand)
                .then(Commands.literal("latest")
                        .requires(source -> hasPermission(source, commandConfig.viewLatestPermissionLevel))
                        .executes(YigdCommands::viewLatest))
                .then(Commands.literal("grave")
                        .requires(source -> hasPermission(source, commandConfig.viewSelfPermissionLevel))
                        .executes(YigdCommands::viewSelf)
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(source -> hasPermission(source, commandConfig.viewUserPermissionLevel))
                                .executes(context -> viewUser(context, EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("moderate")
                        .requires(source -> hasPermission(source, commandConfig.viewAllPermissionLevel))
                        .executes(YigdCommands::viewAll))
                .then(Commands.literal("restore")
                        .requires(source -> hasPermission(source, commandConfig.restorePermissionLevel))
                        .executes(YigdCommands::restore)
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> restore(context, EntityArgument.getPlayer(context, "player")))
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(context -> restore(
                                                context,
                                                EntityArgument.getPlayer(context, "player"),
                                                BlockPosArgument.getLoadedBlockPos(context, "pos"))))))
                .then(Commands.literal("rob")
                        .requires(source -> hasPermission(source, commandConfig.robPermissionLevel))
                        .then(Commands.argument("victim", EntityArgument.player())
                                .executes(context -> rob(context, EntityArgument.getPlayer(context, "victim"))))
                        .then(Commands.argument("grave_id", UuidArgument.uuid())
                                .executes(context -> rob(context, UuidArgument.getUuid(context, "grave_id")))))
                .then(Commands.literal("whitelist")
                        .requires(source -> hasPermission(source, commandConfig.whitelistPermissionLevel))
                        .executes(YigdCommands::showListType)
                        .then(Commands.literal("add")
                                .then(Commands.argument("target", EntityArgument.players())
                                        .executes(context -> addToList(context, EntityArgument.getPlayers(context, "target")))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("target", EntityArgument.players())
                                        .executes(context -> removeFromList(context, EntityArgument.getPlayers(context, "target")))))
                        .then(Commands.literal("toggle")
                                .executes(YigdCommands::toggleListType))
                        .then(Commands.literal("list")
                                .executes(YigdCommands::showList))));
    }

    private static boolean hasPermission(CommandSourceStack source, int requiredLevel) {
        return source.hasPermission(requiredLevel);
    }

    private static int baseCommand(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return viewSelf(context);
    }

    private static int viewLatest(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = getPlayer(context);
        List<GraveComponent> components = DeathInfoManager.INSTANCE.getBackupData(player.getGameProfile());
        List<GraveComponent> unClaimedGraves = new ArrayList<>(components);
        unClaimedGraves.removeIf(graveComponent -> graveComponent.getStatus() != GraveStatus.UNCLAIMED);
        if (unClaimedGraves.isEmpty()) {
            player.sendSystemMessage(Component.translatable("text.yigd.command.latest.fail"));
            return -1;
        }

        ServerPacketHandler.sendGraveOverviewPacket(player, unClaimedGraves.get(0));
        return 1;
    }

    private static int viewSelf(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = getPlayer(context);
        return viewUser(context, player);
    }

    private static int viewUser(CommandContext<CommandSourceStack> context, ServerPlayer user) throws CommandSyntaxException {
        ServerPlayer player = getPlayer(context);
        GameProfile profile = user.getGameProfile();

        List<GraveComponent> components = DeathInfoManager.INSTANCE.getBackupData(profile);

        List<LightGraveData> lightGraveData = new ArrayList<>();
        for (GraveComponent component : components) {
            lightGraveData.add(component.toLightData());
        }

        ServerPacketHandler.sendGraveSelectionPacket(player, profile, lightGraveData);
        return 1;
    }

    private static int viewAll(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = getPlayer(context);

        Map<GameProfile, List<GraveComponent>> players = DeathInfoManager.INSTANCE.getPlayerGraves();

        List<LightPlayerData> lightPlayerData = new ArrayList<>();
        players.forEach((profile, components) -> {
            int unclaimed = 0;
            int destroyed = 0;
            for (GraveComponent component : components) {
                switch (component.getStatus()) {
                    case UNCLAIMED -> unclaimed++;
                    case DESTROYED -> destroyed++;
                }
            }
            LightPlayerData lightData = new LightPlayerData(components.size(), unclaimed, destroyed, profile);
            lightPlayerData.add(lightData);
        });

        ServerPacketHandler.sendPlayerSelectionPacket(player, lightPlayerData);

        return 1;
    }

    private static int restore(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = getPlayer(context);
        return restore(context, player);
    }

    private static int restore(CommandContext<CommandSourceStack> context, ServerPlayer target) {
        GameProfile profile = target.getGameProfile();

        List<GraveComponent> graves = new ArrayList<>(DeathInfoManager.INSTANCE.getBackupData(profile));
        graves.removeIf(graveComponent -> graveComponent.getStatus() == GraveStatus.CLAIMED);
        int size = graves.size();
        if (size < 1) return -1;

        return restore(context, target, graves.get(size - 1));
    }

    private static int restore(CommandContext<CommandSourceStack> context, ServerPlayer target, BlockPos pos) {
        GameProfile profile = target.getGameProfile();

        List<GraveComponent> graves = new ArrayList<>(DeathInfoManager.INSTANCE.getBackupData(profile));
        graves.removeIf(graveComponent -> graveComponent.getStatus() == GraveStatus.CLAIMED);

        for (GraveComponent grave : graves) {
            if (grave.getPos().equals(pos))
                return restore(context, target, grave);
        }
        return -1;
    }

    private static int restore(CommandContext<CommandSourceStack> context, ServerPlayer target, GraveComponent component) {
        component.applyToPlayer(target, (ServerLevel) target.level, target.position(), true);
        component.setStatus(GraveStatus.CLAIMED);

        component.removeGraveBlock();

        context.getSource().sendSystemMessage(Component.translatable("text.yigd.command.restore.success"));
        return 1;
    }

    private static int rob(CommandContext<CommandSourceStack> context, ServerPlayer victim) throws CommandSyntaxException {
        GameProfile profile = victim.getGameProfile();
        List<GraveComponent> graves = new ArrayList<>(DeathInfoManager.INSTANCE.getBackupData(profile));
        graves.removeIf(graveComponent -> graveComponent.getStatus() != GraveStatus.UNCLAIMED);

        int size = graves.size();
        if (size < 1) return -1;
        GraveComponent component = graves.get(size - 1);

        return rob(context, component);
    }

    private static int rob(CommandContext<CommandSourceStack> context, UUID graveId) throws CommandSyntaxException {
        Optional<GraveComponent> component = DeathInfoManager.INSTANCE.getGrave(graveId);
        if (component.isEmpty()) {
            return -1;
        }
        return rob(context, component.get());
    }

    private static int rob(CommandContext<CommandSourceStack> context, GraveComponent component) throws CommandSyntaxException {
        ServerPlayer player = getPlayer(context);

        component.applyToPlayer(player, context.getSource().getLevel(), player.position(), false);
        component.setStatus(GraveStatus.CLAIMED);

        component.removeGraveBlock();

        player.sendSystemMessage(Component.translatable("text.yigd.command.rob.success"));
        return 1;
    }

    private static int showListType(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSystemMessage(Component.translatable("text.yigd.command.whitelist.show_current", DeathInfoManager.INSTANCE.getGraveListMode().name()));
        return 1;
    }

    private static int addToList(CommandContext<CommandSourceStack> context, Collection<ServerPlayer> players) {
        int i = 0;
        for (ServerPlayer player : players) {
            DeathInfoManager.INSTANCE.addToList(player.getGameProfile());
            ++i;
        }
        DeathInfoManager.INSTANCE.setDirty();

        context.getSource().sendSystemMessage(Component.translatable("text.yigd.command.whitelist.added_players", i, DeathInfoManager.INSTANCE.getGraveListMode().name()));
        return i > 0 ? 1 : 0;
    }

    private static int removeFromList(CommandContext<CommandSourceStack> context, Collection<ServerPlayer> players) {
        int i = 0;
        for (ServerPlayer player : players) {
            if (DeathInfoManager.INSTANCE.removeFromList(player.getGameProfile()))
                ++i;
        }

        context.getSource().sendSystemMessage(Component.translatable("text.yigd.command.whitelist.removed_players", i, DeathInfoManager.INSTANCE.getGraveListMode().name()));
        return i > 0 ? 1 : 0;
    }

    private static int toggleListType(CommandContext<CommandSourceStack> context) {
        ListMode listMode = DeathInfoManager.INSTANCE.getGraveListMode();
        ListMode newMode = listMode == ListMode.WHITELIST ? ListMode.BLACKLIST : ListMode.WHITELIST;
        DeathInfoManager.INSTANCE.setGraveListMode(newMode);
        DeathInfoManager.INSTANCE.setDirty();
        context.getSource().sendSystemMessage(Component.translatable("text.yigd.command.whitelist.toggle", newMode.name()));

        return 1;
    }

    private static int showList(CommandContext<CommandSourceStack> context) {
        ListMode listMode = DeathInfoManager.INSTANCE.getGraveListMode();
        Set<GameProfile> affectedPlayers = DeathInfoManager.INSTANCE.getAffectedPlayers();

        StringJoiner joiner = new StringJoiner(", ");
        for (GameProfile profile : affectedPlayers) {
            joiner.add(profile.getName());
        }
        context.getSource().sendSystemMessage(Component.literal(listMode.name() + ": " + joiner));

        return 1;
    }

    private static ServerPlayer getPlayer(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return context.getSource().getPlayerOrException();
    }
}
