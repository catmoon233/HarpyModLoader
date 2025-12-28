package org.agmas.harpymodloader.mixin;

import dev.doctor4t.wathe.api.WatheGameModes;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.events.ModdedRoleRemoved;
import org.agmas.harpymodloader.events.ModifierRemoved;
import org.agmas.harpymodloader.events.ResetPlayerEvent;
import org.agmas.harpymodloader.modifiers.Modifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameFunctions.class)
public class ModifierAndRoleResetMixin {
    @Inject(method = "resetPlayer", at = @At("HEAD"))
    private static void a(ServerPlayerEntity player, CallbackInfo ci) {
        GameWorldComponent gameComponent = (GameWorldComponent)GameWorldComponent.KEY.get(player.getWorld());
        if (gameComponent.getRole(player) != null) {
            ModdedRoleRemoved.EVENT.invoker().removeModdedRole(player, gameComponent.getRole(player));
        }
        WorldModifierComponent worldModifierComponent = WorldModifierComponent.KEY.get(player.getWorld());
        for (Modifier modifier : worldModifierComponent.getModifiers(player)) {
            ModifierRemoved.EVENT.invoker().removeModifier(player, modifier);
        }
        ResetPlayerEvent.EVENT.invoker().resetPlayer(player);
    }

    @Inject(method = "initializeGame", at = @At("HEAD"))
    private static void b(ServerWorld serverWorld, CallbackInfo ci) {
        for (ServerPlayerEntity player : serverWorld.getPlayers()) {
            ResetPlayerEvent.EVENT.invoker().resetPlayer(player);
        }
    }
}
