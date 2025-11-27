package org.agmas.harpymodloader.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.doctor4t.trainmurdermystery.api.TMMRoles;
import dev.doctor4t.trainmurdermystery.cca.GameWorldComponent;
import dev.doctor4t.trainmurdermystery.client.TMMClient;
import dev.doctor4t.trainmurdermystery.client.gui.RoleNameRenderer;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.agmas.harpymodloader.client.HarpymodloaderClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(RoleNameRenderer.class)
public abstract class CustomRolesRoleNameRendererMixin {

    @Shadow private static float nametagAlpha;

    @Inject(method = "renderHud", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;III)I", ordinal = 0))
    private static void b(TextRenderer renderer, ClientPlayerEntity player, DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
         GameWorldComponent gameWorldComponent = (GameWorldComponent) GameWorldComponent.KEY.get(player.getWorld());
         if (HarpymodloaderClient.hudRole != null) {
             if (TMMClient.isPlayerSpectatingOrCreative()) {
                 Text name = Text.translatable("announcement.role." + HarpymodloaderClient.hudRole.identifier().getPath());
                 context.drawTextWithShadow(renderer, name, -renderer.getWidth(name) / 2, 0, HarpymodloaderClient.hudRole.color() | (int) (nametagAlpha * 255.0F) << 24);
             }
         }
    }
    @Inject(method = "renderHud", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;getDisplayName()Lnet/minecraft/text/Text;"))
    private static void b(TextRenderer renderer, ClientPlayerEntity player, DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci, @Local PlayerEntity target) {
        GameWorldComponent gameWorldComponent = (GameWorldComponent) GameWorldComponent.KEY.get(player.getWorld());
        if (gameWorldComponent.getRole(target) != null) {
            HarpymodloaderClient.hudRole = gameWorldComponent.getRole(target);
        } else {
            HarpymodloaderClient.hudRole = TMMRoles.CIVILIAN;
        }
    }
}
