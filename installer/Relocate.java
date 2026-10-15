import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/**
 * Standalone installer helper: the mods are compiled with the author's install folder (D:/forge/) as a string constant.
 * This rewrites that prefix in the constant pool of every class in the given JARs to the new install folder, so the
 * modded game runs from any folder without recompiling.
 *
 *   java Relocate NEW_ROOT jar1 [jar2 ...]      e.g.  java Relocate "E:\Games\Forge" forge-gui-mobile-dev-...jar
 */
public class Relocate {
    static final String OLD_SLASH = "D:/forge/", OLD_BACK = "D:\\forge\\";

    public static void main(String[] args) throws Exception {
        if (args.length < 2) { System.err.println("usage: Relocate NEW_ROOT jar..."); System.exit(1); }
        String root = args[0].replace('\\', '/');
        while (root.endsWith("/")) root = root.substring(0, root.length() - 1);
        String newSlash = root + "/", newBack = newSlash.replace('/', '\\');
        int total = 0;
        for (int i = 1; i < args.length; i++) {
            int n = relocateJar(Path.of(args[i]), newSlash, newBack);
            System.out.println(Path.of(args[i]).getFileName() + ": " + n + " class(es) relocated");
            total += n;
        }
        System.out.println("relocated to " + newSlash + " (" + total + " classes)");
    }

    static int relocateJar(Path jar, String newSlash, String newBack) throws IOException {
        Path tmp = jar.resolveSibling(jar.getFileName() + ".relocating");
        int changed = 0;
        byte[] needle1 = OLD_SLASH.getBytes("UTF-8"), needle2 = OLD_BACK.getBytes("UTF-8");
        try (ZipFile zip = new ZipFile(jar.toFile());
             ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(tmp), 1 << 16))) {
            for (Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements(); ) {
                ZipEntry entry = e.nextElement();
                byte[] data;
                try (InputStream in = zip.getInputStream(entry)) { data = in.readAllBytes(); }
                if (entry.getName().endsWith(".class") && (indexOf(data, needle1) >= 0 || indexOf(data, needle2) >= 0)) {
                    byte[] moved = rewrite(data, newSlash, newBack);
                    if (moved != data) { data = moved; changed++; }
                }
                ZipEntry copy = new ZipEntry(entry.getName());
                copy.setTime(entry.getTime());
                out.putNextEntry(copy);
                out.write(data);
                out.closeEntry();
            }
        }
        if (changed > 0) Files.move(tmp, jar, StandardCopyOption.REPLACE_EXISTING);
        else Files.delete(tmp);
        return changed;
    }

    /** rewrites the CONSTANT_Utf8 entries of one class file; returns the same array when nothing changed */
    static byte[] rewrite(byte[] cls, String newSlash, String newBack) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(cls));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(cls.length + 512);
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(in.readInt());                       // magic
        out.writeShort(in.readUnsignedShort());           // minor
        out.writeShort(in.readUnsignedShort());           // major
        int count = in.readUnsignedShort();
        out.writeShort(count);
        boolean any = false;
        for (int i = 1; i < count; i++) {
            int tag = in.readUnsignedByte();
            out.writeByte(tag);
            switch (tag) {
                case 1 -> {
                    String s = in.readUTF();
                    if (s.contains(OLD_SLASH) || s.contains(OLD_BACK)) {
                        s = s.replace(OLD_SLASH, newSlash).replace(OLD_BACK, newBack);
                        any = true;
                    }
                    out.writeUTF(s);
                }
                case 3, 4, 9, 10, 11, 12, 17, 18 -> out.writeInt(in.readInt());
                case 5, 6 -> { out.writeLong(in.readLong()); i++; }
                case 7, 8, 16, 19, 20 -> out.writeShort(in.readUnsignedShort());
                case 15 -> { out.writeByte(in.readUnsignedByte()); out.writeShort(in.readUnsignedShort()); }
                default -> throw new IOException("unknown constant pool tag " + tag);
            }
        }
        if (!any) return cls;
        in.transferTo(out);                               // rest of the class is index-based and unchanged
        out.flush();
        return bytes.toByteArray();
    }

    static int indexOf(byte[] data, byte[] needle) {
        outer:
        for (int i = 0; i <= data.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) if (data[i + j] != needle[j]) continue outer;
            return i;
        }
        return -1;
    }
}
