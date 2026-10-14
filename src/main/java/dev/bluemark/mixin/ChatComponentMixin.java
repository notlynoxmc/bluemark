package dev.bluemark.mixin;

import dev.bluemark.BlueMarkClient;
import dev.bluemark.BlueMarkConfig;
import dev.bluemark.MarkSync;
import dev.bluemark.NameDecorator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Icons in front of names inside chat messages (player chat + system messages). */
@Mixin(value = ChatComponent.class, remap = false)
public abstract class ChatComponentMixin {

    @ModifyVariable(
            method = {"addPlayerMessage", "addClientSystemMessage", "addServerSystemMessage"},
            at = @At("HEAD"), argsOnly = true, remap = false, require = 0)
    private Component bluemark$chat(Component message) {
        BlueMarkConfig cfg = BlueMarkConfig.get();
        if (!cfg.enabled || !cfg.showInChat) return message;

        Component decorated = NameDecorator.decorate(message, Minecraft.getInstance().getUser().getName(), cfg.mark());
        if (cfg.showOthers) decorated = MarkSync.decorateOthers(decorated);
        if (decorated != message) {
            BlueMarkClient.hit("chat");
        }
        return decorated;
    }
}
