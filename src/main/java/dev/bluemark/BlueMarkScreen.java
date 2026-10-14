package dev.bluemark;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** BlueMark settings, styled like the Essential mod settings (sidebar, search, sections, cards, switches). */
public class BlueMarkScreen extends Screen {

    private enum Tab {
        GENERAL("General"), APPEARANCE("Appearance"), ONLINE("Online"), PLAYERS("Players");
        final String label;
        Tab(String label) { this.label = label; }
    }

    private record ToggleDef(Tab tab, String section, String title, String desc,
                             BooleanSupplier get, Consumer<Boolean> set) {}

    private enum Kind { SECTION, TOGGLE, URL, STATUS, ICONS, COLOR, PREVIEW, PLAYER, NOTE }

    private static final class Item {
        Kind kind;
        String text;
        ToggleDef def;
        Mark mark;
        int y, h;
    }

    private static final int SEC_H = 14;
    private static final int GAP = 4;
    private static final int URL_H = 44;

    private final Screen parent;
    private final BlueMarkConfig cfg = BlueMarkConfig.get();
    private Tab tab = Tab.GENERAL;
    private String query = "";

    private final List<ToggleDef> toggles = List.of(
            new ToggleDef(Tab.GENERAL, "BlueMark", "Mod enabled", "Turn the icons on or off everywhere.",
                    () -> cfg.enabled, v -> cfg.enabled = v),
            new ToggleDef(Tab.GENERAL, "Display", "Nametag", "Show icons above player heads.",
                    () -> cfg.showOnNametag, v -> cfg.showOnNametag = v),
            new ToggleDef(Tab.GENERAL, "Display", "My nametag (F5)", "Show your own nametag in third person.",
                    () -> cfg.showOwnNametag, v -> cfg.showOwnNametag = v),
            new ToggleDef(Tab.GENERAL, "Display", "Chat", "Show icons before names in chat.",
                    () -> cfg.showInChat, v -> cfg.showInChat = v),
            new ToggleDef(Tab.GENERAL, "Display", "Tab list", "Show icons in the player list.",
                    () -> cfg.showInTab, v -> cfg.showInTab = v),
            new ToggleDef(Tab.ONLINE, "Sharing", "Share my icon", "Other BlueMark users see the icon you picked.",
                    () -> cfg.shareMark, v -> cfg.shareMark = v),
            new ToggleDef(Tab.ONLINE, "Sharing", "Show other players", "See the icons other BlueMark users picked.",
                    () -> cfg.showOthers, v -> cfg.showOthers = v)
    );

    private final List<Button> toggleButtons = new ArrayList<>();
    private final List<Button> iconButtons = new ArrayList<>();
    private Button colorButton;
    private EditBox searchBox;
    private EditBox urlBox;

    private final List<Item> items = new ArrayList<>();

    // layout
    private int px1, py1, px2, py2, sideW, cx1, cx2, cardTop;
    private int iconCell, iconStartX;
    private String version = "", mcVersion = "";
    private long lastRefresh = 0;

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
            ty += 13;
        }

        // search box (input handling only, the text is drawn by this screen)
        int searchW = Math.min(170, (cx2 - cx1) * 2 / 3);
        searchBox = new EditBox(this.font, cx1 + 14, py1 + 7, searchW - 16, 14, Component.empty());
        searchBox.setMaxLength(32);
        searchBox.setValue(query);
        searchBox.setResponder(s -> {
            query = s;
            relayout();
        });
        addWidget(searchBox);

        // server url box
        urlBox = new EditBox(this.font, cx1 + 14, cardTop, cx2 - cx1 - 28, 14, Component.empty());
        urlBox.setMaxLength(200);
        urlBox.setValue(cfg.serverUrl == null ? "" : cfg.serverUrl);
        urlBox.setResponder(s -> {
            cfg.serverUrl = s.trim();
            BlueMarkConfig.save();
        });
        addWidget(urlBox);

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
                y = add(y, Kind.SECTION, "Server", null, null, SEC_H, GAP);
                y = add(y, Kind.URL, null, null, null, URL_H, GAP);
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
                y = add(y, Kind.SECTION, "Players using BlueMark", null, null, SEC_H, GAP);
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
                    y = add(y + 4, Kind.NOTE,
                            cfg.serverUrl == null || cfg.serverUrl.isBlank()
                                    ? "Set a server URL in the Online tab to see other players."
                                    : "No other BlueMark users in this world yet.",
                            null, null, 12, 0);
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
        urlBox.visible = false;
        urlBox.active = false;

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
                case URL -> {
                    urlBox.setX(cx1 + 14);
                    urlBox.setY(it.y + 21);
                    urlBox.visible = true;
                    urlBox.active = true;
                }
                default -> { }
            }
        }
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

        // everything below is painted over the (invisible) vanilla buttons
        g.fill(0, 0, this.width, this.height, UiTheme.BG);
        UiTheme.box(g, px1, py1, px2, py2, UiTheme.PANEL, UiTheme.LINE);

        drawBackButton(g, mouseX, mouseY);
        drawSidebar(g, mouseX, mouseY);
        drawHeader(g);

        // the player list changes while you look at it
        long now = System.currentTimeMillis();
        if (tab == Tab.PLAYERS && now - lastRefresh > 1000) {
            lastRefresh = now;
            relayout();
        }

        for (Item it : items) {
            switch (it.kind) {
                case SECTION -> drawSection(g, it);
                case TOGGLE -> drawToggle(g, it, mouseX, mouseY);
                case URL -> drawUrl(g, it);
                case STATUS -> drawStatus(g, it);
                case ICONS -> drawIcons(g, it, mouseX, mouseY);
                case COLOR -> drawColor(g, it, mouseX, mouseY);
                case PREVIEW -> drawPreview(g, it);
                case PLAYER -> drawPlayer(g, it);
                case NOTE -> label(g, it.text, cx1 + 2, it.y + 2, UiTheme.SUB);
            }
        }
    }

    private void drawBackButton(GuiGraphicsExtractor g, int mx, int my) {
        boolean hover = UiTheme.inside(mx, my, 8, py1 + 6, 24, py1 + 22);
        UiTheme.box(g, 8, py1 + 6, 24, py1 + 22, hover ? UiTheme.CARD_HOVER : UiTheme.HEADER, UiTheme.BORDER);
        label(g, "<", 8 + (16 - this.font.width("<")) / 2, py1 + 10, UiTheme.TEXT);
    }

    private void drawSidebar(GuiGraphicsExtractor g, int mx, int my) {
        // sidebar background + header strip
        g.fill(px1 + 1, py1 + 1, px1 + sideW, py2 - 1, UiTheme.SIDEBAR);
        g.fill(px1 + 1, py1 + 1, px1 + sideW, py1 + 28, UiTheme.HEADER);
        g.fill(px1 + sideW, py1 + 1, px1 + sideW + 1, py2 - 1, UiTheme.LINE);
        label(g, UiTheme.BOLD + "BlueMark", px1 + 10, py1 + 10, UiTheme.TEXT);

        boolean searching = !query.trim().isEmpty();
        int y = py1 + 38;
        for (Tab t : Tab.values()) {
            boolean active = !searching && t == tab;
            boolean hover = UiTheme.inside(mx, my, px1 + 4, y - 2, px1 + sideW - 4, y + 10);
            label(g, t.label, px1 + 10, y, active ? UiTheme.BLUE : (hover ? UiTheme.TEXT : UiTheme.TEXT));
            if (hover && !active) g.fill(px1 + 6, y + 9, px1 + 8, y + 10, UiTheme.BLUE);
            y += 13;
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
        UiTheme.box(g, sx, sy, sx + searchW, sy + 16, UiTheme.FIELD, UiTheme.FIELD);
        g.fill(sx, sy + 15, sx + searchW, sy + 16, UiTheme.BORDER);
        UiTheme.magnifier(g, sx + 4, sy + 4, UiTheme.SUB);

        String v = searchBox.getValue();
        int maxW = searchW - 22;
        if (v.isEmpty() && !searchBox.isFocused()) {
            label(g, "Search", sx + 16, sy + 4, UiTheme.SUB);
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

    private void drawToggle(GuiGraphicsExtractor g, Item it, int mx, int my) {
        ToggleDef t = it.def;
        boolean hover = UiTheme.inside(mx, my, cx1, it.y, cx2, it.y + it.h);
        UiTheme.box(g, cx1, it.y, cx2, it.y + it.h, hover ? UiTheme.CARD_HOVER : UiTheme.CARD, UiTheme.BORDER);
        cardTexts(g, it, t.title(), t.desc(), 36);
        UiTheme.toggle(g, cx2 - 30, it.y + it.h / 2 - 5, t.get().getAsBoolean());
    }

    private void drawUrl(GuiGraphicsExtractor g, Item it) {
        UiTheme.box(g, cx1, it.y, cx2, it.y + it.h, UiTheme.CARD, UiTheme.BORDER);
        label(g, UiTheme.BOLD + "Sharing server", cx1 + 10, it.y + 6, UiTheme.TEXT);
        label(g, "Address of your BlueMark server (see server/README.md).",
                cx1 + 10 + this.font.width(UiTheme.BOLD + "Sharing server") + 8, it.y + 6, UiTheme.SUB);

        int fx1 = cx1 + 10, fx2 = cx2 - 10, fy = it.y + 19;
        UiTheme.box(g, fx1, fy, fx2, fy + 18, UiTheme.FIELD, urlBox.isFocused() ? UiTheme.BLUE : UiTheme.BORDER);
        String v = urlBox.getValue();
        if (v.isEmpty() && !urlBox.isFocused()) {
            label(g, "https://your-server.example.com", fx1 + 4, fy + 5, UiTheme.SUB);
        } else {
            label(g, tail(v, fx2 - fx1 - 12) + caret(urlBox), fx1 + 4, fy + 5, UiTheme.TEXT);
        }
    }

    private void drawStatus(GuiGraphicsExtractor g, Item it) {
        UiTheme.box(g, cx1, it.y, cx2, it.y + it.h, UiTheme.CARD, UiTheme.BORDER);
        String s = MarkSync.status();
        cardTexts(g, it, "Status", s, 0);
        int dot = "Connected".equals(s) ? 0xFF3DDC84 : (s.startsWith("No server") ? 0xFF8E8E8E : 0xFFFFB020);
        g.fill(cx2 - 16, it.y + it.h / 2 - 3, cx2 - 10, it.y + it.h / 2 + 3, dot);
    }

    private void drawIcons(GuiGraphicsExtractor g, Item it, int mx, int my) {
        int color = 0xFF000000 | cfg.rgb();
        int cur = BlueMarkConfig.iconIndex(cfg.icon);

        UiTheme.box(g, cx1, it.y, cx2, it.y + it.h, UiTheme.CARD, UiTheme.BORDER);
        label(g, UiTheme.BOLD + "Icon", cx1 + 10, it.y + 6, UiTheme.TEXT);
        label(g, "Pick the symbol shown before your name.",
                cx1 + 10 + this.font.width(UiTheme.BOLD + "Icon") + 8, it.y + 6, UiTheme.SUB);

        int cellY = it.y + 20;
        for (int i = 0; i < BlueMarkConfig.ICONS.size(); i++) {
            int x = iconStartX + i * (iconCell + 4);
            boolean hover = UiTheme.inside(mx, my, x, cellY, x + iconCell, cellY + iconCell - 2);
            UiTheme.box(g, x, cellY, x + iconCell, cellY + iconCell - 2,
                    hover ? UiTheme.CARD_HOVER : 0xFF1B1B1B, i == cur ? UiTheme.BLUE : UiTheme.BORDER);
            String glyph = String.valueOf((char) (0xE000 + i));
            label(g, glyph, x + (iconCell - this.font.width(glyph)) / 2, cellY + (iconCell - 2 - 8) / 2, color);
        }
    }

    private void drawColor(GuiGraphicsExtractor g, Item it, int mx, int my) {
        int color = 0xFF000000 | cfg.rgb();
        boolean hover = UiTheme.inside(mx, my, cx1, it.y, cx2, it.y + it.h);
        UiTheme.box(g, cx1, it.y, cx2, it.y + it.h, hover ? UiTheme.CARD_HOVER : UiTheme.CARD, UiTheme.BORDER);
        cardTexts(g, it, "Color", cfg.color + "  -  click to open the color picker", 40);
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
