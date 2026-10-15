package dev.boltmc;

/** The cosmetic slots Bolt Mc offers. Every one of them has its own on/off switch and color. */
public enum Cosmetic {
    WINGS("Wings", "Two small wings on your back.", "#FFFFFF"),
    CAPE("Cape", "A simple cape down your back.", "#FFFFFF"),
    HAT("Hat", "A small block on top of your head.", "#FFFFFF"),
    AURA("Aura", "Colored particles drifting around you.", "#55C8FF");

    public final String label;
    public final String desc;
    public final String defaultColor;

    Cosmetic(String label, String desc, String defaultColor) {
        this.label = label;
        this.desc = desc;
        this.defaultColor = defaultColor;
    }
}
