import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import java.nio.file.*;
/** VStack.updateSizeAndPosition() ends with StackClearanceMod.after(this): the open stack list never covers a player portrait. */
public class PatchStackClearance {
 public static void main(String[] args)throws Exception{
  ClassNode c=new ClassNode();new ClassReader(Files.readAllBytes(Paths.get(args[0]))).accept(c,0);
  String H="forge/screens/match/views/StackClearanceMod";int count=0;
  for(MethodNode m:c.methods){
   if(!m.name.equals("updateSizeAndPosition")||!m.desc.equals("()V"))continue;
   for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode call&&call.owner.equals(H)){System.exit(2);return;}
   for(AbstractInsnNode n:m.instructions.toArray())if(n.getOpcode()==Opcodes.RETURN){
    InsnList hook=new InsnList();hook.add(new VarInsnNode(Opcodes.ALOAD,0));
    hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,H,"after","(Lforge/screens/match/views/VStack;)V",false));
    m.instructions.insertBefore(n,hook);count++;
   }
  }
  if(count==0){System.err.println("VStack.updateSizeAndPosition not found");System.exit(1);return;}
  ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);Files.write(Paths.get(args[1]),w.toByteArray());
 }
}
