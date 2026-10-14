package dev.bluemark;

/** One player's chosen icon + color. */
public record Mark(String icon, int rgb) {

    /** The private-use character that the font maps to this icon. */
    public String glyph() {
        return String.valueOf((char) (0xE000 + BlueMarkConfig.iconIndex(icon)));
    }

    public String hex() {
        return String.format("#%02X%02X%02X", (rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255);
    }
}
