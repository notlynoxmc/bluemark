package dev.boltmc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Settings live in  .minecraft/config/boltmc.json
 * Re-read about once per second, so hand edits are picked up without a restart.
 */
public final class BoltMcConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // ---- values stored in the JSON file ----
    public boolean enabled = true;

    /** One entry per {@link Cosmetic}: "on" (true/false) and "color" (#RRGGBB). */
    public Map<String, CosmeticState> cosmetics = defaultCosmetics();

    public static final class CosmeticState {
        public boolean on;
        public String color;
        CosmeticState(boolean on, String color) { this.on = on; this.color = color; }
    }

    private static Map<String, CosmeticState> defaultCosmetics() {
        Map<String, CosmeticState> m = new LinkedHashMap<>();
        for (Cosmetic c : Cosmetic.values()) {
            m.put(c.name(), new CosmeticState(c == Cosmetic.AURA, c.defaultColor));
        }
        return m;
    }

    // ---- friends & chat (needs a Bolt Mc relay server, see server/README.md) ----
    /** The relay every copy of the mod talks to. Set before building, see server/README.md. */
    public static final String DEFAULT_SERVER_URL = "";
    /** Optional override in config/boltmc.json. Empty = DEFAULT_SERVER_URL. */
    public String serverUrl = "";
    /** My install's private key. The server uses it to prove messages addressed to me are really for me. */
    public String secret = "";
    public List<Friend> friends = new ArrayList<>();

    public static final class Friend {
        public String name; // as typed
        public String id;   // lowercase name - the key used by the relay (works for original and cracked accounts)
        public Friend() {}
        public Friend(String name, String id) { this.name = name; this.id = id; }
    }

    // ---- cache ----
    private static BoltMcConfig cached = new BoltMcConfig();
    private static long lastCheck = 0;
    private static long lastModified = -1;

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("boltmc.json");
    }

    public static synchronized BoltMcConfig get() {
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
                BoltMcConfig loaded = GSON.fromJson(Files.readString(p), BoltMcConfig.class);
                if (loaded != null) {
                    // fill in any cosmetic slot missing from an older save
                    for (Cosmetic c : Cosmetic.values()) {
                        loaded.cosmetics.computeIfAbsent(c.name(), k -> new CosmeticState(false, c.defaultColor));
                    }
                    if (loaded.friends == null) loaded.friends = new ArrayList<>();
                    loaded.friends.removeIf(f -> f == null || f.id == null || f.name == null);
                    cached = loaded;
                }
            }
        } catch (Exception ignored) {
            // keep the previous values if the file is missing or has a typo
        }
        return cached;
    }

    public static synchronized void save() {
        try {
            Path p = file();
            Files.writeString(p, GSON.toJson(cached));
            lastModified = Files.getLastModifiedTime(p).toMillis();
        } catch (Exception ignored) {
        }
    }

    public CosmeticState state(Cosmetic c) {
        return cosmetics.computeIfAbsent(c.name(), k -> new CosmeticState(false, c.defaultColor));
    }

    public boolean on(Cosmetic c) {
        return enabled && state(c).on;
    }

    /** 0xRRGGBB. Falls back to the cosmetic's default if the text is not a valid color. */
    public int rgb(Cosmetic c) {
        try {
            String s = state(c).color.trim();
            if (s.startsWith("#")) s = s.substring(1);
            return Integer.parseInt(s, 16) & 0xFFFFFF;
        } catch (Exception e) {
            String s = c.defaultColor.substring(1);
            return Integer.parseInt(s, 16);
        }
    }

}
