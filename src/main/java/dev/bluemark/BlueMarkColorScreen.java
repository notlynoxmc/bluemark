package dev.bluemark;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;

/** Color picker: click a color from the palette, or fine-tune with the Red/Green/Blue sliders. */
public class BlueMarkColorScreen extends Screen {

    /** 6 rows x 8 columns, like the "Basic colors" grid of the Windows color dialog. */
    private static final int[] PALETTE = {
            0xFF8080, 0xFFFF80, 0x80FF80, 0x00FF80, 0x80FFFF, 0x0080FF, 0xFF80C0, 0xFF80FF,
            0xFF0000, 0xFFFF00, 0x80FF00, 0x00FF40, 0x00FFFF, 0x0080C0, 0x8080C0, 0xFF00FF,
            0x804040, 0xFF8040, 0x00FF00, 0x008080, 0x004080, 0x8080FF, 0x800040, 0xFF0080,
            0x800000, 0xFF8000, 0x008000, 0x008040, 0x0000FF, 0x0000A0, 0x800080, 0x8000FF,
            0x400000, 0x804000, 0x004000, 0x004040, 0x000080, 0x000040, 0x400040, 0x400080,
            0x000000, 0x808000, 0x808040, 0x808080, 0x408080, 0xC0C0C0, 0x1D6AFF, 0xFFFFFF
    };
    private static final int COLUMNS = 8;
    private static final int CELL = 22;

    private final Screen parent;
    private final BlueMarkConfig cfg = BlueMarkConfig.get();

    private int r, g, b;
    private int px1, py1, px2, py2, gridX, gridY, panelX, sliderW;
    private ChannelSlider sliderR, sliderG, sliderB;

    public BlueMarkColorScreen(Screen parent) {
        super(Component.literal("BlueMark color"));
        this.parent = parent;
    }

    private int rgb() {
        return (r << 16) | (g << 8) | b;
    }

    private void applyToConfig() {
        cfg.color = String.format("#%02X%02X%02X", r, g, b);
    }

    @Override
    protected void init() {
        int rgb = cfg.rgb();
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

        // invisible hit areas for the palette; the colors are painted in extractRenderState
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
        BlueMarkConfig.save();
    }

    private void label(GuiGraphicsExtractor gfx, String s, int x, int y, int color) {
        gfx.text(this.font, s, x, y, color, false);
    }

    private void drawSlider(GuiGraphicsExtractor gfx, ChannelSlider s, int y, String name, int value, int channelColor) {
        label(gfx, UiTheme.BOLD + name + "  " + value, panelX, y, UiTheme.TEXT);
        UiTheme.box(gfx, panelX, y + 11, panelX + sliderW, y + 17, UiTheme.FIELD, UiTheme.BORDER);
        int knobX = panelX + 4 + (int) ((sliderW - 8) * (value / 255.0));
        gfx.fill(panelX + 1, y + 12, knobX, y + 16, channelColor);
        gfx.fill(knobX - 1, y + 9, knobX + 2, y + 19, 0xFFFFFFFF);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
        super.extractRenderState(gfx, mouseX, mouseY, delta);

        gfx.fill(0, 0, this.width, this.height, UiTheme.BG);
        UiTheme.box(gfx, px1, py1, px2, py2, UiTheme.PANEL, UiTheme.LINE);
        gfx.fill(px1 + 1, py1 + 1, px2 - 1, py1 + 28, UiTheme.HEADER);
        label(gfx, UiTheme.BOLD + "Pick a color", px1 + 16, py1 + 10, UiTheme.TEXT);

        int current = rgb();
        for (int i = 0; i < PALETTE.length; i++) {
            int cx = gridX + (i % COLUMNS) * CELL;
            int cy = gridY + (i / COLUMNS) * CELL;
            boolean selected = PALETTE[i] == current;
            gfx.fill(cx, cy, cx + 20, cy + 20, selected ? UiTheme.BLUE : UiTheme.BORDER);
            gfx.fill(cx + 1, cy + 1, cx + 19, cy + 19, 0xFF000000 | PALETTE[i]);
            if (selected) {
                gfx.fill(cx, cy, cx + 20, cy + 1, 0xFFFFFFFF);
                gfx.fill(cx, cy + 19, cx + 20, cy + 20, 0xFFFFFFFF);
                gfx.fill(cx, cy, cx + 1, cy + 20, 0xFFFFFFFF);
                gfx.fill(cx + 19, cy, cx + 20, cy + 20, 0xFFFFFFFF);
            }
        }

        drawSlider(gfx, sliderR, gridY, "Red", r, 0xFFE53935);
        drawSlider(gfx, sliderG, gridY + 30, "Green", g, 0xFF43A047);
        drawSlider(gfx, sliderB, gridY + 60, "Blue", b, 0xFF1E88E5);

        // preview
        int py = gridY + 92;
        UiTheme.box(gfx, panelX, py, panelX + sliderW, py + 22, 0xFF000000 | current, 0xFFFFFFFF);
        label(gfx, String.format("#%02X%02X%02X", r, g, b), panelX, py + 28, UiTheme.SUB);
        label(gfx, cfg.glyph() + " " + this.minecraft.getUser().getName(), panelX, py + 40, 0xFF000000 | current);

        // done button
        boolean hover = UiTheme.inside(mouseX, mouseY, px1 + 8, py2 - 22, px1 + 78, py2 - 6);
        UiTheme.box(gfx, px1 + 8, py2 - 22, px1 + 78, py2 - 6,
                hover ? UiTheme.CARD_HOVER : UiTheme.CARD, hover ? UiTheme.BLUE : UiTheme.BORDER);
        label(gfx, "< Done", px1 + 8 + (70 - this.font.width("< Done")) / 2, py2 - 18, UiTheme.TEXT);
    }

    @Override
    public void onClose() {
        applyToConfig();
        BlueMarkConfig.save();
        this.minecraft.setScreenAndShow(parent);
    }

    /** A 0-255 slider (its own drawing is hidden; the dark-theme bar is painted instead). */
    private static class ChannelSlider extends AbstractSliderButton {
        private final IntConsumer onChange;

        ChannelSlider(int x, int y, int w, int h, int initial, IntConsumer onChange) {
            super(x, y, w, h, Component.empty(), initial / 255.0);
            this.onChange = onChange;
        }

        void setInt(int v) {
            this.value = v / 255.0;
        }

        @Override
        protected void updateMessage() {
            // text is drawn by the screen
        }

        @Override
        protected void applyValue() {
            onChange.accept((int) Math.round(this.value * 255.0));
        }
    }
}
