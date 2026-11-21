import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import java.nio.file.*;
/** ThreatAiMod hooks (user 2026-11-16): at the start of
 *  AiAttackController.choosePreferredDefenderPlayer(Player, boolean) and ComputerUtilCard.getBestCreatureAI(Iterable)
 *  ask ThreatAiMod first; a non-null answer is returned, null continues with Forge's own code. Idempotent (exit 2). */
public class PatchThreatAi {
 static final String H="forge/ai/ThreatAiMod";
 public static void main(String[] args)throws Exception{
  ClassNode c=new ClassNode();new ClassReader(Files.readAllBytes(Paths.get(args[0]))).accept(c,0);int edits=0;
  for(MethodNode m:c.methods){
   String helper=null,desc=null,type=null;boolean twoArgs=false;
   if(c.name.equals("forge/ai/AiAttackController")&&m.name.equals("choosePreferredDefenderPlayer")&&m.desc.equals("(Lforge/game/player/Player;Z)Lforge/game/player/Player;")){
    helper="defender";desc=m.desc;type="forge/game/player/Player";twoArgs=true;}
   if(c.name.equals("forge/ai/ComputerUtilCard")&&m.name.equals("getBestCreatureAI")&&m.desc.equals("(Ljava/lang/Iterable;)Lforge/game/card/Card;")){
    helper="bestCreature";desc=m.desc;type="forge/game/card/Card";}
   if(helper==null)continue;
   boolean done=false;
   for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode call&&call.owner.equals(H))done=true;
   if(done)continue;
   InsnList a=new InsnList();LabelNode go=new LabelNode();
   a.add(new VarInsnNode(Opcodes.ALOAD,0));
   if(twoArgs)a.add(new VarInsnNode(Opcodes.ILOAD,1));
   a.add(new MethodInsnNode(Opcodes.INVOKESTATIC,H,helper,desc,false));
   a.add(new InsnNode(Opcodes.DUP));a.add(new JumpInsnNode(Opcodes.IFNULL,go));a.add(new InsnNode(Opcodes.ARETURN));
   a.add(go);a.add(new FrameNode(Opcodes.F_SAME1,0,null,1,new Object[]{type}));a.add(new InsnNode(Opcodes.POP));
   m.instructions.insert(a);edits++;
  }
  if(edits==0){System.exit(2);return;}
  ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);Files.write(Paths.get(args[1]),w.toByteArray());
 }
}
