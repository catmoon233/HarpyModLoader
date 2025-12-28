package org.agmas.harpymodloader.events;

import dev.doctor4t.wathe.api.Role;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.entity.player.PlayerEntity;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

public interface ModdedRoleRemoved {

    Event<ModdedRoleRemoved> EVENT = createArrayBacked(ModdedRoleRemoved.class, listeners -> (player, role) -> {
        for (ModdedRoleRemoved listener : listeners) {
            listener.removeModdedRole(player, role);
        }
    });

    void removeModdedRole(PlayerEntity player, Role role);
}