import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import java.nio.file.*;

/**
 * Redirects the enemy-encounter call sites to AutoWinMod (same stack shape, so no frame changes):
 *   WorldStage: PlayerSprite.collideWith(MapActor)Z      -> AutoWinMod.worldCollide(PlayerSprite, MapActor)Z
 *   MapStage:   MapStage.beginDuel(EnemySprite)V (calls) -> AutoWinMod.mapBeginDuel(MapStage, EnemySprite)V
 *
 * usage: java --add-exports ... PatchAutoWin.java <in.class> <out.class>
 * exit code: 0 patched, 2 nothing to patch (already patched)
 */
public class PatchAutoWin {
    static final String MOD = "forge/adventure/stage/AutoWinMod";

    public static void main(String[] a) throws Exception {
        ClassNode cn = new ClassNode();
        new ClassReader(Files.readAllBytes(Paths.get(a[0]))).accept(cn, 0);
        int patched = 0;
        for (MethodNode m : cn.methods) {
            for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
                if (!(n instanceof MethodInsnNode) || n.getOpcode() != Opcodes.INVOKEVIRTUAL) continue;
                MethodInsnNode c = (MethodInsnNode) n;
                MethodInsnNode r = null;
                if (cn.name.equals("forge/adventure/stage/WorldStage") && c.owner.equals("forge/adventure/character/PlayerSprite")
                        && c.name.equals("collideWith") && c.desc.equals("(Lforge/adventure/character/MapActor;)Z")) {
                    r = new MethodInsnNode(Opcodes.INVOKESTATIC, MOD, "worldCollide",
                            "(Lforge/adventure/character/PlayerSprite;Lforge/adventure/character/MapActor;)Z", false);
                } else if (cn.name.equals("forge/adventure/stage/MapStage") && c.owner.equals("forge/adventure/stage/MapStage")
                        && c.name.equals("beginDuel") && c.desc.equals("(Lforge/adventure/character/EnemySprite;)V")
                        && !m.name.startsWith("lambda$")) {
                    r = new MethodInsnNode(Opcodes.INVOKESTATIC, MOD, "mapBeginDuel",
                            "(Lforge/adventure/stage/MapStage;Lforge/adventure/character/EnemySprite;)V", false);
                }
                if (r == null) continue;
                m.instructions.set(c, r);
                n = r;
                patched++;
                System.out.println("patched " + cn.name + "." + m.name);
            }
        }
        if (patched == 0) { System.out.println("nothing to patch"); System.exit(2); }
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        Files.write(Paths.get(a[1]), cw.toByteArray());
    }
}
