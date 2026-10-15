import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import java.nio.file.*;
/** Developer mode hook: Forge.render() ends with DevModeMod.frame() (does nothing unless -Dforge.devmode is set). */
public class PatchDevMode {
 public static void main(String[] args)throws Exception{
  ClassNode c=new ClassNode();new ClassReader(Files.readAllBytes(Paths.get(args[0]))).accept(c,0);
  String H="forge/DevModeMod";int count=0;
  for(MethodNode m:c.methods){
   if(!m.name.equals("render")||!m.desc.equals("()V"))continue;
   for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode call&&call.owner.equals(H)){System.exit(2);return;}
   for(AbstractInsnNode n:m.instructions.toArray())if(n.getOpcode()==Opcodes.RETURN){
    m.instructions.insertBefore(n,new MethodInsnNode(Opcodes.INVOKESTATIC,H,"frame","()V",false));count++;
   }
  }
  if(count==0){System.exit(2);return;}
  ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);Files.write(Paths.get(args[1]),w.toByteArray());
 }
}
