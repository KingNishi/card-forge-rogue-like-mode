package forge.adventure.util;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import forge.assets.Assets;

import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Mod: the map screen shows a calm painted map instead of the noisy miniature of every terrain tile.
 * Terrain becomes soft regions (dominant colour per tile, blended between tiles, thin border lines,
 * slightly muted colours, light paper grain); roads and the town/dungeon symbols are kept pixel-exact on top.
 * Hooked into MapViewScene.enter (Assets.getNewMiniMapTexture) by PatchMapStyle.java.
 */
public final class MapStyleMod {
    static final int TILE = 4;                                    // the biome image has 4 px per world tile
    private static final Map<Pixmap, Pixmap> CACHE = new WeakHashMap<>();

    public static Texture miniMap(Assets assets, Pixmap biome) {
        Pixmap styled;
        try {
            synchronized (CACHE) {
                styled = CACHE.get(biome);
                if (styled == null) { styled = style(biome); CACHE.put(biome, styled); }
            }
        } catch (Throwable t) {
            t.printStackTrace();
            styled = biome;
        }
        return assets.getNewMiniMapTexture(styled);
    }

    static boolean road(int c) {
        int r = c >>> 24, g = (c >>> 16) & 255, b = (c >>> 8) & 255;
        return (Math.abs(r - 228) < 6 && Math.abs(g - 210) < 6 && Math.abs(b - 170) < 8)
            || (Math.abs(r - 244) < 6 && Math.abs(g - 210) < 6 && Math.abs(b - 156) < 8);
    }

    static int luma(int c) { return ((c >>> 24) * 3 + ((c >>> 16) & 255) * 6 + ((c >>> 8) & 255)) / 10; }

    static int quant(int c) { return ((c >>> 28) << 8) | (((c >>> 20) & 15) << 4) | ((c >>> 12) & 15); }

    public static Pixmap style(Pixmap src) {
        int w = src.getWidth(), h = src.getHeight();
        int[] px = new int[w * h];
        if (src.getFormat() == Pixmap.Format.RGBA8888) {
            IntBuffer b = src.getPixels().duplicate().order(ByteOrder.BIG_ENDIAN).asIntBuffer();
            b.get(px);
        } else {
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) px[y * w + x] = src.getPixel(x, y);
        }
        int tw = (w + TILE - 1) / TILE, th = (h + TILE - 1) / TILE;
        // 1. per tile: dominant terrain colour (mode of quantised colours) and the plain average
        int[] modeC = new int[tw * th], meanC = new int[tw * th], klass = new int[tw * th], tile = new int[tw * th];
        HashMap<Integer, int[]> votes = new HashMap<>();
        for (int ty = 0; ty < th; ty++) for (int tx = 0; tx < tw; tx++) {
            votes.clear();
            int best = -1, bestN = 0, n = 0, sr = 0, sg = 0, sb = 0;
            for (int y = ty * TILE; y < Math.min(h, ty * TILE + TILE); y++)
                for (int x = tx * TILE; x < Math.min(w, tx * TILE + TILE); x++) {
                    int c = px[y * w + x];
                    if (road(c)) continue;
                    int[] v = votes.computeIfAbsent(quant(c), k -> new int[4]);
                    v[0]++; v[1] += c >>> 24; v[2] += (c >>> 16) & 255; v[3] += (c >>> 8) & 255;
                    n++; sr += c >>> 24; sg += (c >>> 16) & 255; sb += (c >>> 8) & 255;
                    if (v[0] > bestN) { bestN = v[0]; best = quant(c); }
                }
            int i = ty * tw + tx;
            if (best < 0) { modeC[i] = meanC[i] = tile[i] = klass[i] = -1; continue; }
            int[] v = votes.get(best);
            modeC[i] = rgb(v[1] / v[0], v[2] / v[0], v[3] / v[0]);
            meanC[i] = rgb(sr / n, sg / n, sb / n);
            klass[i] = best;
        }
        // 2. map symbols (towns, caves, ...): compact clusters of several colours that differ from the terrain;
        //    terrain specks (trees, rocks) are small or single-coloured. Roads are kept by colour.
        boolean[] cand = new boolean[w * h], keep = new boolean[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = y * w + x, t = tile(modeC, tw, th, x, y);
            if (road(px[i])) { keep[i] = true; continue; }
            cand[i] = t != -1 && dist(px[i], t) > 45;
        }
        int[] stack = new int[w * h]; boolean[] seen = new boolean[w * h];
        int[] members = new int[4096];
        for (int start = 0; start < px.length; start++) {
            if (!cand[start] || seen[start]) continue;
            int sp = 0, n = 0; stack[sp++] = start; seen[start] = true;
            java.util.HashSet<Integer> colours = new java.util.HashSet<>();
            while (sp > 0) {
                int i = stack[--sp]; if (n < members.length) members[n] = i; n++;
                if (colours.size() < 8) colours.add(px[i]);
                int x = i % w, y = i / w;
                for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                    int nx = x + dx, ny = y + dy;
                    if ((dx == 0 && dy == 0) || nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                    int j = ny * w + nx;
                    if (cand[j] && !seen[j]) { seen[j] = true; stack[sp++] = j; }
                }
            }
            if (n >= 12 && n <= members.length && colours.size() >= 3)
                for (int m = 0; m < n; m++) keep[members[m]] = true;
        }
        boolean[] grown = keep.clone();                           // the outline pixel around a symbol
        for (int y = 1; y < h - 1; y++) for (int x = 1; x < w - 1; x++) {
            int i = y * w + x;
            if (keep[i] || !cand[i]) continue;
            for (int dy = -1; dy <= 1 && !grown[i]; dy++) for (int dx = -1; dx <= 1; dx++) {
                int j = (y + dy) * w + x + dx;
                if (keep[j] && !road(px[j])) { grown[i] = true; break; }
            }
        }
        keep = grown;
        for (int i = 0; i < tile.length; i++)                      // mostly the dominant colour, a little of the average
            tile[i] = modeC[i] == -1 ? -1 : lerp(modeC[i], meanC[i], 0.3f);
        // fill tiles without terrain (fully covered by icons/roads) from neighbours
        for (int pass = 0; pass < 6; pass++)
            for (int ty = 0; ty < th; ty++) for (int tx = 0; tx < tw; tx++) {
                int i = ty * tw + tx;
                if (tile[i] != -1) continue;
                for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int nx = tx + d[0], ny = ty + d[1];
                    if (nx < 0 || ny < 0 || nx >= tw || ny >= th) continue;
                    int j = ny * tw + nx;
                    if (tile[j] != -1) { tile[i] = tile[j]; klass[i] = klass[j]; break; }
                }
            }
        // 3. clean single odd tiles (3x3 majority of classes)
        int[] k2 = klass.clone();
        HashMap<Integer, Integer> cnt = new HashMap<>();
        for (int ty = 2; ty < th - 2; ty++) for (int tx = 2; tx < tw - 2; tx++) {
            cnt.clear(); int best = klass[ty * tw + tx], bestN = 0;
            for (int dy = -2; dy <= 2; dy++) for (int dx = -2; dx <= 2; dx++) {
                int j = (ty + dy) * tw + tx + dx; int n = cnt.merge(klass[j], 1, Integer::sum);
                if (n > bestN) { bestN = n; best = klass[j]; }
            }
            k2[ty * tw + tx] = best;
        }
        klass = k2;
        tile = blur(blur(tile, tw, th), tw, th);                    // soft colour field
        // 4. paint: bilinear blend between tile centres, border lines, muted colours, paper grain
        int[] out = new int[w * h];
        for (int y = 0; y < h; y++) {
            float fy = (y + 0.5f) / TILE - 0.5f; int y0 = clamp((int) Math.floor(fy), th - 1), y1 = clamp(y0 + 1, th - 1); float ay = fy - (float) Math.floor(fy);
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                float fx = (x + 0.5f) / TILE - 0.5f; int x0 = clamp((int) Math.floor(fx), tw - 1), x1 = clamp(x0 + 1, tw - 1); float ax = fx - (float) Math.floor(fx);
                int c00 = tile[y0 * tw + x0], c10 = tile[y0 * tw + x1], c01 = tile[y1 * tw + x0], c11 = tile[y1 * tw + x1];
                float r = mix(ch(c00, 24), ch(c10, 24), ch(c01, 24), ch(c11, 24), ax, ay);
                float g = mix(ch(c00, 16), ch(c10, 16), ch(c01, 16), ch(c11, 16), ax, ay);
                float b = mix(ch(c00, 8), ch(c10, 8), ch(c01, 8), ch(c11, 8), ax, ay);
                // border between different terrain classes: thin darker line on the tile edge
                int tx = Math.min(tw - 1, x / TILE), ty = Math.min(th - 1, y / TILE);
                int kc = klass[ty * tw + tx];
                boolean edge = (x % TILE == TILE - 1 && tx + 1 < tw && klass[ty * tw + tx + 1] != kc && Math.abs(luma(tile[ty * tw + tx + 1]) - luma(tile[ty * tw + tx])) > 6)
                            || (y % TILE == TILE - 1 && ty + 1 < th && klass[(ty + 1) * tw + tx] != kc && Math.abs(luma(tile[(ty + 1) * tw + tx]) - luma(tile[ty * tw + tx])) > 6);
                float grain = 1f + 0.035f * (noise(x / 9f, y / 9f) - 0.5f) + 0.02f * (hash(x, y) - 0.5f);
                float shade = edge ? 0.78f : 1f;
                // mute: 18% towards grey, a touch darker
                float l = (r * 0.3f + g * 0.59f + b * 0.11f);
                r = (r * 0.82f + l * 0.18f) * 0.95f * grain * shade;
                g = (g * 0.82f + l * 0.18f) * 0.95f * grain * shade;
                b = (b * 0.82f + l * 0.18f) * 0.95f * grain * shade;
                out[i] = keep[i] ? px[i] : rgb(Math.round(r), Math.round(g), Math.round(b));
            }
        }
        Pixmap dst = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        dst.setBlending(Pixmap.Blending.None);
        dst.getPixels().duplicate().order(ByteOrder.BIG_ENDIAN).asIntBuffer().put(out);
        return dst;
    }

    static int[] blur(int[] t, int tw, int th) {                 // 3x3 box blur of a tile colour grid
        int[] o = new int[t.length];
        for (int y = 0; y < th; y++) for (int x = 0; x < tw; x++) {
            int n = 0, r = 0, g = 0, b = 0;
            for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                int xx = x + dx, yy = y + dy; if (xx < 0 || yy < 0 || xx >= tw || yy >= th) continue;
                int c = t[yy * tw + xx]; if (c == -1) continue; n++; r += c >>> 24; g += (c >>> 16) & 255; b += (c >>> 8) & 255;
            }
            o[y * tw + x] = n == 0 ? t[y * tw + x] : rgb(r / n, g / n, b / n);
        }
        return o;
    }

    static int tile(int[] t, int tw, int th, int x, int y) { return t[Math.min(th - 1, y / TILE) * tw + Math.min(tw - 1, x / TILE)]; }
    static int dist(int a, int b) { int r = (a >>> 24) - (b >>> 24), g = ((a >>> 16) & 255) - ((b >>> 16) & 255), bl = ((a >>> 8) & 255) - ((b >>> 8) & 255); return (int) Math.sqrt(r * r + g * g + bl * bl); }
    static int lerp(int a, int b, float t) { return rgb(Math.round(ch(a, 24) + (ch(b, 24) - ch(a, 24)) * t), Math.round(ch(a, 16) + (ch(b, 16) - ch(a, 16)) * t), Math.round(ch(a, 8) + (ch(b, 8) - ch(a, 8)) * t)); }

    static int clamp(int v, int max) { return v < 0 ? 0 : Math.min(v, max); }
    static float ch(int c, int shift) { return (c >>> shift) & 255; }
    static float mix(float a, float b, float c, float d, float ax, float ay) { return (a * (1 - ax) + b * ax) * (1 - ay) + (c * (1 - ax) + d * ax) * ay; }
    static int rgb(int r, int g, int b) { return (Math.max(0, Math.min(255, r)) << 24) | (Math.max(0, Math.min(255, g)) << 16) | (Math.max(0, Math.min(255, b)) << 8) | 255; }
    static float hash(int x, int y) { int n = x * 374761393 + y * 668265263; n = (n ^ (n >>> 13)) * 1274126177; return ((n ^ (n >>> 16)) & 0xffff) / 65535f; }
    static float noise(float x, float y) {
        int xi = (int) Math.floor(x), yi = (int) Math.floor(y); float fx = x - xi, fy = y - yi;
        fx = fx * fx * (3 - 2 * fx); fy = fy * fy * (3 - 2 * fy);
        return mix(hash(xi, yi), hash(xi + 1, yi), hash(xi, yi + 1), hash(xi + 1, yi + 1), fx, fy);
    }
}
