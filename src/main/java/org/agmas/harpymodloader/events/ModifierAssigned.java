package org.agmas.harpymodloader.events;

import dev.doctor4t.wathe.api.Role;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.entity.player.PlayerEntity;
import org.agmas.harpymodloader.modifiers.Modifier;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

public interface ModifierAssigned {

    Event<ModifierAssigned> EVENT = createArrayBacked(ModifierAssigned.class, listeners -> (player, modifer) -> {
        for (ModifierAssigned listener : listeners) {
            listener.assignModifier(player, modifer);
        }
    });

    void assignModifier(PlayerEntity player, Modifier modifier);
}