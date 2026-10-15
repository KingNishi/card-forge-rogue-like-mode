import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import java.nio.file.*;

/**
 * Map screen / mini map: Assets.getNewMiniMapTexture(biomePixmap) -> MapStyleMod.miniMap(assets, biomePixmap),
 * which returns the texture of the calm painted map. Same stack shape, no new branches.
 * exit code: 0 patched, 2 nothing to patch
 */
public class PatchMapStyle {
    public static void main(String[] a) throws Exception {
        ClassNode cn = new ClassNode();
        new ClassReader(Files.readAllBytes(Paths.get(a[0]))).accept(cn, 0);
        int n = 0;
        for (MethodNode m : cn.methods)
            for (AbstractInsnNode i : m.instructions.toArray())
                if (i instanceof MethodInsnNode c && c.owner.equals("forge/assets/Assets") && c.name.equals("getNewMiniMapTexture")
                        && c.desc.equals("(Lcom/badlogic/gdx/graphics/Pixmap;)Lcom/badlogic/gdx/graphics/Texture;")) {
                    m.instructions.set(c, new MethodInsnNode(Opcodes.INVOKESTATIC, "forge/adventure/util/MapStyleMod", "miniMap",
                            "(Lforge/assets/Assets;Lcom/badlogic/gdx/graphics/Pixmap;)Lcom/badlogic/gdx/graphics/Texture;", false));
                    n++;
                }
        if (n == 0) { System.out.println("nothing to patch"); System.exit(2); }
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS); cn.accept(w);
        Files.write(Paths.get(a[1]), w.toByteArray());
        System.out.println("patched " + n + " mini map texture call(s) in " + cn.name);
    }
}
