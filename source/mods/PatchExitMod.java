import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import java.nio.file.*;
/** forge.app.Main.main: call forge.app.ExitMod.afterMain() before it returns (user 2026-11-16). Idempotent (exit 2). */
public class PatchExitMod {
 public static void main(String[] args)throws Exception{
  ClassNode c=new ClassNode();new ClassReader(Files.readAllBytes(Paths.get(args[0]))).accept(c,0);int edits=0;
  if(c.name.equals("forge/app/Main"))for(MethodNode m:c.methods){
   if(!m.name.equals("main")||!m.desc.equals("([Ljava/lang/String;)V"))continue;
   boolean done=false;
   for(AbstractInsnNode n:m.instructions.toArray())done|=n instanceof MethodInsnNode call&&call.owner.equals("forge/app/ExitMod");
   if(done)continue;
   for(AbstractInsnNode n:m.instructions.toArray())if(n.getOpcode()==Opcodes.RETURN){
    m.instructions.insertBefore(n,new MethodInsnNode(Opcodes.INVOKESTATIC,"forge/app/ExitMod","afterMain","()V",false));edits++;}
  }
  if(edits==0){System.exit(2);return;}
  ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);Files.write(Paths.get(args[1]),w.toByteArray());
 }
}
