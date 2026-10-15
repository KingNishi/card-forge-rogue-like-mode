import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import java.nio.file.*;
public class PatchFloatingMage{
 public static void main(String[] args)throws Exception{
  ClassNode c=new ClassNode();new ClassReader(Files.readAllBytes(Paths.get(args[0]))).accept(c,0);int count=0;
  for(MethodNode m:c.methods)if(m.name.equals("draw"))for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode call){
   if(call.owner.equals("forge/adventure/character/FloatingMageMod"))System.exit(2);
   if(call.owner.equals("com/badlogic/gdx/graphics/g2d/Batch")&&call.name.equals("draw")&&call.desc.equals("(Lcom/badlogic/gdx/graphics/g2d/TextureRegion;FFFF)V")){
    m.instructions.insertBefore(call,new VarInsnNode(Opcodes.ALOAD,0));
    m.instructions.set(call,new MethodInsnNode(Opcodes.INVOKESTATIC,"forge/adventure/character/FloatingMageMod","draw","(Lcom/badlogic/gdx/graphics/g2d/Batch;Lcom/badlogic/gdx/graphics/g2d/TextureRegion;FFFFLforge/adventure/character/CharacterSprite;)V",false));count++;
   }
  }
  if(count!=1)throw new IllegalStateException("Expected one character draw: "+count);
  ClassWriter out=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(out);Files.write(Paths.get(args[1]),out.toByteArray());
 }
}
