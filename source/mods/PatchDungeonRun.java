import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import java.nio.file.*;
/** Dungeon Run replaces the inn events:
 *  InnScene.startEvent()  -> DungeonRunScene.openFromInn(this)
 *  InnScene.refreshStatus() ends with DungeonRunScene.innStatus(this)
 *  AdventureEventController.createEvent(String) -> null  (no more Draft/Sealed/Jumpstart events at inns) */
public class PatchDungeonRun {
 public static void main(String[] args)throws Exception{
  ClassNode c=new ClassNode();new ClassReader(Files.readAllBytes(Paths.get(args[0]))).accept(c,0);String H="forge/adventure/scene/DungeonRunScene";int count=0;
  for(MethodNode m:c.methods){
   boolean hooked=false;for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode call&&call.owner.equals(H))hooked=true;
   if(c.name.equals("forge/adventure/scene/InnScene")&&m.name.equals("startEvent")&&m.desc.equals("()V")&&!hooked){
    m.instructions.clear();m.tryCatchBlocks.clear();if(m.localVariables!=null)m.localVariables.clear();   // whole body replaced
    m.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));m.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,H,"openFromInn","(Lforge/adventure/scene/InnScene;)V",false));m.instructions.add(new InsnNode(Opcodes.RETURN));
    count++;
   }
   if(c.name.equals("forge/adventure/scene/InnScene")&&m.name.equals("refreshStatus")&&m.desc.equals("()V")&&!hooked){
    for(AbstractInsnNode n:m.instructions.toArray())if(n.getOpcode()==Opcodes.RETURN){InsnList a=new InsnList();a.add(new VarInsnNode(Opcodes.ALOAD,0));a.add(new MethodInsnNode(Opcodes.INVOKESTATIC,H,"innStatus","(Lforge/adventure/scene/InnScene;)V",false));m.instructions.insertBefore(n,a);count++;}
   }
   if(c.name.equals("forge/adventure/util/AdventureEventController")&&m.name.equals("createEvent")&&m.desc.equals("(Ljava/lang/String;)Lforge/adventure/data/AdventureEventData;")){
    AbstractInsnNode first=m.instructions.getFirst();while(first!=null&&first.getOpcode()<0)first=first.getNext();
    if(first!=null&&first.getOpcode()==Opcodes.ACONST_NULL&&first.getNext()!=null&&first.getNext().getOpcode()==Opcodes.ARETURN)continue;
    m.instructions.clear();m.tryCatchBlocks.clear();if(m.localVariables!=null)m.localVariables.clear();
    m.instructions.add(new InsnNode(Opcodes.ACONST_NULL));m.instructions.add(new InsnNode(Opcodes.ARETURN));count++;
   }
  }
  if(count==0){System.exit(2);return;}ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);Files.write(Paths.get(args[1]),w.toByteArray());
 }
}
