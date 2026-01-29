package org.agmas.harpymodloader.modded_murder;

import dev.doctor4t.trainmurdermystery.api.Role;
import dev.doctor4t.trainmurdermystery.api.TMMRoles;
import dev.doctor4t.trainmurdermystery.cca.GameWorldComponent;
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
import net.minecraft.util.Pair;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.WeightedUtil;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.config.HarpyModLoaderConfig;
import org.agmas.harpymodloader.events.ModdedRoleAssigned;
import org.agmas.harpymodloader.events.ModifierAssigned;
import org.agmas.harpymodloader.events.ResetPlayerEvent;
import org.agmas.harpymodloader.commands.SetRoleCountCommand;
import org.agmas.harpymodloader.modifiers.HMLModifiers;

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
    public void initializeGame(ServerWorld serverWorld, GameWorldComponent gameWorldComponent,
            List<ServerPlayerEntity> players) {

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

        assignRole(serverWorld, gameWorldComponent, players);
    }

    private void assignRole(ServerWorld serverWorld, GameWorldComponent gameWorldComponent, List<ServerPlayerEntity> players) {
        // 创建角色分配映射表
        Map<PlayerEntity, Role> roleAssignments = new HashMap<>();

        // 初始化角色分配映射，但不预先分配任何角色
        for (PlayerEntity player : players) {
            roleAssignments.put(player, null); // 使用null表示尚未分配角色
        }


        AtomicInteger killerCount = new AtomicInteger(SetRoleCountCommand.getKillerCount(players.size()));
        AtomicInteger vigilanteCount = new AtomicInteger(SetRoleCountCommand.getVigilanteCount(players.size()));
        AtomicInteger natureCount = new AtomicInteger(SetRoleCountCommand.getNatureCount(players.size()));
        int civilian = players.size() - killerCount.get() - vigilanteCount.get() - natureCount.get();
        Harpymodloader.FORCED_MODDED_ROLE_FLIP.forEach(
                (player, forcedRole) -> {
                    roleAssignments.put(serverWorld.getPlayerByUuid(player), forcedRole);
                    if (forcedRole.canUseKiller()){
                        killerCount.set( killerCount.get() - 1);
                    }
                    if (forcedRole == TMMRoles.VIGILANTE){
                        vigilanteCount.set( vigilanteCount.get() - 1);
                    }
                    if (!forcedRole.isInnocent() && !forcedRole.canUseKiller()){
                        natureCount.set( natureCount.get() - 1);
                    }
                }
        );
        if (vigilanteCount.get() <= 0){
            civilian += vigilanteCount.get();
            vigilanteCount.set(0);
        }
        if (killerCount.get() <= 0){
            civilian += killerCount.get();
            killerCount.set(0);
        }
        if (natureCount.get() <= 0){
            civilian += natureCount.get();
            natureCount.set(0);
        }
        var killers = assignKillerReplacingRoles(killerCount.get());
        var vigilantes = assignVigilantesReplacingRoles(vigilanteCount.get());
        var natures = assignNatureReplacingRoles(natureCount.get());
        int roleCount = killers.getLeft().size();

        civilian+=killers.getRight() + vigilantes.getRight() + natures.getRight();

        List<Role> civilianRoles = assignCivilianRoles(civilian);

        Map<Role,Float> roleWeights = new HashMap<>();
        killers.getLeft().forEach(role -> roleWeights.put(role, 1f));
        vigilantes.getLeft().forEach(role -> roleWeights.put(role, 1f));
        natures.getLeft().forEach(role -> roleWeights.put(role, 1f));
        civilianRoles.forEach(role -> roleWeights.put(role, 1f));
        WeightedUtil<Role> roleWeightsUtil = new WeightedUtil<>(roleWeights);


        Map<PlayerEntity, Role> roleReplacementsCache = new HashMap<>();
        roleAssignments.forEach(
                (player, role) -> {
                    if (role == null) {
                        roleReplacementsCache.put(player, roleWeightsUtil.selectRandomKeyBasedOnWeightsAndRemoved());
                    }
                }
        );
        roleAssignments.putAll(roleReplacementsCache);



//        int roleCount = assignVannilaRoles(serverWorld, gameWorldComponent, players, roleAssignments);
//
//        assignCivilianReplacingRoles(roleCount, serverWorld, gameWorldComponent, players, roleAssignments);
//

        int modifierRoleCount = roleCount * HarpyModLoaderConfig.HANDLER.instance().modifierMultiplier;
        assignModifiers(modifierRoleCount, serverWorld, gameWorldComponent, players);

        // 统一应用角色分配并触发相应事件
        for (Map.Entry<PlayerEntity, Role> entry : roleAssignments.entrySet()) {
            final var key = entry.getKey();
            final var value = entry.getValue();
            if (value != null) { // 只有当角色不为null时才分配
                gameWorldComponent.addRole(key, value);
                // 触发模组化角色分配事件
                ModdedRoleAssigned.EVENT.invoker().assignModdedRole(key, value);

                value.getDefaultItems().forEach(
                        item -> key.getInventory().offerOrDrop(item));
            } else {
                // 如果没有分配角色，则分配默认平民角色
                gameWorldComponent.addRole(key, TMMRoles.CIVILIAN);
            }
        }

        for (ServerPlayerEntity player : players) {
            ServerPlayNetworking.send(player,
                    new AnnounceWelcomePayload(gameWorldComponent.getRole(player).getIdentifier().toString(), roleCount,
                            players.size() - roleCount));
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

                if (worldModifierComponent.getModifiers(player) != null && worldModifierComponent.getModifiers(player)
                        .size() >= HarpyModLoaderConfig.HANDLER.instance().modifierMaximum) {
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
                        .append(Texts.join(worldModifierComponent.getModifiers(player), Text.literal(", "),
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



    public List<Role> assignCivilianRoles(int civilianCount) {
        if (civilianCount <= 0) return new ArrayList<>();
        
        // 获取所有可能的平民角色，这些角色不能是杀手、不能是原版角色、不能是无辜角色
        ArrayList<Role> shuffledCivilianRoles = new ArrayList<>(TMMRoles.ROLES.values());
        shuffledCivilianRoles.removeIf(
                role -> Harpymodloader.VANNILA_ROLES.contains(role) || 
                       role.canUseKiller() ||
                        !role.isInnocent() ||
                       role == TMMRoles.CIVILIAN ||
                       HarpyModLoaderConfig.HANDLER.instance().disabled.contains(role.identifier().toString()));
        
        // 构建角色权重映射，每个角色只添加一次
        HashMap<Role, Float> roleWeights = new HashMap<>();
        for (Role role : shuffledCivilianRoles) {
            if (HarpyModLoaderConfig.HANDLER.instance().useCustomRoleWeights) {
                float customWeight = ModdedWeights.getRoleWeight(role);
                if (customWeight > 0) {
                    roleWeights.put(role, customWeight);
                }
            } else {
                roleWeights.put(role, 1f);
            }
        }
        
        // 创建加权工具并选择角色
        WeightedUtil<Role> civilian = new WeightedUtil<>(roleWeights);
        
        // 创建临时角色计数映射，用于本次游戏的角色分配
        Map<Identifier, Integer> tempRoleCounts = new HashMap<>();
        for (Role role : shuffledCivilianRoles) {
            tempRoleCounts.put(role.identifier(), Harpymodloader.ROLE_MAX.getOrDefault(role.identifier(), 1));
        }
        
        List<Role> civilianRoles = new ArrayList<>();
        for (int i = 0; i < civilianCount; i++) {
            // 使用新的方法选择角色，该方法会检查并减少计数
            Role selectedRole = selectRoleWithCountCheck(civilian, "civilian", tempRoleCounts);
            if (selectedRole != null) {
                civilianRoles.add(selectedRole);
            } else {
                // 如果角色池不够，添加普通平民角色
                civilianRoles.add(TMMRoles.CIVILIAN);
            }
        }
        
        return civilianRoles;
    }
    public Pair<List<Role>,Integer> assignKillerReplacingRoles(int killerCount) {
        if (killerCount <= 0) return new Pair<>(new ArrayList<>(), 0);
        ArrayList<Role> shuffledKillerRoles = new ArrayList<>(TMMRoles.ROLES.values());
        shuffledKillerRoles.removeIf(
                role -> Harpymodloader.VANNILA_ROLES.contains(role) || !role.canUseKiller() || role.isInnocent()
                        || role == TMMRoles.CIVILIAN
                        || HarpyModLoaderConfig.HANDLER.instance().disabled.contains(role.identifier().toString()));
        
        // 构建角色权重映射，每个角色只添加一次
        HashMap<Role, Float> roleWeights = new HashMap<>();
        for (Role role : shuffledKillerRoles) {
            if (HarpyModLoaderConfig.HANDLER.instance().useCustomRoleWeights) {
                float customWeight = ModdedWeights.getRoleWeight( role);
                if ( customWeight > 0) {
                    roleWeights.put(role, customWeight);
                }
            } else {
                roleWeights.put(role, 1f);
            }
        }
        
        // 创建临时角色计数映射，用于本次游戏的角色分配
        Map<Identifier, Integer> tempRoleCounts = new HashMap<>();
        for (Role role : shuffledKillerRoles) {
            tempRoleCounts.put(role.identifier(), Harpymodloader.ROLE_MAX.getOrDefault(role.identifier(), 1));
        }
        
        WeightedUtil<Role> killer = new WeightedUtil<>(roleWeights);

        List <Role> killerRoles = new ArrayList<>();
        for (int i = 0; i < killerCount; i++) {
            Role selectedRole = selectRoleWithCountCheck(killer, "killer", tempRoleCounts);
            if (selectedRole != null) {
                killerRoles.add(selectedRole);
            }
        }
        return new Pair<>(killerRoles, Math.max(0, killerCount - killerRoles.size()));
    }
    public Pair<List<Role>,Integer> assignVigilantesReplacingRoles(int killerCount) {

        if (killerCount <= 0) return new Pair<>(new ArrayList<>(), 0);
        ArrayList<Role> shuffledVigilantesRoles = new ArrayList<>(TMMRoles.ROLES.values());
        shuffledVigilantesRoles.removeIf(
                role -> !role.isVigilanteTeam()
                        || HarpyModLoaderConfig.HANDLER.instance().disabled.contains(role.identifier().toString()));

        // 构建角色权重映射，每个角色只添加一次
        HashMap<Role, Float> roleWeights = new HashMap<>();
        for (Role role : shuffledVigilantesRoles) {
            if (HarpyModLoaderConfig.HANDLER.instance().useCustomRoleWeights) {
                float customWeight = ModdedWeights.getRoleWeight( role);
                if ( customWeight > 0) {
                    roleWeights.put(role, customWeight);
                }
            } else {
                roleWeights.put(role, 1f);
            }
        }

        // 创建临时角色计数映射，用于本次游戏的角色分配
        Map<Identifier, Integer> tempRoleCounts = new HashMap<>();
        for (Role role : shuffledVigilantesRoles) {
            tempRoleCounts.put(role.identifier(), Harpymodloader.ROLE_MAX.getOrDefault(role.identifier(), 1));
        }
        
        WeightedUtil<Role> vigilanted = new WeightedUtil<>(roleWeights);

        List <Role> vigilanteRoles = new ArrayList<>();
        for (int i = 0; i < killerCount; i++) {
            Role selectedRole = selectRoleWithCountCheck(vigilanted, "vigilante", tempRoleCounts);
            if (selectedRole != null) {
                vigilanteRoles.add(selectedRole);
            }
        }
        return new Pair<>(vigilanteRoles, Math.max(0, killerCount - vigilanteRoles.size()));
    }
    public Pair<List<Role>,Integer> assignNatureReplacingRoles(int natureCount) {

        if (natureCount <= 0) return new Pair<>(new ArrayList<>(), 0);
        ArrayList<Role> shuffledNatureRoles = new ArrayList<>(TMMRoles.ROLES.values());
        shuffledNatureRoles.removeIf(
                role -> Harpymodloader.VANNILA_ROLES.contains(role) || role.canUseKiller() || role.isInnocent()
                        || role == TMMRoles.CIVILIAN
                        || HarpyModLoaderConfig.HANDLER.instance().disabled.contains(role.identifier().toString()));

        // 构建角色权重映射，每个角色只添加一次
        HashMap<Role, Float> roleWeights = new HashMap<>();
        for (Role role : shuffledNatureRoles) {
            if (HarpyModLoaderConfig.HANDLER.instance().useCustomRoleWeights) {
                float customWeight = ModdedWeights.getRoleWeight( role);
                if ( customWeight > 0) {
                    roleWeights.put(role, customWeight);
                }
            } else {
                roleWeights.put(role, 1f);
            }
        }

        // 创建临时角色计数映射，用于本次游戏的角色分配
        Map<Identifier, Integer> tempRoleCounts = new HashMap<>();
        for (Role role : shuffledNatureRoles) {
            tempRoleCounts.put(role.identifier(), Harpymodloader.ROLE_MAX.getOrDefault(role.identifier(), 1));
        }
        
        WeightedUtil<Role> vigilanted = new WeightedUtil<>(roleWeights);

        List <Role> natureRoles = new ArrayList<>();
        for (int i = 0; i < natureCount; i++) {
            Role selectedRole = selectRoleWithCountCheck(vigilanted, "nature", tempRoleCounts);
            if (selectedRole != null) {
                natureRoles.add(selectedRole);
            }
        }
        return new Pair<>(natureRoles, Math.max(0, natureCount - natureRoles.size()));
    }

    /**
     * 根据权重选择角色并检查角色计数
     * @param weightedUtil 加权工具
     * @param roleType 角色类型（"killer", "civilian", "vigilante", "nature"）
     * @param tempRoleCounts 临时角色计数映射
     * @return 选中的角色，如果没有可用角色则返回null
     */
    private Role selectRoleWithCountCheck(WeightedUtil<Role> weightedUtil, String roleType, Map<Identifier, Integer> tempRoleCounts) {
        if (weightedUtil.isEmpty()) {
            return null;
        }

        // 选择一个角色
        Role selectedRole = weightedUtil.selectRandomKeyBasedOnWeights();
        if (selectedRole == null) {
            return null;
        }

        // 检查该角色的剩余计数
        int remainingCount = tempRoleCounts.getOrDefault(selectedRole.identifier(), Harpymodloader.ROLE_MAX.getOrDefault(selectedRole.identifier(), 1));
        if (remainingCount > 0) {
            // 减少该角色的临时计数
            tempRoleCounts.put(selectedRole.identifier(), remainingCount - 1);
            // 如果计数变为0，从加权工具中移除该角色
            if (remainingCount - 1 <= 0) {
                weightedUtil.removeKey(selectedRole);
            }
            return selectedRole;
        } else {
            // 如果角色计数已用完，从加权工具中移除该角色并递归调用
            weightedUtil.removeKey(selectedRole);
            return selectRoleWithCountCheck(weightedUtil, roleType, tempRoleCounts);
        }
    }


}