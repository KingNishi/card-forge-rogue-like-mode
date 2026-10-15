import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import java.nio.file.*;

public class PatchShopSigns {
    public static void main(String[] args) throws Exception {
        ClassNode cn=new ClassNode();new ClassReader(Files.readAllBytes(Paths.get(args[0]))).accept(cn,0);
        int changed=0;
        for (MethodNode m:cn.methods) {
            if (!m.name.equals("<init>") || !m.desc.equals("(Lcom/badlogic/gdx/graphics/g2d/TextureRegion;)V"))continue;
            for (AbstractInsnNode n:m.instructions.toArray()) {
                if (n instanceof MethodInsnNode call && call.owner.equals("forge/adventure/util/ShopSignMod"))System.exit(2);
                if (n.getOpcode()!=Opcodes.RETURN)continue;
                InsnList add=new InsnList();add.add(new VarInsnNode(Opcodes.ALOAD,0));add.add(new VarInsnNode(Opcodes.ALOAD,1));
                add.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"forge/adventure/util/ShopSignMod","fit",
                    "(Lcom/badlogic/gdx/scenes/scene2d/Actor;Lcom/badlogic/gdx/graphics/g2d/TextureRegion;)V",false));
                m.instructions.insertBefore(n,add);changed++;
            }
        }
        if(changed!=1)throw new IllegalStateException("Expected one TextureSprite constructor, got "+changed);
        ClassWriter out=new ClassWriter(ClassWriter.COMPUTE_MAXS);cn.accept(out);
        Files.write(Paths.get(args[1]),out.toByteArray());System.out.println("Shop signs retain 16x16 logical size");
    }
}
