package forge.adventure.util;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.glutils.FileTextureData;
import com.badlogic.gdx.utils.BufferUtils;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.*;

/** Upload 2x pixel-art textures while keeping TextureData's logical dimensions.
 * Atlas coordinates, tile sizes, character sizes and collision geometry stay original.
 * CPU reads of source pixmaps (world generation) are deliberately unaffected.
 */
public final class WorldSprite2xMod {
    private static final Map<Pixmap, Boolean> WORLD = Collections.synchronizedMap(new WeakHashMap<>());
    private static final ThreadLocal<Boolean> REPLACED = ThreadLocal.withInitial(() -> false);

    public static Pixmap markWorld(Pixmap p) { WORLD.put(p, true); return p; }
    public static Texture.TextureFilter filter(GLTexture texture,Texture.TextureFilter original){
        if(texture instanceof Texture t && t.getTextureData() instanceof FileTextureData d
           && (d.getFileHandle().path().replace('\\','/').contains("/sprites/enemy/")
               || heroCache(d.getFileHandle().path())!=null
               || CalmTerrainMod.eligible(d.getFileHandle().path()))
           && !d.getFileHandle().name().contains("_art")
           && !d.getFileHandle().name().contains("_avatar")
           // town/dungeon tilesets are tile atlases: Linear would blend neighbouring tiles in and draw seams
           && !d.getFileHandle().path().replace('\\','/').contains("/maps/tileset/"))return Texture.TextureFilter.Linear;
        return original;
    }

    public static boolean eligible(String path) {
        String p = path.replace('\\', '/').toLowerCase(Locale.ROOT);
        if (!p.contains("/adventure/")) return false;
        if (p.contains("/heroes/") || p.contains("@2x") || p.contains("_avatar2x")
                || p.endsWith("_art.jpg") || p.contains("avatar_custom_")) return false;
        return p.contains("/sprites/") || p.contains("/maps/")
                || p.contains("/world/tilesets/") || p.endsWith("/ui/sprite_markers.png");
    }

    public static Pixmap consume(TextureData data) {
        REPLACED.set(false);
        Pixmap original = data.consumePixmap();
        boolean world = WORLD.remove(original) != null;
        Pixmap hero = heroDetail(data, original);   // town NPCs (hero sheets) get the same 4x xBR detail as enemies
        if (hero != null) return hero;
        boolean file = data instanceof FileTextureData
                && eligible(((FileTextureData)data).getFileHandle().path());
        if (!world && !file) return original;
        boolean terrain=world || data instanceof FileTextureData d && CalmTerrainMod.eligible(d.getFileHandle().path());
        IntBuffer max = BufferUtils.newIntBuffer(1);
        Gdx.gl.glGetIntegerv(GL20.GL_MAX_TEXTURE_SIZE, max);
        if(file && data instanceof FileTextureData d){
            String source=d.getFileHandle().path().replace('\\','/');
            String name=d.getFileHandle().name();
            if(source.contains("/maps/tileset/") && (name.equals("shopfronts.png") || name.equals("capital_shopfronts.png"))){
                var cached=Gdx.files.absolute("D:/forge/_mods/shop-art/textures/"+name.replace(".png","@4x.png"));
                if(cached.exists()){
                    Pixmap detailed=new Pixmap(cached);
                    if(detailed.getWidth()==original.getWidth()*4 && detailed.getHeight()==original.getHeight()*4
                            && detailed.getWidth()<=max.get(0) && detailed.getHeight()<=max.get(0)){
                        if(data.disposePixmap())original.dispose();REPLACED.set(true);return detailed;
                    }
                    detailed.dispose();
                }
            }
        }
        if(file && data instanceof FileTextureData d){
            String source=d.getFileHandle().path().replace('\\','/');int index=source.indexOf("/sprites/enemy/");
            if(index>=0){
                // Ækashics Librarium creature drawn into the original frame layout (librarium_build.py) wins over the xBR cache
                com.badlogic.gdx.files.FileHandle cached=Gdx.files.absolute("D:/forge/_mods/enemy-sprites-librarium/"+source.substring(index+15));
                if(!cached.exists())cached=Gdx.files.absolute("D:/forge/_mods/enemy-sprites4x/"+source.substring(index+15));
                if(cached.exists() && original.getWidth()*4<=max.get(0) && original.getHeight()*4<=max.get(0)){
                    Pixmap detailed=new Pixmap(cached);
                    if(detailed.getWidth()==original.getWidth()*4&&detailed.getHeight()==original.getHeight()*4){
                        if(data.disposePixmap())original.dispose();REPLACED.set(true);return detailed;
                    }
                    detailed.dispose();
                }
            }
        }
        if (original.getWidth() * 2 > max.get(0) || original.getHeight() * 2 > max.get(0)) {
            System.err.println("[WorldSprite2x] GPU size limit: keeping original texture");
            return original;
        }
        Pixmap scaled = scale2x(original);
        if(terrain){
            if((long)original.getWidth()*original.getHeight()<=2_000_000
                    && original.getWidth()*4<=max.get(0) && original.getHeight()*4<=max.get(0)){
                Pixmap larger=scale2x(scaled);scaled.dispose();scaled=larger;
            }
            String path=data instanceof FileTextureData d ? d.getFileHandle().path():"";
            CalmTerrainMod.refine(scaled,path,world,scaled.getWidth()/original.getWidth());
        }
        if (data.disposePixmap()) original.dispose();
        REPLACED.set(true);
        return scaled;
    }

    /** 4x sheet from _mods/hero-sprites4x (BuildHeroSprites.java); the player's own sprite has no entry there */
    static com.badlogic.gdx.files.FileHandle heroCache(String path){
        String source=path.replace('\\','/');int index=source.indexOf("/sprites/heroes/");
        if(index<0)return null;
        var cached=Gdx.files.absolute("D:/forge/_mods/hero-sprites4x/"+source.substring(index+16));
        return cached.exists()?cached:null;
    }
    private static Pixmap heroDetail(TextureData data,Pixmap original){
        if(!(data instanceof FileTextureData d))return null;
        var cached=heroCache(d.getFileHandle().path());if(cached==null)return null;
        IntBuffer max=BufferUtils.newIntBuffer(1);Gdx.gl.glGetIntegerv(GL20.GL_MAX_TEXTURE_SIZE,max);
        if(original.getWidth()*4>max.get(0)||original.getHeight()*4>max.get(0))return null;
        Pixmap detailed=new Pixmap(cached);
        if(detailed.getWidth()!=original.getWidth()*4||detailed.getHeight()!=original.getHeight()*4){detailed.dispose();return null;}
        if(data.disposePixmap())original.dispose();REPLACED.set(true);return detailed;
    }

    // GLTexture asks twice if format conversion is needed; retain until next consume.
    public static boolean dispose(TextureData data) { return REPLACED.get() || data.disposePixmap(); }

    /** Scale2x: keeps the palette and transparency, smooths pixel-art diagonals. */
    public static Pixmap scale2x(Pixmap p) {
        if (p.getFormat() == Pixmap.Format.RGBA8888) return scale2xRgba(p);
        int w = p.getWidth(), h = p.getHeight();
        int[] pixels = new int[w*h];
        for (int y=0; y<h; y++) for (int x=0; x<w; x++) {
            int c = p.getPixel(x,y);
            pixels[y*w+x] = (c & 255) == 0 ? 0 : c;
        }
        Pixmap out = new Pixmap(w*2, h*2, p.getFormat());
        out.setBlending(Pixmap.Blending.None);
        for (int y=0; y<h; y++) for (int x=0; x<w; x++) {
            int e=pixels[y*w+x], b=pixels[Math.max(0,y-1)*w+x],
                d=pixels[y*w+Math.max(0,x-1)], f=pixels[y*w+Math.min(w-1,x+1)],
                hh=pixels[Math.min(h-1,y+1)*w+x];
            boolean edge=b!=hh && d!=f;
            out.drawPixel(x*2,y*2,edge && d==b ? d:e);
            out.drawPixel(x*2+1,y*2,edge && b==f ? f:e);
            out.drawPixel(x*2,y*2+1,edge && d==hh ? d:e);
            out.drawPixel(x*2+1,y*2+1,edge && hh==f ? f:e);
        }
        return out;
    }

    /** Same Scale2x on the raw RGBA8888 buffer (one bulk read/write instead of a native call per pixel):
     *  world chunks are rebuilt while walking, so this keeps the overworld smooth. */
    static Pixmap scale2xRgba(Pixmap p) {
        int w = p.getWidth(), h = p.getHeight(), w2 = w * 2;
        int[] px = new int[w * h];
        p.getPixels().duplicate().order(ByteOrder.BIG_ENDIAN).asIntBuffer().get(px);   // RGBA bytes -> 0xRRGGBBAA
        for (int i = 0; i < px.length; i++) if ((px[i] & 255) == 0) px[i] = 0;
        int[] o = new int[w2 * h * 2];
        for (int y = 0; y < h; y++) {
            int up = Math.max(0, y - 1) * w, row = y * w, down = Math.min(h - 1, y + 1) * w;
            int o0 = (y * 2) * w2, o1 = o0 + w2;
            for (int x = 0; x < w; x++) {
                int e = px[row + x], b = px[up + x], d = px[row + Math.max(0, x - 1)],
                    f = px[row + Math.min(w - 1, x + 1)], hh = px[down + x];
                boolean edge = b != hh && d != f;
                o[o0 + x * 2] = edge && d == b ? d : e;
                o[o0 + x * 2 + 1] = edge && b == f ? f : e;
                o[o1 + x * 2] = edge && d == hh ? d : e;
                o[o1 + x * 2 + 1] = edge && hh == f ? f : e;
            }
        }
        Pixmap out = new Pixmap(w2, h * 2, Pixmap.Format.RGBA8888);
        out.setBlending(Pixmap.Blending.None);
        out.getPixels().duplicate().order(ByteOrder.BIG_ENDIAN).asIntBuffer().put(o);
        return out;
    }
}
