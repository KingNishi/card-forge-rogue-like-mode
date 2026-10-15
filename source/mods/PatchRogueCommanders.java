import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import java.nio.file.*;
/** Rogue Commanders main-menu button: the StartScene constructor ends with RogueCommandersScene.hookStart(this),
 *  which wires the "Rogue" button added to ui/start_menu*.json. */
public class PatchRogueCommanders {
 public static void main(String[] args)throws Exception{
  ClassNode c=new ClassNode();new ClassReader(Files.readAllBytes(Paths.get(args[0]))).accept(c,0);
  String H="forge/adventure/scene/RogueCommandersScene";int count=0;
  for(MethodNode m:c.methods){
   if(!m.name.equals("<init>"))continue;
   boolean hooked=false;for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode call&&call.owner.equals(H))hooked=true;
   if(hooked)continue;
   for(AbstractInsnNode n:m.instructions.toArray())if(n.getOpcode()==Opcodes.RETURN){
    InsnList a=new InsnList();a.add(new VarInsnNode(Opcodes.ALOAD,0));
    a.add(new MethodInsnNode(Opcodes.INVOKESTATIC,H,"hookStart","(Lforge/adventure/scene/StartScene;)V",false));
    m.instructions.insertBefore(n,a);count++;
   }
  }
  if(count==0){System.exit(2);return;}
  ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);Files.write(Paths.get(args[1]),w.toByteArray());
 }
}
