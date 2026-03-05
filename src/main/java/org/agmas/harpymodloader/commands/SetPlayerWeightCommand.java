package org.agmas.harpymodloader.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.concurrent.CompletableFuture;

import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.modded_murder.PlayerRoleWeightManager;

public class SetPlayerWeightCommand {

  public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
    dispatcher.register(CommandManager.literal("playerRoleWeight")
        .requires(serverCommandSource -> serverCommandSource.hasPermissionLevel(2))
        .then(CommandManager.argument("player", EntityArgumentType.player())
            .then(CommandManager.literal("get")
                .then(CommandManager.argument("role", IntegerArgumentType.integer(1, 4))
                    .suggests(SetPlayerWeightCommand::suggestRoleType)
                    .executes(context -> executeGet(context.getSource(),
                        EntityArgumentType.getPlayer(context, "player"),
                        IntegerArgumentType.getInteger(context, "role")))))
            .then(CommandManager.literal("set")
                .then(CommandManager.argument("role", IntegerArgumentType.integer(1, 4))
                    .suggests(SetPlayerWeightCommand::suggestRoleType)
                    .then(CommandManager.argument("weight", IntegerArgumentType.integer(0))
                        .executes(context -> executeSet(context.getSource(),
                            EntityArgumentType.getPlayer(context, "player"),
                            IntegerArgumentType.getInteger(context, "role"),
                            IntegerArgumentType.getInteger(context, "weight"))))))));
  }

  private static int executeGet(ServerCommandSource source, ServerPlayerEntity player, int roleType)
      throws CommandSyntaxException {
    if (!Harpymodloader.isMojangVerify) {
      return 1;
    }
    // 更新玩家角色权重
    var weightManager = PlayerRoleWeightManager.playerWeights.get(player.getUuid());
    if (weightManager == null) {
      weightManager = new PlayerRoleWeightManager.WeightInfo();
      PlayerRoleWeightManager.playerWeights.putIfAbsent(player.getUuid(), weightManager);
    }
    int weight = weightManager.getWeight(roleType);
    source.sendFeedback(
        () -> Text.translatable("Player [%s]\nRole Type [%s] Weight [%s]", player.getDisplayName(), roleType, weight),
        true);
    return 1;
  }

  private static int executeSet(ServerCommandSource source, ServerPlayerEntity player, int roleType, int weight)
      throws CommandSyntaxException {
    if (!Harpymodloader.isMojangVerify) {
      return 1;
    }
    // 更新玩家角色权重
    var weightManager = PlayerRoleWeightManager.playerWeights.get(player.getUuid());
    if (weightManager == null) {
      weightManager = new PlayerRoleWeightManager.WeightInfo();
      PlayerRoleWeightManager.playerWeights.putIfAbsent(player.getUuid(), weightManager);
    }
    weightManager.putWeight(roleType, weight);
    source.sendFeedback(() -> Text.translatable("Modified successfully!\nPlayer [%s]\nRole Type [%s] Weight [%s]",
        player.getDisplayName(), roleType, weight), true);
    return 1;
  }

  public static CompletableFuture<Suggestions> suggestRoleType(CommandContext<ServerCommandSource> context,
      SuggestionsBuilder builder) {
    return null;
  }
}