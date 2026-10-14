package dev.bluemark;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Shares your icon with other BlueMark users and fetches theirs.
 *
 * Every 3 seconds (on the game thread) it
 *   1. uploads your icon to the BlueMark server when it changed,
 *   2. asks the server which icons the players in the current tab list picked.
 *
 * Players are matched by UUID on the server, but the names used to draw icons in chat / tab list
 * always come from your own game's player list, so nobody can put an icon on someone else's name.
 */
public final class MarkSync {
    private MarkSync() {}

    private static final int BATCH = 40;
    private static final long HIT_TTL_MS = 60_000;   // refresh known icons every minute
    private static final long MISS_TTL_MS = 90_000;  // ask again about players without an icon
    private static final long RETRY_MS = 15_000;     // wait after an error

    private record Entry(Mark mark, long at) {}

    private static final Map<UUID, Entry> CACHE = new ConcurrentHashMap<>();
    private static final Set<UUID> INFLIGHT = ConcurrentHashMap.newKeySet();
    private static volatile Map<String, Mark> byName = Map.of();
    private static volatile String status = "Not configured";

    private static volatile String lastSentKey = null;
    private static volatile boolean busyOwn = false;
    private static volatile long nextOwnAt = 0;
    private static volatile long nextLookupAt = 0;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    // ------------------------------------------------------------------ public API

    public static void start() {
        ScheduledExecutorService ex = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "BlueMark-sync");
            t.setDaemon(true);
            return t;
        });
        ex.scheduleWithFixedDelay(() -> {
            try {
                Minecraft.getInstance().execute(MarkSync::tickSafe);
            } catch (Throwable ignored) {
            }
        }, 3, 3, TimeUnit.SECONDS);
    }

    /** Short text for the settings screen ("Connected", "Server unreachable", ...). */
    public static String status() {
        return status;
    }

    /** The icon another player picked, or null. */
    public static Mark markFor(UUID id) {
        Entry e = CACHE.get(id);
        return e == null ? null : e.mark();
    }

    /** Icons of the other players that are online right now, by player name (sorted). */
    public static Map<String, Mark> visibleMarks() {
        return new TreeMap<>(byName);
    }

    /** Puts the icons of all known other players in front of their names in {@code input}. */
    public static Component decorateOthers(Component input) {
        Map<String, Mark> m = byName;
        if (m.isEmpty()) return input;
        Component out = input;
        for (Map.Entry<String, Mark> e : m.entrySet()) {
            out = NameDecorator.decorate(out, e.getKey(), e.getValue());
        }
        return out;
    }

    // ------------------------------------------------------------------ the periodic work

    private static void tickSafe() {
        try {
            tick();
        } catch (Throwable t) {
            BlueMarkClient.LOGGER.warn("BlueMark sync error: {}", t.toString());
        }
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        BlueMarkConfig cfg = BlueMarkConfig.get();

        if (mc.player == null || mc.getConnection() == null) {
            byName = Map.of();
            return;
        }

        String base = baseUrl(cfg);
        if (base == null) {
            status = cfg.serverUrl == null || cfg.serverUrl.isBlank()
                    ? "No server URL set" : "Invalid URL (use http:// or https://)";
            byName = Map.of();
            return;
        }

        if (cfg.secret == null || cfg.secret.length() < 16) {
            cfg.secret = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
            BlueMarkConfig.save();
        }

        UUID me = mc.player.getUUID();
        long now = System.currentTimeMillis();

        // 1) my own icon
        if (!busyOwn && now >= nextOwnAt) {
            if (cfg.enabled && cfg.shareMark) {
                Mark mine = cfg.mark();
                String key = mine.icon() + "|" + mine.hex();
                if (!key.equals(lastSentKey) || !cfg.sharedOnServer) upload(base, me, cfg, mine, key);
            } else if (cfg.sharedOnServer) {
                remove(base, me, cfg);
            }
        }

        // 2) other players' icons
        if (!(cfg.enabled && cfg.showOthers)) {
            byName = Map.of();
            return;
        }
        Map<UUID, String> names = new HashMap<>();
        List<UUID> need = new ArrayList<>();
        for (Online p : onlinePlayers(mc)) {
            if (p.id().equals(me)) continue;
            names.put(p.id(), p.name());
            Entry e = CACHE.get(p.id());
            long ttl = e != null && e.mark() != null ? HIT_TTL_MS : MISS_TTL_MS;
            boolean stale = e == null || now - e.at() > ttl;
            if (stale && !INFLIGHT.contains(p.id()) && need.size() < BATCH) need.add(p.id());
        }
        rebuildNames(names);
        if (!need.isEmpty() && now >= nextLookupAt) lookup(base, need);
    }

    // ------------------------------------------------------------------ the one place that touches PlayerInfo

    private record Online(UUID id, String name) {}

    /** If the game changes the PlayerInfo / GameProfile API, this is the only method to adjust. */
    private static List<Online> onlinePlayers(Minecraft mc) {
        List<Online> out = new ArrayList<>();
        for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
            out.add(new Online(info.getProfile().id(), info.getProfile().name()));
        }
        return out;
    }

    private static void rebuildNames(Map<UUID, String> names) {
        Map<String, Mark> m = new HashMap<>();
        for (Map.Entry<UUID, String> n : names.entrySet()) {
            Entry e = CACHE.get(n.getKey());
            if (e != null && e.mark() != null) m.put(n.getValue(), e.mark());
        }
        byName = Collections.unmodifiableMap(m);
    }

    // ------------------------------------------------------------------ network

    private static String baseUrl(BlueMarkConfig cfg) {
        if (cfg.serverUrl == null) return null;
        String u = cfg.serverUrl.trim();
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        if (!(u.startsWith("http://") || u.startsWith("https://")) || u.length() < 10) return null;
        return u;
    }

    private static String plain(UUID id) {
        return id.toString().replace("-", "");
    }

    private static CompletableFuture<HttpResponse<String>> post(String url, JsonObject body) {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString());
    }

    private static void upload(String base, UUID me, BlueMarkConfig cfg, Mark mine, String key) {
        busyOwn = true;
        JsonObject body = new JsonObject();
        body.addProperty("uuid", plain(me));
        body.addProperty("secret", cfg.secret);
        body.addProperty("icon", mine.icon());
        body.addProperty("color", mine.hex());
        post(base + "/set", body).whenComplete((resp, err) -> {
            busyOwn = false;
            if (err != null) {
                fail("Server unreachable", RETRY_MS, true);
            } else if (resp.statusCode() == 403) {
                status = "Server refused: this UUID was claimed by another install";
                nextOwnAt = System.currentTimeMillis() + 10 * RETRY_MS;
            } else if (resp.statusCode() / 100 != 2) {
                fail("Server error " + resp.statusCode(), RETRY_MS, true);
            } else {
                lastSentKey = key;
                cfg.sharedOnServer = true;
                BlueMarkConfig.save();
                status = "Connected";
            }
        });
    }

    private static void remove(String base, UUID me, BlueMarkConfig cfg) {
        busyOwn = true;
        JsonObject body = new JsonObject();
        body.addProperty("uuid", plain(me));
        body.addProperty("secret", cfg.secret);
        post(base + "/delete", body).whenComplete((resp, err) -> {
            busyOwn = false;
            if (err != null || resp.statusCode() / 100 != 2) {
                fail("Server unreachable", RETRY_MS, true);
            } else {
                lastSentKey = null;
                cfg.sharedOnServer = false;
                BlueMarkConfig.save();
                status = "Connected";
            }
        });
    }

    private static void lookup(String base, List<UUID> ids) {
        INFLIGHT.addAll(ids);
        JsonArray arr = new JsonArray();
        for (UUID id : ids) arr.add(plain(id));
        JsonObject body = new JsonObject();
        body.add("uuids", arr);

        post(base + "/lookup", body).whenComplete((resp, err) -> {
            try {
                if (err != null) {
                    fail("Server unreachable", RETRY_MS, false);
                    return;
                }
                if (resp.statusCode() / 100 != 2) {
                    fail("Server error " + resp.statusCode(), RETRY_MS, false);
                    return;
                }
                JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
                JsonObject marks = root.has("marks") && root.get("marks").isJsonObject()
                        ? root.getAsJsonObject("marks") : new JsonObject();
                long t = System.currentTimeMillis();
                for (UUID id : ids) {
                    CACHE.put(id, new Entry(parse(marks.get(plain(id))), t));
                }
                status = "Connected";
            } catch (Exception e) {
                fail("Bad server reply", RETRY_MS, false);
            } finally {
                INFLIGHT.removeAll(ids);
            }
        });
    }

    private static void fail(String message, long retryMs, boolean own) {
        status = message;
        long at = System.currentTimeMillis() + retryMs;
        if (own) nextOwnAt = at;
        else nextLookupAt = at;
    }

    /** Only accepts known icons and #RRGGBB colors from the server. */
    private static Mark parse(JsonElement el) {
        if (el == null || !el.isJsonObject()) return null;
        JsonObject o = el.getAsJsonObject();
        if (!o.has("icon") || !o.has("color")) return null;
        try {
            String icon = o.get("icon").getAsString();
            String color = o.get("color").getAsString();
            if (!BlueMarkConfig.ICONS.contains(icon)) return null;
            if (!color.matches("#[0-9A-Fa-f]{6}")) return null;
            return new Mark(icon, Integer.parseInt(color.substring(1), 16));
        } catch (Exception e) {
            return null;
        }
    }
}
