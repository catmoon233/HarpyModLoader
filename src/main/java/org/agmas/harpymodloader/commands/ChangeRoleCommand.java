package org.agmas.harpymodloader.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import dev.doctor4t.trainmurdermystery.TMM;
import dev.doctor4t.trainmurdermystery.api.Role;
import dev.doctor4t.trainmurdermystery.api.replay.ReplayEventTypes.EventType;
import dev.doctor4t.trainmurdermystery.cca.GameWorldComponent;
import dev.doctor4t.trainmurdermystery.game.GameFunctions;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.commands.argument.RoleArgumentType;
import org.agmas.harpymodloader.events.ModdedRoleAssigned;
import org.agmas.harpymodloader.events.ModdedRoleRemoved;

public class ChangeRoleCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("changeRole")
                .requires(serverCommandSource -> serverCommandSource.hasPermissionLevel(2))
                .then(CommandManager.argument("player", EntityArgumentType.player())
                        .then(CommandManager.argument("role", RoleArgumentType.skipVanilla())
                                .executes(ChangeRoleCommand::execute))));
    }

    private static int execute(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity targetPlayer = EntityArgumentType.getPlayer(context, "player");
        Role newRole = RoleArgumentType.getRole(context, "role");

        // 获取游戏世界组件
        GameWorldComponent gameWorldComponent = GameWorldComponent.KEY.get(targetPlayer.getWorld());

        // 获取玩家当前角色
        Role oldRole = gameWorldComponent.getRole(targetPlayer);

        // 移除旧角色事件
        if (oldRole != null) {
            ModdedRoleRemoved.EVENT.invoker().removeModdedRole(targetPlayer, oldRole);
        }

        // 分配新角色
        gameWorldComponent.addRole(targetPlayer, newRole);

        // 触发新角色分配事件
        ModdedRoleAssigned.EVENT.invoker().assignModdedRole(targetPlayer, newRole);

        if (gameWorldComponent.isRunning()) {
            TMM.REPLAY_MANAGER.recordPlayerRoleChange(targetPlayer.getUuid(), oldRole, newRole);
        }

        // 发送反馈消息
        final MutableText newRoleText = Harpymodloader.getRoleName(newRole).withColor(newRole.color())
                .styled(style -> style.withHoverEvent(
                        new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(newRole.identifier().toString()))));

        if (oldRole != null) {
            final MutableText oldRoleText = Harpymodloader.getRoleName(oldRole).withColor(oldRole.color())
                    .styled(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            Text.literal(oldRole.identifier().toString()))));
            context.getSource().sendFeedback(() -> Text.translatable("commands.changerole.success.changed", oldRoleText,
                    newRoleText, targetPlayer.getDisplayName()), true);
        } else {
            context.getSource().sendFeedback(() -> Text.translatable("commands.changerole.success.assigned",
                    newRoleText, targetPlayer.getDisplayName()), true);
        }

        // 通知玩家角色已改变
        targetPlayer.sendMessage(Text.translatable("commands.changerole.player.notification", newRoleText), false);

        return 1;
    }
}