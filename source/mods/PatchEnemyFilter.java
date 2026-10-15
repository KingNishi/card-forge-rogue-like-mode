import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import java.nio.file.*;
public class PatchEnemyFilter {
 public static void main(String[] args)throws Exception{
  ClassNode c=new ClassNode();new ClassReader(Files.readAllBytes(Paths.get(args[0]))).accept(c,0);int count=0;
  for(MethodNode m:c.methods)if(m.name.equals("setFilter")&&m.desc.equals("(Lcom/badlogic/gdx/graphics/Texture$TextureFilter;Lcom/badlogic/gdx/graphics/Texture$TextureFilter;)V")){
   for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode call&&call.name.equals("filter")&&call.owner.equals("forge/adventure/util/WorldSprite2xMod")){System.exit(2);return;}
   InsnList add=new InsnList();for(int i=1;i<=2;i++){add.add(new VarInsnNode(Opcodes.ALOAD,0));add.add(new VarInsnNode(Opcodes.ALOAD,i));add.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"forge/adventure/util/WorldSprite2xMod","filter","(Lcom/badlogic/gdx/graphics/GLTexture;Lcom/badlogic/gdx/graphics/Texture$TextureFilter;)Lcom/badlogic/gdx/graphics/Texture$TextureFilter;",false));add.add(new VarInsnNode(Opcodes.ASTORE,i));}m.instructions.insert(add);count++;
  }
  if(count!=1)throw new IllegalStateException("Enemy filter hook missing");ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);Files.write(Paths.get(args[1]),w.toByteArray());
 }
}
