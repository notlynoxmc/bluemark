package dev.boltmc.mixin;

import dev.boltmc.cosmetics.CosmeticLayer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Registers {@link CosmeticLayer} on the player renderer, right after vanilla sets its own layers up. */
@Mixin(value = PlayerRenderer.class, remap = false)
public abstract class PlayerRendererMixin extends net.minecraft.client.renderer.entity.LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    public PlayerRendererMixin(EntityRendererProvider.Context context, PlayerModel<AbstractClientPlayer> model, float shadowRadius) {
        super(context, model, shadowRadius);
    }

    @Inject(method = "<init>", at = @At("TAIL"), remap = false, require = 0)
    private void boltmc$addCosmeticLayer(EntityRendererProvider.Context context, boolean slim, CallbackInfo ci) {
        @SuppressWarnings({"unchecked", "rawtypes"})
        RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> self =
                (RenderLayerParent) this;
        this.addLayer(new CosmeticLayer(self));
    }
}
