package forge.adventure.util;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.ObjectMap;
import com.github.tommyettinger.textra.Font;
import com.ray3k.tenpatch.TenPatchDrawable;
import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Shared Adventure skin; no actor bounds, game rules, or save data are changed. */
public final class ModernSkinMod {
    private ModernSkinMod() {}
    private static final String BODY = "__modern_skin_body", TITLE = "__modern_skin_title";
    private static final Map<Skin, Font> buttonFonts = new WeakHashMap<>();
    private static final Map<Font, Boolean> measuredFonts = new WeakHashMap<>();
    private static final Class<?>[] STYLES = {
        Button.ButtonStyle.class, TextButton.TextButtonStyle.class,
        ImageButton.ImageButtonStyle.class, ImageTextButton.ImageTextButtonStyle.class,
        CheckBox.CheckBoxStyle.class, Label.LabelStyle.class, Window.WindowStyle.class,
        ScrollPane.ScrollPaneStyle.class, SelectBox.SelectBoxStyle.class,
        com.badlogic.gdx.scenes.scene2d.ui.List.ListStyle.class, TextField.TextFieldStyle.class,
        TextTooltip.TextTooltipStyle.class, Slider.SliderStyle.class,
        ProgressBar.ProgressBarStyle.class, SplitPane.SplitPaneStyle.class,
        Tree.TreeStyle.class, Touchpad.TouchpadStyle.class
    };

    /** Runs on the render thread, after loading and before the first skin consumer. */
    public static Skin apply(Skin skin) {
        if (skin == null || skin.has(BODY, Boolean.class)) return skin;
        BitmapFont original = skin.getFont("default");
        BitmapFont body = smooth("ui.ttf", original);
        BitmapFont title;
        try { title = smooth("fantasy.ttf", original); }
        catch (RuntimeException failure) { body.dispose(); throw failure; }
        IdentityHashMap<BitmapFont, BitmapFont> fonts = new IdentityHashMap<>();
        fonts.put(original, body);
        // Preserve old resources for Skin.dispose(), including their texture ownership.
        skin.add("__modern_skin_original_default", original, BitmapFont.class);
        skin.add("default", body, BitmapFont.class);
        skin.add(TITLE, title, BitmapFont.class);
        ObjectMap<String, BitmapFont> registered = skin.getAll(BitmapFont.class);
        // A future skin can expose additional font aliases; preserve each font's metrics.
        for (ObjectMap.Entry<String, BitmapFont> entry : new ObjectMap<>(registered)) {
            if (entry.key.startsWith("__modern_skin_") || entry.value == body) continue;
            BitmapFont replacement = fonts.get(entry.value);
            if (replacement == null) {
                replacement = smooth("ui.ttf", entry.value);
                fonts.put(entry.value, replacement);
                skin.add("__modern_skin_original_" + entry.key, entry.value, BitmapFont.class);
            }
            skin.add(entry.key, replacement, BitmapFont.class);
        }

        IdentityHashMap<Drawable, Drawable> drawables = new IdentityHashMap<>();
        ObjectMap<String, TenPatchDrawable> patches = skin.getAll(TenPatchDrawable.class);
        if (patches != null) for (ObjectMap.Entry<String, TenPatchDrawable> e : patches)
            replace(skin, e.key, e.value, drawables);
        String[] names = { "windowMain", "thinwindow", "transwindow", "map", "9patch4",
            "unpressedMap", "pressedMap", "BKey_3_9", "BKey_3_14", "unpressedround", "pressedround",
            "item_frame", "item_frame_hover", "item_frame_selected", "item_frame_selected_hover",
            "left", "left_down", "left_f", "right", "right_down", "right_f",
            "check", "uncheck", "scroll", "scroll2", "bar", "select", "cursor",
            "touchBackground", "touchKnob", "unpressed", "pressed" };
        for (String name : names) {
            if (!skin.has(name, TextureRegion.class) && !skin.has(name, Drawable.class)) continue;
            replace(skin, name, skin.getDrawable(name), drawables);
        }
        IdentityHashMap<Object, Boolean> visited = new IdentityHashMap<>();
        for (Class<?> type : STYLES) {
            ObjectMap<String, ?> styles = skin.getAll(type);
            if (styles != null) for (Object style : styles.values())
                restyle(style, fonts, title, drawables, visited);
        }
        // Marker is added last so incomplete installation cannot masquerade as success.
        skin.add(BODY, Boolean.TRUE, Boolean.class);
        return skin;
    }

    private static BitmapFont smooth(String file, BitmapFont old) {
        var path = Gdx.files.absolute("D:/forge/_mods/modern-hud/" + file);
        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(path);
        try {
            var parameter = new FreeTypeFontGenerator.FreeTypeFontParameter();
            // Native logical size avoids Textra's unscaled BitmapFont-glyph conversion.
            parameter.size = Math.max(8, Math.round(old.getCapHeight() * 1.4f));
            parameter.minFilter = parameter.magFilter = Texture.TextureFilter.Linear;
            parameter.hinting = FreeTypeFontGenerator.Hinting.None;
            StringBuilder chars = new StringBuilder();
            for (int c = 32; c < 0xE000; c++)
                if ((c < 0x500 || old.getData().hasGlyph((char)c)) && generator.hasGlyph(c))
                    chars.append((char)c);
            parameter.characters = chars.toString();
            BitmapFont font = generator.generateFont(parameter);
            font.setUseIntegerPositions(false);
            var data = font.getData(); var previous = old.getData();
            data.lineHeight = old.getLineHeight(); data.capHeight = old.getCapHeight();
            data.xHeight = old.getXHeight(); data.ascent = old.getAscent(); data.descent = old.getDescent();
            data.down = previous.down; data.blankLineScale = previous.blankLineScale;
            data.markupEnabled = previous.markupEnabled;
            return font;
        } finally { generator.dispose(); }
    }

    /** Used by Adventure's Textra/Typing buttons, including subsequent setStyle calls. */
    public static Font getButtonFont() {
        Skin skin = Controls.getSkin();
        Font font = buttonFonts.get(skin);
        if (font == null) {
            font = new Font(skin.getFont(TITLE), 0, 2, 0, 1);
            font.addAtlas(Config.instance().getAtlas("sprites/items.atlas"), 0, 6, 0);
            font.integerPosition = false;
            metrics(font, 1);
            // Font owns only its auxiliary block texture; the skin owns bitmap pages.
            skin.add("__modern_skin_button_font", font, Font.class);
            buttonFonts.put(skin, font);
        }
        return font;
    }

    /** Textra derives cell height from glyph bounds, rather than BitmapFont.lineHeight. */
    public static Font metrics(Font font, float adjustment) {
        if (measuredFonts.containsKey(font)) return font;
        BitmapFont old = Controls.getSkin().getFont("__modern_skin_original_default");
        float height = 0;
        for (BitmapFont.Glyph[] page : old.getData().glyphs) if (page != null)
            for (BitmapFont.Glyph glyph : page) if (glyph != null) height = Math.max(height, glyph.height);
        if (font.originalCellHeight > 0) font.adjustLineHeight((height + adjustment)/font.originalCellHeight);
        font.integerPosition = false;
        measuredFonts.put(font, Boolean.TRUE);
        return font;
    }

    private static void restyle(Object style, IdentityHashMap<BitmapFont, BitmapFont> fonts,
            BitmapFont title, IdentityHashMap<Drawable, Drawable> drawables,
            IdentityHashMap<Object, Boolean> visited) {
        if (visited.put(style, Boolean.TRUE) != null) return;
        try {
            for (Field field : style.getClass().getFields()) {
                Object old = field.get(style);
                if (old instanceof BitmapFont bitmap) {
                    BitmapFont replacement = field.getName().equals("titleFont") ||
                        (field.getName().equals("font") && style instanceof TextButton.TextButtonStyle)
                        ? title : fonts.get(bitmap);
                    if (replacement != null) field.set(style, replacement);
                } else if (old instanceof Drawable drawable) {
                    Drawable replacement = drawables.get(drawable);
                    if (replacement != null) field.set(style, replacement);
                } else if (old != null && field.getType().getName().endsWith("Style")) {
                    restyle(old, fonts, title, drawables, visited);
                }
            }
        } catch (IllegalAccessException failure) { throw new IllegalStateException("Adventure skin style", failure); }
    }

    private static void replace(Skin skin, String name, Drawable old,
            IdentityHashMap<Drawable, Drawable> replacements) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        if (lower.equals("unpressedround") || lower.equals("pressedround")) return;
        int variant = lower.contains("disable") ? 3 : lower.contains("hover") ||
            lower.contains("focus") || lower.contains("selected") ? 1 :
            lower.contains("pressed") && !lower.contains("unpressed") || lower.contains("down") ||
            lower.equals("bkey_3_14") ? 2 : 0;
        if (lower.startsWith("scroll") || lower.equals("select")) variant = 1;
        // Only rectangular button surfaces use the image atlas. Preserve round hints,
        // dropdown arrows, check symbols, inventory slots and all window resources.
        boolean rectangularButton = !lower.contains("sb") && !lower.contains("selectbox") &&
            !lower.contains("round") && (lower.startsWith("unpressed") ||
            lower.startsWith("pressed") || lower.startsWith("goldpressed") ||
            lower.equals("bkey_3_9") || lower.equals("bkey_3_14") ||
            lower.equals("button10patch") || lower.startsWith("button_down_") ||
            lower.startsWith("button_hover_"));
        if (rectangularButton && FantasyButtonArt.available()) {
            Drawable replacement = FantasyButtonArt.drawable(old, variant, lower.contains("gold") || lower.startsWith("bkey_3_"));
            replacements.put(old, replacement);
            skin.add(name, replacement, Drawable.class);
            return;
        }
        TextureRegion symbol = null;
        if (lower.startsWith("left") || lower.startsWith("right") || lower.equals("check") || lower.equals("uncheck"))
            symbol = icon(skin, name, lower);
        boolean frame = lower.startsWith("item_frame");
        NinePatch patch = frame ? frame(skin, name, variant) : RpgThemeMod.panel(variant);
        Drawable replacement = new ModernDrawable(old, patch, symbol,
            lower.contains("sb") ? icon(skin, name + "_arrow", "down") : null);
        replacements.put(old, replacement);
        skin.add(name, replacement, Drawable.class);
    }

    private static NinePatch frame(Skin skin, String name, int variant) {
        Pixmap pixels = new Pixmap(96, 96, Pixmap.Format.RGBA8888);
        pixels.setColor(variant == 1 ? RpgThemeMod.GOLD : Color.valueOf("927344"));
        for (int inset = 2; inset < 6; inset++) pixels.drawRectangle(inset, inset, 96 - 2*inset, 96 - 2*inset);
        Texture texture = texture(skin, name, pixels);
        NinePatch patch = new NinePatch(texture, 12, 12, 12, 12);
        patch.scale(.25f, .25f);
        return patch;
    }

    private static TextureRegion icon(Skin skin, String name, String type) {
        Pixmap pixels = new Pixmap(96, 96, Pixmap.Format.RGBA8888);
        pixels.setColor(RpgThemeMod.TEXT);
        if (type.startsWith("left")) pixels.fillTriangle(25, 48, 68, 20, 68, 76);
        else if (type.startsWith("right")) pixels.fillTriangle(71, 48, 28, 20, 28, 76);
        else if (type.equals("down")) pixels.fillTriangle(20, 28, 76, 28, 48, 70);
        else {
            for (int inset = 18; inset < 24; inset++) pixels.drawRectangle(inset, inset, 96 - 2*inset, 96 - 2*inset);
            if (type.equals("check")) {
                pixels.fillTriangle(25, 45, 42, 72, 47, 57);
                pixels.fillTriangle(42, 72, 77, 25, 65, 25);
            }
        }
        return new TextureRegion(texture(skin, name, pixels));
    }

    private static Texture texture(Skin skin, String name, Pixmap pixels) {
        Texture texture;
        try { texture = new Texture(pixels); } finally { pixels.dispose(); }
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        skin.add("__modern_skin_texture_" + name, texture, Texture.class);
        return texture;
    }

    private static final class ModernDrawable extends BaseDrawable {
        final NinePatch panel; final TextureRegion symbol, arrow;
        ModernDrawable(Drawable old, NinePatch panel, TextureRegion symbol, TextureRegion arrow) {
            super(old); this.panel = panel; this.symbol = symbol; this.arrow = arrow;
        }
        @Override public void draw(Batch batch, float x, float y, float width, float height) {
            panel.draw(batch, x, y, width, height);
            if (symbol != null) batch.draw(symbol, x, y, width, height);
            if (arrow != null) {
                float size = Math.min(8, height * .55f);
                batch.draw(arrow, x + width - size - 2, y + (height - size)/2, size, size);
            }
        }
    }
}
