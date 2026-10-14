package dev.bluemark;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Gives BlueMark a "Configure" (settings) button in Mod Menu. */
public class BlueMarkModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return BlueMarkScreen::new;
    }
}
