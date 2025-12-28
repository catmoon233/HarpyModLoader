package org.agmas.harpymodloader.modded_murder;

import dev.doctor4t.wathe.api.Role;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ModdedWeights {
    public static Map<Role, HashMap<UUID, Integer>> roleWeights = new HashMap<>();
    public static Map<Role, HashMap<UUID, Integer>> roleRounds = new HashMap<>();

    public static void init() {}
}
