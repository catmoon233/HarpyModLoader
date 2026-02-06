package org.agmas.harpymodloader.modded_murder;

import dev.doctor4t.trainmurdermystery.api.Role;
import net.minecraft.entity.player.PlayerEntity;
import org.agmas.harpymodloader.Harpymodloader;

import java.util.*;

/**
 * 管理角色对应关系和配对分配
 * 支持同时分配两个关联角色（例如医生和毒师）
 */
public class RoleAssignmentManager {

    /**
     * 根据 Occupations_Roles 映射获取关联角色
     * 
     * @param role 主角色
     * @return 关联的角色，如果没有则返回null
     */
    public static Role getCompanionRole(Role role) {
        return Harpymodloader.Occupations_Roles.get(role);
    }

    /**
     * 检查一个角色是否有关联角色
     */
    public static boolean hasCompanionRole(Role role) {
        return Harpymodloader.Occupations_Roles.containsKey(role);
    }

    /**
     * 展开角色列表：如果角色有关联角色，添加关联角色
     * 例如：[医生] -> [医生, 毒师]
     * 
     * @param roles 原始角色列表
     * @return 展开后的角色列表（包含所有关联角色）
     */
    public static List<Role> expandWithCompanionRoles(List<Role> roles) {
        List<Role> expandedRoles = new ArrayList<>(roles);
        List<Role> companionRoles = new ArrayList<>();
        
        for (Role role : roles) {
            Role companion = getCompanionRole(role);
            if (companion != null) {
                companionRoles.add(companion);
            }
        }
        
        expandedRoles.addAll(companionRoles);
        return expandedRoles;
    }

    /**
     * 将角色分配给玩家，同时处理关联角色
     * 如果玩家已经有角色，则使用 expandWithCompanionRoles 来确保关联角色也被分配
     * 
     * @param playerToRole 玩家到角色的映射
     * @param player 玩家
     * @param role 要分配的角色
     */
    public static void assignRoleWithCompanion(Map<PlayerEntity, Role> playerToRole, PlayerEntity player, Role role) {
        playerToRole.put(player, role);
        
        // 如果该角色有关联角色，需要为其他玩家分配相应的关联角色
        Role companionRole = getCompanionRole(role);
        if (companionRole != null) {
            Harpymodloader.LOGGER.fine(
                String.format("Role %s has companion role %s", 
                    role.getIdentifier(), companionRole.getIdentifier())
            );
        }
    }

    /**
     * 获取所有角色对应关系
     */
    public static Map<Role, Role> getOccupationsRoles() {
        return Harpymodloader.Occupations_Roles;
    }

    /**
     * 清空所有角色对应关系
     */
    public static void clearOccupationsRoles() {
        Harpymodloader.Occupations_Roles.clear();
    }

    /**
     * 添加角色对应关系
     */
    public static void addOccupationRole(Role mainRole, Role companionRole) {
        Harpymodloader.Occupations_Roles.put(mainRole, companionRole);
    }
}
