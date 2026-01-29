package org.agmas.harpymodloader.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import dev.doctor4t.trainmurdermystery.api.Role;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.commands.argument.RoleArgumentType;
import org.agmas.harpymodloader.config.HarpyModLoaderConfig;

public class SetRoleWeightCommand {
    private static final SimpleCommandExceptionType FAILED_EXCEPTION = new SimpleCommandExceptionType(
            Text.translatable("commands.tmm.setroleweight.failed"));

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("setRoleWeight")
                .requires(serverCommandSource -> serverCommandSource.hasPermissionLevel(2))
                .then(CommandManager.argument("role", RoleArgumentType.skipVanilla())
                        .then(CommandManager.argument("weight", FloatArgumentType.floatArg(0))
                                .executes(context -> execute(context.getSource(),
                                        RoleArgumentType.getRole(context, "role"),
                                        IntegerArgumentType.getInteger(context, "weight"))))));
    }

    private static int execute(ServerCommandSource source, Role role, float weight) throws CommandSyntaxException {
        // 更新配置中的角色权重
        HarpyModLoaderConfig.HANDLER.instance().roleWeights.put(role.identifier(), weight);
        
        // 保存配置
        HarpyModLoaderConfig.HANDLER.save();

        source.sendFeedback(() -> Text.translatable("commands.tmm.setroleweight.success", 
                role.getIdentifier().toString(), weight), true);
        return 1;
    }
}