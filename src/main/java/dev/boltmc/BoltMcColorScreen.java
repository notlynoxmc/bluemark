package dev.boltmc;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;

/** Color picker for one cosmetic slot: click a swatch, or fine-tune with the Red/Green/Blue sliders. */
public class BoltMcColorScreen extends Screen {

    private static final int[] PALETTE = {
            0xFFFFFF, 0xF2F3F5, 0xCFCFCF, 0x9AA0A8, 0x555555, 0x000000, 0x2F6BFF, 0x55C8FF,
            0xFF5A5A, 0xFF9A3D, 0xFFD23D, 0x7CE65A, 0x2FD6A4, 0x3DD9FF, 0xA06BFF, 0xFF6BD6,
            0x800000, 0x804000, 0x008000, 0x004080, 0x400080, 0x800040, 0x004040, 0x404000
    };
    private static final int COLUMNS = 8;
    private static final int CELL = 22;

    private final Screen parent;
    private final Cosmetic cosmetic;
    private final BoltMcConfig cfg = BoltMcConfig.get();

    private int r, g, b;
    private int px1, py1, px2, py2, gridX, gridY, panelX, sliderW;
    private ChannelSlider sliderR, sliderG, sliderB;

    public BoltMcColorScreen(Screen parent, Cosmetic cosmetic) {
        super(Component.literal("Bolt Mc color"));
        this.parent = parent;
        this.cosmetic = cosmetic;
    }

    private int rgb() { return (r << 16) | (g << 8) | b; }

    private void applyToConfig() {
        cfg.state(cosmetic).color = String.format("#%02X%02X%02X", r, g, b);
    }

    @Override
    protected void init() {
        int rgb = cfg.rgb(cosmetic);
        r = (rgb >> 16) & 255;
        g = (rgb >> 8) & 255;
        b = rgb & 255;

        px1 = 14;
        py1 = 14;
        px2 = this.width - 14;
        py2 = this.height - 14;
        gridX = px1 + 16;
        gridY = py1 + 40;
        panelX = gridX + COLUMNS * CELL + 16;
        sliderW = Math.max(90, px2 - 16 - panelX);

        for (int i = 0; i < PALETTE.length; i++) {
            int cx = gridX + (i % COLUMNS) * CELL;
            int cy = gridY + (i / COLUMNS) * CELL;
            final int color = PALETTE[i];
            addRenderableWidget(Button.builder(Component.empty(), btn -> pick(color))
                    .bounds(cx, cy, 20, 20).build());
        }

        sliderR = new ChannelSlider(panelX, gridY, sliderW, 20, r, v -> { r = v; applyToConfig(); });
        sliderG = new ChannelSlider(panelX, gridY + 30, sliderW, 20, g, v -> { g = v; applyToConfig(); });
        sliderB = new ChannelSlider(panelX, gridY + 60, sliderW, 20, b, v -> { b = v; applyToConfig(); });
        addRenderableWidget(sliderR);
        addRenderableWidget(sliderG);
        addRenderableWidget(sliderB);

        addRenderableWidget(Button.builder(Component.empty(), btn -> onClose())
                .bounds(px1 + 8, py2 - 22, 70, 16).build());
    }

    private void pick(int color) {
        r = (color >> 16) & 255;
        g = (color >> 8) & 255;
        b = color & 255;
        sliderR.setInt(r);
        sliderG.setInt(g);
        sliderB.setInt(b);
        applyToConfig();
        BoltMcConfig.save();
    }

    private void label(GuiGraphicsExtractor gfx, String s, int x, int y, int color) {
        gfx.text(this.font, s, x, y, color, false);
    }

    private void drawSlider(GuiGraphicsExtractor gfx, ChannelSlider s, int y, String name, int value, int channelColor) {
        label(gfx, UiTheme.BOLD + name + "  " + value, panelX, y, UiTheme.TEXT);
        UiTheme.box(gfx, panelX, y + 11, panelX + sliderW, y + 17, UiTheme.FIELD, UiTheme.BORDER);
        int knobX = panelX + 4 + (int) ((sliderW - 8) * (value / 255.0));
        gfx.fill(panelX + 1, y + 12, knobX, y + 16, channelColor);
        gfx.fill(knobX - 1, y + 9, knobX + 2, y + 19, UiTheme.TEXT);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
        super.extractRenderState(gfx, mouseX, mouseY, delta);

        gfx.fill(0, 0, this.width, this.height, UiTheme.BG);
        UiTheme.box(gfx, px1, py1, px2, py2, UiTheme.PANEL, UiTheme.LINE);
        gfx.fill(px1 + 1, py1 + 1, px2 - 1, py1 + 28, UiTheme.HEADER);
        label(gfx, UiTheme.BOLD + cosmetic.label + " color", px1 + 16, py1 + 10, UiTheme.TEXT);

        int current = rgb();
        for (int i = 0; i < PALETTE.length; i++) {
            int cx = gridX + (i % COLUMNS) * CELL;
            int cy = gridY + (i / COLUMNS) * CELL;
            boolean selected = PALETTE[i] == current;
            gfx.fill(cx, cy, cx + 20, cy + 20, selected ? UiTheme.BLUE : UiTheme.BORDER);
            gfx.fill(cx + 1, cy + 1, cx + 19, cy + 19, 0xFF000000 | PALETTE[i]);
        }

        drawSlider(gfx, sliderR, gridY, "Red", r, 0xFFE53935);
        drawSlider(gfx, sliderG, gridY + 30, "Green", g, 0xFF43A047);
        drawSlider(gfx, sliderB, gridY + 60, "Blue", b, 0xFF1E88E5);

        int py = gridY + 92;
        UiTheme.box(gfx, panelX, py, panelX + sliderW, py + 22, 0xFF000000 | current, UiTheme.BORDER);
        label(gfx, String.format("#%02X%02X%02X", r, g, b), panelX, py + 28, UiTheme.SUB);

        boolean hover = UiTheme.inside(mouseX, mouseY, px1 + 8, py2 - 22, px1 + 78, py2 - 6);
        UiTheme.box(gfx, px1 + 8, py2 - 22, px1 + 78, py2 - 6,
                hover ? UiTheme.CARD_HOVER : UiTheme.CARD, hover ? UiTheme.BLUE : UiTheme.BORDER);
        label(gfx, "< Done", px1 + 8 + (70 - this.font.width("< Done")) / 2, py2 - 18, UiTheme.TEXT);
    }

    @Override
    public void onClose() {
        applyToConfig();
        BoltMcConfig.save();
        this.minecraft.setScreenAndShow(parent);
    }

    private static class ChannelSlider extends AbstractSliderButton {
        private final IntConsumer onChange;

        ChannelSlider(int x, int y, int w, int h, int initial, IntConsumer onChange) {
            super(x, y, w, h, Component.empty(), initial / 255.0);
            this.onChange = onChange;
        }

        void setInt(int v) { this.value = v / 255.0; }

        @Override
        protected void updateMessage() { }

        @Override
        protected void applyValue() {
            onChange.accept((int) Math.round(this.value * 255.0));
        }
    }
}
