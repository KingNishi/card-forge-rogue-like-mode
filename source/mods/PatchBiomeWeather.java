import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import java.nio.file.*;
public class PatchBiomeWeather {
 public static void main(String[] args)throws Exception{
  ClassNode c=new ClassNode();new ClassReader(Files.readAllBytes(Paths.get(args[0]))).accept(c,0);String helper="forge/adventure/util/BiomeWeatherMod";
  for(MethodNode m:c.methods)for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode call&&call.owner.equals(helper)){System.exit(2);return;}
  int count=0;
  for(MethodNode m:c.methods)if(m.name.equals("draw")){
   if(c.name.endsWith("WorldStage")&&m.desc.equals("()V"))for(AbstractInsnNode n:m.instructions.toArray())if(n.getOpcode()==Opcodes.RETURN){InsnList hook=new InsnList();hook.add(new VarInsnNode(Opcodes.ALOAD,0));hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,helper,"draw","(Lforge/adventure/stage/WorldStage;)V",false));m.instructions.insertBefore(n,hook);count++;}
   if(c.name.endsWith("WorldBackground")&&m.desc.equals("(Lcom/badlogic/gdx/graphics/g2d/Batch;F)V")){
    InsnList hook=new InsnList();hook.add(new VarInsnNode(Opcodes.ALOAD,1));hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,helper,"before","(Lcom/badlogic/gdx/graphics/g2d/Batch;)V",false));m.instructions.insert(hook);count++;
    for(AbstractInsnNode n:m.instructions.toArray())if(n.getOpcode()==Opcodes.RETURN){hook=new InsnList();hook.add(new VarInsnNode(Opcodes.ALOAD,1));hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,helper,"after","(Lcom/badlogic/gdx/graphics/g2d/Batch;)V",false));m.instructions.insertBefore(n,hook);count++;}
   }
  }
  if(count==0)throw new IllegalStateException("No weather hooks");ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);Files.write(Paths.get(args[1]),w.toByteArray());
 }
}
