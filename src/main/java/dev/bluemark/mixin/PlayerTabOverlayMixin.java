package dev.bluemark.mixin;

import dev.bluemark.BlueMarkClient;
import dev.bluemark.BlueMarkConfig;
import dev.bluemark.NameDecorator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Icon in the Tab player list (only your own name). */
@Mixin(value = PlayerTabOverlay.class, remap = false)
public abstract class PlayerTabOverlayMixin {

    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void bluemark$tabName(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
        BlueMarkConfig cfg = BlueMarkConfig.get();
        if (!cfg.enabled || !cfg.showInTab) return;

        Component original = cir.getReturnValue();
        Component decorated = NameDecorator.decorate(original, Minecraft.getInstance().getUser().getName(), cfg);
        if (decorated != original) {
            BlueMarkClient.hit("tab-list");
            cir.setReturnValue(decorated);
        }
    }
}
