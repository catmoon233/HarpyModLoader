package org.agmas.harpymodloader.events;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

import java.util.List;

import dev.doctor4t.trainmurdermystery.cca.GameWorldComponent;

public interface GameInitializeEvent {

    Event<GameInitializeEvent> EVENT = createArrayBacked(GameInitializeEvent.class,
            listeners -> (serverWorld, gameWorldComponent, players) -> {
                for (GameInitializeEvent listener : listeners) {
                    listener.initializeGame(serverWorld, gameWorldComponent, players);
                }
            });

    void initializeGame(ServerWorld serverWorld, GameWorldComponent gameWorldComponent,
            List<ServerPlayerEntity> players);
}