package org.agmas.harpymodloader.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import org.agmas.harpymodloader.commands.argument.ModifierArgumentType;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.events.ModifierAssigned;
import org.agmas.harpymodloader.events.ModifierRemoved;
import org.agmas.harpymodloader.modifiers.Modifier;

public class AssignModifierCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("changeModifier")
                .requires(serverCommandSource -> serverCommandSource.hasPermissionLevel(2))
                .then(CommandManager.argument("player", EntityArgumentType.player())
                        .then(CommandManager.argument("modifier", ModifierArgumentType.create())
                                .executes(AssignModifierCommand::execute))));
    }

    private static int execute(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity targetPlayer = EntityArgumentType.getPlayer(context, "player");
        Modifier modifier = ModifierArgumentType.getModifier(context, "modifier");

        // 获取游戏世界组件
        WorldModifierComponent worldModifierComponent = WorldModifierComponent.KEY.get(targetPlayer.getWorld());

        // 获取玩家当前Modifier状态
        var modifiers = worldModifierComponent.getModifiers(targetPlayer.getUuid());
        final MutableText feedbackText;

        if (modifiers.contains(modifier)) {
            ModifierRemoved.EVENT.invoker().removeModifier(targetPlayer, modifier);
            worldModifierComponent.removeModifier(targetPlayer.getUuid(), modifier);
            feedbackText = Text.translatable("commands.changemodifier.player.notification.remove",
                    targetPlayer.getDisplayName(), modifier.getName());
        } else {
            worldModifierComponent.addModifier(targetPlayer.getUuid(), modifier);
            ModifierAssigned.EVENT.invoker().assignModifier(targetPlayer, modifier);
            feedbackText = Text.translatable("commands.changemodifier.player.notification.add",
                    targetPlayer.getDisplayName(), modifier.getName());
        }
        // 发送反馈消息
        context.getSource().sendFeedback(() -> feedbackText, true);

        return 1;
    }
}