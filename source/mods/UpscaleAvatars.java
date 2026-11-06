import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.*;

/**
 * Upscales the "Avatar" region of every enemy sprite atlas 2x with the xBR pixel-art algorithm.
 * The upscaled avatar goes into a separate image "<sprite>_avatar2x.png", added to the atlas as a
 * second page; the original sprite sheet (map animations) is not touched.
 *
 * usage: java UpscaleAvatars.java <root dir> [--exclude <dir-name>] [--filter Nearest|Linear] [--check]
 * prints one line per atlas; exit code 0.
 */
public class UpscaleAvatars {
    static final String SUFFIX = "_avatar2x.png";

    public static void main(String[] a) throws Exception {
        Path root = Paths.get(a[0]);
        Set<String> exclude = new HashSet<>();
        String filter = "Nearest";
        boolean check = false;
        for (int i = 1; i < a.length; i++) {
            if (a[i].equals("--exclude")) exclude.add(a[++i]);
            else if (a[i].equals("--filter")) filter = a[++i];
            else if (a[i].equals("--check")) check = true;
        }
        List<Path> atlases;
        try (Stream<Path> s = Files.walk(root)) {
            atlases = s.filter(p -> p.toString().endsWith(".atlas"))
                    .filter(p -> { for (Path q : root.relativize(p)) if (exclude.contains(q.toString())) return false; return true; })
                    .sorted().collect(Collectors.toList());
        }
        int done = 0, skipped = 0, todo = 0;
        for (Path atlas : atlases) {
          try {
            String text = new String(Files.readAllBytes(atlas), StandardCharsets.UTF_8);
            if (text.contains(SUFFIX) || text.contains("_art.jpg")) { skipped++; continue; }   // _art.jpg: card art portrait (art_avatars.py)
            List<String> lines = new ArrayList<>(Arrays.asList(text.split("\r?\n", -1)));
            // locate "Avatar" region blocks (name line + indented property lines)
            List<int[]> blocks = new ArrayList<>();
            for (int i = 0; i < lines.size(); i++) {
                if (!lines.get(i).trim().equals("Avatar") || Character.isWhitespace(lines.get(i).isEmpty() ? 'x' : lines.get(i).charAt(0))) continue;
                int j = i + 1;
                while (j < lines.size() && !lines.get(j).isEmpty() && Character.isWhitespace(lines.get(j).charAt(0))) j++;
                blocks.add(new int[]{i, j});
            }
            if (blocks.isEmpty()) continue;
            if (check) { todo++; continue; }
            String pageName = lines.stream().map(String::trim).filter(l -> l.toLowerCase().endsWith(".png")).findFirst().orElse(null);
            if (pageName == null) { System.out.println("!! no page image: " + atlas); continue; }
            File pageFile = atlas.resolveSibling(pageName).toFile();
            BufferedImage page = ImageIO.read(pageFile);
            List<BufferedImage> outs = new ArrayList<>();
            List<List<String>> extra = new ArrayList<>();
            for (int[] b : blocks) {
                int x = 0, y = 0, w = 0, h = 0;
                List<String> keep = new ArrayList<>();
                for (int k = b[0] + 1; k < b[1]; k++) {
                    String l = lines.get(k).trim();
                    Matcher m = Pattern.compile("(xy|size|bounds|orig|offset):\\s*(-?\\d+),\\s*(-?\\d+)(?:,\\s*(-?\\d+),\\s*(-?\\d+))?").matcher(l);
                    if (m.find()) {
                        String key = m.group(1);
                        int p1 = Integer.parseInt(m.group(2)), p2 = Integer.parseInt(m.group(3));
                        if (key.equals("xy")) { x = p1; y = p2; }
                        else if (key.equals("size")) { w = p1; h = p2; }
                        else if (key.equals("bounds")) { x = p1; y = p2; w = Integer.parseInt(m.group(4)); h = Integer.parseInt(m.group(5)); }
                        else keep.add("  " + key + ": " + (p1 * 2) + ", " + (p2 * 2));
                    } else if (!l.startsWith("rotate")) keep.add("  " + l);
                }
                if (x < 0 || y < 0 || w <= 0 || h <= 0 || x + w > page.getWidth() || y + h > page.getHeight())
                    throw new IllegalStateException("Avatar region " + x + "," + y + " " + w + "x" + h + " outside " + pageName + " (" + page.getWidth() + "x" + page.getHeight() + ")");
                outs.add(xbr2x(page.getSubimage(x, y, w, h)));
                extra.add(keep);
            }
            // stack the upscaled avatars vertically in the new page
            int W = outs.stream().mapToInt(BufferedImage::getWidth).max().orElse(0);
            int H = outs.stream().mapToInt(BufferedImage::getHeight).sum();
            BufferedImage sheet = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
            StringBuilder pageText = new StringBuilder();
            String newName = pageName.replaceAll("(?i)\\.png$", "") + SUFFIX;
            pageText.append("\n").append(newName).append("\nsize: ").append(W).append(",").append(H)
                    .append("\nformat: RGBA8888\nfilter: ").append(filter).append(",").append(filter).append("\nrepeat: none\n");
            int yy = 0;
            for (int i = 0; i < outs.size(); i++) {
                BufferedImage o = outs.get(i);
                sheet.getGraphics().drawImage(o, 0, yy, null);
                pageText.append("Avatar\n  xy: 0, ").append(yy).append("\n  size: ").append(o.getWidth()).append(", ").append(o.getHeight()).append("\n");
                for (String e : extra.get(i)) pageText.append(e).append("\n");
                yy += o.getHeight();
            }
            ImageIO.write(sheet, "png", atlas.resolveSibling(newName).toFile());
            for (int i = blocks.size() - 1; i >= 0; i--) lines.subList(blocks.get(i)[0], blocks.get(i)[1]).clear();
            String body = String.join("\n", lines).replaceAll("\\s+$", "");
            Files.write(atlas, (body + "\n" + pageText).getBytes(StandardCharsets.UTF_8));
            done++;
          } catch (Exception ex) {
            System.out.println("skipped " + root.relativize(atlas) + ": " + ex.getMessage());
          }
        }
        if (check) System.out.println("would upscale " + todo + " atlases, " + skipped + " already done");
        else System.out.println("upscaled " + done + " atlases, " + skipped + " already done");
    }

    // ------------------------------------------------------------------ xBR 2x (Hyllian), alpha aware
    static int px(BufferedImage img, int x, int y) {
        x = Math.max(0, Math.min(img.getWidth() - 1, x));
        y = Math.max(0, Math.min(img.getHeight() - 1, y));
        int c = img.getRGB(x, y);
        return (c >>> 24) == 0 ? 0 : c;   // all fully transparent pixels are the same colour
    }

    static double[] yuva(int c) {
        double a = (c >>> 24), r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        return new double[]{0.299 * r + 0.587 * g + 0.114 * b, -0.169 * r - 0.331 * g + 0.5 * b, 0.5 * r - 0.419 * g - 0.081 * b, a};
    }

    static int d(int p, int q) {
        if (p == q) return 0;
        double[] a = yuva(p), b = yuva(q);
        return (int) (48 * Math.abs(a[0] - b[0]) + 7 * Math.abs(a[1] - b[1]) + 6 * Math.abs(a[2] - b[2]) + 48 * Math.abs(a[3] - b[3]));
    }

    static boolean eq(int p, int q) {
        if (p == q) return true;
        double[] a = yuva(p), b = yuva(q);
        return Math.abs(a[0] - b[0]) <= 48 && Math.abs(a[1] - b[1]) <= 7 && Math.abs(a[2] - b[2]) <= 6 && Math.abs(a[3] - b[3]) <= 48;
    }

    static int blend(int dst, int src, double w) {
        double ad = (dst >>> 24) / 255.0, as = (src >>> 24) / 255.0;
        double a = ad * (1 - w) + as * w;
        if (a <= 0) return 0;
        int out = (int) Math.round(a * 255) << 24;
        for (int sh = 16; sh >= 0; sh -= 8) {
            double c = (((dst >> sh) & 255) * ad * (1 - w) + ((src >> sh) & 255) * as * w) / a;
            out |= (Math.max(0, Math.min(255, (int) Math.round(c)))) << sh;
        }
        return out;
    }

    /** returns new {n1, n2, n3} */
    static int[] kernel(int pe, int pi, int ph, int pf, int pg, int pc, int pd, int pb, int f4, int i4, int h5, int i5, int n1, int n2, int n3) {
        if (pe == ph || pe == pf) return new int[]{n1, n2, n3};
        int e = d(pe, pc) + d(pe, pg) + d(pi, h5) + d(pi, f4) + (d(ph, pf) << 2);
        int i = d(ph, pd) + d(ph, i5) + d(pf, i4) + d(pf, pb) + (d(pe, pi) << 2);
        int px = d(pe, pf) <= d(pe, ph) ? pf : ph;
        if (e < i && (!eq(pf, pb) && !eq(ph, pd) || eq(pe, pi) && (!eq(pf, i4) && !eq(ph, i5)) || eq(pe, pg) || eq(pe, pc))) {
            int ke = d(pf, pg), ki = d(ph, pc);
            boolean ex2 = pe != pc && pb != pc, ex3 = pe != pg && pd != pg;
            boolean left = (ke << 1) <= ki && ex3, up = ke >= (ki << 1) && ex2;
            if (left || up) {
                if (left) { n3 = blend(n3, px, 0.75); n2 = blend(n2, px, 0.25); }
                if (up) { n3 = blend(n3, px, 0.75); n1 = blend(n1, px, 0.25); }
            } else {
                n3 = blend(n3, px, 0.5);
            }
        } else if (e <= i) {
            n3 = blend(n3, px, 0.25);
        }
        return new int[]{n1, n2, n3};
    }

    static BufferedImage xbr2x(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage out = new BufferedImage(w * 2, h * 2, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int a1 = px(src, x - 1, y - 2), b1 = px(src, x, y - 2), c1 = px(src, x + 1, y - 2);
            int a0 = px(src, x - 2, y - 1), pa = px(src, x - 1, y - 1), pb = px(src, x, y - 1), pc = px(src, x + 1, y - 1), c4 = px(src, x + 2, y - 1);
            int d0 = px(src, x - 2, y), pd = px(src, x - 1, y), pe = px(src, x, y), pf = px(src, x + 1, y), f4 = px(src, x + 2, y);
            int g0 = px(src, x - 2, y + 1), pg = px(src, x - 1, y + 1), ph = px(src, x, y + 1), pi = px(src, x + 1, y + 1), i4 = px(src, x + 2, y + 1);
            int g5 = px(src, x - 1, y + 2), h5 = px(src, x, y + 2), i5 = px(src, x + 1, y + 2);
            int e0 = pe, e1 = pe, e2 = pe, e3 = pe, r[];
            r = kernel(pe, pi, ph, pf, pg, pc, pd, pb, f4, i4, h5, i5, e1, e2, e3); e1 = r[0]; e2 = r[1]; e3 = r[2];
            r = kernel(pe, pc, pf, pb, pi, pa, ph, pd, b1, c1, f4, c4, e0, e3, e1); e0 = r[0]; e3 = r[1]; e1 = r[2];
            r = kernel(pe, pa, pb, pd, pc, pg, pf, ph, d0, a0, b1, a1, e2, e1, e0); e2 = r[0]; e1 = r[1]; e0 = r[2];
            r = kernel(pe, pg, pd, ph, pa, pi, pb, pf, h5, g5, d0, g0, e3, e0, e2); e3 = r[0]; e0 = r[1]; e2 = r[2];
            out.setRGB(2 * x, 2 * y, e0); out.setRGB(2 * x + 1, 2 * y, e1);
            out.setRGB(2 * x, 2 * y + 1, e2); out.setRGB(2 * x + 1, 2 * y + 1, e3);
        }
        return out;
    }
}
