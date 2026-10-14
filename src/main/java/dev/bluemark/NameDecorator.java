package dev.bluemark;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NameDecorator {
    private NameDecorator() {}

    /** The colored icon (followed by a space). */
    public static MutableComponent icon(Mark mark) {
        return Component.literal(mark.glyph() + " ")
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(mark.rgb())));
    }

    /**
     * Returns a copy of {@code input} where every occurrence of {@code name}
     * (as a whole word) has the icon in front of it. If the name is not there,
     * the same object is returned. Never adds the icon twice.
     */
    public static Component decorate(Component input, String name, Mark mark) {
        if (input == null || name == null || name.isEmpty() || mark == null) return input;
        if (!input.getString().contains(name)) return input; // cheap check before the regex

        Pattern p = Pattern.compile("(?<![A-Za-z0-9_])" + Pattern.quote(name) + "(?![A-Za-z0-9_])");
        if (!p.matcher(input.getString()).find()) return input;

        String prefix = mark.glyph() + " ";
        MutableComponent out = Component.empty();
        StringBuilder sofar = new StringBuilder();

        input.<Boolean>visit((style, text) -> {
            Matcher m = p.matcher(text);
            int last = 0;
            while (m.find()) {
                String before = text.substring(last, m.start());
                sofar.append(before);
                if (!before.isEmpty()) out.append(Component.literal(before).withStyle(style));

                if (!sofar.toString().endsWith(prefix)) {
                    out.append(icon(mark));
                    sofar.append(prefix);
                }
                out.append(Component.literal(m.group()).withStyle(style));
                sofar.append(m.group());
                last = m.end();
            }
            String rest = text.substring(last);
            sofar.append(rest);
            if (!rest.isEmpty()) out.append(Component.literal(rest).withStyle(style));
            return Optional.empty();
        }, Style.EMPTY);

        return out;
    }
}
