package dev.bluemark.mixin;

import dev.bluemark.BlueMarkClient;
import dev.bluemark.BlueMarkConfig;
import dev.bluemark.MarkSync;
import dev.bluemark.NameDecorator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Icons in the Tab player list: yours and other BlueMark users'. */
@Mixin(value = PlayerTabOverlay.class, remap = false)
public abstract class PlayerTabOverlayMixin {

    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void bluemark$tabName(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
        BlueMarkConfig cfg = BlueMarkConfig.get();
        if (!cfg.enabled || !cfg.showInTab) return;

        Component original = cir.getReturnValue();
        Component decorated = NameDecorator.decorate(original, Minecraft.getInstance().getUser().getName(), cfg.mark());
        if (cfg.showOthers) decorated = MarkSync.decorateOthers(decorated);
        if (decorated != original) {
            BlueMarkClient.hit("tab-list");
            cir.setReturnValue(decorated);
        }
    }
}
