package org.agmas.harpymodloader.events;


import dev.doctor4t.trainmurdermystery.api.Role;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.entity.player.PlayerEntity;
import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

import java.util.Map;
public interface OnGamePlayerRolesConfirm {

    Event<OnGamePlayerRolesConfirm> EVENT = createArrayBacked(OnGamePlayerRolesConfirm.class, listeners -> (roleAssignments) -> {
        for (OnGamePlayerRolesConfirm listener : listeners) {
            listener.beforeAssignRole(roleAssignments);
        }
    });

    void beforeAssignRole(Map<PlayerEntity, Role> roleAssignments);
}