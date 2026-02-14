package org.agmas.harpymodloader.modded_murder;

import dev.doctor4t.trainmurdermystery.api.Role;
import dev.doctor4t.trainmurdermystery.api.TMMRoles;
import dev.doctor4t.trainmurdermystery.cca.GameWorldComponent;
import dev.doctor4t.trainmurdermystery.cca.PlayerShopComponent;
import dev.doctor4t.trainmurdermystery.cca.TrainWorldComponent;
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

import java.util.*;
import java.util.stream.Collectors;

import net.minecraft.util.PathUtil;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.WeightedUtil;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.config.HarpyModLoaderConfig;
import org.agmas.harpymodloader.events.GameInitializeEvent;
import org.agmas.harpymodloader.events.ModdedRoleAssigned;
import org.agmas.harpymodloader.events.ModifierAssigned;
import org.agmas.harpymodloader.events.ResetPlayerEvent;
import org.agmas.harpymodloader.commands.SetRoleCountCommand;
import org.agmas.harpymodloader.modifiers.HMLModifiers;
import org.agmas.harpymodloader.modifiers.Modifier;

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
        Harpymodloader.FORCED_MODDED_ROLE.clear();
        Harpymodloader.FORCED_MODDED_MODIFIER.clear();
        Harpymodloader.FORCED_MODDED_ROLE_FLIP.clear();
        WorldModifierComponent worldModifierComponent = WorldModifierComponent.KEY.get(serverWorld);
        worldModifierComponent.modifiers.clear();
        worldModifierComponent.sync();
        super.finalizeGame(serverWorld, gameWorldComponent);
    }

    @Override
    public void initializeGame(ServerWorld serverWorld, GameWorldComponent gameWorldComponent,
            List<ServerPlayerEntity> players) {

        GameInitializeEvent.EVENT.invoker().initializeGame(serverWorld, gameWorldComponent, players);

        Harpymodloader.refreshRoles();

        HarpyModLoaderConfig.HANDLER.load();

        ((TrainWorldComponent) TrainWorldComponent.KEY.get(serverWorld))
                .setTimeOfDay(TrainWorldComponent.TimeOfDay.NIGHT);
        gameWorldComponent.clearRoleMap();
        for (ServerPlayerEntity player : players) {
            ResetPlayerEvent.EVENT.invoker().resetPlayer(player);
            // 暂时不直接添加角色，而是记录到映射表中
        }

        // 将所有玩家添加到队伍中
        addPlayersToTeam(serverWorld.getServer().getCommandSource(), players, "harpymodloader_game");

        // 执行游戏开始时的函数
        executeFunction(serverWorld.getServer().getCommandSource(), "harpymodloader:start_game");

        Harpymodloader.setRoleMaximum(TMMRoles.VIGILANTE.getIdentifier(), 100);
        assignRole(serverWorld, gameWorldComponent, players);
    }

    private void assignRole(ServerWorld serverWorld, GameWorldComponent gameWorldComponent,
            List<ServerPlayerEntity> players) {
        // 新的模块化角色分配流程
        Map<PlayerEntity, Role> roleAssignments = assignRolesToPlayers(serverWorld, players);

        // 计算有特殊角色的玩家数量（用于AnnounceWelcomePayload）
        long killCount = roleAssignments.values().stream()
                .filter(role -> role != null && role != TMMRoles.CIVILIAN && role.canUseKiller())
                .count();

        // 统一应用角色分配并触发相应事件
        for (Map.Entry<PlayerEntity, Role> entry : roleAssignments.entrySet()) {
            final var key = entry.getKey();
            final var value = entry.getValue();
            if (value != null) {
                gameWorldComponent.addRole(key, value);

                value.getDefaultItems().forEach(item -> key.getInventory().offerOrDrop(item));
                Harpymodloader.LOGGER.fine("Assigned role " + value.getIdentifier() + " to " + key.getName());
                if (value.canUseKiller()) {
                    PlayerShopComponent playerShopComponent = PlayerShopComponent.KEY.get(key);
                    playerShopComponent.setBalance(100 + playerShopComponent.balance);
                }
            } else {
                // 如果没有分配角色，则分配默认平民角色
                gameWorldComponent.addRole(key, TMMRoles.CIVILIAN);
                Harpymodloader.LOGGER
                        .fine("Assigned role " + TMMRoles.CIVILIAN.getIdentifier() + " to " + key.getName());
            }
        }

        for (ServerPlayerEntity player : players) {
            var role = gameWorldComponent.getRole(player);
            ServerPlayNetworking.send(player,
                    new AnnounceWelcomePayload(role.getIdentifier().toString(), (int) killCount,
                            (int) (players.size() - killCount)));
            ModdedRoleAssigned.EVENT.invoker().assignModdedRole(player, role);
        }
        // 分配修饰符（修饰符放在职业分配后）
        int modifierRoleCount = (int) killCount * HarpyModLoaderConfig.HANDLER.instance().modifierMultiplier;
        assignModifiers(modifierRoleCount, serverWorld, gameWorldComponent, players);

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
            source.getServer().getCommandManager().executeWithPrefix(source,
                    "team add " + teamName + " " + teamName + "_team");

            // 将所有玩家添加到队伍中
            for (ServerPlayerEntity player : players) {
                source.getServer().getCommandManager().executeWithPrefix(source,
                        "team join " + teamName + " " + player.getName().getString());
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

    public void assignModifiers(int desiredRoleCount, ServerWorld serverWorld, GameWorldComponent gameWorldComponent,
            List<ServerPlayerEntity> players) {
        WorldModifierComponent worldModifierComponent = WorldModifierComponent.KEY.get(serverWorld);
        worldModifierComponent.getModifiers().clear();

        // 使用临时映射存储要添加的修饰符，避免在遍历过程中修改数据结构
        Map<UUID, List<Modifier>> tempModifierAssignments = new HashMap<>();

        int killerMods = (int) HMLModifiers.MODIFIERS.stream().filter(modifier -> modifier.killerOnly).count();
        HMLModifiers.MODIFIERS.forEach((mod) -> {
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
                        // 临时存储，稍后统一添加
                        tempModifierAssignments.computeIfAbsent(player.getUuid(), k -> new ArrayList<>()).add(mod);
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

                if (worldModifierComponent.getModifiers(player) != null && worldModifierComponent.getModifiers(player)
                        .size() >= HarpyModLoaderConfig.HANDLER.instance().modifierMaximum) {
                    valid = false;
                }

                if (!valid) {
                    continue;
                }

                // 临时存储，稍后统一添加
                tempModifierAssignments.computeIfAbsent(player.getUuid(), k -> new ArrayList<>()).add(mod);
                ModifierAssigned.EVENT.invoker().assignModifier(player, mod);
                playersAssigned++;
            }

        });

        // 统一将临时存储的修饰符添加到组件中
        for (Map.Entry<UUID, List<Modifier>> entry : tempModifierAssignments.entrySet()) {
            UUID playerUuid = entry.getKey();
            for (Modifier mod : entry.getValue()) {
                worldModifierComponent.addModifier(playerUuid, mod);
            }
        }

        // 等所有修饰符都添加完成后，再同步整个组件
        worldModifierComponent.sync();

        for (ServerPlayerEntity player : players) {
            var modifiers = worldModifierComponent.getDisplayableModifiers(player);
            if (!modifiers.isEmpty()) {
                MutableText modifiersText = Text.translatable("announcement.modifier").formatted(Formatting.GRAY)
                        .append(Texts.join(modifiers, Text.literal(", "),
                                modifier -> modifier.getName(false).withColor(modifier.color)));
                player.sendMessage(modifiersText, true);
            } else {
                if (!HMLModifiers.MODIFIERS.isEmpty()) {
                    player.sendMessage(Text.translatable("announcement.no_modifiers").formatted(Formatting.DARK_GRAY),
                            true);
                }
            }
        }
    }

    /**
     * 新的模块化角色分配方法
     * 处理强制角色、计算各类型角色数量、创建角色池、分配角色以及处理关联角色
     */
    private Map<PlayerEntity, Role> assignRolesToPlayers(ServerWorld serverWorld, List<ServerPlayerEntity> players) {
        Map<PlayerEntity, Role> roleAssignments = new HashMap<>();
        for (PlayerEntity player : players) {
            roleAssignments.put(player, null);
        }

        // 第一步：处理强制分配的角色
        Map<UUID, Role> forcedRoles = new HashMap<>(Harpymodloader.FORCED_MODDED_ROLE_FLIP);
        int killerCount = SetRoleCountCommand.getKillerCount(players.size());
        int vigilanteCount = SetRoleCountCommand.getVigilanteCount(players.size());
        int natureCount = SetRoleCountCommand.getNatureCount(players.size());

        // 处理强制分配的角色，减少对应角色类型的数量需求
        for (Map.Entry<UUID, Role> entry : forcedRoles.entrySet()) {
            PlayerEntity player = serverWorld.getPlayerByUuid(entry.getKey());
            if (player != null) {
                Role role = entry.getValue();
                if (role != null) {
                    roleAssignments.put(player, role);

                    // 根据角色类型减少对应的数量需求
                    if (role.canUseKiller()) {
                        killerCount--;
                    } else if (role.isVigilanteTeam()) {
                        vigilanteCount--;
                    } else if (!role.isInnocent()) {
                        natureCount--;
                    }
                }
            }
        }

        // 确保数量不为负数
        killerCount = Math.max(0, killerCount);
        vigilanteCount = Math.max(0, vigilanteCount);
        natureCount = Math.max(0, natureCount);

        // 第二步：创建角色池并分配角色
        // 杀手池
        RoleAssignmentPool killerPool = RoleAssignmentPool.create("Killer",
                role -> !Harpymodloader.VANNILA_ROLES.contains(role) &&
                        role.canUseKiller() &&
                        !role.isInnocent() &&
                        role != TMMRoles.CIVILIAN);
        List<Role> assignedKillers = killerPool.selectRoles(killerCount);

        // 警卫池 - 使用无限重复模式，因为警卫职业数量有限
        RoleAssignmentPool vigilantePool = RoleAssignmentPool.create("Vigilante", Role::isVigilanteTeam);
        List<Role> assignedVigilantes = vigilantePool.selectRoles(vigilanteCount);

        // 中立池
        RoleAssignmentPool naturePool = RoleAssignmentPool.create("Nature",
                role -> !Harpymodloader.VANNILA_ROLES.contains(role) &&
                        !role.canUseKiller() &&
                        !role.isInnocent() &&
                        role != TMMRoles.CIVILIAN);
        List<Role> assignedNatures = naturePool.selectRoles(natureCount);

        // 第三步：计算平民数量（只分配基础非平民角色，不包含补充的平民角色）
        int assignedSpecialCount = assignedKillers.size() + assignedVigilantes.size() + assignedNatures.size();
        int civilianCount = players.size() - assignedSpecialCount - forcedRoles.size();

        // 平民池（只包含真正的"非平民"角色，例如医生、探长等）
        RoleAssignmentPool civilianPool = RoleAssignmentPool.create("Civilian",
                role -> !Harpymodloader.VANNILA_ROLES.contains(role) &&
                        !role.isVigilanteTeam() &&
                        !role.canUseKiller() &&
                        role.isInnocent() &&
                        role != TMMRoles.CIVILIAN);
        List<Role> assignedCivilians = civilianPool.selectRoles(civilianCount);

        // 第四步：合并所有分配的角色（包括处理关联角色）
        List<Role> allRoles = new ArrayList<>();
        allRoles.addAll(assignedKillers);
        allRoles.addAll(assignedVigilantes);
        allRoles.addAll(assignedNatures);
        allRoles.addAll(assignedCivilians);

        // 展开关联角色
        List<RoleInstant> roleInstantList = new ArrayList<>();
        for (Role role : allRoles) {
            roleInstantList.add(new RoleInstant(UUID.randomUUID(), role));
        }
        List<RoleInstant> expandedRoles = RoleAssignmentManager.expandWithCompanionRoles(roleInstantList);

        // 第五步：为未分配的玩家分配角色
        List<ServerPlayerEntity> unassignedPlayers = new ArrayList<>();
        for (ServerPlayerEntity player : players) {
            if (roleAssignments.get(player) == null) {
                unassignedPlayers.add(player);
            }
        }

        // 创建权重分布用于分配展开后的角色
        List<Map.Entry<RoleInstant, Float>> roleWeights = new ArrayList<>();

        for (var role : expandedRoles) {
            roleWeights.add(new AbstractMap.SimpleEntry<>(role, 1f));
        }

        final var collect = roleWeights
                .stream()
                .collect(Collectors.toMap(
                        a -> a.getKey(),
                        a -> a.getValue(),
                        (existing, replacement) -> existing, // 如果键重复，保留第一个值
                        LinkedHashMap::new));
        var hashMap = new HashMap<>(collect);
        WeightedUtil<RoleInstant> roleSelector = new WeightedUtil<>(hashMap);

        // 分配展开后的角色给未分配的玩家
        Collections.shuffle(unassignedPlayers);
        
        for (ServerPlayerEntity player : unassignedPlayers) {
            Role selectedRole = roleSelector.selectRandomKeyBasedOnWeightsAndRemoved().role();
            if (selectedRole != null) {
                roleAssignments.put(player, selectedRole);
            } else {
                // 如果没有足够的特殊角色，分配平民
                roleAssignments.put(player, TMMRoles.CIVILIAN);
            }
        }

        return roleAssignments;
    }

    public record RoleInstant(UUID uuid, Role role) {

    }

}
