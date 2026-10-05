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
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.arguments.BlockPosArgument;
import net.minecraft.command.arguments.EntityArgument;
import net.minecraft.command.arguments.UUIDArgument;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.entity.player.ServerPlayerEntity;
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
                        .then(Commands.argument("grave_id", UUIDArgument.uuid())
                                .executes(context -> rob(context, UUIDArgument.getUuid(context, "grave_id")))))
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

    private static boolean hasPermission(CommandSource source, int requiredLevel) {
        return source.hasPermission(requiredLevel);
    }

    private static int baseCommand(CommandContext<CommandSource> context) throws CommandSyntaxException {
        return viewSelf(context);
    }

    private static int viewLatest(CommandContext<CommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = getPlayer(context);
        List<GraveComponent> components = DeathInfoManager.INSTANCE.getBackupData(player.getGameProfile());
        List<GraveComponent> unClaimedGraves = new ArrayList<>(components);
        unClaimedGraves.removeIf(graveComponent -> graveComponent.getStatus() != GraveStatus.UNCLAIMED);
        if (unClaimedGraves.isEmpty()) {
            player.sendMessage(new TranslationTextComponent("text.yigd.command.latest.fail"), net.minecraft.util.Util.NIL_UUID);
            return -1;
        }

        ServerPacketHandler.sendGraveOverviewPacket(player, unClaimedGraves.get(0));
        return 1;
    }

    private static int viewSelf(CommandContext<CommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = getPlayer(context);
        return viewUser(context, player);
    }

    private static int viewUser(CommandContext<CommandSource> context, ServerPlayerEntity user) throws CommandSyntaxException {
        ServerPlayerEntity player = getPlayer(context);
        GameProfile profile = user.getGameProfile();

        List<GraveComponent> components = DeathInfoManager.INSTANCE.getBackupData(profile);

        List<LightGraveData> lightGraveData = new ArrayList<>();
        for (GraveComponent component : components) {
            lightGraveData.add(component.toLightData());
        }

        ServerPacketHandler.sendGraveSelectionPacket(player, profile, lightGraveData);
        return 1;
    }

    private static int viewAll(CommandContext<CommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = getPlayer(context);

        Map<GameProfile, List<GraveComponent>> players = DeathInfoManager.INSTANCE.getPlayerGraves();

        List<LightPlayerData> lightPlayerData = new ArrayList<>();
        players.forEach((profile, components) -> {
            int unclaimed = 0;
            int destroyed = 0;
            for (GraveComponent component : components) {
                switch (component.getStatus()) {
                    case UNCLAIMED:
                        unclaimed++;
                        break;
                    case DESTROYED:
                        destroyed++;
                        break;
                    default:
                        break;
                }
            }
            LightPlayerData lightData = new LightPlayerData(components.size(), unclaimed, destroyed, profile);
            lightPlayerData.add(lightData);
        });

        ServerPacketHandler.sendPlayerSelectionPacket(player, lightPlayerData);

        return 1;
    }

    private static int restore(CommandContext<CommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = getPlayer(context);
        return restore(context, player);
    }

    private static int restore(CommandContext<CommandSource> context, ServerPlayerEntity target) {
        GameProfile profile = target.getGameProfile();

        List<GraveComponent> graves = new ArrayList<>(DeathInfoManager.INSTANCE.getBackupData(profile));
        graves.removeIf(graveComponent -> graveComponent.getStatus() == GraveStatus.CLAIMED);
        int size = graves.size();
        if (size < 1) return -1;

        return restore(context, target, graves.get(size - 1));
    }

    private static int restore(CommandContext<CommandSource> context, ServerPlayerEntity target, BlockPos pos) {
        GameProfile profile = target.getGameProfile();

        List<GraveComponent> graves = new ArrayList<>(DeathInfoManager.INSTANCE.getBackupData(profile));
        graves.removeIf(graveComponent -> graveComponent.getStatus() == GraveStatus.CLAIMED);

        for (GraveComponent grave : graves) {
            if (grave.getPos().equals(pos))
                return restore(context, target, grave);
        }
        return -1;
    }

    private static int restore(CommandContext<CommandSource> context, ServerPlayerEntity target, GraveComponent component) {
        component.applyToPlayer(target, (ServerWorld) target.level, target.position(), true);
        component.setStatus(GraveStatus.CLAIMED);

        component.removeGraveBlock();

        context.getSource().sendSuccess(new TranslationTextComponent("text.yigd.command.restore.success"), false);
        return 1;
    }

    private static int rob(CommandContext<CommandSource> context, ServerPlayerEntity victim) throws CommandSyntaxException {
        GameProfile profile = victim.getGameProfile();
        List<GraveComponent> graves = new ArrayList<>(DeathInfoManager.INSTANCE.getBackupData(profile));
        graves.removeIf(graveComponent -> graveComponent.getStatus() != GraveStatus.UNCLAIMED);

        int size = graves.size();
        if (size < 1) return -1;
        GraveComponent component = graves.get(size - 1);

        return rob(context, component);
    }

    private static int rob(CommandContext<CommandSource> context, UUID graveId) throws CommandSyntaxException {
        Optional<GraveComponent> component = DeathInfoManager.INSTANCE.getGrave(graveId);
        if (component.isEmpty()) {
            return -1;
        }
        return rob(context, component.get());
    }

    private static int rob(CommandContext<CommandSource> context, GraveComponent component) throws CommandSyntaxException {
        ServerPlayerEntity player = getPlayer(context);

        component.applyToPlayer(player, context.getSource().getLevel(), player.position(), false);
        component.setStatus(GraveStatus.CLAIMED);

        component.removeGraveBlock();

        player.sendMessage(new TranslationTextComponent("text.yigd.command.rob.success"), net.minecraft.util.Util.NIL_UUID);
        return 1;
    }

    private static int showListType(CommandContext<CommandSource> context) {
        context.getSource().sendSuccess(new TranslationTextComponent("text.yigd.command.whitelist.show_current", DeathInfoManager.INSTANCE.getGraveListMode().name()), false);
        return 1;
    }

    private static int addToList(CommandContext<CommandSource> context, Collection<ServerPlayerEntity> players) {
        int i = 0;
        for (ServerPlayerEntity player : players) {
            DeathInfoManager.INSTANCE.addToList(player.getGameProfile());
            ++i;
        }
        DeathInfoManager.INSTANCE.setDirty();

        context.getSource().sendSuccess(new TranslationTextComponent("text.yigd.command.whitelist.added_players", i, DeathInfoManager.INSTANCE.getGraveListMode().name()), false);
        return i > 0 ? 1 : 0;
    }

    private static int removeFromList(CommandContext<CommandSource> context, Collection<ServerPlayerEntity> players) {
        int i = 0;
        for (ServerPlayerEntity player : players) {
            if (DeathInfoManager.INSTANCE.removeFromList(player.getGameProfile()))
                ++i;
        }

        context.getSource().sendSuccess(new TranslationTextComponent("text.yigd.command.whitelist.removed_players", i, DeathInfoManager.INSTANCE.getGraveListMode().name()), false);
        return i > 0 ? 1 : 0;
    }

    private static int toggleListType(CommandContext<CommandSource> context) {
        ListMode listMode = DeathInfoManager.INSTANCE.getGraveListMode();
        ListMode newMode = listMode == ListMode.WHITELIST ? ListMode.BLACKLIST : ListMode.WHITELIST;
        DeathInfoManager.INSTANCE.setGraveListMode(newMode);
        DeathInfoManager.INSTANCE.setDirty();
        context.getSource().sendSuccess(new TranslationTextComponent("text.yigd.command.whitelist.toggle", newMode.name()), false);

        return 1;
    }

    private static int showList(CommandContext<CommandSource> context) {
        ListMode listMode = DeathInfoManager.INSTANCE.getGraveListMode();
        Set<GameProfile> affectedPlayers = DeathInfoManager.INSTANCE.getAffectedPlayers();

        StringJoiner joiner = new StringJoiner(", ");
        for (GameProfile profile : affectedPlayers) {
            joiner.add(profile.getName());
        }
        context.getSource().sendSuccess(new StringTextComponent(listMode.name() + ": " + joiner), false);

        return 1;
    }

    private static ServerPlayerEntity getPlayer(CommandContext<CommandSource> context) throws CommandSyntaxException {
        return context.getSource().getPlayerOrException();
    }
}
