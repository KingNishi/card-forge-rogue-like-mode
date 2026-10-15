package forge.adventure.util;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FileTextureData;
import com.badlogic.gdx.scenes.scene2d.Actor;

/** High-resolution sign artwork occupies the same world footprint as the old 16px signs. */
public final class ShopSignMod {
    public static void fit(Actor actor, TextureRegion region) {
        if (region.getTexture() == null) return;
        if (region.getTexture().getTextureData() instanceof FileTextureData data
            && (data.getFileHandle().name().equals("clear_shop_signs.png")
                || data.getFileHandle().name().equals("readable_shop_signs.png"))) {
            actor.setSize(16f, 16f);
        } else if (region.getTexture().getTextureData() instanceof FileTextureData data
            && data.getFileHandle().name().equals("upscaled_shop_signs.png")) {
            actor.setSize(region.getRegionWidth() / 4f, region.getRegionHeight() / 4f);
        }
    }
}
