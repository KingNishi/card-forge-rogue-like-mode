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
import forge.toolbox.FDisplayObject;
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

    private static MenuButton button(MatchScreen screen) {
        for (FDisplayObject child : screen.getChildren()) {
            if (child instanceof MenuButton b) return b;
        }
        return null;
    }

    /** Injected after MatchScreen.doLayout(FFF), after the native HUD layout. */
    public static void layout(MatchScreen screen) {
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
        float size = Math.max(Utils.scale(38f), Math.min(Utils.scale(46f), screen.getHeight() * .062f));
        Rectangle boundary = screen.getDropDownBoundary();
        float gap = Math.max(4f, size * .16f);
        b.setBounds(boundary.x + gap, boundary.y + gap, size, size);
        b.syncScreenPosition();
        b.anchorTabs();
        b.setVisible(true);
        if (b.popup.isVisible()) b.popup.update();
    }

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
            g.fillRoundRect(GLASS, 0, 0, getWidth(), getHeight(), button.getWidth() * .15f);
        }
        @Override protected void drawOverlay(Graphics g) {
            g.drawRoundRect(Math.max(1f, button.getWidth() * .02f), GOLD, 0, 0, getWidth(), getHeight(), button.getWidth() * .15f);
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
