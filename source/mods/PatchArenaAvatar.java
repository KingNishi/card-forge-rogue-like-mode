import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import java.nio.file.*;

/**
 * The arena scene shows enemy avatars at their pixel size. After the avatars were upscaled 2x
 * (UpscaleAvatars.java) they would appear twice as big, so this halves the size of every Image
 * built from EnemySprite.getAvatar() in ArenaScene: image.setSize(image.getWidth()/2, image.getHeight()/2).
 *
 * usage: java --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED
 *             --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED
 *             PatchArenaAvatar.java <in ArenaScene.class> <out ArenaScene.class>
 * exit code: 0 patched, 2 nothing to patch
 */
public class PatchArenaAvatar {
    static final String IMAGE = "com/badlogic/gdx/scenes/scene2d/ui/Image";

    public static void main(String[] a) throws Exception {
        ClassNode cn = new ClassNode();
        new ClassReader(Files.readAllBytes(Paths.get(a[0]))).accept(cn, 0);
        int patched = 0;
        for (MethodNode m : cn.methods) {
            for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
                if (!(n instanceof MethodInsnNode)) continue;
                MethodInsnNode get = (MethodInsnNode) n;
                if (!get.owner.equals("forge/adventure/character/EnemySprite") || !get.name.equals("getAvatar")) continue;
                AbstractInsnNode next = get.getNext();
                if (!(next instanceof MethodInsnNode)) continue;
                MethodInsnNode init = (MethodInsnNode) next;
                if (init.getOpcode() != Opcodes.INVOKESPECIAL || !init.owner.equals(IMAGE) || !init.name.equals("<init>")) continue;
                if (init.getNext() instanceof InsnNode && init.getNext().getOpcode() == Opcodes.NOP) continue;   // already patched marker
                // stack after <init>: [img]  ->  img.setSize(img.getWidth()/2, img.getHeight()/2), keeping [img]
                InsnList l = new InsnList();
                l.add(new InsnNode(Opcodes.NOP));                        // marker
                l.add(new InsnNode(Opcodes.DUP));                        // img img
                l.add(new InsnNode(Opcodes.DUP));                        // img img img
                l.add(new InsnNode(Opcodes.DUP));                        // img img img img
                l.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, IMAGE, "getWidth", "()F", false));   // img img img w
                l.add(new InsnNode(Opcodes.FCONST_2));
                l.add(new InsnNode(Opcodes.FDIV));                       // img img img w/2
                l.add(new InsnNode(Opcodes.SWAP));                       // img img w/2 img
                l.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, IMAGE, "getHeight", "()F", false));  // img img w/2 h
                l.add(new InsnNode(Opcodes.FCONST_2));
                l.add(new InsnNode(Opcodes.FDIV));                       // img img w/2 h/2
                l.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, IMAGE, "setSize", "(FF)V", false));  // img
                m.instructions.insert(init, l);
                patched++;
            }
        }
        if (patched == 0) { System.out.println("nothing to patch"); System.exit(2); }
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        Files.write(Paths.get(a[1]), cw.toByteArray());
        System.out.println("patched " + patched + " avatar image(s) in " + cn.name);
    }
}
