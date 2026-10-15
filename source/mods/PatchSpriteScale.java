import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import java.nio.file.*;

/**
 * CharacterSprite: the actor size comes from the frame's pixel size. Replaces
 *   TextureRegion.getRegionWidth()/getRegionHeight()  ->  SpriteScaleMod.w()/h()
 *   Sprite.getWidth()/getHeight()                      ->  SpriteScaleMod.sw()/sh()
 * so "@2x" sprite sheets keep the normal size on the map. Same stack shape, no new branches.
 *
 * usage: java --add-exports ... PatchSpriteScale.java <in.class> <out.class>
 * exit code: 0 patched, 2 nothing to patch
 */
public class PatchSpriteScale {
    static final String MOD = "forge/adventure/character/SpriteScaleMod";

    public static void main(String[] a) throws Exception {
        ClassNode cn = new ClassNode();
        new ClassReader(Files.readAllBytes(Paths.get(a[0]))).accept(cn, 0);
        int patched = 0;
        for (MethodNode m : cn.methods) {
            for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
                if (!(n instanceof MethodInsnNode) || n.getOpcode() != Opcodes.INVOKEVIRTUAL) continue;
                MethodInsnNode c = (MethodInsnNode) n;
                String repl = null, desc = null;
                if (c.owner.equals("com/badlogic/gdx/graphics/g2d/TextureRegion") && c.desc.equals("()I")) {
                    if (c.name.equals("getRegionWidth")) repl = "w";
                    if (c.name.equals("getRegionHeight")) repl = "h";
                    desc = "(Lcom/badlogic/gdx/graphics/g2d/TextureRegion;)I";
                } else if (c.owner.equals("com/badlogic/gdx/graphics/g2d/Sprite") && c.desc.equals("()F")) {
                    if (c.name.equals("getWidth")) repl = "sw";
                    if (c.name.equals("getHeight")) repl = "sh";
                    desc = "(Lcom/badlogic/gdx/graphics/g2d/Sprite;)F";
                }
                if (repl == null) continue;
                MethodInsnNode r = new MethodInsnNode(Opcodes.INVOKESTATIC, MOD, repl, desc, false);
                m.instructions.set(c, r);
                n = r;
                patched++;
            }
        }
        if (patched == 0) { System.out.println("nothing to patch"); System.exit(2); }
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        Files.write(Paths.get(a[1]), cw.toByteArray());
        System.out.println("patched " + patched + " size call(s) in " + cn.name);
    }
}
