import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import java.nio.file.*;
/** Replace only DuelScene's encounter dialog factory; retain match startup and callback. */
public class PatchEnemyIntro {
 public static void main(String[] args)throws Exception{
  ClassNode c=new ClassNode();new ClassReader(Files.readAllBytes(Path.of(args[0]))).accept(c,0);
  String helper="forge/adventure/scene/EnemyIntroMod";int changes=0;
  for(MethodNode m:c.methods)if(m.name.equals("createFOption")){
   for(AbstractInsnNode n:m.instructions)if(n instanceof MethodInsnNode call&&call.owner.equals(helper)){System.exit(2);return;}
   m.instructions.clear();m.tryCatchBlocks.clear();if(m.localVariables!=null)m.localVariables.clear();
   for(int i=0;i<5;i++)m.instructions.add(new VarInsnNode(Opcodes.ALOAD,i));
   m.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,helper,"create","(Lforge/adventure/scene/DuelScene;Ljava/lang/String;Ljava/lang/String;Lforge/assets/FBufferedImage;Ljava/lang/Runnable;)Lforge/toolbox/FOptionPane;",false));
   m.instructions.add(new InsnNode(Opcodes.ARETURN));changes++;
  }
  if(changes!=1)throw new IllegalStateException("Expected exactly one dialog factory, got "+changes);
  ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);Files.write(Path.of(args[1]),w.toByteArray());
 }
}
