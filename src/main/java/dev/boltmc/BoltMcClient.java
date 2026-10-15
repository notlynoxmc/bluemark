package dev.boltmc;

import dev.boltmc.friends.ChatRelay;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BoltMcClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("BoltMc");

    @Override
    public void onInitializeClient() {
        BoltMcConfig.get();
        ChatRelay.start();
        AuraTicker.start();
        LOGGER.info("Bolt Mc loaded. Settings: config/boltmc.json or Mod Menu.");
    }
}
