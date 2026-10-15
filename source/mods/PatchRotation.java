import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import java.nio.file.*;

/**
 * Rotating town shops in Forge Adventure pick their shop type with the seed
 * "world random + LocalDate.now().toEpochDay()", i.e. once per calendar day.
 * This replaces LocalDate.now().toEpochDay() in MapStage with
 * System.currentTimeMillis() / periodMillis, so they rotate every period (default 1 hour).
 *
 * usage: java --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED
 *             --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED
 *             PatchRotation.java <in MapStage.class> <out MapStage.class> [periodMillis]
 * exit code: 0 patched, 2 nothing to patch (already patched / code changed)
 */
public class PatchRotation {
    public static void main(String[] a) throws Exception {
        long period = a.length > 2 ? Long.parseLong(a[2]) : 3_600_000L;
        ClassNode cn = new ClassNode();
        new ClassReader(Files.readAllBytes(Paths.get(a[0]))).accept(cn, 0);
        int patched = 0;
        for (MethodNode m : cn.methods) {
            for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
                if (!(n instanceof MethodInsnNode)) continue;
                MethodInsnNode now = (MethodInsnNode) n;
                if (now.getOpcode() != Opcodes.INVOKESTATIC || !now.owner.equals("java/time/LocalDate") || !now.name.equals("now") || !now.desc.equals("()Ljava/time/LocalDate;")) continue;
                AbstractInsnNode next = now.getNext();
                if (!(next instanceof MethodInsnNode)) continue;
                MethodInsnNode day = (MethodInsnNode) next;
                if (!day.owner.equals("java/time/LocalDate") || !day.name.equals("toEpochDay")) continue;
                InsnList repl = new InsnList();
                InsnNode div = new InsnNode(Opcodes.LDIV);
                repl.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/System", "currentTimeMillis", "()J", false));
                repl.add(new LdcInsnNode(period));
                repl.add(div);
                m.instructions.insertBefore(now, repl);
                m.instructions.remove(day);
                m.instructions.remove(now);
                n = div;   // continue scanning after the replacement
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
