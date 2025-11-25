package org.agmas.harpymodloader.client;

import dev.doctor4t.trainmurdermystery.api.Role;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public class HarpymodloaderClient implements ClientModInitializer {

    public static float rainbowRoleTime = 0;
    public static Role hudRole = null;
    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register((t)->{
            rainbowRoleTime += 1;
        });
    }
}
