package dev.bluemark;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

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
    public String color = "#1D6AFF";
    public String icon = "bolt";
    /** Icon in the nametag above heads. */
    public boolean showOnNametag = true;
    /** Show your own nametag (visible in third person, F5). */
    public boolean showOwnNametag = true;
    public boolean showInChat = true;
    public boolean showInTab = true;

    // ---- online sharing (needs a BlueMark server, see server/README.md) ----
    /**
     * The sharing server every copy of the mod talks to. Put the address of YOUR deployed server here
     * (e.g. "https://bluemark.yourname.workers.dev") before building, so all players use the same one.
     */
    public static final String DEFAULT_SERVER_URL = "";

    /** Optional override in config/bluemark.json (not shown in the settings screen). Empty = DEFAULT_SERVER_URL. */
    public String serverUrl = "";
    /** Upload my icon so other BlueMark users can see it. */
    public boolean shareMark = true;
    /** Show the icons other BlueMark users picked. */
    public boolean showOthers = true;
    /** Random secret that proves to the server that updates for my UUID come from me. Keep it private. */
    public String secret = "";
    /** True while the server holds my icon (so it can be removed when sharing is turned off). */
    public boolean sharedOnServer = false;

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

    /** Index of an icon name in {@link #ICONS} (0 if unknown). */
    public static int iconIndex(String name) {
        int i = name == null ? 0 : ICONS.indexOf(name.toLowerCase(Locale.ROOT).trim());
        return Math.max(i, 0);
    }

    /** The private-use character that the font maps to the chosen icon. */
    public String glyph() {
        return mark().glyph();
    }

    /** Icon name normalized to one of {@link #ICONS}. */
    public String iconName() {
        return ICONS.get(iconIndex(icon));
    }

    /** My own mark (icon + color). */
    public Mark mark() {
        return new Mark(iconName(), rgb());
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
