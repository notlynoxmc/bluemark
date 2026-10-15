package dev.boltmc.friends;

import dev.boltmc.BoltMcConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Adds / removes friends. A friend is matched by UUID, found from the current tab list by name. */
public final class FriendsManager {
    private FriendsManager() {}

    public enum AddResult { OK, ALREADY_FRIENDS, NOT_FOUND, IS_YOU }

    /** Looks the name up in the players currently visible in the Tab list (same world/server). */
    public static AddResult addByName(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && name.equalsIgnoreCase(mc.player.getGameProfile().name())) {
            return AddResult.IS_YOU;
        }
        if (mc.getConnection() == null) return AddResult.NOT_FOUND;

        for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
            if (info.getProfile().name().equalsIgnoreCase(name)) {
                return add(info.getProfile().name(), info.getProfile().id().toString().replace("-", ""));
            }
        }
        return AddResult.NOT_FOUND;
    }

    public static AddResult add(String name, String plainUuid) {
        BoltMcConfig cfg = BoltMcConfig.get();
        for (BoltMcConfig.Friend f : cfg.friends) {
            if (f.uuid.equalsIgnoreCase(plainUuid)) return AddResult.ALREADY_FRIENDS;
        }
        cfg.friends.add(new BoltMcConfig.Friend(name, plainUuid.toLowerCase(Locale.ROOT)));
        BoltMcConfig.save();
        return AddResult.OK;
    }

    public static void remove(BoltMcConfig.Friend f) {
        BoltMcConfig cfg = BoltMcConfig.get();
        cfg.friends.removeIf(x -> x.uuid.equalsIgnoreCase(f.uuid));
        BoltMcConfig.save();
    }

    public static List<BoltMcConfig.Friend> list() {
        return new ArrayList<>(BoltMcConfig.get().friends);
    }

    public static Optional<BoltMcConfig.Friend> byUuid(String plainUuid) {
        return BoltMcConfig.get().friends.stream().filter(f -> f.uuid.equalsIgnoreCase(plainUuid)).findFirst();
    }
}
