import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import java.nio.file.*;

/**
 * MapStage.loadObjects, normal (non-rotating) shop: the shop rarity roll
 *     WorldSave.getCurrentSave().getWorld().getRandom().nextInt(100)
 * becomes
 *     ShopHourMod.shopRandom(WorldSave.getCurrentSave().getWorld(), this, id).nextInt(100)
 * so the shop type is re-rolled every hour. Stack-neutral apart from two extra pushes, no new branches.
 *
 * usage: java --add-exports ... PatchShopHour.java <in MapStage.class> <out MapStage.class>
 * exit code: 0 patched, 2 nothing to patch (already patched / code changed)
 */
public class PatchShopHour {
    static final String MOD = "forge/adventure/stage/ShopHourMod";

    public static void main(String[] a) throws Exception {
        ClassNode cn = new ClassNode();
        new ClassReader(Files.readAllBytes(Paths.get(a[0]))).accept(cn, 0);
        int patched = 0;
        for (MethodNode m : cn.methods) {
            // already patched?
            boolean has = false;
            for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext())
                if (n instanceof MethodInsnNode && ((MethodInsnNode) n).owner.equals(MOD)) has = true;
            if (has) continue;
            // local variable holding the shop object id: the int passed to setRotatingShopSeed(int, long)
            int idVar = -1;
            for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
                if (n instanceof MethodInsnNode && ((MethodInsnNode) n).name.equals("setRotatingShopSeed")) {
                    AbstractInsnNode p = n.getPrevious();             // lload seed
                    if (p != null) p = p.getPrevious();               // iload id
                    if (p instanceof VarInsnNode && p.getOpcode() == Opcodes.ILOAD) idVar = ((VarInsnNode) p).var;
                }
            }
            if (idVar < 0) continue;
            for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
                if (!(n instanceof MethodInsnNode)) continue;
                MethodInsnNode g = (MethodInsnNode) n;
                if (!g.owner.equals("forge/adventure/world/World") || !g.name.equals("getRandom")) continue;
                AbstractInsnNode b = g.getNext(), ni = b == null ? null : b.getNext();
                if (!(b instanceof IntInsnNode) || ((IntInsnNode) b).operand != 100) continue;
                if (!(ni instanceof MethodInsnNode) || !((MethodInsnNode) ni).name.equals("nextInt")) continue;
                InsnList l = new InsnList();
                l.add(new VarInsnNode(Opcodes.ALOAD, 0));
                l.add(new VarInsnNode(Opcodes.ILOAD, idVar));
                MethodInsnNode call = new MethodInsnNode(Opcodes.INVOKESTATIC, MOD, "shopRandom",
                        "(Lforge/adventure/world/World;Lforge/adventure/stage/MapStage;I)Ljava/util/Random;", false);
                l.add(call);
                m.instructions.insertBefore(g, l);
                m.instructions.remove(g);
                n = call;
                patched++;
                System.out.println("patched shop roll in " + cn.name + "." + m.name + " (id var " + idVar + ")");
            }
        }
        if (patched == 0) { System.out.println("nothing to patch"); System.exit(2); }
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        Files.write(Paths.get(a[1]), cw.toByteArray());
    }
}
