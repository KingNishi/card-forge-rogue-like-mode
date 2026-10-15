import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import java.nio.file.*;

/**
 * Keeps big (custom 32x32) player portraits at 16x16 where Forge uses their pixel size:
 *   MapViewScene: Current.player().avatar().getRegionWidth()/Height()  -> AvatarSizeMod.w()/h()
 *   ArenaScene:   new Image(Current.player().avatar())                 -> ... ; AvatarSizeMod.fit(image)
 *                 new Image(enemy.getAvatar()) (+ PatchArenaAvatar halving)  -> ... ; AvatarSizeMod.fit(image)
 * No new branches, so stack map frames stay valid.
 *
 * usage: java --add-exports ... PatchAvatarSize.java <in.class> <out.class>
 * exit code: 0 patched, 2 nothing to patch
 */
public class PatchAvatarSize {
    static final String MOD = "forge/adventure/util/AvatarSizeMod";
    static final String IMAGE = "com/badlogic/gdx/scenes/scene2d/ui/Image";

    static boolean isAvatarCall(AbstractInsnNode n) {
        return n instanceof MethodInsnNode && ((MethodInsnNode) n).owner.equals("forge/adventure/player/AdventurePlayer")
                && ((MethodInsnNode) n).name.equals("avatar");
    }

    static boolean isEnemyAvatarCall(AbstractInsnNode n) {
        return n instanceof MethodInsnNode && ((MethodInsnNode) n).owner.equals("forge/adventure/character/EnemySprite")
                && ((MethodInsnNode) n).name.equals("getAvatar");
    }

    static boolean fitFollows(AbstractInsnNode at) {
        AbstractInsnNode d = at.getNext();
        return d != null && d.getOpcode() == Opcodes.DUP && d.getNext() instanceof MethodInsnNode
                && ((MethodInsnNode) d.getNext()).owner.equals(MOD);
    }

    /** enemy avatars in the arena (card art portraits are 256 px): fit after PatchArenaAvatar's halving, if present */
    static int patchEnemy(MethodNode m) {
        int patched = 0;
        for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
            if (!isEnemyAvatarCall(n) || !(n.getNext() instanceof MethodInsnNode)) continue;
            MethodInsnNode init = (MethodInsnNode) n.getNext();
            if (init.getOpcode() != Opcodes.INVOKESPECIAL || !init.owner.equals(IMAGE) || !init.name.equals("<init>")) continue;
            AbstractInsnNode at = init;
            if (init.getNext() != null && init.getNext().getOpcode() == Opcodes.NOP) {      // PatchArenaAvatar marker
                AbstractInsnNode s = init.getNext();
                for (int i = 0; s != null && i < 16; i++, s = s.getNext())
                    if (s instanceof MethodInsnNode && ((MethodInsnNode) s).name.equals("setSize")) { at = s; break; }
            }
            if (fitFollows(at)) continue;
            InsnList l = new InsnList();
            l.add(new InsnNode(Opcodes.DUP));
            l.add(new MethodInsnNode(Opcodes.INVOKESTATIC, MOD, "fit", "(Lcom/badlogic/gdx/scenes/scene2d/Actor;)V", false));
            m.instructions.insert(at, l);
            patched++;
        }
        return patched;
    }

    public static void main(String[] a) throws Exception {
        ClassNode cn = new ClassNode();
        new ClassReader(Files.readAllBytes(Paths.get(a[0]))).accept(cn, 0);
        int patched = 0;
        for (MethodNode m : cn.methods) {
            if (cn.name.endsWith("/ArenaScene")) patched += patchEnemy(m);
            for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
                if (!isAvatarCall(n)) continue;
                AbstractInsnNode next = n.getNext();
                if (!(next instanceof MethodInsnNode)) continue;
                MethodInsnNode c = (MethodInsnNode) next;
                if (c.owner.equals("com/badlogic/gdx/graphics/g2d/TextureRegion")
                        && (c.name.equals("getRegionWidth") || c.name.equals("getRegionHeight"))) {
                    MethodInsnNode r = new MethodInsnNode(Opcodes.INVOKESTATIC, MOD, c.name.equals("getRegionWidth") ? "w" : "h",
                            "(Lcom/badlogic/gdx/graphics/g2d/TextureRegion;)I", false);
                    m.instructions.set(c, r);
                    n = r;
                    patched++;
                } else if (c.getOpcode() == Opcodes.INVOKESPECIAL && c.owner.equals(IMAGE) && c.name.equals("<init>")) {
                    AbstractInsnNode after = c.getNext();
                    if (after != null && after.getOpcode() == Opcodes.DUP && after.getNext() instanceof MethodInsnNode
                            && ((MethodInsnNode) after.getNext()).owner.equals(MOD)) continue;   // already patched
                    InsnList l = new InsnList();
                    l.add(new InsnNode(Opcodes.DUP));
                    l.add(new MethodInsnNode(Opcodes.INVOKESTATIC, MOD, "fit", "(Lcom/badlogic/gdx/scenes/scene2d/Actor;)V", false));
                    m.instructions.insert(c, l);
                    patched++;
                }
            }
        }
        if (patched == 0) { System.out.println("nothing to patch"); System.exit(2); }
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        Files.write(Paths.get(a[1]), cw.toByteArray());
        System.out.println("patched " + patched + " portrait size use(s) in " + cn.name);
    }
}
