package forge.adventure.scene;

import com.badlogic.gdx.graphics.Color;
import forge.Forge;
import forge.Graphics;
import forge.assets.FSkinColor;
import forge.assets.FSkinFont;
import forge.itemmanager.ItemManager;
import forge.itemmanager.filters.StatTypeFilter;
import forge.screens.TabPageScreen;
import forge.toolbox.FComboBox;
import forge.toolbox.FContainer;
import forge.toolbox.FDisplayObject;
import forge.toolbox.FLabel;
import forge.toolbox.FTextField;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/** Drawing only: no replacement widgets, layout changes, listeners, or saved preferences.
 * Install with PatchDeckEditorStyle --list. Shared widget hooks fall through outside this editor.
 * Search retains Forge's actual font fitting, selection, caret and context menu implementation.
 */
public final class DeckEditorStyleMod {
    private DeckEditorStyleMod() {}
    private static final Color NAVY = Color.valueOf("111722"), HOVER = Color.valueOf("17202e"),
            SELECTED = Color.valueOf("1c2738"), GOLD = Color.valueOf("e0b24f"),
            TEXT = Color.valueOf("ece6d8"), MUTED = Color.valueOf("8a93a2"),
            EDGE = Color.valueOf("303b4d"), GOLD_LINE = Color.valueOf("6b5733"), DEEP = Color.valueOf("0b1019");
    private static final Map<FLabel, String> ROLES = new WeakHashMap<>();
    private static WeakReference<FDisplayObject> owner = new WeakReference<>(null);
    // Cache metadata, never an editor or any of its children.
    private static final ClassValue<Map<String, Field>> FIELDS = new ClassValue<>() {
        @Override protected Map<String, Field> computeValue(Class<?> type) {
            Map<String, Field> result = new HashMap<>();
            for (Class<?> c = type; c != null; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (!result.containsKey(f.getName()) && f.trySetAccessible()) result.put(f.getName(), f);
                }
            }
            return result;
        }
    };
    private static Object member(Object object, String name) {
        if (object == null) return null;
        Field f = FIELDS.get(object.getClass()).get(name);
        try { return f == null ? null : f.get(object); }
        catch (IllegalAccessException ex) { throw new IllegalStateException(ex); }
    }
    public static boolean active() {
        return Forge.isMobileAdventureMode && Forge.getCurrentScreen() instanceof AdventureDeckEditor;
    }
    private static void role(Object container, String field, String role) {
        if (member(container, field) instanceof FLabel label) ROLES.put(label, role);
    }
    private static void discover(FDisplayObject node) {
        if (node instanceof ItemManager<?>) {
            role(node, "btnSearch", "search");
            role(node, "btnView", "view");
            role(node, "btnAdvancedSearchOptions", "tools");
        }
        String name = node.getClass().getName();
        if (name.equals("forge.itemmanager.views.ImageView$ExpandCollapseButton") && node instanceof FLabel label)
            ROLES.put(label, "expand");
        if (name.endsWith("AdventureDeckEditor$AdventureDeckHeader")) {
            role(node, "lblName", "title"); role(node, "lblGold", "gold");
            role(node, "btnSave", "save"); role(node, "btnMoreOptions", "more");
            role(node, "btnDraftLog", "textButton"); role(node, "btnChat", "textButton");
        }
        if (name.equals("forge.screens.TabPageScreen$TabHeader")) role(node, "btnBack", "back");
        if (name.equals("forge.itemmanager.filters.ItemFilter$Widget")) {
            Object filter = member(node, "this$0");
            if (filter instanceof StatTypeFilter<?> && member(filter, "buttonMap") instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    if (e.getValue() instanceof FLabel label) ROLES.put(label, "filter:" + e.getKey());
                }
            }
        }
        if (node instanceof FContainer container) {
            for (FDisplayObject child : container.getChildren()) if (child.isVisible()) discover(child);
        }
    }

    /** kind: screen=0, header=1, page=2, rail=3. The transparent page exposes the screen gradient. */
    public static boolean background(FDisplayObject widget, Graphics g, int kind) {
        if (!active()) return false;
        float w = widget.getWidth(), h = widget.getHeight();
        if (kind == 0) {
            if (owner.get() != widget) { ROLES.clear(); owner = new WeakReference<>(widget); }
            discover(widget); // also finds newly built filters and newly activated pages
            return false;     // transparent (user): Forge draws its own background picture behind the editor
        } else if (kind == 1) {
            g.fillGradientRect(alpha(HOVER, .62f), alpha(NAVY, .62f), true, 0, 0, w, h);
        } else if (kind == 3) {
            g.fillGradientRect(alpha(DEEP, .55f), alpha(NAVY, .55f), true, 0, 0, w, h);
        }
        return true;
    }
    public static boolean overlay(FDisplayObject widget, Graphics g, int kind) {
        if (!active()) return false;
        if (kind == 1) g.drawLine(1, GOLD_LINE, 0, widget.getHeight() - 1, widget.getWidth(), widget.getHeight() - 1);
        else if (Forge.isLandscapeMode()) g.drawLine(1, EDGE, 0, 0, 0, widget.getHeight());
        return true;
    }
    private static FSkinFont fit(FSkinFont font, String text, float w, float h) {
        while (font.canShrink() && (font.getBounds(text).width > Math.max(1, w) || font.getCapHeight() > h * .65f)) font = font.shrink();
        return font;
    }
    private static void text(Graphics g, String s, FSkinFont f, Color c, float x, float y, float w, float h, int align) {
        if (w <= 0 || h <= 0 || s == null || s.isEmpty()) return;
        g.drawText(s, fit(f, s, w, h), c, x, y, w, h, false, align, true);
    }
    private static void tile(Graphics g, float x, float y, float w, float h, Color base, Color edge) {
        if (w <= 2 || h <= 2) return;
        float radius = Math.min(10, h * .25f);
        g.fillRoundRect(edge, x, y, w, h, radius);
        g.fillRoundRect(base, x + 1, y + 1, w - 2, h - 2, Math.max(0, radius - 1));
    }
    public static boolean label(FLabel label, Graphics g) {
        if (!active()) return false;
        String role = ROLES.get(label);
        if (role == null) return false;
        float w = label.getWidth(), h = label.getHeight(), pad = Math.max(2, h * .10f);
        Color base = label.isPressed() || label.isSelected() ? SELECTED : label.isHovered() ? HOVER : NAVY;
        Color ink = label.isEnabled() ? TEXT : MUTED;
        if (role.startsWith("filter:")) {
            float d = Math.max(2, Math.min(w, h) - 2 * pad), x = w / 2, y = h / 2;
            String key = role.substring(7);
            Color tone = filterColor(key), on = label.isSelected() && label.isEnabled() ? tone : mixColor(NAVY, tone, .5f);
            g.fillCircle(label.isSelected() ? tone : mixColor(NAVY, tone, .35f), x, y, d / 2);
            g.fillCircle(label.isSelected() ? mixColor(NAVY, tone, .22f) : base, x, y, Math.max(0, d / 2 - (label.isSelected() ? 2f : 1.2f)));
            symbol(g, key, x, y, d * .28f, on);
        } else if (role.equals("title")) {
            text(g, label.getText(), label.getFont(), ink, pad * 2, 0, w - 4 * pad, h, 8);
        } else if (role.equals("gold")) {
            tile(g, pad, pad, w - 2 * pad, h - 2 * pad, SELECTED, EDGE);
            float r = h * .17f, cx = pad * 2 + r;
            g.drawCircle(Math.max(1, h * .028f), GOLD, cx, h / 2, r);
            g.drawLine(1, GOLD, cx, h * .40f, cx, h * .60f);
            text(g, label.getText(), label.getFont(), GOLD, cx + r + pad, 0, w - cx - r - 3 * pad, h, 1);
        } else {
            tile(g, pad, pad, w - 2 * pad, h - 2 * pad, base, label.isSelected() ? GOLD : EDGE);
            if (role.equals("textButton")) text(g, label.getText(), label.getFont(), ink, 2 * pad, 0, w - 4 * pad, h, 1);
            else if (role.equals("expand")) {
                float r = Math.min(w, h) * .23f, cx = w / 2, cy = h / 2;
                g.drawRoundRect(1, ink, cx - r, cy - r, 2 * r, 2 * r, r * .2f);
                line(g, ink, cx - r * .5f, cy, cx + r * .5f, cy);
                if (Boolean.TRUE.equals(member(label, "isAllCollapsed"))) line(g, ink, cx, cy - r * .5f, cx, cy + r * .5f);
            }
            else icon(g, role, w / 2, h / 2, Math.min(w, h) * .23f, label.isSelected() ? GOLD : ink);
        }
        return true;
    }
    /** one colour per filter: mana colours like the cards, card types each their own hue */
    private static Color filterColor(String key) {
        return Color.valueOf(switch (key) {
            case "WHITE" -> "f3e3a6"; case "BLUE" -> "5aa7ff"; case "BLACK" -> "b593e6"; case "RED" -> "ff6b4a";
            case "GREEN" -> "5ccf6f"; case "COLORLESS" -> "c0c7d1"; case "MULTICOLOR" -> "e0b24f";
            case "LAND" -> "c9a46a"; case "ARTIFACT" -> "aeb9c6"; case "CREATURE" -> "f2a65a"; case "ENCHANTMENT" -> "e8d06a";
            case "PLANESWALKER" -> "e07ad8"; case "INSTANT" -> "4fc3f7"; case "SORCERY" -> "ff8a65"; case "BATTLE" -> "d4d4d4";
            default -> "e0b24f";
        });
    }
    private static Color mixColor(Color a, Color b, float t) {
        return new Color(a.r + (b.r - a.r) * t, a.g + (b.g - a.g) * t, a.b + (b.b - a.b) * t, 1);
    }
    private static Color alpha(Color c, float a) { return new Color(c.r, c.g, c.b, a); }
    private static void line(Graphics g, Color c, float x1, float y1, float x2, float y2) {
        g.drawLine(1.5f, c, x1, y1, x2, y2);
    }
    private static void icon(Graphics g, String role, float x, float y, float r, Color c) {
        switch (role) {
            case "search" -> {
                g.drawCircle(1.5f, c, x - r * .2f, y - r * .2f, r * .65f);
                line(g, c, x + r * .3f, y + r * .3f, x + r, y + r);
            }
            case "more" -> { for (int i = -1; i <= 1; i++) g.fillCircle(c, x + i * r * .8f, y, Math.max(1.5f, r * .13f)); }
            case "back" -> {
                line(g, c, x + r * .2f, y - r * .7f, x - r * .5f, y);
                line(g, c, x - r * .5f, y, x + r * .2f, y + r * .7f);
                line(g, c, x - r * .5f, y, x + r, y);
            }
            case "tools" -> {
                for (int i = -1; i <= 1; i++) {
                    float yy = y + i * r * .7f, xx = x + (i == 0 ? .4f : -.3f) * r;
                    line(g, c, x - r, yy, x + r, yy);
                    g.fillCircle(c, xx, yy, r * .20f);
                }
            }
            case "save" -> {
                g.drawRoundRect(1.5f, c, x - r, y - r, 2 * r, 2 * r, r * .15f);
                g.drawRect(1, c, x - r * .5f, y - r, r, r * .7f);
                g.drawRect(1, c, x - r * .5f, y + r * .2f, r, r * .8f);
            }
            default -> { // native view chooser; neutral grid glyph also suits list/image switching
                for (int a = -1; a <= 1; a += 2) for (int b = -1; b <= 1; b += 2)
                    g.drawRoundRect(1, c, x + a * r * .55f - r * .32f, y + b * r * .55f - r * .32f, r * .64f, r * .64f, r * .10f);
            }
        }
    }
    private static void symbol(Graphics g, String key, float x, float y, float r, Color c) {
        switch (key) {
            case "WHITE" -> {
                g.drawCircle(1.5f, c, x, y, r * .52f);
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4;
                    line(g, c, x + (float)Math.cos(a) * r * .75f, y + (float)Math.sin(a) * r * .75f,
                            x + (float)Math.cos(a) * r, y + (float)Math.sin(a) * r);
                }
            }
            case "BLUE" -> { g.fillCircle(c, x, y + r * .25f, r * .58f); g.fillTriangle(c, x - r * .52f, y, x + r * .52f, y, x + r * .15f, y - r); }
            case "BLACK" -> {
                g.fillCircle(c, x, y - r * .20f, r * .72f);
                g.fillRect(c, x - r * .4f, y + r * .25f, r * .8f, r * .6f);
                g.fillCircle(NAVY, x - r * .26f, y - r * .1f, r * .16f);
                g.fillCircle(NAVY, x + r * .26f, y - r * .1f, r * .16f);
            }
            case "RED" -> { g.fillCircle(c, x, y + r * .25f, r * .60f); g.fillTriangle(c, x - r * .55f, y + r * .2f, x + r * .55f, y + r * .3f, x + r * .35f, y - r); }
            case "GREEN" -> { g.fillCircle(c, x, y - r * .25f, r * .7f); g.fillRect(c, x - r * .13f, y, r * .26f, r); }
            case "COLORLESS" -> {
                line(g, c, x, y - r, x + r * .8f, y); line(g, c, x + r * .8f, y, x, y + r);
                line(g, c, x, y + r, x - r * .8f, y); line(g, c, x - r * .8f, y, x, y - r);
            }
            case "MULTICOLOR" -> {
                g.drawCircle(1, c, x, y - r * .45f, r * .5f);
                g.drawCircle(1, c, x - r * .4f, y + r * .3f, r * .5f);
                g.drawCircle(1, c, x + r * .4f, y + r * .3f, r * .5f);
            }
            default -> {
                String s = switch (key) {
                    case "LAND" -> "L"; case "ARTIFACT" -> "A"; case "CREATURE" -> "Cr";
                    case "ENCHANTMENT" -> "E"; case "PLANESWALKER" -> "P"; case "INSTANT" -> "I";
                    case "SORCERY" -> "S"; case "BATTLE" -> "B"; case "PACK_OR_DECK" -> "D";
                    default -> key.startsWith("CMC_") ? key.substring(4) : key.substring(0, Math.min(2, key.length()));
                };
                text(g, s, FSkinFont.forHeight(r * 1.35f), c, x - r, y - r, 2 * r, 2 * r, 1);
            }
        }
    }
    public static boolean combo(FComboBox<?> box, Graphics g) {
        if (!active()) return false;
        float w = box.getWidth(), h = box.getHeight(), p = Math.max(3, h * .1f);
        boolean open = box.getDropDownisVisible();
        tile(g, p / 2, p / 2, w - p, h - p, open ? SELECTED : box.isHovered() ? HOVER : NAVY, open ? GOLD : EDGE);
        float cx = w - h * .35f, cy = h / 2, d = h * .10f;
        Color c = box.isEnabled() ? TEXT : MUTED;
        line(g, open ? GOLD : c, cx - d, cy - d / 2, cx, cy + d / 2);
        line(g, open ? GOLD : c, cx, cy + d / 2, cx + d, cy - d / 2);
        text(g, box.getText(), box.getFont(), c, p * 2, 0, Math.max(1, cx - d - p * 3), h, box.getAlignment());
        return true;
    }
    // Bridges replace ONLY the native background and outer border. Selection rectangles are untouched.
    public static void fieldBackground(Graphics g, FSkinColor original, float x, float y, float w, float h, FTextField field) {
        if (!active()) { g.fillRect(original, x, y, w, h); return; }
        tile(g, 1, 1, w - 2, h - 2, field.isHovered() ? HOVER : NAVY, field.isEditing() ? GOLD : EDGE);
    }
    public static void fieldBorder(Graphics g, float thickness, FSkinColor original, float x, float y, float w, float h) {
        if (!active()) g.drawRect(thickness, original, x, y, w, h);
    }
    public static FSkinColor foreground(FSkinColor original) { return active() ? FSkinColor.getStandardColor(TEXT) : original; }
    public static FSkinColor muted(FSkinColor original) { return active() ? FSkinColor.getStandardColor(MUTED) : original; }
    public static FSkinColor accent(FSkinColor original) { return active() ? FSkinColor.getStandardColor(GOLD) : original; }
    public static FSkinColor selection(FSkinColor original) { return active() ? FSkinColor.getStandardColor(SELECTED) : original; }
    public static Color cardSelection(Color original) { return active() ? GOLD : original; }
    public static void gridBorder(Graphics g, float thickness, FSkinColor original, float x, float y, float w, float h) {
        if (!active()) g.drawRect(thickness, original, x, y, w, h);
    }
    public static void groupText(Graphics g, String caption, FSkinFont font, FSkinColor original,
            float x, float y, float w, float h, boolean wrap, int align, boolean center) {
        if (!active()) { g.drawText(caption, font, original, x, y, w, h, wrap, align, center); return; }
        String s = caption.toUpperCase(Locale.ROOT);
        FSkinFont f = fit(font, s, w - x, h);
        text(g, s, f, GOLD, x, y, w - x, h, align);
        float start = Math.min(w, x + f.getBounds(s).width + h * .5f);
        if (start < w - 2) g.drawLine(1, GOLD_LINE, start, y + h / 2, w, y + h / 2);
    }
    public static void groupLine(Graphics g, float thickness, FSkinColor original, float x1, float y1, float x2, float y2) {
        if (!active()) g.drawLine(thickness, original, x1, y1, x2, y2);
    }
    public static boolean tab(FDisplayObject tab, Graphics g, TabPageScreen.TabPage<?> page) {
        if (!active()) return false;
        boolean selected = ((AdventureDeckEditor)Forge.getCurrentScreen()).getSelectedPage() == page;
        float w = tab.getWidth(), h = tab.getHeight(), p = Math.max(3, Math.min(w, h) * .08f);
        tile(g, p, p, w - 2 * p, h - 2 * p, selected ? SELECTED : tab.isHovered() ? HOVER : NAVY, selected ? GOLD : EDGE);
        String caption = page.getCaption() == null ? "" : page.getCaption(), count = "";
        int left = caption.lastIndexOf('(');
        if (left >= 0 && caption.endsWith(")")) { count = caption.substring(left + 1, caption.length() - 1); caption = caption.substring(0, left).trim(); }
        FSkinFont font = FSkinFont.get(12);
        if (Forge.isLandscapeMode()) {
            float iy = h * .29f, r = Math.min(w * .17f, h * .17f);
            g.drawRoundRect(1, selected ? GOLD : MUTED, w / 2 - r, iy - r, r * 1.5f, r * 1.65f, r * .18f);
            g.drawRoundRect(1, selected ? GOLD : MUTED, w / 2 - r * .5f, iy - r * .65f, r * 1.5f, r * 1.65f, r * .18f);
            text(g, caption, font, selected ? TEXT : MUTED, 2 * p, h * .48f, w - 4 * p, h * .24f, 1);
            if (!count.isEmpty()) {
                float bw = Math.min(w - 4 * p, Math.max(h * .28f, font.getBounds(count).width + 2 * p));
                tile(g, (w - bw) / 2, h * .74f, bw, h * .20f, selected ? GOLD : HOVER, selected ? GOLD : EDGE);
                text(g, count, font, selected ? NAVY : MUTED, (w - bw) / 2, h * .74f, bw, h * .20f, 1);
            }
        } else {
            float bw = count.isEmpty() ? 0 : Math.min(w * .35f, font.getBounds(count).width + 2 * p);
            text(g, caption, font, selected ? TEXT : MUTED, 2 * p, 0, w - bw - 5 * p, h, 1);
            if (bw > 0) {
                tile(g, w - bw - 2 * p, h * .25f, bw, h * .5f, selected ? GOLD : HOVER, selected ? GOLD : EDGE);
                text(g, count, font, selected ? NAVY : MUTED, w - bw - 2 * p, 0, bw, h, 1);
            }
        }
        return true;
    }
}
