package org.agmas.harpymodloader.modded_murder;

import dev.doctor4t.trainmurdermystery.api.Role;
import dev.doctor4t.trainmurdermystery.api.TMMRoles;
import dev.doctor4t.trainmurdermystery.cca.GameWorldComponent;
import dev.doctor4t.trainmurdermystery.cca.ScoreboardRoleSelectorComponent;
import dev.doctor4t.trainmurdermystery.cca.TrainWorldComponent;
import dev.doctor4t.trainmurdermystery.client.gui.RoleAnnouncementTexts;
import dev.doctor4t.trainmurdermystery.game.MurderGameMode;
import dev.doctor4t.trainmurdermystery.util.AnnounceWelcomePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.Texts;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.WeightedUtil;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.config.HarpyModLoaderConfig;
import org.agmas.harpymodloader.events.ModdedRoleAssigned;
import org.agmas.harpymodloader.events.ModifierAssigned;
import org.agmas.harpymodloader.events.ResetPlayerEvent;
import org.agmas.harpymodloader.commands.SetRoleCountCommand;
import org.agmas.harpymodloader.modifiers.HMLModifiers;
import org.agmas.harpymodloader.modifiers.Modifier;
import org.jetbrains.annotations.NotNull;

public class ModdedMurderGameMode extends MurderGameMode {

    public ModdedMurderGameMode(Identifier identifier) {
        super(identifier);
    }

    @Override
    public void finalizeGame(ServerWorld serverWorld, GameWorldComponent gameWorldComponent) {
        // 执行游戏结束时的函数
        executeFunction(serverWorld.getServer().getCommandSource(), "harpymodloader:end_game");
        
        // 将玩家从队伍中移除
        removePlayersFromTeam(serverWorld.getServer().getCommandSource(), "harpymodloader_game");
        
        WorldModifierComponent worldModifierComponent = WorldModifierComponent.KEY.get(serverWorld);
        worldModifierComponent.getModifiers().clear();
        super.finalizeGame(serverWorld, gameWorldComponent);
    }

    @Override
    public void initializeGame(ServerWorld serverWorld, GameWorldComponent gameWorldComponent, List<ServerPlayerEntity> players) {

        Harpymodloader.refreshRoles();

        HarpyModLoaderConfig.HANDLER.load();


        ((TrainWorldComponent)TrainWorldComponent.KEY.get(serverWorld)).setTimeOfDay(TrainWorldComponent.TimeOfDay.NIGHT);
        gameWorldComponent.clearRoleMap();
        for(ServerPlayerEntity player : players) {
            ResetPlayerEvent.EVENT.invoker().resetPlayer(player);
            // 暂时不直接添加角色，而是记录到映射表中
        }

        // 将所有玩家添加到队伍中
        addPlayersToTeam(serverWorld.getServer().getCommandSource(), players, "harpymodloader_game");
        
        // 执行游戏开始时的函数
        executeFunction(serverWorld.getServer().getCommandSource(), "harpymodloader:start_game");

        // 创建角色分配映射表
        Map<ServerPlayerEntity, Role> roleAssignments = new HashMap<>();
        
        // 初始化角色分配映射，但不预先分配任何角色
        for(ServerPlayerEntity player : players) {
            roleAssignments.put(player, null); // 使用null表示尚未分配角色
        }

        int roleCount = assignVannilaRoles(serverWorld,gameWorldComponent,players, roleAssignments);

        assignCivilianReplacingRoles(roleCount,serverWorld,gameWorldComponent,players, roleAssignments);

        assignKillerReplacingRoles(roleCount,serverWorld,gameWorldComponent,players, roleAssignments);

        int modifierRoleCount = roleCount * HarpyModLoaderConfig.HANDLER.instance().modifierMultiplier;
        assignModifiers(modifierRoleCount, serverWorld,gameWorldComponent,players);

        // 统一应用角色分配并触发相应事件
        for(Map.Entry<ServerPlayerEntity, Role> entry : roleAssignments.entrySet()) {
            final var key = entry.getKey();
            final var value = entry.getValue();
            if (value != null) { // 只有当角色不为null时才分配
                gameWorldComponent.addRole(key, value);
                // 触发模组化角色分配事件
                ModdedRoleAssigned.EVENT.invoker().assignModdedRole(key, value);
                value.onInit(key.getServer(), key);
                value.getDefaultItems().forEach(
                        item ->  key.getInventory().offerOrDrop(item)
                );
            } else {
                // 如果没有分配角色，则分配默认平民角色
                gameWorldComponent.addRole(key, TMMRoles.CIVILIAN);
            }
        }

        for(ServerPlayerEntity player : players) {
//            if (Harpymodloader.VANNILA_ROLES.contains(gameWorldComponent.getRole(player))) {
//                ServerPlayNetworking.send(player, new AnnounceWelcomePayload(gameWorldComponent.isRole(player, TMMRoles.KILLER) ? RoleAnnouncementTexts.KILLER.getName() : (gameWorldComponent.isRole(player, TMMRoles.VIGILANTE) ? RoleAnnouncementTexts.VIGILANTE : RoleAnnouncementTexts.CIVILIAN).getName().toString(), roleCount, players.size() - roleCount));
//            } else {
                ServerPlayNetworking.send(player, new AnnounceWelcomePayload(gameWorldComponent.getRole( player).getIdentifier().toString(), roleCount, players.size() - roleCount));
//            }
        }


        Harpymodloader.FORCED_MODDED_ROLE.clear();
        Harpymodloader.FORCED_MODDED_ROLE_FLIP.clear();

        Harpymodloader.FORCED_MODDED_MODIFIER.clear();
    }

    // 执行指定函数的辅助方法
    private void executeFunction(ServerCommandSource source, String function) {
        try {
            source.getServer().getCommandManager().executeWithPrefix(source, "function " + function);
        } catch (Exception e) {
            Log.warn(LogCategory.GENERAL, "Failed to execute function: " + function + ", error: " + e.getMessage());
        }
    }
    
    // 将玩家添加到队伍的辅助方法
    private void addPlayersToTeam(ServerCommandSource source, List<ServerPlayerEntity> players, String teamName) {
        try {
            // 首先尝试创建队伍（如果不存在）
            source.getServer().getCommandManager().executeWithPrefix(source, "team add " + teamName + " " + teamName + "_team");
            
            // 将所有玩家添加到队伍中
            for (ServerPlayerEntity player : players) {
                source.getServer().getCommandManager().executeWithPrefix(source, "team join " + teamName + " " + player.getName().getString());
            }
        } catch (Exception e) {
            Log.warn(LogCategory.GENERAL, "Failed to manage team: " + teamName + ", error: " + e.getMessage());
        }
    }
    
    // 将玩家从队伍中移除的辅助方法
    private void removePlayersFromTeam(ServerCommandSource source, String teamName) {
        try {
            // 将所有玩家从队伍中移除
            source.getServer().getCommandManager().executeWithPrefix(source, "team empty " + teamName);
            
            // 删除队伍
            source.getServer().getCommandManager().executeWithPrefix(source, "team remove " + teamName);
        } catch (Exception e) {
            Log.warn(LogCategory.GENERAL, "Failed to remove team: " + teamName + ", error: " + e.getMessage());
        }
    }

    public void assignModifiers(int desiredRoleCount, ServerWorld serverWorld, GameWorldComponent gameWorldComponent, List<ServerPlayerEntity> players) {
        WorldModifierComponent worldModifierComponent = WorldModifierComponent.KEY.get(serverWorld);
        worldModifierComponent.getModifiers().clear();
        int killerMods = (int) HMLModifiers.MODIFIERS.stream().filter(modifier -> modifier.killerOnly).count();
        HMLModifiers.MODIFIERS.forEach((mod)->{
            int playersAssigned = 0;
            int specificDesiredRoleCount = desiredRoleCount;

            if (mod.killerOnly) {
                specificDesiredRoleCount = (int) Math.floor(Math.floor((double) players.size() / 7) / killerMods);
                specificDesiredRoleCount = Math.max(specificDesiredRoleCount, 1);
            }

            ArrayList<ServerPlayerEntity> shuffledPlayers = new ArrayList<>(players);
            Collections.shuffle(shuffledPlayers);
            Collections.shuffle(shuffledPlayers);


            if (Harpymodloader.FORCED_MODDED_MODIFIER.containsKey(mod)) {
                for (ServerPlayerEntity player : shuffledPlayers) {
                    if (Harpymodloader.FORCED_MODDED_MODIFIER.get(mod).contains(player.getUuid())) {
                        worldModifierComponent.addModifier(player.getUuid(), mod);
                        ModifierAssigned.EVENT.invoker().assignModifier(player, mod);
                        playersAssigned++;
                    }
                }
            }

            for (ServerPlayerEntity player : shuffledPlayers) {
                if (HarpyModLoaderConfig.HANDLER.instance().disabledModifiers.contains(mod.identifier.toString())) {
                    continue;
                }
                if (playersAssigned >= specificDesiredRoleCount) {
                    continue;
                }

                if (Harpymodloader.MODIFIER_MAX.containsKey(mod.identifier)) {
                    if (playersAssigned >= Harpymodloader.MODIFIER_MAX.get(mod.identifier)) {
                        continue;
                    }
                }

                boolean valid = true;

                if (mod.canOnlyBeAppliedTo != null) {
                    if (gameWorldComponent.getRole(player) != null) {
                        valid = mod.canOnlyBeAppliedTo.contains(gameWorldComponent.getRole(player));
                    }
                }
                if (mod.cannotBeAppliedTo != null) {
                    if (gameWorldComponent.getRole(player) != null) {
                        valid = !mod.cannotBeAppliedTo.contains(gameWorldComponent.getRole(player));
                    }
                }
                if (!valid) {
                    continue;
                }

                if (mod.killerOnly) {
                    valid = gameWorldComponent.canUseKillerFeatures(player);
                }
                if (mod.civilianOnly) {
                    valid = !gameWorldComponent.canUseKillerFeatures(player);
                }
                if (!valid) {
                    continue;
                }

                if (worldModifierComponent.getModifiers(player) != null && worldModifierComponent.getModifiers(player).size() >= HarpyModLoaderConfig.HANDLER.instance().modifierMaximum) {
                    valid = false;
                }

                if (!valid) {
                    continue;
                }

                worldModifierComponent.addModifier(player.getUuid(), mod);
                ModifierAssigned.EVENT.invoker().assignModifier(player, mod);
                playersAssigned++;
            }

        });
        for (ServerPlayerEntity player : players) {
            if (!worldModifierComponent.getModifiers(player).isEmpty()) {
                MutableText modifiersText = Text.translatable("announcement.modifier").formatted(Formatting.GRAY)
                        .append(Texts.join(worldModifierComponent.getModifiers(player), Text.literal(", "), modifier -> modifier.getName(false).withColor(modifier.color)));
                player.sendMessage(modifiersText, true);
            } else {
                if (!HMLModifiers.MODIFIERS.isEmpty()) {
                    player.sendMessage(Text.translatable("announcement.no_modifiers").formatted(Formatting.DARK_GRAY), true);
                }
            }
        }
    }

    public void assignCivilianReplacingRoles(int desiredRoleCount, ServerWorld serverWorld, GameWorldComponent gameWorldComponent, List<ServerPlayerEntity> players, Map<ServerPlayerEntity, Role> roleAssignments) {

        // shuffle roles so modded roles are different every time
        ArrayList<Role> shuffledCivillianRoles = new ArrayList<>(TMMRoles.ROLES.values());
        // 从平民角色中排除CIVILIAN
        shuffledCivillianRoles.removeIf(role -> Harpymodloader.VANNILA_ROLES.contains(role) || role.canUseKiller() || !role.isInnocent() || role == TMMRoles.CIVILIAN);

        ArrayList<Role> shuffledNeutralRoles = new ArrayList<>(TMMRoles.ROLES.values());
        // 从中立角色中排除CIVILIAN
        shuffledNeutralRoles.removeIf(role -> Harpymodloader.VANNILA_ROLES.contains(role) || role.canUseKiller() || role.isInnocent() || role == TMMRoles.CIVILIAN);

        ArrayList<ServerPlayerEntity> playersForCivillianRoles = new ArrayList<>(players);
        playersForCivillianRoles.removeIf(player -> {
            Role role = gameWorldComponent.getRole(player);
            return !Harpymodloader.OVERWRITE_ROLES.contains(role) || role.canUseKiller();
        });

        Collections.shuffle(shuffledCivillianRoles);
        Collections.shuffle(shuffledCivillianRoles);
        Collections.shuffle(shuffledNeutralRoles);
        Collections.shuffle(shuffledNeutralRoles);

        int baseCount = players.size() / 5;  // 整数除法自动向下取整
        int assignedNeutralRoles = 0;
        int neutralRoleCount = 0;

        for (Role role : shuffledNeutralRoles) {
            if (HarpyModLoaderConfig.HANDLER.instance().disabled.contains(role.identifier().toString())) {
                continue;
            }
            neutralRoleCount++;
        }

        for (Role role : shuffledNeutralRoles) {
            if (HarpyModLoaderConfig.HANDLER.instance().disabled.contains(role.identifier().toString())) {
                continue;
            }
            if (assignedNeutralRoles >= baseCount) {
                continue;
            }
            int roleSpecificDesireCount = Math.min((int) Math.ceil((double) playersForCivillianRoles.size() / neutralRoleCount), desiredRoleCount);
            if (Harpymodloader.ROLE_MAX.containsKey(role.identifier())) {
                roleSpecificDesireCount = Harpymodloader.ROLE_MAX.get(role.identifier());
            }

            assignedNeutralRoles += findAndAssignPlayers(roleSpecificDesireCount, role, playersForCivillianRoles,gameWorldComponent,serverWorld, roleAssignments);
            playersForCivillianRoles.removeIf(player -> {
                Role role2 = gameWorldComponent.getRole(player);
                return !Harpymodloader.OVERWRITE_ROLES.contains(role2);
            });
        }

        playersForCivillianRoles.removeIf(player -> {
            Role role = gameWorldComponent.getRole(player);
            return !role.isInnocent();
        });

        int roleCount= 0;
        for (Role role : shuffledCivillianRoles) {
            if (HarpyModLoaderConfig.HANDLER.instance().disabled.contains(role.identifier().toString())) {
                continue;
            }
            roleCount++;
        }

        for (Role role : shuffledCivillianRoles) {
            if (HarpyModLoaderConfig.HANDLER.instance().disabled.contains(role.identifier().toString())) {
                continue;
            }
            int roleSpecificDesireCount = Math.min((int) Math.max(Math.round((double) playersForCivillianRoles.size() / roleCount),1), desiredRoleCount);
            if (Harpymodloader.ROLE_MAX.containsKey(role.identifier())) {
                roleSpecificDesireCount = Harpymodloader.ROLE_MAX.get(role.identifier());
            }

            findAndAssignPlayers(roleSpecificDesireCount, role, playersForCivillianRoles,gameWorldComponent,serverWorld, roleAssignments);
            playersForCivillianRoles.removeIf(player -> {
                Role role2 = gameWorldComponent.getRole(player);
                return !Harpymodloader.OVERWRITE_ROLES.contains(role2);
            });
        }
    }




    public void assignKillerReplacingRoles(int desiredRoleCount, ServerWorld serverWorld, GameWorldComponent gameWorldComponent, List<ServerPlayerEntity> players, Map<ServerPlayerEntity, Role> roleAssignments) {

        // shuffle roles so modded roles are different every time
        ArrayList<Role> shuffledKillerRoles = new ArrayList<>(TMMRoles.ROLES.values());
        // 从杀手角色中排除CIVILIAN
        shuffledKillerRoles.removeIf(role -> Harpymodloader.VANNILA_ROLES.contains(role) || !role.canUseKiller() || role == TMMRoles.CIVILIAN);

        ArrayList<ServerPlayerEntity> playersForKillerRoles = new ArrayList<>(players);
        playersForKillerRoles.removeIf(player -> {
            Role role = gameWorldComponent.getRole(player);
            return !Harpymodloader.OVERWRITE_ROLES.contains(role) || !role.canUseKiller();
        });

        Collections.shuffle(shuffledKillerRoles);
        Collections.shuffle(shuffledKillerRoles);
        Collections.shuffle(shuffledKillerRoles);

        int roleCount= 0;
        for (Role role : shuffledKillerRoles) {
            if (HarpyModLoaderConfig.HANDLER.instance().disabled.contains(role.identifier().toString())) {
                continue;
            }
            roleCount++;
        }
        for (Role role : shuffledKillerRoles) {
            if (HarpyModLoaderConfig.HANDLER.instance().disabled.contains(role.identifier().toString())) {
                continue;
            }
            int roleSpecificDesireCount = Math.min((int) Math.max(Math.round((double) playersForKillerRoles.size() / roleCount),1), desiredRoleCount);
            if (Harpymodloader.ROLE_MAX.containsKey(role.identifier())) {
                roleSpecificDesireCount = Harpymodloader.ROLE_MAX.get(role.identifier());
            }

            findAndAssignPlayers(roleSpecificDesireCount, role, playersForKillerRoles,gameWorldComponent,serverWorld, roleAssignments);
            playersForKillerRoles.removeIf(player -> {
                Role role2 = gameWorldComponent.getRole(player);
                return !Harpymodloader.OVERWRITE_ROLES.contains(role2);
            });
        }
    }

    private static int findAndAssignPlayers(int desiredRoleCount, Role role, @NotNull List<ServerPlayerEntity> players, GameWorldComponent gameWorldComponent, World world, Map<ServerPlayerEntity, Role> roleAssignments) {

        ArrayList<ServerPlayerEntity> assignedPlayers = new ArrayList<>();

        if (Harpymodloader.FORCED_MODDED_ROLE.containsKey(role)) {
            for (UUID uuid : Harpymodloader.FORCED_MODDED_ROLE.get(role)) {
                PlayerEntity player = world.getPlayerByUuid(uuid);
                if (player instanceof ServerPlayerEntity serverPlayer) {
                    if (players.contains(serverPlayer)) {
                        assignedPlayers.add(serverPlayer);
                        --desiredRoleCount;
                        ModdedWeights.roleRounds.get(role).put(player.getUuid(), ModdedWeights.roleRounds.get(role).getOrDefault(player.getUuid(), 1) + 1);
                    }
                }
            }
        }

        // 使用 WeightedUtil 重构权重选择逻辑
        HashMap<ServerPlayerEntity, Float> weightMap = new HashMap<>();
        for(ServerPlayerEntity player : players) {
            if (!Harpymodloader.FORCED_MODDED_ROLE_FLIP.containsKey(player.getUuid()) && Harpymodloader.OVERWRITE_ROLES.contains(gameWorldComponent.getRole(player))) {
                float weight = (float)Math.exp((-ModdedWeights.roleRounds.get(role).getOrDefault(player.getUuid(),1) * 4));
                if (!gameWorldComponent.areWeightsEnabled()) {
                    weight = 1.0F;
                }

                weightMap.put(player, weight);
            }
        }

        WeightedUtil<ServerPlayerEntity> weightedUtil = new WeightedUtil<>(weightMap);

        // 选择指定数量的玩家
        for(int i = 0; i < desiredRoleCount && !weightedUtil.isEmpty(); ++i) {
            ServerPlayerEntity selectedPlayer = weightedUtil.selectRandomKeyBasedOnWeightsAndRemoved();
            if (selectedPlayer != null) {
                assignedPlayers.add(selectedPlayer);
                // 更新角色轮次计数
                ModdedWeights.roleRounds.get(role).put(selectedPlayer.getUuid(), ModdedWeights.roleRounds.get(role).getOrDefault(selectedPlayer.getUuid(), 1) + 1);
            }
        }

        int i = 0;
        for(ServerPlayerEntity player : assignedPlayers) {
            // 不直接添加角色，而是记录到角色分配映射表中
            roleAssignments.put(player, role);
            Log.info(LogCategory.GENERAL, player.getNameForScoreboard() + " || " + role.identifier());
            i++;
        }
        return i;
    }

    public int assignVannilaRoles(ServerWorld serverWorld, GameWorldComponent gameWorldComponent, List<ServerPlayerEntity> players, Map<ServerPlayerEntity, Role> roleAssignments) {
        ScoreboardRoleSelectorComponent roleSelector = ScoreboardRoleSelectorComponent.KEY.get(serverWorld.getScoreboard());

        int killerCount = SetRoleCountCommand.getKillerCount(players.size());
        int vigilanteCount = SetRoleCountCommand.getVigilanteCount(players.size());

        List<ServerPlayerEntity> playersForVigilante = new ArrayList<>(players);
        playersForVigilante.removeIf(player -> Harpymodloader.FORCED_MODDED_ROLE_FLIP.containsKey(player.getUuid()));

        List<ServerPlayerEntity> playersForKiller = new ArrayList<>(players);
        playersForKiller.removeIf(player -> {
            if (Harpymodloader.FORCED_MODDED_ROLE_FLIP.containsKey(player.getUuid())) {
                if (Harpymodloader.FORCED_MODDED_ROLE_FLIP.get(player.getUuid()).canUseKiller()) {
                    roleSelector.forcedKillers.add(player.getUuid());
                    return false;
                } else {
                    return true;
                }
            }
            return false;
        });


        // 处理杀手分配
        List<ServerPlayerEntity> selectedKillers = new ArrayList<>();
        int actualKillerCount = Math.min(killerCount, playersForVigilante.size());
        
        // 随机选择杀手
        List<ServerPlayerEntity> shuffledPlayersForKillers = new ArrayList<>(playersForVigilante);
        Collections.shuffle(shuffledPlayersForKillers);
        
        for (int i = 0; i < actualKillerCount && i < shuffledPlayersForKillers.size(); i++) {
            selectedKillers.add(shuffledPlayersForKillers.get(i));
        }
        
        // 记录到角色分配映射表
        for (ServerPlayerEntity player : selectedKillers) {
            roleAssignments.put(player, TMMRoles.KILLER);
        }
        
        // 处理义警分配
        List<ServerPlayerEntity> selectedVigilantes = new ArrayList<>();
        int actualVigilanteCount = Math.min(vigilanteCount, playersForKiller.size());
        
        // 随机选择义警
        List<ServerPlayerEntity> shuffledPlayersForVigilantes = new ArrayList<>(playersForKiller);
        Collections.shuffle(shuffledPlayersForVigilantes);
        
        for (int i = 0; i < actualVigilanteCount && i < shuffledPlayersForVigilantes.size(); i++) {
            selectedVigilantes.add(shuffledPlayersForVigilantes.get(i));
        }
        
        // 记录到角色分配映射表
        for (ServerPlayerEntity player : selectedVigilantes) {
            roleAssignments.put(player, TMMRoles.VIGILANTE);
        }
        
        int total = selectedKillers.size();
        return total;
    }
}