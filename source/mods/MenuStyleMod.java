package forge.menu;

import com.badlogic.gdx.graphics.Color;
import forge.Forge;
import forge.Graphics;
import forge.assets.FImage;
import forge.assets.FSkinFont;
import java.lang.reflect.Field;

/** Popup menus in a duel in the modern battle style (user 2026-10-18: the stack's "Auto-Yield / Yield to stack /
 *  Resolve entire stack / Zoom" menu "hat noch ein altes Menue"): dark glass, gold rim, hover row, gold check box -
 *  like the round match menu. FMenuItem.draw starts with {@code if(MenuStyleMod.drawItem(this,g))return;},
 *  FPopupMenu.drawBackground / drawOverlay with {@code if(MenuStyleMod.drawMenu(this,g,overlay))return;} (PatchMenuStyle).
 *  Outside a duel (and in portrait mode) everything stays Forge's. */
public final class MenuStyleMod {
    private MenuStyleMod() {}
    private static final Color GLASS = new Color(0x121823ff), HOVER = new Color(0x253044ff), PRESSED = new Color(0x35435aff),
            GOLD = new Color(0xe0b24fff), TEXT = new Color(0xf2f0eaff), DIM = new Color(0x8f97a8ff), SEPARATOR = new Color(0xe0b24f2e),
            BOX = new Color(0x0b0f17ff);
    private static Field icon, pressed, checked;

    private static boolean on() {
        try { return Forge.isLandscapeMode() && Forge.getCurrentScreen() instanceof forge.screens.match.MatchScreen; }
        catch (Throwable t) { return false; }
    }

    public static boolean drawMenu(FPopupMenu m, Graphics g, boolean overlay) {
        if (!on()) return false;
        float w = m.getWidth(), h = m.getHeight(), r = Math.min(14f, Math.min(w, h) * .06f);
        if (overlay) return true;   // the rim is part of the background: nested fills, no drawRoundRect corner marks
        float line = Math.max(1.5f, Math.min(w, h) * .006f);
        g.fillRoundRect(GOLD, 0, 0, w, h, r);
        g.fillRoundRect(GLASS, line, line, w - 2 * line, h - 2 * line, Math.max(0f, r - line));
        return true;
    }

    public static boolean drawItem(FMenuItem item, Graphics g) {
        if (!on()) return false;
        try {
            if (icon == null) {
                icon = FMenuItem.class.getDeclaredField("icon"); icon.setAccessible(true);
                pressed = FMenuItem.class.getDeclaredField("pressed"); pressed.setAccessible(true);
                checked = FCheckBoxMenuItem.class.getDeclaredField("checked"); checked.setAccessible(true);
            }
            float w = item.getWidth(), h = item.getHeight(), pad = h * .22f;
            boolean down = pressed.getBoolean(item), enabled = item.isEnabled();
            if (enabled && (down || item.isHovered())) g.fillRoundRect(down ? PRESSED : HOVER, 2, 2, w - 4, h - 4, h * .14f);
            float x = pad;
            FImage img = (FImage) icon.get(item);
            if (img != null) { float s = h * .5f; img.draw(g, x, (h - s) / 2f, s, s); x += s + pad * .8f; }
            boolean box = item instanceof FCheckBoxMenuItem;
            float bs = h * .44f, bx = w - pad - bs;
            g.drawText(item.getText(), FSkinFont.forHeight(h * .40f), enabled ? TEXT : DIM, x, 0, Math.max(1f, (box ? bx - pad : w - pad) - x), h, false, 8, true);
            if (box) {
                float ln = Math.max(1f, bs * .08f);   // nested fills: Forge's drawRoundRect draws cross lines
                g.fillRoundRect(GOLD, bx, (h - bs) / 2f, bs, bs, bs * .2f);
                g.fillRoundRect(BOX, bx + ln, (h - bs) / 2f + ln, bs - 2 * ln, bs - 2 * ln, Math.max(0f, bs * .2f - ln));
                if (checked.getBoolean(item)) {
                    float cx = bx + bs * .22f, cy = (h - bs) / 2f + bs * .52f, t = Math.max(1.5f, bs * .12f);
                    g.drawLine(t, GOLD, cx, cy, cx + bs * .2f, cy + bs * .22f);
                    g.drawLine(t, GOLD, cx + bs * .2f, cy + bs * .22f, cx + bs * .58f, cy - bs * .26f);
                }
            }
            g.drawLine(1f, SEPARATOR, pad, h, w - pad, h);
            return true;
        } catch (Throwable t) { return false; }
    }
}
