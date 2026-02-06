package org.agmas.harpymodloader.modded_murder;

import dev.doctor4t.trainmurdermystery.api.Role;
import dev.doctor4t.trainmurdermystery.api.TMMRoles;
import net.minecraft.util.Identifier;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.WeightedUtil;
import org.agmas.harpymodloader.config.HarpyModLoaderConfig;

import java.util.*;
import java.util.function.Predicate;

/**
 * 模块化角色分配池 - 处理通用的角色选择和计数逻辑
 * 避免重复代码，提高可维护性
 */
public class RoleAssignmentPool {
    private final WeightedUtil<Role> roleWeights;
    private final Map<Identifier, Integer> roleCountMap;
    private final String poolName;

    private RoleAssignmentPool(String poolName, WeightedUtil<Role> roleWeights, Map<Identifier, Integer> roleCountMap) {
        this.poolName = poolName;
        this.roleWeights = roleWeights;
        this.roleCountMap = roleCountMap;
    }

    /**
     * 创建一个角色分配池
     * 
     * @param poolName 池的名称（用于日志）
     * @param filter   角色过滤条件（返回true表示该角色应该被包含在池中）
     * @return 创建的RoleAssignmentPool实例
     */
    public static RoleAssignmentPool create(String poolName, Predicate<Role> filter) {
        // 获取所有符合条件的角色
        ArrayList<Role> availableRoles = new ArrayList<>(TMMRoles.ROLES.values());
        availableRoles.removeIf(role -> 
            HarpyModLoaderConfig.HANDLER.instance().disabled.contains(role.identifier().toString()) ||
            !filter.test(role));

        // 构建权重映射
        HashMap<Role, Float> roleWeights = new HashMap<>();
        for (Role role : availableRoles) {
            float weight = 1f;
            if (HarpyModLoaderConfig.HANDLER.instance().useCustomRoleWeights) {
                weight = ModdedWeights.getRoleWeight(role);
                if (weight <= 0) continue;
            }
            roleWeights.put(role, weight);
        }

        // 构建计数映射
        Map<Identifier, Integer> countMap = new HashMap<>();
        for (Role role : availableRoles) {
            countMap.put(role.identifier(), Harpymodloader.ROLE_MAX.getOrDefault(role.identifier(), 1));
        }

        return new RoleAssignmentPool(poolName, new WeightedUtil<>(roleWeights), countMap);
    }

    /**
     * 从池中选择一个角色
     * 
     * @return 选中的角色，如果池为空则返回null
     */
    public Role selectRole() {
        return selectRoleWithCountCheck();
    }

    /**
     * 从池中批量选择角色
     * 
     * @param count 要选择的角色数量
     * @return 选中的角色列表
     */
    public List<Role> selectRoles(int count) {
        List<Role> selected = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Role role = selectRole();
            if (role != null) {
                selected.add(role);
            }
        }
        return selected;
    }

    /**
     * 检查池中是否还有可用的角色
     */
    public boolean isEmpty() {
        return roleWeights.isEmpty();
    }

    /**
     * 获取池中剩余的角色数量
     */
    public int getRemainingCount() {
        return roleCountMap.values().stream().mapToInt(Integer::intValue).sum();
    }

    /**
     * 获取池的名称
     */
    public String getPoolName() {
        return poolName;
    }

    /**
     * 内部方法：根据权重和计数限制选择角色
     */
    private Role selectRoleWithCountCheck() {
        if (isEmpty()) {
            return null;
        }

        Role selectedRole = roleWeights.selectRandomKeyBasedOnWeights();
        if (selectedRole == null) {
            return null;
        }

        int remainingCount = roleCountMap.getOrDefault(selectedRole.identifier(), 1);
        if (remainingCount > 0) {
            roleCountMap.put(selectedRole.identifier(), remainingCount - 1);
            if (remainingCount - 1 <= 0) {
                roleWeights.removeKey(selectedRole);
            }
            return selectedRole;
        } else {
            roleWeights.removeKey(selectedRole);
            return selectRoleWithCountCheck();
        }
    }
}
