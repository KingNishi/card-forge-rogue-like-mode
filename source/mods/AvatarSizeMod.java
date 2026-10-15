package forge.adventure.util;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;

/**
 * Mod: player portraits replaced by a bigger picture (32x32, 64x64, 96x96, ...) keep the normal 16x16
 * on-screen size wherever Forge uses the portrait's pixel size directly (mini map player marker, arena bracket).
 * Normal 16x16 portraits are left alone.
 */
public final class AvatarSizeMod {
    static final int LIMIT = 20;
    static final int NORMAL = 16;

    static float scale(float w, float h) {
        float m = Math.max(w, h);
        return m > LIMIT ? NORMAL / m : 1f;
    }

    public static int w(TextureRegion r) {
        return Math.round(r.getRegionWidth() * scale(r.getRegionWidth(), r.getRegionHeight()));
    }

    public static int h(TextureRegion r) {
        return Math.round(r.getRegionHeight() * scale(r.getRegionWidth(), r.getRegionHeight()));
    }

    public static void fit(Actor a) {
        if (a == null) return;
        float s = scale(a.getWidth(), a.getHeight());
        if (s != 1f) a.setSize(a.getWidth() * s, a.getHeight() * s);
    }
}
