package dev.bluemark;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * BlueMark settings, styled like the Essential mod settings (sidebar, search, sections, cards, switches)
 * with small animations: fade-in on open, slide/fade when changing tab, sliding switches,
 * smooth hover on cards, and a sliding marker in the sidebar.
 */
public class BlueMarkScreen extends Screen {

    private enum Tab {
        GENERAL("General"), APPEARANCE("Appearance"), ONLINE("Online"), PLAYERS("Players");
        final String label;
        Tab(String label) { this.label = label; }
    }

    private record ToggleDef(Tab tab, String section, String title, String desc,
                             BooleanSupplier get, Consumer<Boolean> set) {}

    private enum Kind { SECTION, TOGGLE, STATUS, ICONS, COLOR, PREVIEW, PLAYER, NOTE }

    private static final class Item {
        Kind kind;
        String text;
        ToggleDef def;
        Mark mark;
        int y, h;
    }

    private static final int SEC_H = 14;
    private static final int GAP = 4;
    private static final int PANEL_RGB = 0x1B1B1B;
    private static final int BG_RGB = 0x151515;

    private final Screen parent;
    private final BlueMarkConfig cfg = BlueMarkConfig.get();
    private Tab tab = Tab.GENERAL;
    private String query = "";

    private final List<ToggleDef> toggles = List.of(
            new ToggleDef(Tab.GENERAL, "Main", "Enable BlueMark", "Master switch. Off = no icons anywhere.",
                    () -> cfg.enabled, v -> cfg.enabled = v),
            new ToggleDef(Tab.GENERAL, "Show icons in", "Above heads", "The name floating above players.",
                    () -> cfg.showOnNametag, v -> cfg.showOnNametag = v),
            new ToggleDef(Tab.GENERAL, "Show icons in", "Chat", "Next to names in chat messages.",
                    () -> cfg.showInChat, v -> cfg.showInChat = v),
            new ToggleDef(Tab.GENERAL, "Show icons in", "Player list (Tab)", "The list you open with the Tab key.",
                    () -> cfg.showInTab, v -> cfg.showInTab = v),
            new ToggleDef(Tab.GENERAL, "Show icons in", "My own head (F5)", "See your own name tag in third person.",
                    () -> cfg.showOwnNametag, v -> cfg.showOwnNametag = v),
            new ToggleDef(Tab.ONLINE, "Sharing", "Share my icon", "Other BlueMark players will see your icon.",
                    () -> cfg.shareMark, v -> cfg.shareMark = v),
            new ToggleDef(Tab.ONLINE, "Sharing", "See other players' icons", "Show the icons that other players picked.",
                    () -> cfg.showOthers, v -> cfg.showOthers = v)
    );

    private final List<Button> toggleButtons = new ArrayList<>();
    private final List<Button> iconButtons = new ArrayList<>();
    private Button colorButton;
    private EditBox searchBox;

    private final List<Item> items = new ArrayList<>();

    // layout
    private int px1, py1, px2, py2, sideW, cx1, cx2, cardTop;
    private int iconCell, iconStartX;
    private String version = "", mcVersion = "";
    private long lastRefresh = 0;

    // animation state
    private long lastNs = 0;
    private float dt = 0;
    private float appear = 0;      // screen fade-in 0..1
    private float tabAnim = 0;     // content slide/fade 0..1
    private float markerY = -100;  // sidebar marker position
    private final Map<String, Float> anims = new HashMap<>();

    public BlueMarkScreen(Screen parent) {
        super(Component.literal("BlueMark"));
        this.parent = parent;
    }

    // ------------------------------------------------------------------ setup

    @Override
    protected void init() {
        toggleButtons.clear();
        iconButtons.clear();
        items.clear();
        appear = 0;
        tabAnim = 0;
        markerY = -100;

        version = FabricLoader.getInstance().getModContainer("bluemark")
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");
        mcVersion = FabricLoader.getInstance().getModContainer("minecraft")
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");

        px1 = 30;
        py1 = 16;
        px2 = this.width - 12;
        py2 = this.height - 16;
        sideW = 104;
        cx1 = px1 + sideW + 10;
        cx2 = px2 - 10;
        cardTop = py1 + 38;

        // back button (square, left of the panel)
        addRenderableWidget(Button.builder(Component.empty(), b -> onClose())
                .bounds(8, py1 + 6, 16, 16).build());

        // sidebar tabs (invisible hit areas, drawn in extractRenderState)
        int ty = py1 + 38;
        for (Tab t : Tab.values()) {
            final Tab target = t;
            addRenderableWidget(Button.builder(Component.empty(), b -> setTab(target))
                    .bounds(px1 + 4, ty - 2, sideW - 8, 12).build());
            ty += 14;
        }

        // search box (input handling only, the text is drawn by this screen)
        int searchW = Math.min(170, (cx2 - cx1) * 2 / 3);
        searchBox = new EditBox(this.font, cx1 + 14, py1 + 7, searchW - 16, 14, Component.empty());
        searchBox.setMaxLength(32);
        searchBox.setValue(query);
        searchBox.setResponder(s -> {
            boolean wasSearching = !query.trim().isEmpty();
            query = s;
            if (wasSearching != !query.trim().isEmpty()) tabAnim = 0;
            relayout();
        });
        addWidget(searchBox);

        // one invisible button per switch
        for (ToggleDef t : toggles) {
            Button bt = Button.builder(Component.empty(), b -> {
                t.set().accept(!t.get().getAsBoolean());
                BlueMarkConfig.save();
            }).bounds(cx1, cardTop, cx2 - cx1, 30).build();
            toggleButtons.add(bt);
            addRenderableWidget(bt);
        }

        // icon cells
        int contentW = cx2 - cx1;
        iconCell = Math.max(18, Math.min(26, (contentW - 20 - 6 * 4) / BlueMarkConfig.ICONS.size()));
        iconStartX = cx1 + 10;
        for (int i = 0; i < BlueMarkConfig.ICONS.size(); i++) {
            final int idx = i;
            Button bt = Button.builder(Component.empty(), b -> {
                cfg.icon = BlueMarkConfig.ICONS.get(idx);
                BlueMarkConfig.save();
            }).bounds(iconStartX + i * (iconCell + 4), cardTop, iconCell, iconCell - 2).build();
            iconButtons.add(bt);
            addRenderableWidget(bt);
        }

        // color card opens the picker
        colorButton = Button.builder(Component.empty(),
                b -> this.minecraft.setScreenAndShow(new BlueMarkColorScreen(this)))
                .bounds(cx1, cardTop, contentW, 30).build();
        addRenderableWidget(colorButton);

        relayout();
    }

    private void setTab(Tab t) {
        if (t != tab || !query.trim().isEmpty()) tabAnim = 0;
        this.tab = t;
        this.query = "";
        searchBox.setValue("");
        setFocused(null);
        relayout();
    }

    // ------------------------------------------------------------------ layout

    private int add(int y, Kind kind, String text, ToggleDef def, Mark mark, int h, int gap) {
        Item it = new Item();
        it.kind = kind;
        it.text = text;
        it.def = def;
        it.mark = mark;
        it.y = y;
        it.h = h;
        items.add(it);
        return y + h + gap;
    }

    private int addToggles(int y, int cardH, Tab t) {
        String section = null;
        for (ToggleDef d : toggles) {
            if (d.tab() != t) continue;
            if (!d.section().equals(section)) {
                section = d.section();
                y = add(y, Kind.SECTION, section, null, null, SEC_H, GAP);
            }
            y = add(y, Kind.TOGGLE, null, d, null, cardH, GAP);
        }
        return y;
    }

    /** Builds the list of things to draw for the current tab / search. Returns the bottom y. */
    private int build(int cardH) {
        items.clear();
        int y = cardTop;
        String q = query.trim().toLowerCase(Locale.ROOT);

        if (!q.isEmpty()) {
            y = add(y, Kind.SECTION, "Search results", null, null, SEC_H, GAP);
            int found = 0;
            for (ToggleDef d : toggles) {
                if (d.title().toLowerCase(Locale.ROOT).contains(q) || d.desc().toLowerCase(Locale.ROOT).contains(q)) {
                    y = add(y, Kind.TOGGLE, null, d, null, cardH, GAP);
                    found++;
                }
            }
            if (found == 0) y = add(y, Kind.NOTE, "Nothing found.", null, null, 14, 0);
            return y;
        }

        switch (tab) {
            case GENERAL -> y = addToggles(y, cardH, Tab.GENERAL);
            case ONLINE -> {
                y = addToggles(y, cardH, Tab.ONLINE);
                y = add(y, Kind.SECTION, "Connection", null, null, SEC_H, GAP);
                y = add(y, Kind.STATUS, null, null, null, cardH, GAP);
            }
            case APPEARANCE -> {
                y = add(y, Kind.SECTION, "Icon", null, null, SEC_H, GAP);
                y = add(y, Kind.ICONS, null, null, null, 20 + iconCell + 6, GAP);
                y = add(y, Kind.SECTION, "Color", null, null, SEC_H, GAP);
                y = add(y, Kind.COLOR, null, null, null, cardH, GAP);
                y = add(y, Kind.SECTION, "Preview", null, null, SEC_H, GAP);
                y = add(y, Kind.PREVIEW, null, null, null, cardH, GAP);
            }
            case PLAYERS -> {
                y = add(y, Kind.SECTION, "Players with BlueMark", null, null, SEC_H, GAP);
                String me = this.minecraft.getUser().getName();
                y = add(y, Kind.PLAYER, me + "  (you)", null, cfg.mark(), 16, 2);
                Map<String, Mark> others = MarkSync.visibleMarks();
                int shown = 0;
                for (Map.Entry<String, Mark> e : others.entrySet()) {
                    if (y + 16 > py2 - 22) break;
                    y = add(y, Kind.PLAYER, e.getKey(), null, e.getValue(), 16, 2);
                    shown++;
                }
                if (others.size() > shown) {
                    y = add(y, Kind.NOTE, "+" + (others.size() - shown) + " more", null, null, 12, 0);
                } else if (others.isEmpty()) {
                    y = add(y + 4, Kind.NOTE, "Nobody else with BlueMark is here right now.", null, null, 12, 0);
                }
            }
        }
        return y;
    }

    /** Picks the biggest card height that fits, then moves the real widgets onto the items. */
    private void relayout() {
        if (toggleButtons.isEmpty()) return;

        for (int h = 34; h >= 24; h -= 2) {
            int bottom = build(h);
            if (bottom <= py2 - 8) break;
        }

        for (Button b : toggleButtons) b.visible = false;
        for (Button b : iconButtons) b.visible = false;
        colorButton.visible = false;

        for (Item it : items) {
            switch (it.kind) {
                case TOGGLE -> {
                    Button b = toggleButtons.get(toggles.indexOf(it.def));
                    b.setX(cx1);
                    b.setY(it.y);
                    b.setWidth(cx2 - cx1);
                    b.setHeight(it.h);
                    b.visible = true;
                }
                case ICONS -> {
                    for (int i = 0; i < iconButtons.size(); i++) {
                        Button b = iconButtons.get(i);
                        b.setX(iconStartX + i * (iconCell + 4));
                        b.setY(it.y + 20);
                        b.visible = true;
                    }
                }
                case COLOR -> {
                    colorButton.setX(cx1);
                    colorButton.setY(it.y);
                    colorButton.setWidth(cx2 - cx1);
                    colorButton.setHeight(it.h);
                    colorButton.visible = true;
                }
                default -> { }
            }
        }
    }

    // ------------------------------------------------------------------ animation helpers

    private static float ease(float t) {
        float u = 1f - Math.max(0f, Math.min(1f, t));
        return 1f - u * u * u;
    }

    /** Smooth 0..1 value that moves toward (on ? 1 : 0). New keys start at their target (no first-frame jump). */
    private float anim(String key, boolean on, float seconds) {
        float v = anims.getOrDefault(key, on ? 1f : 0f);
        float step = dt / seconds;
        v = on ? Math.min(1f, v + step) : Math.max(0f, v - step);
        anims.put(key, v);
        return ease(v);
    }

    /** Same as anim but starting from 0 (so it plays when first shown). */
    private float hover(String key, boolean on) {
        float v = anims.getOrDefault(key, 0f);
        float step = dt / 0.14f;
        v = on ? Math.min(1f, v + step) : Math.max(0f, v - step);
        anims.put(key, v);
        return ease(v);
    }

    // ------------------------------------------------------------------ drawing

    private void label(GuiGraphicsExtractor g, String s, int x, int y, int color) {
        g.text(this.font, s, x, y, color, false);
    }

    /** Cuts the text with ".." so it fits in maxW pixels. */
    private String fit(String s, int maxW) {
        if (this.font.width(s) <= maxW) return s;
        String t = s;
        while (!t.isEmpty() && this.font.width(t + "..") > maxW) t = t.substring(0, t.length() - 1);
        return t + "..";
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);

        long now = System.nanoTime();
        dt = lastNs == 0 ? 0f : Math.min(0.05f, (now - lastNs) / 1_000_000_000f);
        lastNs = now;
        appear = Math.min(1f, appear + dt / 0.28f);
        tabAnim = Math.min(1f, tabAnim + dt / 0.20f);
        float te = ease(tabAnim);

        // everything below is painted over the (invisible) vanilla buttons
        g.fill(0, 0, this.width, this.height, UiTheme.BG);
        UiTheme.box(g, px1, py1, px2, py2, UiTheme.PANEL, UiTheme.LINE);

        drawBackButton(g, mouseX, mouseY);
        drawSidebar(g, mouseX, mouseY);
        drawHeader(g);

        // the player list changes while you look at it
        long ms = System.currentTimeMillis();
        if (tab == Tab.PLAYERS && ms - lastRefresh > 1000) {
            lastRefresh = ms;
            relayout();
        }

        int slide = Math.round((1f - te) * 8);
        for (Item it : items) {
            it.y += slide;
            switch (it.kind) {
                case SECTION -> drawSection(g, it);
                case TOGGLE -> drawToggle(g, it, mouseX, mouseY);
                case STATUS -> drawStatus(g, it);
                case ICONS -> drawIcons(g, it, mouseX, mouseY);
                case COLOR -> drawColor(g, it, mouseX, mouseY);
                case PREVIEW -> drawPreview(g, it);
                case PLAYER -> drawPlayer(g, it);
                case NOTE -> label(g, it.text, cx1 + 2, it.y + 2, UiTheme.SUB);
            }
            it.y -= slide;
        }

        // fade-in of the content when the tab changes
        int a = Math.round((1f - te) * 255);
        if (a > 3) g.fill(cx1 - 9, py1 + 29, px2 - 1, py2 - 1, (a << 24) | PANEL_RGB);

        // fade-in of the whole screen when it opens
        int a2 = Math.round((1f - ease(appear)) * 255);
        if (a2 > 3) g.fill(0, 0, this.width, this.height, (a2 << 24) | BG_RGB);
    }

    private void drawBackButton(GuiGraphicsExtractor g, int mx, int my) {
        float h = hover("back", UiTheme.inside(mx, my, 8, py1 + 6, 24, py1 + 22));
        UiTheme.box(g, 8, py1 + 6, 24, py1 + 22, UiTheme.mix(UiTheme.HEADER, UiTheme.CARD_HOVER, h),
                UiTheme.mix(UiTheme.BORDER, UiTheme.BLUE, h));
        label(g, "<", 8 + (16 - this.font.width("<")) / 2 - Math.round(h), py1 + 10, UiTheme.TEXT);
    }

    private void drawSidebar(GuiGraphicsExtractor g, int mx, int my) {
        // sidebar background + header strip
        g.fill(px1 + 1, py1 + 1, px1 + sideW, py2 - 1, UiTheme.SIDEBAR);
        g.fill(px1 + 1, py1 + 1, px1 + sideW, py1 + 28, UiTheme.HEADER);
        g.fill(px1 + sideW, py1 + 1, px1 + sideW + 1, py2 - 1, UiTheme.LINE);
        label(g, UiTheme.BOLD + "BlueMark", px1 + 10, py1 + 10, UiTheme.TEXT);

        boolean searching = !query.trim().isEmpty();

        // marker that slides to the active tab
        int targetY = py1 + 38 + tab.ordinal() * 14 - 1;
        markerY = markerY < -50 ? targetY : markerY + (targetY - markerY) * Math.min(1f, dt * 16f);
        float markerOn = anim("marker", !searching, 0.15f);
        if (markerOn > 0.02f) {
            g.fill(px1 + 1, Math.round(markerY), px1 + 3, Math.round(markerY) + 10,
                    UiTheme.mix(UiTheme.SIDEBAR, UiTheme.BLUE, markerOn));
        }

        int y = py1 + 38;
        for (Tab t : Tab.values()) {
            boolean active = !searching && t == tab;
            boolean over = UiTheme.inside(mx, my, px1 + 4, y - 2, px1 + sideW - 4, y + 10);
            float a = anim("tab:" + t, active, 0.15f);
            float h = hover("tabhover:" + t, over && !active);
            int color = UiTheme.mix(UiTheme.mix(UiTheme.SUB, UiTheme.TEXT, 0.55f + 0.45f * h), UiTheme.BLUE, a);
            label(g, t.label, px1 + 10 + Math.round(2 * Math.max(a, h)), y, color);
            y += 14;
        }

        // footer: version info
        g.fill(px1 + 1, py2 - 36, px1 + sideW, py2 - 35, UiTheme.LINE);
        label(g, "BlueMark v" + version, px1 + 10, py2 - 30, UiTheme.SUB);
        label(g, "MC " + mcVersion + " [Fabric]", px1 + 10, py2 - 19, UiTheme.SUB);
    }

    private void drawHeader(GuiGraphicsExtractor g) {
        g.fill(px1 + sideW + 1, py1 + 1, px2 - 1, py1 + 28, UiTheme.HEADER);

        // search box
        int searchW = Math.min(170, (cx2 - cx1) * 2 / 3);
        int sx = cx1, sy = py1 + 6;
        float f = anim("searchfocus", searchBox.isFocused(), 0.15f);
        UiTheme.box(g, sx, sy, sx + searchW, sy + 16, UiTheme.FIELD, UiTheme.FIELD);
        g.fill(sx, sy + 15, sx + searchW, sy + 16, UiTheme.mix(UiTheme.BORDER, UiTheme.BLUE, f));
        UiTheme.magnifier(g, sx + 4, sy + 4, UiTheme.mix(UiTheme.SUB, UiTheme.TEXT, f));

        String v = searchBox.getValue();
        int maxW = searchW - 22;
        if (v.isEmpty() && !searchBox.isFocused()) {
            label(g, "Search settings", sx + 16, sy + 4, UiTheme.SUB);
        } else {
            label(g, tail(v, maxW) + caret(searchBox), sx + 16, sy + 4, UiTheme.TEXT);
        }
    }

    /** Last part of the text that still fits in maxW pixels. */
    private String tail(String s, int maxW) {
        String t = s;
        while (!t.isEmpty() && this.font.width(t) > maxW) t = t.substring(1);
        return t;
    }

    private String caret(EditBox box) {
        return box.isFocused() && (System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "";
    }

    private void drawSection(GuiGraphicsExtractor g, Item it) {
        int w = this.font.width(it.text);
        int mid = (cx1 + cx2) / 2;
        int tx = mid - w / 2;
        int ly = it.y + 4;
        g.fill(cx1, ly, tx - 8, ly + 1, UiTheme.BORDER);
        g.fill(tx + w + 8, ly, cx2, ly + 1, UiTheme.BORDER);
        label(g, it.text, tx, it.y, UiTheme.TEXT);
    }

    private void cardTexts(GuiGraphicsExtractor g, Item it, String title, String desc, int rightPad) {
        int ty = it.h >= 30 ? it.y + 7 : it.y + 4;
        int maxW = cx2 - cx1 - 20 - rightPad;
        label(g, UiTheme.BOLD + fit(title, maxW), cx1 + 10, ty, UiTheme.TEXT);
        label(g, fit(desc, maxW), cx1 + 10, ty + 11, UiTheme.SUB);
    }

    /** Card box whose color and border glow smoothly when the mouse is over it. */
    private float card(GuiGraphicsExtractor g, String key, int x1, int y1, int x2, int y2, int mx, int my, boolean hoverable) {
        float h = hover(key, hoverable && UiTheme.inside(mx, my, x1, y1 + 0, x2, y2));
        UiTheme.box(g, x1, y1, x2, y2, UiTheme.mix(UiTheme.CARD, UiTheme.CARD_HOVER, h),
                UiTheme.mix(UiTheme.BORDER, 0xFF4B6FB5, h));
        return h;
    }

    private void drawToggle(GuiGraphicsExtractor g, Item it, int mx, int my) {
        ToggleDef t = it.def;
        card(g, "card:" + t.title(), cx1, it.y, cx2, it.y + it.h, mx, my, true);
        cardTexts(g, it, t.title(), t.desc(), 64);

        boolean on = t.get().getAsBoolean();
        float a = anim("sw:" + t.title(), on, 0.16f);
        int sx = cx2 - 30;
        UiTheme.toggle(g, sx, it.y + it.h / 2 - 5, a);

        String word = on ? "ON" : "OFF";
        label(g, word, sx - 6 - this.font.width(word), it.y + it.h / 2 - 4, UiTheme.mix(UiTheme.SUB, UiTheme.BLUE, a));
    }

    private void drawStatus(GuiGraphicsExtractor g, Item it) {
        UiTheme.box(g, cx1, it.y, cx2, it.y + it.h, UiTheme.CARD, UiTheme.BORDER);
        String s = MarkSync.status();
        String shown = switch (s) {
            case "Connected" -> "Connected. Other players can see your icon.";
            case "Sharing server not set up" -> "Not available in this build of the mod.";
            default -> s;
        };
        cardTexts(g, it, "Connection", shown, 20);
        int dot = "Connected".equals(s) ? 0xFF3DDC84 : (s.startsWith("Sharing server") ? 0xFF8E8E8E : 0xFFFFB020);
        int pulse = "Connected".equals(s) ? 0 : Math.round(1 + (float) Math.sin(System.currentTimeMillis() / 300.0));
        g.fill(cx2 - 16 - pulse, it.y + it.h / 2 - 3 - pulse, cx2 - 10 + pulse, it.y + it.h / 2 + 3 + pulse, dot);
    }

    private static String pretty(String icon) {
        return icon.substring(0, 1).toUpperCase(Locale.ROOT) + icon.substring(1);
    }

    private void drawIcons(GuiGraphicsExtractor g, Item it, int mx, int my) {
        int color = 0xFF000000 | cfg.rgb();
        int cur = BlueMarkConfig.iconIndex(cfg.icon);

        UiTheme.box(g, cx1, it.y, cx2, it.y + it.h, UiTheme.CARD, UiTheme.BORDER);
        label(g, UiTheme.BOLD + "Choose your icon", cx1 + 10, it.y + 6, UiTheme.TEXT);
        String chosen = "Selected: " + pretty(BlueMarkConfig.ICONS.get(cur));
        label(g, chosen, cx2 - 10 - this.font.width(chosen), it.y + 6, UiTheme.SUB);

        int cellY = it.y + 20;
        for (int i = 0; i < BlueMarkConfig.ICONS.size(); i++) {
            int x = iconStartX + i * (iconCell + 4);
            boolean over = UiTheme.inside(mx, my, x, cellY, x + iconCell, cellY + iconCell - 2);
            float h = hover("icon:" + i, over);
            float sel = anim("iconsel:" + i, i == cur, 0.15f);
            int lift = Math.round(2 * h);
            UiTheme.box(g, x, cellY - lift, x + iconCell, cellY + iconCell - 2 - lift,
                    UiTheme.mix(0xFF1B1B1B, UiTheme.CARD_HOVER, h),
                    UiTheme.mix(UiTheme.mix(UiTheme.BORDER, 0xFF4B6FB5, h), UiTheme.BLUE, sel));
            String glyph = String.valueOf((char) (0xE000 + i));
            label(g, glyph, x + (iconCell - this.font.width(glyph)) / 2, cellY + (iconCell - 2 - 8) / 2 - lift, color);
        }
    }

    private void drawColor(GuiGraphicsExtractor g, Item it, int mx, int my) {
        int color = 0xFF000000 | cfg.rgb();
        card(g, "color", cx1, it.y, cx2, it.y + it.h, mx, my, true);
        cardTexts(g, it, "Icon color", cfg.color + "  -  click to change", 40);
        int sh = Math.min(18, it.h - 8);
        int sy = it.y + (it.h - sh) / 2;
        UiTheme.box(g, cx2 - 34, sy, cx2 - 10, sy + sh, color, 0xFFFFFFFF);
    }

    private void drawPreview(GuiGraphicsExtractor g, Item it) {
        int color = 0xFF000000 | cfg.rgb();
        UiTheme.box(g, cx1, it.y, cx2, it.y + it.h, UiTheme.CARD, UiTheme.BORDER);
        int ty = it.y + it.h / 2 - 4;
        String glyph = cfg.glyph() + " ";
        label(g, glyph, cx1 + 10, ty, color);
        label(g, this.minecraft.getUser().getName(), cx1 + 10 + this.font.width(glyph), ty, UiTheme.TEXT);
        String hint = "This is how others see you";
        label(g, hint, cx2 - 10 - this.font.width(hint), ty, UiTheme.SUB);
    }

    private void drawPlayer(GuiGraphicsExtractor g, Item it) {
        UiTheme.box(g, cx1, it.y, cx2, it.y + it.h, UiTheme.CARD, UiTheme.BORDER);
        int color = 0xFF000000 | it.mark.rgb();
        String glyph = it.mark.glyph() + " ";
        label(g, glyph, cx1 + 8, it.y + 4, color);
        label(g, fit(it.text, cx2 - cx1 - 80), cx1 + 8 + this.font.width(glyph), it.y + 4, UiTheme.TEXT);
        String hex = it.mark.hex();
        label(g, hex, cx2 - 8 - this.font.width(hex), it.y + 4, UiTheme.SUB);
    }

    @Override
    public void onClose() {
        BlueMarkConfig.save();
        this.minecraft.setScreenAndShow(parent);
    }
}
