package org.agmas.harpymodloader.events;


import dev.doctor4t.trainmurdermystery.api.Role;
import dev.doctor4t.trainmurdermystery.api.RoleMethodDispatcher;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;
public interface ModdedRoleAssigned {

    Event<ModdedRoleAssigned> EVENT = createArrayBacked(ModdedRoleAssigned.class, listeners -> (player, role) -> {
        for (ModdedRoleAssigned listener : listeners) {
            listener.assignModdedRole(player, role);
            if (player instanceof ServerPlayerEntity serverPlayer) {
                RoleMethodDispatcher.onInit(role,serverPlayer.getServer(), serverPlayer);
            }
        }
    });

    void assignModdedRole(PlayerEntity player, Role role);
}