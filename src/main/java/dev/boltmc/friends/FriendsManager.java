package dev.boltmc.friends;

import dev.boltmc.BoltMcConfig;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Adds / removes friends. A friend is identified by player NAME (lowercase), so it works for
 * original and cracked accounts alike, and you don't have to be on the same server.
 */
public final class FriendsManager {
    private FriendsManager() {}

    public enum AddResult { OK, ALREADY_FRIENDS, INVALID, IS_YOU }

    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9_]{1,16}");

    public static AddResult addByName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (!VALID.matcher(name).matches()) return AddResult.INVALID;
        if (name.equalsIgnoreCase(Minecraft.getInstance().getUser().getName())) return AddResult.IS_YOU;
        return add(name);
    }

    public static AddResult add(String name) {
        String id = name.toLowerCase(Locale.ROOT);
        BoltMcConfig cfg = BoltMcConfig.get();
        for (BoltMcConfig.Friend f : cfg.friends) {
            if (f.id.equals(id)) return AddResult.ALREADY_FRIENDS;
        }
        cfg.friends.add(new BoltMcConfig.Friend(name, id));
        BoltMcConfig.save();
        return AddResult.OK;
    }

    /** Someone who messaged you shows up in your friends list automatically (remove them with the x). */
    public static void addIncoming(String name) {
        if (VALID.matcher(name).matches()) add(name);
    }

    public static void remove(BoltMcConfig.Friend f) {
        BoltMcConfig cfg = BoltMcConfig.get();
        cfg.friends.removeIf(x -> x.id.equals(f.id));
        BoltMcConfig.save();
    }

    public static List<BoltMcConfig.Friend> list() {
        return new ArrayList<>(BoltMcConfig.get().friends);
    }

    public static Optional<BoltMcConfig.Friend> byId(String id) {
        return BoltMcConfig.get().friends.stream().filter(f -> f.id.equals(id)).findFirst();
    }
}
