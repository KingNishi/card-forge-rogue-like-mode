package forge.adventure.character;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.TextureData;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FileTextureData;

import java.util.WeakHashMap;

/**
 * Mod: map sprites whose image file name contains "@2x" (e.g. sprites/heroes/misa@2x.png) are drawn in
 * double resolution at the normal size: a 32x32 frame takes the space of a 16x16 one.
 * Hooked in by PatchSpriteScale.java (CharacterSprite.load / draw use these instead of the region size).
 */
public final class SpriteScaleMod {
    private static final WeakHashMap<Texture, Float> FACTOR = new WeakHashMap<>();

    static synchronized float factor(TextureRegion r) {
        Texture t = r.getTexture();
        if (t == null) return 1f;
        Float f = FACTOR.get(t);
        if (f == null) {
            f = 1f;
            try {
                TextureData d = t.getTextureData();
                if (d instanceof FileTextureData) {
                    FileHandle file = ((FileTextureData) d).getFileHandle();
                    if (file != null) {
                        String name=file.name().toLowerCase();
                        if(name.contains("@8x"))f=.125f;
                        else if(name.contains("@2x"))f=.5f;
                    }
                }
            } catch (Throwable ignored) {
            }
            FACTOR.put(t, f);
        }
        return f;
    }

    public static int w(TextureRegion r) {
        return Math.round(r.getRegionWidth() * factor(r));
    }

    public static int h(TextureRegion r) {
        return Math.round(r.getRegionHeight() * factor(r));
    }

    public static float sw(Sprite s) {
        return s.getWidth() * factor(s);
    }

    public static float sh(Sprite s) {
        return s.getHeight() * factor(s);
    }
}
