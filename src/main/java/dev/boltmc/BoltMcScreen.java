package dev.boltmc;

import dev.boltmc.friends.ChatRelay;
import dev.boltmc.friends.FriendsManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Bolt Mc settings: sidebar, search, sections, cards, switches — white theme, with small animations. */
public class BoltMcScreen extends Screen {

    private enum Tab {
        GENERAL("General"), COSMETICS("Cosmetics"), FRIENDS("Friends");
        final String label;
        Tab(String label) { this.label = label; }
    }

    private enum Kind { SECTION, MASTER, COSMETIC, ADD_FRIEND, FRIEND, STATUS, NOTE }

    private static final class Item {
        Kind kind;
        String text;
        Cosmetic cosmetic;
        BoltMcConfig.Friend friend;
        int y, h;
    }

    private static final int SEC_H = 14;
    private static final int GAP = 4;

    private final Screen parent;
    private final BoltMcConfig cfg = BoltMcConfig.get();
    private Tab tab = Tab.GENERAL;
    private String query = "";
    private String notice = "";
    private long noticeUntil = 0;

    private final List<Button> cosmeticToggleButtons = new ArrayList<>();
    private final List<Button> friendOpenButtons = new ArrayList<>();
    private final List<Button> friendRemoveButtons = new ArrayList<>();
    private Button masterToggle;
    private Button addFriendButton;
    private EditBox searchBox;
    private EditBox addFriendBox;

    private final List<Item> items = new ArrayList<>();
    private List<BoltMcConfig.Friend> friendsSnapshot = new ArrayList<>();

    private int px1, py1, px2, py2, sideW, cx1, cx2, cardTop;
    private String version = "", mcVersion = "";
    private long lastRefresh = 0;

    private long lastNs = 0;
    private float dt = 0;
    private float appear = 0;
    private float tabAnim = 0;
    private float markerY = -100;
    private final java.util.Map<String, Float> anims = new java.util.HashMap<>();

    public BoltMcScreen(Screen parent) {
        super(Component.literal("Bolt Mc"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        cosmeticToggleButtons.clear();
        friendOpenButtons.clear();
        friendRemoveButtons.clear();
        items.clear();
        appear = 0;
        tabAnim = 0;
        markerY = -100;

        version = FabricLoader.getInstance().getModContainer("boltmc")
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");
        mcVersion = FabricLoader.getInstance().getModContainer("minecraft")
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");

        px1 = 30; py1 = 16; px2 = this.width - 12; py2 = this.height - 16;
        sideW = 104;
        cx1 = px1 + sideW + 10;
        cx2 = px2 - 10;
        cardTop = py1 + 38;

        addRenderableWidget(Button.builder(Component.empty(), b -> onClose())
                .bounds(8, py1 + 6, 16, 16).build());

        int ty = py1 + 38;
        for (Tab t : Tab.values()) {
            final Tab target = t;
            addRenderableWidget(Button.builder(Component.empty(), b -> setTab(target))
                    .bounds(px1 + 4, ty - 2, sideW - 8, 12).build());
            ty += 14;
        }

        int searchW = Math.min(170, (cx2 - cx1) * 2 / 3);
        searchBox = new EditBox(this.font, cx1 + 14, py1 + 7, searchW - 16, 14, Component.empty());
        searchBox.setMaxLength(32);
        searchBox.setResponder(s -> { query = s; relayout(); });
        addWidget(searchBox);

        masterToggle = Button.builder(Component.empty(), b -> {
            cfg.enabled = !cfg.enabled;
            BoltMcConfig.save();
        }).bounds(cx1, cardTop, cx2 - cx1, 30).build();
        addRenderableWidget(masterToggle);

        for (Cosmetic c : Cosmetic.values()) {
            Button tbtn = Button.builder(Component.empty(), b -> {
                cfg.state(c).on = !cfg.state(c).on;
                BoltMcConfig.save();
            }).bounds(cx1, cardTop, 20, 10).build();
            cosmeticToggleButtons.add(tbtn);
            addRenderableWidget(tbtn);
        }

        addFriendBox = new EditBox(this.font, cx1 + 10, cardTop + 18, cx2 - cx1 - 90, 16, Component.empty());
        addFriendBox.setMaxLength(24);
        addWidget(addFriendBox);
        addFriendButton = Button.builder(Component.literal("Add"), b -> addFriend())
                .bounds(cx2 - 70, cardTop + 18, 60, 16).build();
        addRenderableWidget(addFriendButton);

        rebuildFriendButtons();
        relayout();
    }

    private void rebuildFriendButtons() {
        for (Button b : friendOpenButtons) removeWidget(b);
        for (Button b : friendRemoveButtons) removeWidget(b);
        friendOpenButtons.clear();
        friendRemoveButtons.clear();
        friendsSnapshot = FriendsManager.list();
        for (BoltMcConfig.Friend f : friendsSnapshot) {
            Button open = Button.builder(Component.empty(),
                    b -> this.minecraft.setScreenAndShow(new BoltMcChatScreen(this, f)))
                    .bounds(cx1, cardTop, cx2 - cx1 - 24, 18).build();
            friendOpenButtons.add(open);
            addRenderableWidget(open);

            Button remove = Button.builder(Component.literal("x"), b -> {
                FriendsManager.remove(f);
                rebuildFriendButtons();
                relayout();
            }).bounds(cx2 - 20, cardTop, 18, 18).build();
            friendRemoveButtons.add(remove);
            addRenderableWidget(remove);
        }
    }

    private void addFriend() {
        String name = addFriendBox.getValue().trim();
        if (name.isEmpty()) return;
        FriendsManager.AddResult r = FriendsManager.addByName(name);
        notice = switch (r) {
            case OK -> "Added " + name + ".";
            case ALREADY_FRIENDS -> name + " is already a friend.";
            case IS_YOU -> "That's you!";
            case NOT_FOUND -> name + " isn't online with you right now.";
        };
        noticeUntil = System.currentTimeMillis() + 3000;
        if (r == FriendsManager.AddResult.OK) {
            addFriendBox.setValue("");
            rebuildFriendButtons();
        }
        relayout();
    }

    private void setTab(Tab t) {
        if (t != tab || !query.isBlank()) tabAnim = 0;
        this.tab = t;
        this.query = "";
        searchBox.setValue("");
        setFocused(null);
        relayout();
    }

    private int add(int y, Kind kind, String text, Cosmetic c, BoltMcConfig.Friend f, int h, int gap) {
        Item it = new Item();
        it.kind = kind; it.text = text; it.cosmetic = c; it.friend = f; it.y = y; it.h = h;
        items.add(it);
        return y + h + gap;
    }

    private int build() {
        items.clear();
        int y = cardTop;
        switch (tab) {
            case GENERAL -> {
                y = add(y, Kind.SECTION, "Main", null, null, SEC_H, GAP);
                y = add(y, Kind.MASTER, null, null, null, 30, GAP);
                y = add(y, Kind.SECTION, "About", null, null, SEC_H, GAP);
                y = add(y, Kind.NOTE, "Bolt Mc v" + version + "  -  MC " + mcVersion + " [Fabric]", null, null, 12, 0);
            }
            case COSMETICS -> {
                y = add(y, Kind.SECTION, "Appearance", null, null, SEC_H, GAP);
                for (Cosmetic c : Cosmetic.values()) {
                    y = add(y, Kind.COSMETIC, null, c, null, 32, GAP);
                }
            }
            case FRIENDS -> {
                y = add(y, Kind.SECTION, "Add a friend", null, null, SEC_H, GAP);
                y = add(y, Kind.ADD_FRIEND, null, null, null, 38, GAP);
                y = add(y, Kind.SECTION, "Your friends", null, null, SEC_H, GAP);
                for (BoltMcConfig.Friend f : friendsSnapshot) {
                    y = add(y, Kind.FRIEND, null, null, f, 18, 3);
                }
                if (friendsSnapshot.isEmpty()) {
                    y = add(y, Kind.NOTE, "No friends added yet.", null, null, 12, GAP);
                }
                y = add(y, Kind.SECTION, "Relay", null, null, SEC_H, GAP);
                y = add(y, Kind.STATUS, null, null, null, 30, GAP);
            }
        }
        return y;
    }

    private void relayout() {
        if (masterToggle == null) return;
        build();

        masterToggle.visible = false;
        for (Button b : cosmeticToggleButtons) b.visible = false;
        addFriendBox.visible = false; addFriendBox.active = false;
        addFriendButton.visible = false;
        for (Button b : friendOpenButtons) b.visible = false;
        for (Button b : friendRemoveButtons) b.visible = false;

        int fi = 0;
        for (Item it : items) {
            switch (it.kind) {
                case MASTER -> {
                    masterToggle.setX(cx1); masterToggle.setY(it.y);
                    masterToggle.setWidth(cx2 - cx1); masterToggle.setHeight(it.h);
                    masterToggle.visible = true;
                }
                case COSMETIC -> {
                    int idx = it.cosmetic.ordinal();
                    Button tb = cosmeticToggleButtons.get(idx);
                    tb.setX(cx2 - 30); tb.setY(it.y + it.h / 2 - 5); tb.visible = true;
                }
                case ADD_FRIEND -> {
                    addFriendBox.setX(cx1 + 10); addFriendBox.setY(it.y + 18);
                    addFriendBox.visible = true; addFriendBox.active = true;
                    addFriendButton.setX(cx2 - 70); addFriendButton.setY(it.y + 18); addFriendButton.visible = true;
                }
                case FRIEND -> {
                    Button open = friendOpenButtons.get(fi);
                    open.setX(cx1); open.setY(it.y); open.setWidth(cx2 - cx1 - 22); open.setHeight(it.h);
                    open.visible = true;
                    Button rm = friendRemoveButtons.get(fi);
                    rm.setX(cx2 - 18); rm.setY(it.y); rm.visible = true;
                    fi++;
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

    private float hover(String key, boolean on) {
        float v = anims.getOrDefault(key, 0f);
        float step = dt / 0.14f;
        v = on ? Math.min(1f, v + step) : Math.max(0f, v - step);
        anims.put(key, v);
        return ease(v);
    }

    private float anim(String key, boolean on, float seconds) {
        float v = anims.getOrDefault(key, on ? 1f : 0f);
        float step = dt / seconds;
        v = on ? Math.min(1f, v + step) : Math.max(0f, v - step);
        anims.put(key, v);
        return ease(v);
    }

    // ------------------------------------------------------------------ drawing

    private void label(GuiGraphicsExtractor g, String s, int x, int y, int color) {
        g.text(this.font, s, x, y, color, false);
    }

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

        g.fill(0, 0, this.width, this.height, UiTheme.BG);
        UiTheme.box(g, px1, py1, px2, py2, UiTheme.PANEL, UiTheme.LINE);

        drawBackButton(g, mouseX, mouseY);
        drawSidebar(g, mouseX, mouseY);
        drawHeader(g);

        if (tab == Tab.FRIENDS && System.currentTimeMillis() - lastRefresh > 1000) {
            lastRefresh = System.currentTimeMillis();
        }

        int slide = Math.round((1f - te) * 8);
        for (Item it : items) {
            it.y += slide;
            switch (it.kind) {
                case SECTION -> drawSection(g, it);
                case MASTER -> drawMaster(g, it, mouseX, mouseY);
                case COSMETIC -> drawCosmetic(g, it, mouseX, mouseY);
                case ADD_FRIEND -> drawAddFriend(g, it);
                case FRIEND -> drawFriend(g, it, mouseX, mouseY);
                case STATUS -> drawStatus(g, it);
                case NOTE -> label(g, it.text, cx1 + 2, it.y + 2, UiTheme.SUB);
            }
            it.y -= slide;
        }

        if (!notice.isEmpty() && System.currentTimeMillis() < noticeUntil) {
            label(g, notice, cx1, py2 - 10, UiTheme.BLUE);
        }

        int a = Math.round((1f - te) * 255);
        if (a > 3) g.fill(cx1 - 9, py1 + 29, px2 - 1, py2 - 1, (a << 24) | 0xFFFFFF);
        int a2 = Math.round((1f - ease(appear)) * 255);
        if (a2 > 3) g.fill(0, 0, this.width, this.height, (a2 << 24) | 0xF2F3F5);
    }

    private void drawBackButton(GuiGraphicsExtractor g, int mx, int my) {
        float h = hover("back", UiTheme.inside(mx, my, 8, py1 + 6, 24, py1 + 22));
        UiTheme.box(g, 8, py1 + 6, 24, py1 + 22, UiTheme.mix(UiTheme.HEADER, UiTheme.CARD_HOVER, h),
                UiTheme.mix(UiTheme.BORDER, UiTheme.BLUE, h));
        label(g, "<", 8 + (16 - this.font.width("<")) / 2, py1 + 10, UiTheme.TEXT);
    }

    private void drawSidebar(GuiGraphicsExtractor g, int mx, int my) {
        g.fill(px1 + 1, py1 + 1, px1 + sideW, py2 - 1, UiTheme.SIDEBAR);
        g.fill(px1 + 1, py1 + 1, px1 + sideW, py1 + 28, UiTheme.HEADER);
        g.fill(px1 + sideW, py1 + 1, px1 + sideW + 1, py2 - 1, UiTheme.LINE);
        label(g, UiTheme.BOLD + "Bolt Mc", px1 + 10, py1 + 10, UiTheme.TEXT);

        int targetY = py1 + 38 + tab.ordinal() * 14 - 1;
        markerY = markerY < -50 ? targetY : markerY + (targetY - markerY) * Math.min(1f, dt * 16f);
        g.fill(px1 + 1, Math.round(markerY), px1 + 3, Math.round(markerY) + 10, UiTheme.BLUE);

        int y = py1 + 38;
        for (Tab t : Tab.values()) {
            boolean active = t == tab;
            boolean over = UiTheme.inside(mx, my, px1 + 4, y - 2, px1 + sideW - 4, y + 10);
            float a = anim("tab:" + t, active, 0.15f);
            float h = hover("tabhover:" + t, over && !active);
            int color = UiTheme.mix(UiTheme.mix(UiTheme.SUB, UiTheme.TEXT, 0.55f + 0.45f * h), UiTheme.BLUE, a);
            label(g, t.label, px1 + 10 + Math.round(2 * Math.max(a, h)), y, color);
            y += 14;
        }

        g.fill(px1 + 1, py2 - 24, px1 + sideW, py2 - 23, UiTheme.LINE);
        label(g, "Bolt Mc v" + version, px1 + 10, py2 - 18, UiTheme.SUB);
    }

    private void drawHeader(GuiGraphicsExtractor g) {
        g.fill(px1 + sideW + 1, py1 + 1, px2 - 1, py1 + 28, UiTheme.HEADER);
        int searchW = Math.min(170, (cx2 - cx1) * 2 / 3);
        int sx = cx1, sy = py1 + 6;
        float f = anim("searchfocus", searchBox.isFocused(), 0.15f);
        UiTheme.box(g, sx, sy, sx + searchW, sy + 16, UiTheme.FIELD, UiTheme.FIELD);
        g.fill(sx, sy + 15, sx + searchW, sy + 16, UiTheme.mix(UiTheme.BORDER, UiTheme.BLUE, f));
        UiTheme.magnifier(g, sx + 4, sy + 4, UiTheme.mix(UiTheme.SUB, UiTheme.TEXT, f));
        String v = searchBox.getValue();
        if (v.isEmpty() && !searchBox.isFocused()) {
            label(g, "Search settings", sx + 16, sy + 4, UiTheme.SUB);
        } else {
            label(g, tail(v, searchW - 22), sx + 16, sy + 4, UiTheme.TEXT);
        }
    }

    private String tail(String s, int maxW) {
        String t = s;
        while (!t.isEmpty() && this.font.width(t) > maxW) t = t.substring(1);
        return t;
    }

    private void drawSection(GuiGraphicsExtractor g, Item it) {
        int w = this.font.width(it.text);
        int mid = (cx1 + cx2) / 2;
        int tx = mid - w / 2;
        int ly = it.y + 4;
        g.fill(cx1, ly, tx - 8, ly + 1, UiTheme.BORDER);
        g.fill(tx + w + 8, ly, cx2, ly + 1, UiTheme.BORDER);
        label(g, it.text, tx, it.y, UiTheme.SUB);
    }

    private float card(GuiGraphicsExtractor g, String key, int x1, int y1, int x2, int y2, int mx, int my) {
        float h = hover(key, UiTheme.inside(mx, my, x1, y1, x2, y2));
        UiTheme.box(g, x1, y1, x2, y2, UiTheme.mix(UiTheme.CARD, UiTheme.CARD_HOVER, h),
                UiTheme.mix(UiTheme.BORDER, UiTheme.BLUE, h));
        return h;
    }

    private void drawMaster(GuiGraphicsExtractor g, Item it, int mx, int my) {
        card(g, "master", cx1, it.y, cx2, it.y + it.h, mx, my);
        label(g, UiTheme.BOLD + "Enable Bolt Mc", cx1 + 10, it.y + 7, UiTheme.TEXT);
        label(g, "Master switch for cosmetics and friends chat.", cx1 + 10, it.y + 18, UiTheme.SUB);
        float a = anim("sw:master", cfg.enabled, 0.16f);
        UiTheme.toggle(g, cx2 - 30, it.y + it.h / 2 - 5, a);
    }

    private void drawCosmetic(GuiGraphicsExtractor g, Item it, int mx, int my) {
        Cosmetic c = it.cosmetic;
        card(g, "cos:" + c, cx1, it.y, cx2, it.y + it.h, mx, my);
        int color = 0xFF000000 | cfg.rgb(c);
        label(g, UiTheme.BOLD + c.label, cx1 + 10, it.y + 6, UiTheme.TEXT);
        label(g, c.desc, cx1 + 10, it.y + 17, UiTheme.SUB);

        // swatch opens the color picker (the real button sits here invisibly)
        int swX = cx2 - 76, swY = it.y + it.h / 2 - 7;
        boolean overSwatch = UiTheme.inside(mx, my, swX, swY, swX + 30, swY + 14);
        UiTheme.box(g, swX, swY, swX + 30, swY + 14, color, overSwatch ? UiTheme.BLUE : UiTheme.BORDER);

        float a = anim("sw:cos:" + c, cfg.state(c).on, 0.16f);
        UiTheme.toggle(g, cx2 - 30, it.y + it.h / 2 - 5, a);
    }

    private void drawAddFriend(GuiGraphicsExtractor g, Item it) {
        UiTheme.box(g, cx1, it.y, cx2, it.y + it.h, UiTheme.CARD, UiTheme.BORDER);
        label(g, "Type a name of a player you're online with, then press Add.", cx1 + 10, it.y + 5, UiTheme.SUB);
        int fx1 = cx1 + 10, fx2 = cx2 - 76, fy = it.y + 18;
        UiTheme.box(g, fx1, fy, fx2, fy + 16, UiTheme.FIELD, addFriendBox.isFocused() ? UiTheme.BLUE : UiTheme.BORDER);
        String v = addFriendBox.getValue();
        if (v.isEmpty() && !addFriendBox.isFocused()) {
            label(g, "Player name", fx1 + 4, fy + 4, UiTheme.SUB);
        } else {
            label(g, tail(v, fx2 - fx1 - 8), fx1 + 4, fy + 4, UiTheme.TEXT);
        }
    }

    private void drawFriend(GuiGraphicsExtractor g, Item it, int mx, int my) {
        card(g, "friend:" + it.friend.uuid, cx1, it.y, cx2 - 22, it.y + it.h, mx, my);
        label(g, it.friend.name, cx1 + 8, it.y + 5, UiTheme.TEXT);
        boolean overRemove = UiTheme.inside(mx, my, cx2 - 18, it.y, cx2, it.y + it.h);
        UiTheme.box(g, cx2 - 18, it.y, cx2, it.y + it.h, overRemove ? 0xFFFFE3E3 : UiTheme.CARD,
                overRemove ? 0xFFE5484D : UiTheme.BORDER);
        label(g, "x", cx2 - 18 + (18 - this.font.width("x")) / 2, it.y + it.h / 2 - 4,
                overRemove ? 0xFFE5484D : UiTheme.SUB);
    }

    private void drawStatus(GuiGraphicsExtractor g, Item it) {
        UiTheme.box(g, cx1, it.y, cx2, it.y + it.h, UiTheme.CARD, UiTheme.BORDER);
        String s = ChatRelay.status();
        String shown = switch (s) {
            case "Connected" -> "Connected to the relay.";
            case "Not set up" -> "Not available in this build of the mod.";
            default -> s;
        };
        label(g, UiTheme.BOLD + "Relay", cx1 + 10, it.y + 7, UiTheme.TEXT);
        label(g, shown, cx1 + 10, it.y + 18, UiTheme.SUB);
        int dot = "Connected".equals(s) ? 0xFF2FB344 : (s.startsWith("Not set up") ? UiTheme.SUB : 0xFFE09B1A);
        g.fill(cx2 - 16, it.y + it.h / 2 - 3, cx2 - 10, it.y + it.h / 2 + 3, dot);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // the color swatch on a cosmetic card opens the color picker (drawn, not a real widget, to keep one card look)
        if (tab == Tab.COSMETICS) {
            for (Item it : items) {
                if (it.kind != Kind.COSMETIC) continue;
                int swX = cx2 - 76, swY = it.y + it.h / 2 - 7;
                if (UiTheme.inside((int) mouseX, (int) mouseY, swX, swY, swX + 30, swY + 14)) {
                    this.minecraft.setScreenAndShow(new BoltMcColorScreen(this, it.cosmetic));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        BoltMcConfig.save();
        this.minecraft.setScreenAndShow(parent);
    }
}
