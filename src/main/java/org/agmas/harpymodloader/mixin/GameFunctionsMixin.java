package org.agmas.harpymodloader.mixin;

import dev.doctor4t.trainmurdermystery.api.TMMGameModes;
import dev.doctor4t.trainmurdermystery.cca.GameWorldComponent;
import dev.doctor4t.trainmurdermystery.game.GameFunctions;
import net.minecraft.server.world.ServerWorld;
import org.agmas.harpymodloader.Harpymodloader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameFunctions.class)
public class GameFunctionsMixin {
    @Inject(method = "initializeGame", at = @At("HEAD"))
    private static void a(ServerWorld serverWorld, CallbackInfo ci) {
        GameWorldComponent gameComponent = (GameWorldComponent)GameWorldComponent.KEY.get(serverWorld);
        if (gameComponent.getGameMode().equals(TMMGameModes.MURDER) && !Harpymodloader.wantsToStartVannila) {
            gameComponent.setGameMode(Harpymodloader.MODDED_GAMEMODE);
        }
        Harpymodloader.wantsToStartVannila = false;
    }
}
