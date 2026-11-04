package forge.screens.match;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Rectangle;
import forge.Forge;
import forge.Graphics;
import forge.assets.FSkinFont;
import forge.menu.FDropDown;
import forge.menu.FMenuBar;
import forge.menu.FMenuItem;
import forge.menu.FMenuTab;
import forge.menu.FPopupMenu;
import forge.adventure.util.BattleUiSettings;
import forge.toolbox.FDialog;
import forge.toolbox.FDisplayObject;
import forge.toolbox.FOverlay;
import forge.toolbox.FScrollPane.ScrollBounds;
import forge.util.Utils;
import java.lang.reflect.Field;

/** Floating access to the native match tabs. No dependency on the other HUD mods. */
public final class MatchMenuMod {
    private static final Color GLASS = new Color(0x121823d9);
    private static final Color HOVER = new Color(0x253044ef);
    private static final Color PRESSED = new Color(0x35435aff);
    private static final Color GOLD = new Color(0xe0b24fff);
    private static final Color TEXT = new Color(0xf2f0eaff);
    private static final Color SEPARATOR = new Color(0xe0b24f26);
    private static final Field TAB_TEXT = field("text");
    private static final Field TAB_ICON = field("iconOnly");

    private MatchMenuMod() {}

    private static Field field(String name) {
        try {
            Field f = FMenuTab.class.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Match menu tab API changed: " + name, e);
        }
    }

    private static String text(FMenuTab tab) {
        try { return (String) TAB_TEXT.get(tab); }
        catch (IllegalAccessException e) { throw new IllegalStateException(e); }
    }

    private static String label(FMenuTab tab) {
        try { return TAB_ICON.getBoolean(tab) ? "Show/Hide cards" : text(tab); }
        catch (IllegalAccessException e) { throw new IllegalStateException(e); }
    }

    private static boolean active(MatchScreen screen) {
        return Forge.isLandscapeMode() && screen.getHeader() instanceof FMenuBar
                && screen.getHeader().getHeight() == 0f;
    }

    /** rounded box with a rim, drawn as two opaque fills (Forge's drawRoundRect draws cross lines, see forge-src-1009
     *  Graphics.drawRoundRectShapes; translucent fillRoundRect shows seams where its parts overlap) */
    static void rimBox(Graphics g, Color rim, Color fill, float x, float y, float w, float h, float r, float line) {
        g.fillRoundRect(opaque(rim), x, y, w, h, r);
        g.fillRoundRect(opaque(fill), x + line, y + line, Math.max(0f, w - 2 * line), Math.max(0f, h - 2 * line), Math.max(0f, r - line));
    }
    private static Color opaque(Color c) { return c.a >= 1f ? c : new Color(c.r * c.a, c.g * c.a, c.b * c.a, 1f); }

    private static MenuButton button(MatchScreen screen) {
        for (FDisplayObject child : screen.getChildren()) {
            if (child instanceof MenuButton b) return b;
        }
        return null;
    }

    /** Injected after MatchScreen.doLayout(FFF), after the native HUD layout. */
    public static void layout(MatchScreen screen) {
        layoutChoiceButton(screen);
        MenuButton b = button(screen);
        if (!active(screen)) {
            if (b != null) {
                b.popup.hide();
                b.setVisible(false);
                b.pressed = false;
                if (b.headerHidden) {
                    screen.getHeader().setVisible(b.headerWasVisible);
                    b.headerHidden = false;
                }
            }
            return;
        }
        if (b == null) b = screen.add(new MenuButton(screen));
        if (!b.headerHidden) {
            b.headerWasVisible = screen.getHeader().isVisible();
            b.headerHidden = true;
        }
        // The original bar retains height zero and all its tab visibility/action state.
        // Hiding its drawing also removes the native one-pixel selection/separator edges.
        screen.getHeader().setVisible(false);
        float size = Math.max(Utils.scale(38f), Math.min(Utils.scale(46f), screen.getHeight() * .062f)) * sideSize();
        Rectangle boundary = screen.getDropDownBoundary();
        float gap = Math.max(4f, size * .16f);
        b.setBounds(boundary.x + gap, boundary.y + gap, size, size);
        b.syncScreenPosition();
        b.anchorTabs();
        b.setVisible(true);
        if (b.popup.isVisible()) b.popup.update();
    }

    // ---------------------------------------------------------------- "Back to the choice" (user 2026-10-18)
    // A choice dialog can be swiped down to look at the battlefield; only a thin bar at the bottom brought it back.
    // While a dialog is swiped away, a big button in the middle of the screen restores it.
    private static void layoutChoiceButton(MatchScreen screen) {
        ChoiceButton c = null;
        for (FDisplayObject child : screen.getChildren()) if (child instanceof ChoiceButton x) c = x;
        if (c == null) c = screen.add(new ChoiceButton());
        TaxBadges tax = null;
        for (FDisplayObject child : screen.getChildren()) if (child instanceof TaxBadges x) tax = x;
        if (tax == null) tax = screen.add(new TaxBadges(screen));
        tax.setBounds(0, 0, screen.getWidth(), screen.getHeight());
        float w = screen.getWidth(), h = screen.getHeight();
        float bw = Math.max(Utils.scale(150f), w * .17f), bh = Math.max(Utils.scale(30f), h * .052f);
        c.setBounds((w - bw) / 2f, h * .625f - bh, bw, bh);
    }

    // ---------------------------------------------------------------- commander tax (user 2026-10-18)
    /** "TAX +n" on every commander in a command zone (both players): n = 2 x casts from the command zone so far */
    private static final class TaxBadges extends FDisplayObject {
        private final MatchScreen screen;
        private TaxBadges(MatchScreen screen) { this.screen = screen; }
        @Override public void buildTouchListeners(float x, float y, java.util.List<FDisplayObject> listeners) { }   // display only
        @Override public void draw(Graphics g) {
            try {
                for (forge.screens.match.views.VPlayerPanel p : screen.getPlayerPanelsList()) {
                    forge.game.player.PlayerView pv = p.getPlayer();
                    var zone = p.getCommandZone();
                    if (pv == null || zone == null || !zone.isVisible()) continue;
                    for (FDisplayObject child : zone.getChildren()) {
                        if (!(child instanceof forge.toolbox.FCardPanel cp) || !cp.isVisible() || cp.getCard() == null || !cp.getCard().isCommander()) continue;
                        Rectangle r = cp.screenPos;
                        if (r.width < 20f || r.height < 20f) continue;
                        int tax = 2 * pv.getCommanderCast(cp.getCard());
                        String label = "TAX +" + tax;
                        float h = Math.max(Utils.scale(18f), Math.min(r.height * .17f, Utils.scale(32f)));
                        FSkinFont f = FSkinFont.forHeight(h * .66f);
                        float w = Math.min(r.width, f.getBounds(label).width + h * 1.2f);
                        boolean topPlayer = p == screen.getTopPlayerPanel();
                        float x = r.x - screenPos.x + (r.width - w) / 2f, y = topPlayer ? r.y - screenPos.y + r.height + Utils.scale(2f)   // opponent: below the card (above it sits the menu button)
                                : Math.max(0f, r.y - screenPos.y - h - Utils.scale(2f));   // you: just above the card, title stays readable
                        rimBox(g, tax > 0 ? GOLD : new Color(0x8a93a7ff), new Color(0x0b0f17ff), x, y, w, h, h / 2f, Math.max(1f, h * .08f));
                        g.drawText(label, f, tax > 0 ? GOLD : new Color(0xb8c2d1ff), x, y, w, h, false, 1, true);
                    }
                }
            } catch (Throwable ignored) { }
        }
    }

    /** the open choice dialog when it is swiped away (not revealed), else null */
    private static FDialog collapsedDialog() {
        FOverlay top = FOverlay.getTopOverlay();
        return top instanceof FDialog d && d.isVisible() && !d.preventInputBehindOverlay() ? d : null;
    }

    /** drawn by the match screen above the overlays; the clicks go to a ChoiceTap inside the dialog, because a
     *  (swiped-away) dialog still takes every touch before the screen gets it */
    private static final class ChoiceButton extends FDisplayObject {
        @Override protected boolean drawAboveOverlay() { return true; }
        @Override public void draw(Graphics g) {
            FDialog d = collapsedDialog();
            if (d == null) return;
            ChoiceTap t = null;
            for (FDisplayObject child : d.getChildren()) if (child instanceof ChoiceTap x) t = x;
            if (t == null) t = d.add(new ChoiceTap(d));
            t.setBounds(screenPos.x - d.screenPos.x, screenPos.y - d.screenPos.y, getWidth(), getHeight());
            t.screenPos.set(screenPos);   // hit test right away, even before the dialog draws it
            float w = getWidth(), h = getHeight(), r = h * .3f;
            float pulse = .5f + .5f * (float) Math.sin(System.currentTimeMillis() / 260.0);
            g.fillRoundRect(new Color(0x00000066), w * .01f, h * .08f, w, h, r);
            rimBox(g, GOLD, t.pressed ? PRESSED : t.isHovered() ? HOVER : GLASS, 0, 0, w, h, r, Math.max(1.5f, h * (.05f + .03f * pulse)));
            g.drawText("Back to the choice", FSkinFont.forHeight(h * .42f), GOLD, 0, 0, w, h, false, 1, true);
            requestRendering();   // keep the pulse running
        }
    }

    /** invisible click area inside the dialog; only active while the dialog is swiped away */
    private static final class ChoiceTap extends FDisplayObject {
        private final FDialog dialog;
        private boolean pressed;
        private ChoiceTap(FDialog dialog) { this.dialog = dialog; }
        @Override public void buildTouchListeners(float x, float y, java.util.List<FDisplayObject> listeners) {
            if (collapsedDialog() == dialog) super.buildTouchListeners(x, y, listeners);
        }
        @Override public boolean press(float x, float y) { pressed = true; requestRendering(); return true; }
        @Override public boolean release(float x, float y) { pressed = false; requestRendering(); return true; }
        @Override public boolean tap(float x, float y, int count) {
            pressed = false;
            if (collapsedDialog() == dialog) dialog.fling(0f, -Utils.scale(2400f));   // the dialog's own upward swipe: reveal animation
            requestRendering();
            return true;
        }
        @Override public void draw(Graphics g) { }   // ChoiceButton draws it above the overlay
    }

    // ---------------------------------------------------------------- battle settings in the duel (user 2026-10-18)
    // Same sizes and keys as Rogue Commanders Settings > Battle UI / Keys; applied right away, saved on close.
    private static void openBattleSettings(MatchScreen screen) { new BattleSettings(screen).show(); }

    private static final class BattleSettings extends FOverlay {
        private static final Color BACK = new Color(0x05070cb4), PANEL = new Color(0x121823f5), CELL = new Color(0x1c2434ff),
                BORDER = new Color(0x5b6378ff), MUTED = new Color(0xaab2c4ff), LISTEN = new Color(0x4a3a12ff);
        private final MatchScreen screen;
        private final java.util.List<Cell> minus = new java.util.ArrayList<>(), plus = new java.util.ArrayList<>(), keys = new java.util.ArrayList<>();
        private final Cell resetSizes, resetKeys, close;
        private int listening = -1;
        private float scale = 1f, px, py, pw, ph, row, pad, colW, top;

        BattleSettings(MatchScreen screen) {
            this.screen = screen;
            for (int i = 0; i < BattleUiSettings.SIZE_IDS.length; i++) {
                final int k = i;
                minus.add(add(new Cell(() -> "-", () -> step(k, -.05f), -1)));
                plus.add(add(new Cell(() -> "+", () -> step(k, .05f), -1)));
            }
            for (int i = 0; i < BattleUiSettings.ACTIONS.length; i++) {
                final int k = i;
                keys.add(add(new Cell(() -> listening == k ? "Press a key ..." : BattleUiSettings.keyName(k),
                        () -> { listening = listening == k ? -1 : k; requestRendering(); }, k)));
            }
            resetSizes = add(new Cell(() -> "Reset sizes", () -> { BattleUiSettings.resetSizes(); applied(); }, -1));
            resetKeys = add(new Cell(() -> "Reset keys", () -> { BattleUiSettings.resetKeys(); listening = -1; requestRendering(); }, -1));
            close = add(new Cell(() -> "Close", this::close, -1));
            close.gold = true;
        }

        private void step(int group, float d) { BattleUiSettings.setSize(group, BattleUiSettings.size(group) + d); applied(); }
        private void applied() { screen.revalidate(); requestRendering(); }
        private void close() { BattleUiSettings.save(); hide(); screen.revalidate(); }

        @Override protected void doLayout(float w, float h) {
            scale = Math.max(.65f, Math.min(2.5f, Math.min(w / 1600f, h / 900f)));
            pad = 22f * scale; row = 34f * scale;
            float gap = 7f * scale;
            int rows = BattleUiSettings.ACTIONS.length + 2;   // heading + keys + reset
            pw = Math.min(w - 2 * pad, 1180f * scale);
            ph = pad + 46f * scale + 30f * scale + rows * (row + gap) + row * 1.3f + pad * 1.6f;
            ph = Math.min(ph, h - pad);
            px = (w - pw) / 2f; py = Math.max(pad * .5f, (h - ph) / 2f);
            colW = (pw - 3 * pad) / 2f;
            top = py + pad + 46f * scale + 30f * scale;
            float lx = px + pad, y = top + row + gap, cb = row * 1.25f, vw = 70f * scale;
            for (int i = 0; i < minus.size(); i++) {
                float right = lx + colW;
                plus.get(i).setBounds(right - cb, y, cb, row);
                minus.get(i).setBounds(right - cb - vw - cb, y, cb, row);
                y += row + gap;
            }
            resetSizes.setBounds(lx + colW - 200f * scale, y + gap, 200f * scale, row);
            float rx = px + 2 * pad + colW;
            y = top + row + gap;
            for (Cell k : keys) { k.setBounds(rx + colW * .6f, y, colW * .4f, row); y += row + gap; }
            resetKeys.setBounds(rx + colW * .6f, y + gap * .5f, colW * .4f, row);
            close.setBounds(px + (pw - 240f * scale) / 2f, py + ph - pad - row * 1.3f, 240f * scale, row * 1.3f);
        }

        @Override protected void drawBackground(Graphics g) {
            g.fillRect(BACK, 0, 0, getWidth(), getHeight());
            float r = 14f * scale;
            rimBox(g, GOLD, PANEL, px, py, pw, ph, r, Math.max(1.5f, 2f * scale));
            g.drawText("Battle settings", font(26), GOLD, px + pad, py + pad * .5f, pw - 2 * pad, 46f * scale, false, 8, true);
            String hint = listening >= 0
                    ? "Press the new key for \"" + BattleUiSettings.ACTION_LABELS[listening] + "\"  -  Backspace clears it, Esc cancels."
                    : "Changes apply right away and are saved when you close. Click a key to change it.";
            g.drawText(hint, font(14), listening >= 0 ? GOLD : MUTED, px + pad, py + pad * .5f + 46f * scale, pw - 2 * pad, 30f * scale, false, 8, true);
            float lx = px + pad, rx = px + 2 * pad + colW;
            g.drawText("Sizes", font(19), GOLD, lx, top, colW, row, false, 8, true);
            g.drawText("Keys", font(19), GOLD, rx, top, colW, row, false, 8, true);
            for (int i = 0; i < minus.size(); i++) {
                Cell m = minus.get(i), p = plus.get(i);
                g.drawText(BattleUiSettings.SIZE_LABELS[i], font(15), TEXT, lx, m.getTop(), m.getLeft() - lx - pad * .5f, row, false, 8, true);
                g.drawText(Math.round(BattleUiSettings.size(i) * 100) + "%", font(16), TEXT, m.getRight(), m.getTop(), p.getLeft() - m.getRight(), row, false, 1, true);
            }
            for (int i = 0; i < keys.size(); i++) {
                Cell k = keys.get(i);
                g.drawText(BattleUiSettings.ACTION_LABELS[i], font(15), TEXT, rx, k.getTop(), colW * .58f, row, false, 8, true);
            }
        }

        @Override public boolean keyDown(int key) {
            if (listening >= 0) {
                if (key == com.badlogic.gdx.Input.Keys.DEL || key == com.badlogic.gdx.Input.Keys.FORWARD_DEL) BattleUiSettings.clearKey(listening);
                else if (key != com.badlogic.gdx.Input.Keys.ESCAPE && key != com.badlogic.gdx.Input.Keys.BACK) BattleUiSettings.setKey(listening, key);
                listening = -1;
            } else if (key == com.badlogic.gdx.Input.Keys.ESCAPE || key == com.badlogic.gdx.Input.Keys.BACK) close();
            requestRendering();
            return true;   // no key reaches the duel while this is open
        }

        // layout already scales in screen pixels (as in DeckPeekMod)
        private FSkinFont font(int size) { return FSkinFont._get(Math.max(10, Math.round(size * scale))); }

        private final class Cell extends FDisplayObject {
            private final java.util.function.Supplier<String> label;
            private final Runnable action;
            private final int key;
            private boolean gold;
            Cell(java.util.function.Supplier<String> label, Runnable action, int key) { this.label = label; this.action = action; this.key = key; }
            @Override public boolean tap(float x, float y, int count) { action.run(); return true; }
            @Override public void draw(Graphics g) {
                boolean hot = key >= 0 && listening == key;
                float w = getWidth(), h = getHeight(), r = 8f * scale;
                rimBox(g, hot || gold ? GOLD : BORDER, hot ? LISTEN : isHovered() ? HOVER : CELL, 0, 0, w, h, r, Math.max(1f, 1.5f * scale));
                g.drawText(label.get(), font(16), hot || gold ? GOLD : TEXT, 4f * scale, 0, w - 8f * scale, h, false, 1, true);
            }
        }
    }

    /** Settings > Battle UI: side buttons size (user 2026-10-17) */
    private static float sideSize() { return forge.adventure.util.BattleUiSettings.size(2); }

    /** Injected after the virtual owner query in FDropDown.updateSizeAndPosition.
     *  This also covers VLog's owner override. Keep the real tab as owner so native
     *  scroll indicators, backdrop handling and tab actions retain their semantics. */
    public static FDisplayObject dropDownOwner(FDisplayObject original, FDropDown dropDown) {
        if (!(original instanceof FMenuTab)) return original;
        MatchScreen screen = dropDown.getDropDownContainer() instanceof MatchScreen s ? s
                : Forge.getCurrentScreen() instanceof MatchScreen s ? s : null;
        if (screen == null || !active(screen)) return original;
        boolean belongs = false;
        for (FDisplayObject tab : screen.getHeader().getChildren()) {
            if (tab == original) { belongs = true; break; }
        }
        if (!belongs) return original;
        MenuButton b = button(screen);
        if (b == null) return original;
        b.syncScreenPosition();
        b.anchorTabs();
        // A newly auto-opened Stack must stay accessible if the chooser was open.
        if (!dropDown.isVisible() && b.popup.isVisible()) b.popup.hide();
        return original;
    }

    private static void requestRendering() {
        if (Gdx.graphics != null) Gdx.graphics.requestRendering();
    }

    private static final class MenuButton extends FDisplayObject {
        private final MatchScreen screen;
        private final MenuPopup popup;
        private boolean pressed, headerHidden, headerWasVisible;

        private MenuButton(MatchScreen screen) {
            this.screen = screen;
            this.popup = new MenuPopup(this);
            popup.setDropDownContainer(screen);
        }

        private void syncScreenPosition() {
            // screenPos is normally populated by Graphics.draw. Seed it at layout
            // time too: Stack may open before the first rendered frame.
            screenPos.set(screen.screenPos.x + getLeft(), screen.screenPos.y + getTop(), getWidth(), getHeight());
        }

        private void anchorTabs() {
            for (FDisplayObject child : screen.getHeader().getChildren()) {
                if (child instanceof FMenuTab tab) {
                    tab.setBounds(getLeft() - screen.getHeader().getLeft(),
                            getTop() - screen.getHeader().getTop(), getWidth(), getHeight());
                    tab.screenPos.set(screenPos);
                }
            }
        }

        @Override protected boolean drawAboveOverlay() { return true; }
        @Override public boolean press(float x, float y) { pressed = true; requestRendering(); return true; }
        @Override public boolean release(float x, float y) { pressed = false; requestRendering(); return true; }
        @Override public boolean tap(float x, float y, int count) {
            pressed = false;
            if (!active(screen)) return false;
            if (popup.isVisible()) popup.hide();
            else popup.show(this, 0f, getHeight() + getWidth() * .12f);
            requestRendering();
            return true;
        }

        @Override public void draw(Graphics g) {
            if (!active(screen)) return;
            float d = getWidth(), c = d / 2f, r = c - 1f;
            g.fillCircle(new Color(0x00000040), c + d * .03f, c + d * .06f, r);
            g.fillCircle(pressed || popup.isVisible() ? PRESSED : isHovered() ? HOVER : GLASS, c, c, r);
            g.drawCircle(Math.max(1f, d * .025f), GOLD, c, c, r);
            for (int i = -1; i <= 1; i++) {          // three slim lines (the bold ones looked heavy)
                float y = c + i * d * .11f;
                g.drawLine(Math.max(1f, d * .018f), TEXT, d * .34f, y, d * .66f, y);
            }
            String stackText = text(screen.getStack().getMenuTab());
            int left = stackText.lastIndexOf('('), right = stackText.lastIndexOf(')');
            if (left >= 0 && right > left) {
                try {
                    int n = Integer.parseInt(stackText.substring(left + 1, right));
                    if (n > 0) {
                        float bd = d * .40f, bx = d * .77f, by = d * .20f;
                        g.fillCircle(GOLD, bx, by, bd / 2f);
                        g.drawText(n > 99 ? "99+" : Integer.toString(n), FSkinFont.get(10), GLASS,
                                bx - bd / 2f, by - bd / 2f, bd, bd, false, 1, true);
                    }
                } catch (NumberFormatException ignored) { /* a non-numeric native label */ }
            }
        }
    }

    private static final class MenuPopup extends FPopupMenu {
        private final MenuButton button;
        private MenuPopup(MenuButton button) { this.button = button; }

        @Override protected void buildMenu() {
            for (FDisplayObject child : button.screen.getHeader().getChildren()) {
                if (child instanceof FMenuTab tab && tab.isVisible()) {
                    addItem(new MenuEntry(label(tab), tab, this));
                }
            }
            // battlefield zoom (the floating "- Auto +" buttons are gone in this layout)
            addItem(new ActionEntry("Zoom in", () -> battleZoom("in"), this));
            addItem(new ActionEntry("Zoom out", () -> battleZoom("out"), this));
            addItem(new ActionEntry("Auto zoom", () -> battleZoom("reset"), this));
            addItem(new ActionEntry("Battle settings", () -> openBattleSettings(button.screen), this));   // sizes + keys in the duel (user 2026-10-18)
            // "View deck" moved to the Deck button above the mana tile (user 2026-10-16)
            // concede (Forge's own game menu action), with a confirmation because it ends the duel as a loss
            addItem(new ActionEntry("Concede", MenuPopup::confirmConcede, this));
        }

        private static void confirmConcede() {
            forge.toolbox.FOptionPane.showConfirmDialog("Concede this game? It counts as a loss.", "Concede", "Concede", "Keep playing",
                    yes -> { if (Boolean.TRUE.equals(yes)) forge.util.ThreadUtil.invokeInGameThread(() -> forge.screens.match.MatchController.instance.concede()); });
        }

        @Override protected ScrollBounds updateAndGetPaneSize(float maxWidth, float maxHeight) {
            clearItems();
            buildMenu();
            float padding = button.getWidth() * .12f;
            float width = Math.min(maxWidth, Math.max(Utils.scale(190f), determineMenuWidth() + 2f * padding));
            float row = button.getWidth() * .82f, y = padding;
            for (FMenuItem item : items) {
                item.setBounds(padding, y, Math.max(0f, width - 2f * padding), row);
                y += row;
            }
            return new ScrollBounds(width, y + padding);
        }

        @Override protected void updateSizeAndPosition() {
            button.syncScreenPosition();
            Rectangle boundary = button.screen.getDropDownBoundary();
            float x = button.screenPos.x, y = button.screenPos.y + button.getHeight() * 1.12f;
            float bottom = boundary.y + boundary.height;
            paneSize = updateAndGetPaneSize(boundary.width, Math.max(1f, bottom - y));
            x = Math.max(boundary.x, Math.min(x, boundary.x + boundary.width - paneSize.getWidth()));
            setBounds(Math.round(x), Math.round(y), paneSize.getWidth(), Math.min(paneSize.getHeight(), Math.max(1f, bottom - y)));
        }

        @Override protected void drawBackground(Graphics g) {
            rimBox(g, GOLD, GLASS, 0, 0, getWidth(), getHeight(), button.getWidth() * .15f, Math.max(1f, button.getWidth() * .02f));
        }
        @Override protected void drawOverlay(Graphics g) {
            // rim drawn with the background (rimBox)
        }
    }

    /** BattleZoomMod (forge.adventure.util, installed by another step) by reflection */
    private static void battleZoom(String what) {
        try {
            Class<?> z = Class.forName("forge.adventure.util.BattleZoomMod");
            if (what.equals("reset")) z.getMethod("reset").invoke(null);
            else {
                float s = (Float) z.getMethod("scale").invoke(null);
                z.getMethod("setScale", float.class).invoke(null, what.equals("in") ? s * 1.10f : s / 1.10f);
            }
        } catch (Throwable ignored) { }
    }

    /** ArenaBattleHudMod.scaleHand (forge.adventure.util, installed by another step) by reflection */
    private static void handSize(float factor) {
        try {
            Class.forName("forge.adventure.util.ArenaBattleHudMod").getMethod("scaleHand", float.class).invoke(null, factor);
        } catch (Throwable ignored) { }
    }

    private static final class ActionEntry extends FMenuItem {
        private final Runnable action;
        private final MenuPopup popup;
        private boolean pressed;
        private ActionEntry(String label, Runnable action, MenuPopup popup) {
            super(label, e -> {}, true);
            this.action = action;
            this.popup = popup;
        }
        @Override public boolean press(float x, float y) { pressed = true; requestRendering(); return true; }
        @Override public boolean release(float x, float y) { pressed = false; requestRendering(); return true; }
        @Override public boolean tap(float x, float y, int count) { popup.hide(); action.run(); return true; }
        @Override public void draw(Graphics g) {
            float w = getWidth(), h = getHeight(), gap = h * .22f;
            if (pressed || isHovered()) g.fillRoundRect(pressed ? PRESSED : HOVER, 0, 0, w, h, h * .12f);
            g.drawText(getText(), FSkinFont.get(12), TEXT, gap, 0, w - gap * 2, h, false, 8, true);
            g.drawLine(1f, SEPARATOR, gap, h, w - gap, h);
        }
    }

    private static final class MenuEntry extends FMenuItem {
        private final FMenuTab tab;
        private final MenuPopup popup;
        private boolean pressed;
        private MenuEntry(String label, FMenuTab tab, MenuPopup popup) {
            super(label, e -> {}, tab.isEnabled());
            this.tab = tab;
            this.popup = popup;
        }
        @Override public boolean press(float x, float y) { pressed = true; requestRendering(); return true; }
        @Override public boolean release(float x, float y) { pressed = false; requestRendering(); return true; }
        @Override public boolean tap(float x, float y, int count) {
            popup.hide();
            // Call the real tab, including overrides and show/hide behavior.
            if (active(popup.button.screen) && tab.isVisible() && tab.isEnabled()) tab.tap(0f, 0f, count);
            return true;
        }
        @Override public void draw(Graphics g) {
            float w = getWidth(), h = getHeight(), gap = h * .22f;
            if (pressed || isHovered()) g.fillRoundRect(pressed ? PRESSED : HOVER, 0, 0, w, h, h * .12f);
            g.drawText(getText(), FSkinFont.get(12), TEXT, gap, 0, w - gap * 2, h, false, 8, true);
            g.drawLine(1f, SEPARATOR, gap, h, w - gap, h);
        }
    }
}
