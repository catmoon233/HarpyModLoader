package org.agmas.harpymodloader.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.doctor4t.trainmurdermystery.api.Role;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.commands.argument.RoleArgumentType;

public class SetOccupationRoleCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("setOccupationRole")
                .requires(serverCommandSource -> serverCommandSource.hasPermissionLevel(2))
                .then(CommandManager.argument("mainRole", RoleArgumentType.skipVanilla())
                        .then(CommandManager.argument("companionRole", RoleArgumentType.skipVanilla())
                                .executes(SetOccupationRoleCommand::setOccupationRole)))
                .then(CommandManager.literal("remove")
                        .then(CommandManager.argument("role", RoleArgumentType.skipVanilla())
                                .executes(SetOccupationRoleCommand::removeOccupationRole)))
                .then(CommandManager.literal("clear")
                        .executes(SetOccupationRoleCommand::clearOccupationRoles))
                .then(CommandManager.literal("list")
                        .executes(SetOccupationRoleCommand::listOccupationRoles)));
    }

    private static int setOccupationRole(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        Role mainRole = RoleArgumentType.getRole(context, "mainRole");
        Role companionRole = RoleArgumentType.getRole(context, "companionRole");

        if (mainRole.equals(companionRole)) {
            context.getSource().sendError(Text.literal("Main role and companion role cannot be the same!"));
            return 0;
        }

        Harpymodloader.setOccupationRole(mainRole, companionRole);
        context.getSource().sendFeedback(
                () -> Text.literal(String.format(
                        "§aSet occupation role: %s -> %s",
                        mainRole.identifier(),
                        companionRole.identifier())),
                true);
        return 1;
    }

    private static int removeOccupationRole(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        Role role = RoleArgumentType.getRole(context, "role");

        if (!Harpymodloader.hasOccupationRole(role)) {
            context.getSource().sendError(Text.literal("Role " + role.identifier() + " has no occupation role!"));
            return 0;
        }

        Harpymodloader.removeOccupationRole(role);
        context.getSource().sendFeedback(
                () -> Text.literal(String.format("§aRemoved occupation role for: %s", role.identifier())),
                true);
        return 1;
    }

    private static int clearOccupationRoles(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        int count = Harpymodloader.Occupations_Roles.size();
        Harpymodloader.clearOccupationRoles();
        context.getSource().sendFeedback(
                () -> Text.literal(String.format("§aCleared all occupation roles (%d removed)", count)),
                true);
        return 1;
    }

    private static int listOccupationRoles(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        if (Harpymodloader.Occupations_Roles.isEmpty()) {
            context.getSource().sendFeedback(() -> Text.literal("§cNo occupation roles configured"), false);
            return 0;
        }

        context.getSource().sendFeedback(() -> Text.literal("§6Current occupation roles:"), false);
        Harpymodloader.Occupations_Roles.forEach((mainRole, companionRole) ->
                context.getSource().sendFeedback(
                        () -> Text.literal(String.format("  §a%s §f-> §a%s",
                                mainRole.identifier(), companionRole.identifier())),
                        false));
        return 1;
    }
}
