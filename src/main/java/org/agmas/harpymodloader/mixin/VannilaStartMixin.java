package org.agmas.harpymodloader.mixin;

import dev.doctor4t.trainmurdermystery.api.GameMode;

import dev.doctor4t.trainmurdermystery.api.TMMGameModes;
import dev.doctor4t.trainmurdermystery.cca.AutoStartComponent;
import dev.doctor4t.trainmurdermystery.cca.GameWorldComponent;
import dev.doctor4t.trainmurdermystery.command.StartCommand;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.world.World;
import org.agmas.harpymodloader.Harpymodloader;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(StartCommand.class)
public class VannilaStartMixin {

    @Inject(method = "execute", at = @At("HEAD"))
    private static void a(ServerCommandSource source, GameMode gameMode, int minutes, CallbackInfoReturnable<Integer> cir) {
        if (gameMode.equals(TMMGameModes.MURDER)) Harpymodloader.wantsToStartVannila = true;
     }
}
