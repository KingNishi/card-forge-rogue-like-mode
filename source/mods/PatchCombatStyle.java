import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import java.nio.file.*;
public class PatchCombatStyle {
 public static void main(String[] args)throws Exception{
  ClassNode c=new ClassNode();new ClassReader(Files.readAllBytes(Paths.get(args[0]))).accept(c,0);
  String helper="forge/adventure/util/CombatStyleMod";int changes=0;
  for(MethodNode m:c.methods){
   if(c.name.equals("forge/screens/match/MatchController")&&m.name.equals("getPlayerSleeve")){
    boolean exists=false;for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode call&&call.owner.equals(helper)&&call.name.equals("playerSleeve"))exists=true;
    if(!exists)for(AbstractInsnNode n:m.instructions.toArray())if(n.getOpcode()==Opcodes.ARETURN){InsnList hook=new InsnList();hook.add(new VarInsnNode(Opcodes.ALOAD,0));hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,helper,"playerSleeve","(Lforge/assets/FImage;Lforge/game/player/PlayerView;)Lforge/assets/FImage;",false));m.instructions.insertBefore(n,hook);changes++;}
   }
   boolean menuExists=false;for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode call&&call.owner.equals(helper)&&(call.name.equals("deckMenu")||call.name.equals("combatMenu")))menuExists=true;
   if(m.name.equals("buildMenu")&&!menuExists&&(c.name.equals("forge/deck/FDeckEditor$1")||c.name.equals("forge/screens/match/views/VGameMenu"))){
    for(AbstractInsnNode n:m.instructions.toArray())if(n.getOpcode()==Opcodes.RETURN){
     InsnList hook=new InsnList();hook.add(new VarInsnNode(Opcodes.ALOAD,0));
     if(c.name.equals("forge/deck/FDeckEditor$1")){hook.add(new VarInsnNode(Opcodes.ALOAD,0));hook.add(new FieldInsnNode(Opcodes.GETFIELD,c.name,"this$0","Lforge/deck/FDeckEditor;"));hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,helper,"deckMenu","(Lforge/menu/FDropDownMenu;Lforge/deck/FDeckEditor;)V",false));}
     else hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,helper,"combatMenu","(Lforge/menu/FDropDownMenu;)V",false));
     m.instructions.insertBefore(n,hook);changes++;
    }
   }
   for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode call){
    if(call.owner.equals("forge/assets/FSkinColor")&&call.name.equals("get")&&call.desc.equals("(Lforge/assets/FSkinColor$Colors;)Lforge/assets/FSkinColor;")){call.owner=helper;call.name="color";changes++;}
    if(c.name.equals("forge/screens/constructed/SleeveSelector")&&m.name.equals("<init>")){
     if(call.owner.equals("forge/assets/FSkin")&&call.name.equals("getSleeves")){call.owner=helper;call.name="available";changes++;}
     else if(call.owner.equals(c.name)&&call.name.equals("addSleevesLabel")){call.setOpcode(Opcodes.INVOKESTATIC);call.owner=helper;call.name="label";call.desc="(Lforge/screens/constructed/SleeveSelector;Lforge/assets/FImage;I)V";changes++;}
    }
   }
  }
  if(changes==0){System.exit(2);return;}ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);Files.write(Paths.get(args[1]),w.toByteArray());
 }
}
