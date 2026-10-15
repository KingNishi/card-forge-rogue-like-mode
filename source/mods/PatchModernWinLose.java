import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import java.nio.file.*;
/** ViewWinLose: doLayout and drawBackground go to ModernWinLose; the control switch reads ModernWinLose.controlType(...)
 *  so matches started from Adventure mode (also Rogue Commanders' Commander games) get the Adventure control. */
public class PatchModernWinLose {
 public static void main(String[] args)throws Exception{
  ClassNode c=new ClassNode();new ClassReader(Files.readAllBytes(Paths.get(args[0]))).accept(c,0);
  String H="forge/screens/match/winlose/ModernWinLose",V="Lforge/screens/match/winlose/ViewWinLose;";
  for(MethodNode m:c.methods)if(m.name.equals("drawBackground")){System.exit(2);return;}
  int done=0;
  for(MethodNode m:c.methods){
   if(m.name.equals("<init>")){
    for(AbstractInsnNode n:m.instructions.toArray())
     if(n instanceof MethodInsnNode call&&call.owner.equals("forge/game/GameView")&&call.name.equals("getGameType")){
      m.instructions.insert(n,new MethodInsnNode(Opcodes.INVOKESTATIC,H,"controlType","(Lforge/game/GameType;)Lforge/game/GameType;",false));done++;}
   }
   if(m.name.equals("doLayout")&&m.desc.equals("(FF)V")){
    m.instructions.clear();m.tryCatchBlocks.clear();m.localVariables=null;
    m.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));m.instructions.add(new VarInsnNode(Opcodes.FLOAD,1));m.instructions.add(new VarInsnNode(Opcodes.FLOAD,2));
    m.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,H,"layout","("+V+"FF)V",false));
    m.instructions.add(new InsnNode(Opcodes.RETURN));done++;
   }
  }
  MethodNode draw=new MethodNode(Opcodes.ACC_PROTECTED,"drawBackground","(Lforge/Graphics;)V",null,null);
  draw.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));draw.instructions.add(new VarInsnNode(Opcodes.ALOAD,1));
  draw.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,H,"paint","("+V+"Lforge/Graphics;)V",false));
  draw.instructions.add(new InsnNode(Opcodes.RETURN));
  c.methods.add(draw);
  if(done<2){System.err.println("ViewWinLose: expected getGameType + doLayout, found "+done);System.exit(1);}
  ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);Files.write(Paths.get(args[1]),w.toByteArray());
 }
}
