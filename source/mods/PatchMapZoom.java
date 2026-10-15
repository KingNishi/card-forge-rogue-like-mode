import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import java.nio.file.*;

public class PatchMapZoom {
    public static void main(String[] args) throws Exception {
        ClassNode cn = new ClassNode();
        new ClassReader(Files.readAllBytes(Paths.get(args[0]))).accept(cn, 0);
        int changed = 0;
        for (MethodNode method : cn.methods) {
            if (!method.name.equals("updateCamera")) continue;
            for (AbstractInsnNode n : method.instructions.toArray()) {
                if (!(n instanceof MethodInsnNode call)) continue;
                if (call.owner.equals("forge/adventure/util/MapZoomMod") && call.name.equals("update")) System.exit(2);
                if (call.getOpcode() != Opcodes.INVOKEVIRTUAL || !call.owner.equals("com/badlogic/gdx/graphics/Camera")
                    || !call.name.equals("update") || !call.desc.equals("()V")) continue;
                InsnList extra = new InsnList();
                extra.add(new VarInsnNode(Opcodes.ALOAD, 0));
                extra.add(new FieldInsnNode(Opcodes.GETFIELD, cn.name, "stage", "Lforge/adventure/stage/MapStage;"));
                extra.add(new VarInsnNode(Opcodes.ALOAD, 0));
                method.instructions.insertBefore(call, extra);
                method.instructions.set(call, new MethodInsnNode(Opcodes.INVOKESTATIC, "forge/adventure/util/MapZoomMod", "update",
                    "(Lcom/badlogic/gdx/graphics/Camera;Lforge/adventure/stage/MapStage;Lforge/adventure/stage/PointOfInterestMapRenderer;)V", false));
                changed++;
            }
        }
        if (changed != 1) throw new IllegalStateException("Expected one camera update, found " + changed);
        ClassWriter out = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(out); Files.write(Paths.get(args[1]), out.toByteArray());
        System.out.println("Patched zoom-aware map culling and camera bounds");
    }
}
