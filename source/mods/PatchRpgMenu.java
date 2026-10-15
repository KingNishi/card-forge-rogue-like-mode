import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import java.nio.file.*;
public class PatchRpgMenu {
 public static void main(String[] args)throws Exception{
  ClassNode c=new ClassNode();new ClassReader(Files.readAllBytes(Paths.get(args[0]))).accept(c,0);
  int count=0;
  for(MethodNode m:c.methods)if((m.name.equals("enter")&&m.desc.equals("()V"))||(c.name.endsWith("InventoryScene")&&m.name.equals("act")&&m.desc.equals("(F)V"))){
   boolean exists=false;for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode call&&call.owner.equals("forge/adventure/util/RpgThemeMod"))exists=true;if(exists)continue;
   for(AbstractInsnNode n:m.instructions.toArray())if(n.getOpcode()==Opcodes.RETURN){InsnList hook=new InsnList();hook.add(new VarInsnNode(Opcodes.ALOAD,0));hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"forge/adventure/util/RpgThemeMod","scene","(Lforge/adventure/scene/UIScene;)V",false));m.instructions.insertBefore(n,hook);count++;}
  }
  if(count==0){System.exit(2);return;}ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);Files.write(Paths.get(args[1]),w.toByteArray());
 }
}
