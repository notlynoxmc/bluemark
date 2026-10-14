package dev.bluemark;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class BlueMarkClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("BlueMark");
    private static final Set<String> HITS = ConcurrentHashMap.newKeySet();

    @Override
    public void onInitializeClient() {
        BlueMarkConfig.get();
        LOGGER.info("BlueMark loaded. Settings: config/bluemark.json or Mod Menu.");
    }

    /** Logs one line the first time each feature really runs (helps to see what works in latest.log). */
    public static void hit(String feature) {
        if (HITS.add(feature)) {
            LOGGER.info("BlueMark: '{}' hook is active", feature);
        }
    }
}
