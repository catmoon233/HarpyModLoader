
package org.agmas.harpymodloader.client.mixin;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.WatheClient;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.awt.*;

@Mixin(WatheClient.class)
public abstract class InstinctMixin {


    @Shadow public static KeyBinding instinctKeybind;


    @Inject(method = "getInstinctHighlight", at = @At("HEAD"), cancellable = true)
    private static void b(Entity target, CallbackInfoReturnable<Integer> cir) {
        GameWorldComponent gameWorldComponent = (GameWorldComponent) GameWorldComponent.KEY.get(MinecraftClient.getInstance().player.getWorld());
        if (target instanceof PlayerEntity && WatheClient.isInstinctEnabled()) {
            if (!(target).isSpectator()) {
                if (GameFunctions.isPlayerSpectatingOrCreative(MinecraftClient.getInstance().player)) {
                    Role role = gameWorldComponent.getRole((PlayerEntity) target);
                    if (role == null) {
                        cir.setReturnValue(WatheRoles.CIVILIAN.color());
                    } else {
                        cir.setReturnValue(role.color());
                    }
                }
            }
        }
    }
}
