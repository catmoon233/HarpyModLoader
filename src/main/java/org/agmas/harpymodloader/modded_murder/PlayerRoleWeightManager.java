package org.agmas.harpymodloader.modded_murder;

import java.util.HashMap;
import java.util.UUID;

import dev.doctor4t.trainmurdermystery.api.Role;
import net.minecraft.entity.player.PlayerEntity;

public class PlayerRoleWeightManager {
    public static HashMap<UUID, WeightInfo> playerWeights = new HashMap<>();

    public static double getRoleWeightPercent(UUID player, int type) {
        var weightManager = PlayerRoleWeightManager.playerWeights.get(player);
        if (weightManager == null) {
            weightManager = new PlayerRoleWeightManager.WeightInfo();
            PlayerRoleWeightManager.playerWeights.putIfAbsent(player, weightManager);
        }
        int typeWeight = weightManager.getWeight(type);
        int total = weightManager.getWeightSum();
        if (total <= 0)
            total = 1;
        return 1. - (double) typeWeight / (double) total;
    }

    public static double getRoleWeightPercent(PlayerEntity playerEntity, int roleType) {
        return getRoleWeightPercent(playerEntity.getUuid(), roleType);
    }

    public static int getWeight(PlayerEntity player, int type) {
        return getWeight(player.getUuid(), type);
    }

    public static void resetWeight(PlayerEntity player) {
        resetWeight(player.getUuid());
    }

    public static void clearWeight(UUID player) {
        if (playerWeights.containsKey(player))
            playerWeights.remove(player);
    }

    public static void resetWeight(UUID player) {
        var weightManager = new PlayerRoleWeightManager.WeightInfo();
        PlayerRoleWeightManager.playerWeights.put(player, weightManager);
    }

    public static int getWeight(UUID player, int type) {
        var weightManager = PlayerRoleWeightManager.playerWeights.get(player);
        if (weightManager == null) {
            weightManager = new PlayerRoleWeightManager.WeightInfo();
            PlayerRoleWeightManager.playerWeights.putIfAbsent(player, weightManager);
        }
        return weightManager.getWeight(type);
    }

    public static void addWeight(PlayerEntity player, int type, int weightPlus) {
        addWeight(player.getUuid(), type, weightPlus);
    }

    public static void addWeight(UUID player, int type, int weightPlus) {
        var weightManager = PlayerRoleWeightManager.playerWeights.get(player);
        if (weightManager == null) {
            weightManager = new PlayerRoleWeightManager.WeightInfo();
            PlayerRoleWeightManager.playerWeights.putIfAbsent(player, weightManager);
        }
        if (weightManager.getWeight(type) >= 1000) {
            // 重置
            weightManager = new PlayerRoleWeightManager.WeightInfo();
            PlayerRoleWeightManager.playerWeights.put(player, weightManager);
        }
        weightManager.addWeight(type, weightPlus);
    }

    /**
     * Get Role type(int) for a role
     * 
     * @param role
     * @return - 0: Innocent and Cannot Use Killer
     *         - 1: Innocent but can Use Killer
     *         - 2: Neturals but not for killer
     *         - 3: Neturals for killer
     *         - 4: Killer
     */
    public static int getRoleType(Role role) {
        if (role == null)
            return -1;
        if (role.isInnocent()) {
            return 1;
        }

        if (role.isNeutrals() && !role.isNeutralForKiller()) {
            return 2;
        }
        if (role.isNeutrals() && role.isNeutralForKiller()) {
            return 3;
        }
        if (!role.isInnocent() && !role.canUseKiller() && !role.isNeutralForKiller()) {
            return 2;
        }
        if (!role.isInnocent() && !role.canUseKiller() && role.isNeutralForKiller()) {
            return 3;
        }
        if (role.canUseKiller()) {
            return 4;
        }
        return -1; // Unknown
    }

    public static int getRoleType_OnlyDistinctKiller(Role r) {
        int rt = getRoleType(r);
        if (rt == -1)
            return -1;
        if (rt <= 3) {
            return 1;
        }
        return rt;
    }

    public static int getRoleType_IgnoreNeutralType(Role r) {
        int rt = getRoleType(r);
        if (rt == -1)
            return -1;
        if (rt <= 1) {
            return 1;
        } else if (rt <= 3) {
            return 2;
        }
        return rt;
    }

    public static class WeightInfo {
        public int innocentWeight = 1;
        public int killerWeight = 1;
        public int neutralsWeight = 1;
        public int neutralsForKillerWeight = 1;

        public WeightInfo() {
        }

        public int getWeightSum() {
            return this.innocentWeight + this.killerWeight + this.neutralsForKillerWeight + this.neutralsWeight;
        }

        public void putInnocentWeight(int weight) {
            innocentWeight = weight;
        }

        public void putKillerWeight(int weight) {
            killerWeight = weight;
        }

        public void putNeutralsWeight(int weight) {
            neutralsWeight = weight;
        }

        public void putNeutralsForKillerWeight(int weight) {
            neutralsForKillerWeight = weight;
        }

        /**
         * 
         * @param type
         *               - 1: Innocent but can Use Killer
         *               - 2: Neturals but not for killer
         *               - 3: Neturals for killer
         *               - 4: Killer
         * @param weight
         */
        public void addWeight(int type, int weight) {
            if (type <= 1) {
                this.innocentWeight += weight;
                return;
            }
            if (type == 2) {
                this.neutralsWeight += (weight);
                return;
            }
            if (type == 3) {
                this.neutralsForKillerWeight += (weight);
                return;
            }
            if (type == 4) {
                this.killerWeight += (weight);
                return;
            }
        }

        /**
         * 
         * @param type
         *               - 1: Innocent but can Use Killer
         *               - 2: Neturals but not for killer
         *               - 3: Neturals for killer
         *               - 4: Killer
         * @param weight
         */
        public void putWeight(int type, int weight) {
            if (type <= 1) {
                putInnocentWeight(weight);
                return;
            }
            if (type == 2) {
                putNeutralsWeight(weight);
                return;
            }
            if (type == 3) {
                putNeutralsForKillerWeight(weight);
                return;
            }
            if (type == 4) {
                putKillerWeight(weight);
                return;
            }
        }

        /**
         * 
         * @param type
         *             - 1: Innocent but can Use Killer
         *             - 2: Neturals but not for killer
         *             - 3: Neturals for killer
         *             - 4: Killer
         */
        public int getWeight(int type) {
            if (type <= 1)
                return innocentWeight;
            if (type == 2) {
                return this.neutralsWeight;
            }
            if (type == 3) {
                return this.neutralsForKillerWeight;
            }
            if (type == 4) {
                return this.killerWeight;
            }
            return -1;
        }
    }

}
