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
import net.minecraft.util.Formatting;

import java.util.concurrent.CompletableFuture;

import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.modded_murder.PlayerRoleWeightManager;

public class SetPlayerWeightCommand {

  public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
    dispatcher.register(CommandManager.literal("playerRoleWeight")
        .requires(serverCommandSource -> serverCommandSource.hasPermissionLevel(2))
        .then(CommandManager.argument("player", EntityArgumentType.player())
            .then(CommandManager.literal("get")
                .executes(context -> executeGet(context.getSource(),
                    EntityArgumentType.getPlayer(context, "player"),
                    0))
                .then(CommandManager.argument("role", IntegerArgumentType.integer(0, 4))
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
    final String[] TypeMappings = { "ALL", "INNOCENT", "NEUTRALS", "NEUTRALS_FOR_KILLER", "KILLER" };
    if (roleType == 0) {
      var weightManager = PlayerRoleWeightManager.playerWeights.get(player.getUuid());
      if (weightManager == null) {
        weightManager = new PlayerRoleWeightManager.WeightInfo();
        PlayerRoleWeightManager.playerWeights.putIfAbsent(player.getUuid(), weightManager);
      }
      int weightTotal = weightManager.getWeightSum();
      if (weightTotal == 0)
        weightTotal = 1;
      source.sendFeedback(
          () -> Text.translatable("Player [%s]", player.getDisplayName()).formatted(Formatting.GOLD),
          false);
      for (int i = 1; i <= 4; i++) {
        // 获取玩家角色权重
        final int roleType_1 = i;
        int weight = weightManager.getWeight(i);
        double percent = 100 * (1. - (double) weight / (double) weightTotal);
        source.sendMessage(Text.translatable("%s(%s): Role Selected Weight: %s%%",
            TypeMappings[roleType_1],
            roleType_1, String.format("%.2f", percent)));
      }
      return 1;
    }
    if (roleType >= TypeMappings.length)
      return 0;
    // 获取玩家角色权重
    var weightManager = PlayerRoleWeightManager.playerWeights.get(player.getUuid());
    if (weightManager == null) {
      weightManager = new PlayerRoleWeightManager.WeightInfo();
      PlayerRoleWeightManager.playerWeights.putIfAbsent(player.getUuid(), weightManager);
    }
    int weight = weightManager.getWeight(roleType);
    source.sendFeedback(
        () -> Text.translatable("Player [%s]\nRole Type [%s(%s)] Weight [%s]", player.getDisplayName(),
            TypeMappings[roleType],
            roleType, weight),
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
    builder.suggest(2, Text.literal("Neutrals"));
    builder.suggest(3, Text.literal("Neutrals For Killers"));
    builder.suggest(4, Text.literal("Killers"));
    return builder.buildFuture();
  }
}