import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import java.nio.file.*;

public class PatchWorldSprite2x {
    static final String MOD="forge/adventure/util/WorldSprite2xMod";
    public static void main(String[] args) throws Exception {
        ClassNode c=new ClassNode();
        new ClassReader(Files.readAllBytes(Paths.get(args[0]))).accept(c,0);
        for (MethodNode m:c.methods) for (AbstractInsnNode n:m.instructions.toArray())
            if(n instanceof MethodInsnNode && ((MethodInsnNode)n).owner.equals(MOD)) System.exit(2);
        int count=0;
        for(MethodNode m:c.methods) for(AbstractInsnNode n:m.instructions.toArray()) {
            if(!(n instanceof MethodInsnNode)) continue;
            MethodInsnNode call=(MethodInsnNode)n;
            if(c.name.equals("com/badlogic/gdx/graphics/GLTexture") && m.name.equals("uploadImageData")
                    && call.owner.equals("com/badlogic/gdx/graphics/TextureData")) {
                String name=null, desc=null;
                if(call.name.equals("consumePixmap")) { name="consume"; desc="(Lcom/badlogic/gdx/graphics/TextureData;)Lcom/badlogic/gdx/graphics/Pixmap;"; }
                if(call.name.equals("disposePixmap")) { name="dispose"; desc="(Lcom/badlogic/gdx/graphics/TextureData;)Z"; }
                if(name!=null) { m.instructions.set(call,new MethodInsnNode(Opcodes.INVOKESTATIC,MOD,name,desc,false)); count++; }
            }
            if(c.name.equals("forge/adventure/stage/WorldBackground") && m.name.equals("buildChunkTexture")
                    && call.owner.equals("com/badlogic/gdx/graphics/Texture") && call.name.equals("<init>")
                    && call.desc.equals("(Lcom/badlogic/gdx/graphics/Pixmap;)V")) {
                m.instructions.insertBefore(call,new MethodInsnNode(Opcodes.INVOKESTATIC,MOD,"markWorld",
                    "(Lcom/badlogic/gdx/graphics/Pixmap;)Lcom/badlogic/gdx/graphics/Pixmap;",false)); count++;
            }
        }
        int expected=c.name.endsWith("GLTexture") ? 3 : 1;
        if(count!=expected) throw new IllegalStateException("Expected "+expected+" hooks, found "+count+" in "+c.name);
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS); c.accept(w);
        Files.write(Paths.get(args[1]),w.toByteArray());
        System.out.println("Installed "+count+" hooks in "+c.name);
    }
}
