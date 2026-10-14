package dev.bluemark.mixin;

import dev.bluemark.BlueMarkClient;
import dev.bluemark.BlueMarkConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanilla never draws your own nametag. This lets it show (visible in third person, F5). */
@Mixin(value = LivingEntityRenderer.class, remap = false)
public abstract class LivingEntityRendererMixin {

    @Inject(method = "shouldShowName", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void bluemark$showOwnName(LivingEntity entity, double distanceSq, CallbackInfoReturnable<Boolean> cir) {
        BlueMarkConfig cfg = BlueMarkConfig.get();
        if (!cfg.enabled || !cfg.showOwnNametag || !cfg.showOnNametag) return;

        Minecraft mc = Minecraft.getInstance();
        if (entity == mc.player && !mc.options.hideGui) {
            BlueMarkClient.hit("own-nametag");
            cir.setReturnValue(true);
        }
    }
}
