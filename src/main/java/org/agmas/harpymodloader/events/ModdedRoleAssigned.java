package org.agmas.harpymodloader.events;

import dev.doctor4t.wathe.api.Role;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.entity.player.PlayerEntity;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;
public interface ModdedRoleAssigned {

    Event<ModdedRoleAssigned> EVENT = createArrayBacked(ModdedRoleAssigned.class, listeners -> (player, role) -> {
        for (ModdedRoleAssigned listener : listeners) {
            listener.assignModdedRole(player, role);
        }
    });

    void assignModdedRole(PlayerEntity player, Role role);
}