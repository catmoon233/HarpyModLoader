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
import org.agmas.harpymodloader.config.HarpyModLoaderConfig;

public class SetCompanionRoleCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("setCompanionRole")
                .requires(serverCommandSource -> serverCommandSource.hasPermissionLevel(2))
                .then(CommandManager.argument("primaryRole", RoleArgumentType.skipVanilla())
                        .then(CommandManager.argument("companionRole", RoleArgumentType.skipVanilla())
                                .executes(SetCompanionRoleCommand::execute)))
                .then(CommandManager.literal("remove")
                        .then(CommandManager.argument("primaryRole", RoleArgumentType.skipVanilla())
                                .executes(SetCompanionRoleCommand::removeCompanion))));
    }

    private static int execute(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        if(!Harpymodloader.isMojangVerify) {
            return 1;
        }
        Role primaryRole = RoleArgumentType.getRole(context, "primaryRole");
        Role companionRole = RoleArgumentType.getRole(context, "companionRole");
        
        // 更新配置
        HarpyModLoaderConfig.HANDLER.instance().companionRoles.put(
            primaryRole.identifier(), 
            companionRole.identifier()
        );
        
        HarpyModLoaderConfig.HANDLER.save();
        
        Text primaryRoleText = Harpymodloader.getRoleName(primaryRole).withColor(primaryRole.color());
        Text companionRoleText = Harpymodloader.getRoleName(companionRole).withColor(companionRole.color());
        
        context.getSource().sendFeedback(
            () -> Text.translatable("commands.tmm.setcompanionrole.success", 
                primaryRoleText, companionRoleText), 
            true
        );
        
        return 1;
    }
    
    private static int removeCompanion(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        Role primaryRole = RoleArgumentType.getRole(context, "primaryRole");
        
        // 从配置中移除
        if (HarpyModLoaderConfig.HANDLER.instance().companionRoles.remove(primaryRole.identifier()) != null) {
            HarpyModLoaderConfig.HANDLER.save();
            
            Text primaryRoleText = Harpymodloader.getRoleName(primaryRole).withColor(primaryRole.color());
            
            context.getSource().sendFeedback(
                () -> Text.translatable("commands.tmm.removecompanionrole.success", primaryRoleText), 
                true
            );
        } else {
            context.getSource().sendFeedback(
                () -> Text.translatable("commands.tmm.removecompanionrole.notfound", 
                    Harpymodloader.getRoleName(primaryRole).withColor(primaryRole.color())), 
                false
            );
        }
        
        return 1;
    }
}