package dev.bluemark.mixin;

import dev.bluemark.BlueMarkClient;
import dev.bluemark.BlueMarkConfig;
import dev.bluemark.NameDecorator;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Nametag above the head (and anything else that uses the player's display name). */
@Mixin(value = Player.class, remap = false)
public abstract class PlayerDisplayNameMixin {

    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void bluemark$addMark(CallbackInfoReturnable<Component> cir) {
        BlueMarkConfig cfg = BlueMarkConfig.get();
        if (!cfg.enabled || !cfg.showOnNametag) return;

        Player self = (Player) (Object) this;
        String myName = Minecraft.getInstance().getUser().getName();
        boolean me = self.getName().getString().equals(myName);
        if (!me && !cfg.allPlayers) return;

        Component original = cir.getReturnValue();
        if (original == null || original.getString().startsWith(cfg.glyph())) return;

        BlueMarkClient.hit("nametag");
        cir.setReturnValue(Component.empty().append(NameDecorator.icon(cfg)).append(original));
    }
}
