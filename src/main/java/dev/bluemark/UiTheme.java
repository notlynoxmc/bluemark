package dev.bluemark;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Colors and tiny drawing helpers for the dark "Essential-style" settings theme. */
final class UiTheme {
    private UiTheme() {}

    static final int BG = 0xFF151515;
    static final int PANEL = 0xFF1B1B1B;
    static final int SIDEBAR = 0xFF181818;
    static final int HEADER = 0xFF222222;
    static final int CARD = 0xFF252525;
    static final int CARD_HOVER = 0xFF2B2B2B;
    static final int FIELD = 0xFF2A2A2A;
    static final int BORDER = 0xFF3A3A3A;
    static final int LINE = 0xFF2C2C2C;
    static final int TEXT = 0xFFFFFFFF;
    static final int SUB = 0xFF8E8E8E;
    static final int BLUE = 0xFF1A73FF;

    /** Legacy formatting code for bold text (works inside plain strings). */
    static final String BOLD = "§l";

    static void box(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int fill, int border) {
        g.fill(x1, y1, x2, y2, border);
        g.fill(x1 + 1, y1 + 1, x2 - 1, y2 - 1, fill);
    }

    static boolean inside(int mx, int my, int x1, int y1, int x2, int y2) {
        return mx >= x1 && mx < x2 && my >= y1 && my < y2;
    }

    /** Essential-style switch, 20x10: blue frame, dark half + blue half with a small "I" when on. */
    static void toggle(GuiGraphicsExtractor g, int x, int y, boolean on) {
        box(g, x, y, x + 20, y + 10, 0xFF0E0E0E, on ? BLUE : 0xFF5A5A5A);
        if (on) {
            g.fill(x + 10, y + 1, x + 19, y + 9, BLUE);
            g.fill(x + 14, y + 3, x + 15, y + 7, 0xFFFFFFFF);
        } else {
            g.fill(x + 1, y + 1, x + 10, y + 9, 0xFF5A5A5A);
        }
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
