package org.agmas.harpymodloader.modded_murder;



import dev.doctor4t.trainmurdermystery.api.Role;
import net.minecraft.util.Identifier;
import org.agmas.harpymodloader.config.HarpyModLoaderConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ModdedWeights {

    public static Map<Role, HashMap<UUID, Integer>> roleRounds = new HashMap<>();
    public static Map<Identifier,Float> getWeights(){
        return HarpyModLoaderConfig.HANDLER.instance().roleWeights;
    }
    public static float getRoleWeight(Role role){
        var customWeight = getWeights().get(role.identifier());
        if (customWeight != null && customWeight > 0) {
            return customWeight;
        }
        // 返回默认权重
        return 1.0f;
    }
    public static void init() {}
}