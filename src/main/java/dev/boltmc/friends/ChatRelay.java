package dev.boltmc.friends;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.boltmc.BoltMcClient;
import dev.boltmc.BoltMcConfig;
import net.minecraft.client.Minecraft;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Text chat between friends, through a small Bolt Mc relay server (see server/README.md).
 * Messages are queued server-side per recipient UUID and picked up the next time that
 * player's game polls (every 2 seconds), then deleted from the server.
 */
public final class ChatRelay {
    private ChatRelay() {}

    private static final Map<String, List<ChatMessage>> HISTORY = new ConcurrentHashMap<>();
    private static volatile String status = "Not set up";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    public static void start() {
        ScheduledExecutorService ex = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "BoltMc-chat");
            t.setDaemon(true);
            return t;
        });
        ex.scheduleWithFixedDelay(() -> {
            try { Minecraft.getInstance().execute(ChatRelay::poll); } catch (Throwable ignored) { }
        }, 2, 2, TimeUnit.SECONDS);
    }

    public static String status() { return status; }

    public static List<ChatMessage> historyWith(String friendUuid) {
        return HISTORY.computeIfAbsent(friendUuid.toLowerCase(), k -> new CopyOnWriteArrayList<>());
    }

    /** Adds the message locally and sends it to the relay, so the friend picks it up on their next poll. */
    public static void send(String friendUuid, String text) {
        if (text == null || text.isBlank()) return;
        text = text.length() > 240 ? text.substring(0, 240) : text;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        String myUuid = mc.player.getUUID().toString().replace("-", "");

        historyWith(friendUuid).add(new ChatMessage(myUuid, text, System.currentTimeMillis(), true));

        String base = baseUrl();
        if (base == null) { status = "Not set up"; return; }
        ensureSecret();

        JsonObject body = new JsonObject();
        body.addProperty("from", myUuid);
        body.addProperty("to", friendUuid.toLowerCase());
        body.addProperty("secret", BoltMcConfig.get().secret);
        body.addProperty("text", text);
        post(base + "/send", body).whenComplete((resp, err) -> {
            if (err != null || resp.statusCode() / 100 != 2) {
                status = err != null ? "Relay unreachable" : "Relay error " + resp.statusCode();
            } else {
                status = "Connected";
            }
        });
    }

    private static void poll() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        String base = baseUrl();
        if (base == null) { status = "Not set up"; return; }
        ensureSecret();

        String myUuid = mc.player.getUUID().toString().replace("-", "");
        JsonObject body = new JsonObject();
        body.addProperty("uuid", myUuid);
        body.addProperty("secret", BoltMcConfig.get().secret);

        post(base + "/inbox", body).whenComplete((resp, err) -> {
            if (err != null) { status = "Relay unreachable"; return; }
            if (resp.statusCode() / 100 != 2) { status = "Relay error " + resp.statusCode(); return; }
            try {
                JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
                JsonArray arr = root.has("messages") ? root.getAsJsonArray("messages") : new JsonArray();
                for (JsonElement el : arr) {
                    JsonObject m = el.getAsJsonObject();
                    String from = m.get("from").getAsString();
                    String text = m.get("text").getAsString();
                    long at = m.has("at") ? m.get("at").getAsLong() : System.currentTimeMillis();
                    historyWith(from).add(new ChatMessage(from, text, at, false));
                }
                status = "Connected";
            } catch (Exception e) {
                status = "Bad relay reply";
            }
        });
    }

    private static void ensureSecret() {
        BoltMcConfig cfg = BoltMcConfig.get();
        if (cfg.secret == null || cfg.secret.length() < 16) {
            cfg.secret = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
            BoltMcConfig.save();
        }
    }

    private static String baseUrl() {
        BoltMcConfig cfg = BoltMcConfig.get();
        String u = (cfg.serverUrl == null || cfg.serverUrl.isBlank()
                ? BoltMcConfig.DEFAULT_SERVER_URL : cfg.serverUrl).trim();
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        if (!(u.startsWith("http://") || u.startsWith("https://")) || u.length() < 10) return null;
        return u;
    }

    private static java.util.concurrent.CompletableFuture<HttpResponse<String>> post(String url, JsonObject body) {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString());
    }
}
