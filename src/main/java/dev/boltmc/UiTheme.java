package dev.boltmc;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Colors and tiny drawing helpers for the white Bolt Mc settings theme. */
final class UiTheme {
    private UiTheme() {}

    static final int BG = 0xFFF2F3F5;
    static final int PANEL = 0xFFFFFFFF;
    static final int SIDEBAR = 0xFFF7F8FA;
    static final int HEADER = 0xFFFFFFFF;
    static final int CARD = 0xFFFFFFFF;
    static final int CARD_HOVER = 0xFFF2F6FF;
    static final int FIELD = 0xFFF0F1F3;
    static final int BORDER = 0xFFE1E3E8;
    static final int LINE = 0xFFE7E8EC;
    static final int TEXT = 0xFF17181C;
    static final int SUB = 0xFF80838C;
    static final int BLUE = 0xFF2F6BFF;

    /** Legacy formatting code for bold text (works inside plain strings). */
    static final String BOLD = "§l";

    static void box(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int fill, int border) {
        g.fill(x1, y1, x2, y2, border);
        g.fill(x1 + 1, y1 + 1, x2 - 1, y2 - 1, fill);
    }

    static boolean inside(int mx, int my, int x1, int y1, int x2, int y2) {
        return mx >= x1 && mx < x2 && my >= y1 && my < y2;
    }

    /** Blends two opaque colors: t = 0 gives a, t = 1 gives b. */
    static int mix(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int gr = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | (r << 16) | (gr << 8) | bl;
    }

    /** Switch, 20x10. t = 0 (off) .. 1 (on): the block slides right and turns blue. */
    static void toggle(GuiGraphicsExtractor g, int x, int y, float t) {
        int track = mix(0xFFD6D8DD, BLUE, t);
        box(g, x, y, x + 20, y + 10, track, track);
        int kx = x + 1 + Math.round(9 * t);
        g.fill(kx, y + 1, kx + 9, y + 9, 0xFFFFFFFF);
    }

    /** Small magnifier icon for the search box. */
    static void magnifier(GuiGraphicsExtractor g, int x, int y, int color) {
        g.fill(x + 1, y, x + 5, y + 1, color);
        g.fill(x + 1, y + 5, x + 5, y + 6, color);
        g.fill(x, y + 1, x + 1, y + 5, color);
        g.fill(x + 5, y + 1, x + 6, y + 5, color);
        g.fill(x + 6, y + 6, x + 7, y + 7, color);
        g.fill(x + 7, y + 7, x + 8, y + 8, color);
    }
}
