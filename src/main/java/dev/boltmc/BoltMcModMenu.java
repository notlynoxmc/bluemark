package dev.boltmc;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Gives Bolt Mc a "Configure" (settings) button in Mod Menu. */
public class BoltMcModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return BoltMcScreen::new;
    }
}
