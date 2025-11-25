package org.agmas.harpymodloader.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import dev.doctor4t.trainmurdermystery.api.Role;
import dev.doctor4t.trainmurdermystery.api.TMMRoles;
import dev.doctor4t.trainmurdermystery.cca.GameWorldComponent;
import dev.doctor4t.trainmurdermystery.client.gui.LobbyPlayersRenderer;
import dev.doctor4t.trainmurdermystery.client.gui.RoleAnnouncementTexts;
import dev.doctor4t.trainmurdermystery.game.GameFunctions;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.client.HarpymodloaderClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.awt.*;


@Mixin(LobbyPlayersRenderer.class)
public abstract class LoadedRolesMixin {

    @Inject(method = "renderHud", at = @At(value = "INVOKE", target = "Ldev/doctor4t/ratatouille/util/TextUtils;getWithLineBreaks(Lnet/minecraft/text/Text;)Ljava/util/List;", shift = At.Shift.BEFORE))
    private static void b(TextRenderer renderer, ClientPlayerEntity player, DrawContext context, CallbackInfo ci, @Local(ordinal = 1) LocalRef<MutableText> text) {
        GameWorldComponent gameWorldComponent = (GameWorldComponent) GameWorldComponent.KEY.get(MinecraftClient.getInstance().player.getWorld());
        if (HarpymodloaderClient.rainbowRoleTime >= 60*(TMMRoles.ROLES.size()-1)) {
            HarpymodloaderClient.rainbowRoleTime = 0;
        }
        Role role1 = TMMRoles.ROLES.get((int)Math.floor(HarpymodloaderClient.rainbowRoleTime/60));
        Role role2 = TMMRoles.ROLES.get((int)Math.ceil(HarpymodloaderClient.rainbowRoleTime/60));

        Color start = new Color(role1.color());
        Color end = new Color(role2.color());

        int red = MathHelper.lerp((HarpymodloaderClient.rainbowRoleTime%60)/60, start.getRed(), end.getRed());
        int green = MathHelper.lerp((HarpymodloaderClient.rainbowRoleTime%60)/60, start.getGreen(), end.getGreen());
        int blue = MathHelper.lerp((HarpymodloaderClient.rainbowRoleTime%60)/60, start.getBlue(), end.getBlue());
        Color mix = new Color(red,green,blue);

        Text literal = Text.literal("\nHarpyModLoader - " + (TMMRoles.ROLES.size()-Harpymodloader.VANNILA_ROLES.size()) + " custom roles loaded!").withColor(mix.getRGB());

        text.set(text.get().withColor(-1).append(literal).withColor(mix.getRGB()));
    }
}
