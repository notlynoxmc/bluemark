package dev.bluemark.mixin;

import dev.bluemark.BlueMarkConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerDisplayNameMixin {

    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void bluemark$addMark(CallbackInfoReturnable<Component> cir) {
        BlueMarkConfig cfg = BlueMarkConfig.get();
        if (!cfg.enabled) return;

        Player self = (Player) (Object) this;
        String myName = Minecraft.getInstance().getUser().getName();

        if (cfg.allPlayers || self.getName().getString().equals(myName)) {
            Component mark = Component.literal(cfg.glyph() + " ")
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(cfg.rgb())));
            cir.setReturnValue(Component.empty().append(mark).append(cir.getReturnValue()));
        }
    }
}
