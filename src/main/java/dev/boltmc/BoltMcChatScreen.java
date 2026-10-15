package dev.boltmc;

import dev.boltmc.friends.ChatMessage;
import dev.boltmc.friends.ChatRelay;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Simple text chat with one friend. */
public class BoltMcChatScreen extends Screen {

    private final Screen parent;
    private final BoltMcConfig.Friend friend;

    private int px1, py1, px2, py2;
    private EditBox input;
    private int scroll = 0; // lines scrolled up from the bottom

    public BoltMcChatScreen(Screen parent, BoltMcConfig.Friend friend) {
        super(Component.literal("Chat with " + friend.name));
        this.parent = parent;
        this.friend = friend;
    }

    @Override
    protected void init() {
        px1 = 30; py1 = 16; px2 = this.width - 12; py2 = this.height - 16;

        addRenderableWidget(Button.builder(Component.empty(), b -> onClose())
                .bounds(8, py1 + 6, 16, 16).build());

        input = new EditBox(this.font, px1 + 12, py2 - 30, px2 - px1 - 90, 18, Component.empty());
        input.setMaxLength(240);
        addWidget(input);
        setInitialFocus(input);

        addRenderableWidget(Button.builder(Component.literal("Send"), b -> send())
                .bounds(px2 - 70, py2 - 30, 58, 18).build());
    }

    private void send() {
        String text = input.getValue().trim();
        if (text.isEmpty()) return;
        ChatRelay.send(friend.uuid, text);
        input.setValue("");
        scroll = 0;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        if (keyCode == 257 || keyCode == 335) { // Enter / numpad Enter
            send();
            return true;
        }
        return super.keyPressed(event);
    }

    private static String plain(FormattedCharSequence seq) {
        StringBuilder sb = new StringBuilder();
        seq.accept((index, style, codePoint) -> {
            sb.appendCodePoint(codePoint);
            return true;
        });
        return sb.toString();
    }

    private void label(GuiGraphicsExtractor g, String s, int x, int y, int color) {
        g.text(this.font, s, x, y, color, false);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);

        g.fill(0, 0, this.width, this.height, UiTheme.BG);
        UiTheme.box(g, px1, py1, px2, py2, UiTheme.PANEL, UiTheme.LINE);

        boolean hover = UiTheme.inside(mouseX, mouseY, 8, py1 + 6, 24, py1 + 22);
        UiTheme.box(g, 8, py1 + 6, 24, py1 + 22, hover ? UiTheme.CARD_HOVER : UiTheme.HEADER, UiTheme.BORDER);
        label(g, "<", 8 + (16 - this.font.width("<")) / 2, py1 + 10, UiTheme.TEXT);

        g.fill(px1 + 1, py1 + 1, px2 - 1, py1 + 26, UiTheme.HEADER);
        g.fill(px1 + 1, py1 + 25, px2 - 1, py1 + 26, UiTheme.LINE);
        label(g, UiTheme.BOLD + friend.name, px1 + 12, py1 + 9, UiTheme.TEXT);

        int msgTop = py1 + 32;
        int msgBottom = py2 - 38;
        List<ChatMessage> msgs = ChatRelay.historyWith(friend.uuid);

        List<String> lines = new ArrayList<>();
        List<Boolean> mine = new ArrayList<>();
        int wrapW = px2 - px1 - 40;
        for (ChatMessage m : msgs) {
            for (FormattedCharSequence l : this.font.split(Component.literal(m.text()), wrapW)) {
                lines.add(plain(l));
                mine.add(m.mine());
            }
        }

        int rows = (msgBottom - msgTop) / 12;
        int maxScroll = Math.max(0, lines.size() - rows);
        scroll = Math.min(scroll, maxScroll);
        int start = Math.max(0, lines.size() - rows - scroll);
        int y = msgTop;
        for (int i = start; i < lines.size() && y < msgBottom; i++) {
            boolean isMine = mine.get(i);
            label(g, lines.get(i), isMine ? px2 - 20 - this.font.width(lines.get(i)) : px1 + 12, y,
                    isMine ? UiTheme.BLUE : UiTheme.TEXT);
            y += 12;
        }
        if (lines.isEmpty()) {
            label(g, "Say hi to " + friend.name + "!", px1 + 12, msgTop, UiTheme.SUB);
        }

        String st = ChatRelay.status();
        label(g, st, px1 + 12, py2 - 44, "Connected".equals(st) ? UiTheme.SUB : 0xFFCC5500);

        g.fill(px1 + 1, py2 - 36, px2 - 1, py2 - 35, UiTheme.LINE);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreenAndShow(parent);
    }
}
