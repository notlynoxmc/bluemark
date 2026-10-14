package dev.bluemark;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class BlueMarkScreen extends Screen {

    private final Screen parent;
    private final BlueMarkConfig cfg = BlueMarkConfig.get();

    private int colorLabelY;

    public BlueMarkScreen(Screen parent) {
        super(Component.literal("BlueMark"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = this.width / 2 - 155;
        int right = this.width / 2 + 5;
        int w = 150;
        int y = 50;
        int step = 24;

        // left column
        addRenderableWidget(toggle(left, y, w, "Mod", () -> cfg.enabled, v -> cfg.enabled = v));

        addRenderableWidget(Button.builder(iconLabel(), b -> {
            int i = BlueMarkConfig.ICONS.indexOf(String.valueOf(cfg.icon).toLowerCase().trim());
            cfg.icon = BlueMarkConfig.ICONS.get((i + 1) % BlueMarkConfig.ICONS.size());
            b.setMessage(iconLabel());
            BlueMarkConfig.save();
        }).bounds(left, y + step, w, 20).build());

        addRenderableWidget(toggle(left, y + 2 * step, w, "Nametag", () -> cfg.showOnNametag, v -> cfg.showOnNametag = v));
        addRenderableWidget(toggle(left, y + 3 * step, w, "My nametag (F5)", () -> cfg.showOwnNametag, v -> cfg.showOwnNametag = v));

        // right column
        addRenderableWidget(toggle(right, y, w, "Chat", () -> cfg.showInChat, v -> cfg.showInChat = v));
        addRenderableWidget(toggle(right, y + step, w, "Tab list", () -> cfg.showInTab, v -> cfg.showInTab = v));
        addRenderableWidget(toggle(right, y + 2 * step, w, "Other players", () -> cfg.allPlayers, v -> cfg.allPlayers = v));

        // color box
        int yColor = y + 4 * step + 12;
        this.colorLabelY = yColor - 11;
        EditBox colorBox = new EditBox(this.font, left, yColor, 310, 20, Component.literal("Color"));
        colorBox.setMaxLength(7);
        colorBox.setValue(cfg.color);
        colorBox.setResponder(text -> {
            String t = text.trim();
            if (t.matches("#?[0-9A-Fa-f]{6}")) {
                cfg.color = "#" + t.replace("#", "").toUpperCase();
                BlueMarkConfig.save();
            }
        });
        addRenderableWidget(colorBox);

        // done
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(this.width / 2 - 100, yColor + 34, 200, 20).build());
    }

    private Button toggle(int x, int y, int w, String name, BooleanSupplier get, Consumer<Boolean> set) {
        return Button.builder(label(name, get.getAsBoolean()), b -> {
            set.accept(!get.getAsBoolean());
            b.setMessage(label(name, get.getAsBoolean()));
            BlueMarkConfig.save();
        }).bounds(x, y, w, 20).build();
    }

    private static Component label(String name, boolean on) {
        return Component.literal(name + ": " + (on ? "ON" : "OFF"));
    }

    private Component iconLabel() {
        return Component.literal("Icon: ")
                .append(NameDecorator.icon(cfg))
                .append(Component.literal(String.valueOf(cfg.icon)));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        String title = "BlueMark settings";
        graphics.text(this.font, title, this.width / 2 - this.font.width(title) / 2, 22, 0xFFFFFFFF, true);
        graphics.text(this.font, "Color (hex, e.g. #1D6AFF):", this.width / 2 - 155, this.colorLabelY, 0xFFAAAAAA, true);
    }

    @Override
    public void onClose() {
        BlueMarkConfig.save();
        this.minecraft.setScreenAndShow(parent);
    }
}
