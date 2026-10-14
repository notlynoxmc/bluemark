package dev.bluemark;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Settings live in  .minecraft/config/bluemark.json
 * You can edit the file by hand (it is re-read about once per second)
 * or use the settings screen in Mod Menu.
 */
public final class BlueMarkConfig {

    /** Icon names, in the same order as the glyphs U+E000, U+E001, ... in font/default.json */
    public static final List<String> ICONS =
            List.of("cross", "heart", "star", "crown", "diamond", "bolt", "custom");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // ---- values stored in the JSON file ----
    public boolean enabled = true;
    /** Icon above the heads of OTHER players too (only on your screen). */
    public boolean allPlayers = false;
    public String color = "#1D6AFF";
    public String icon = "cross";
    /** Icon in the nametag above heads. */
    public boolean showOnNametag = true;
    /** Show your own nametag (visible in third person, F5). */
    public boolean showOwnNametag = true;
    public boolean showInChat = true;
    public boolean showInTab = true;

    // ---- cache ----
    private static BlueMarkConfig cached = new BlueMarkConfig();
    private static long lastCheck = 0;
    private static long lastModified = -1;

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("bluemark.json");
    }

    public static synchronized BlueMarkConfig get() {
        long now = System.currentTimeMillis();
        if (now - lastCheck < 1000) return cached;
        lastCheck = now;
        try {
            Path p = file();
            if (!Files.exists(p)) {
                Files.writeString(p, GSON.toJson(cached));
            }
            long mod = Files.getLastModifiedTime(p).toMillis();
            if (mod != lastModified) {
                lastModified = mod;
                BlueMarkConfig loaded = GSON.fromJson(Files.readString(p), BlueMarkConfig.class);
                if (loaded != null) cached = loaded;
            }
        } catch (Exception ignored) {
            // keep the previous values if the file is missing or has a typo
        }
        return cached;
    }

    /** Writes the current values to disk (used by the settings screen). */
    public static synchronized void save() {
        try {
            Path p = file();
            Files.writeString(p, GSON.toJson(cached));
            lastModified = Files.getLastModifiedTime(p).toMillis();
        } catch (Exception ignored) {
        }
    }

    /** The private-use character that the font maps to the chosen icon. */
    public String glyph() {
        int i = icon == null ? 0 : ICONS.indexOf(icon.toLowerCase().trim());
        if (i < 0) i = 0;
        return String.valueOf((char) (0xE000 + i));
    }

    /** "#RRGGBB" -> int. Falls back to blue if the text is not a valid color. */
    public int rgb() {
        try {
            String c = color.trim();
            if (c.startsWith("#")) c = c.substring(1);
            return Integer.parseInt(c, 16) & 0xFFFFFF;
        } catch (Exception e) {
            return 0x1D6AFF;
        }
    }
}
