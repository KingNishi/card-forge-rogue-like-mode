import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import java.nio.file.*;
public class PatchAchievementRewards {
 public static void main(String[] args)throws Exception{
  ClassNode c=new ClassNode();new ClassReader(Files.readAllBytes(Paths.get(args[0]))).accept(c,0);
  String helper="forge/localinstance/achievements/AchievementBoosterMod";
  String extra="forge/localinstance/achievements/AdventureRewardAchievements";
  int changes=0;
  if(c.name.endsWith("/AdventureAchievements")){
   MethodNode m=c.methods.stream().filter(n->n.name.equals("addAchievements")).findFirst().orElseThrow();
   if(m.instructions.iterator().hasNext()){
    boolean exists=false;for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode call&&call.owner.equals(extra))exists=true;
    if(!exists)for(AbstractInsnNode n:m.instructions.toArray())if(n.getOpcode()==Opcodes.RETURN){InsnList hook=new InsnList();hook.add(new VarInsnNode(Opcodes.ALOAD,0));hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,extra,"add","(Lforge/localinstance/achievements/AchievementCollection;)V",false));m.instructions.insertBefore(n,hook);changes++;}
   }
  }else if(c.name.endsWith("/AchievementCollection")){
   for(MethodNode m:c.methods)if(m.name.equals("updateAll")&&m.desc.equals("(Lforge/game/player/Player;)V"))for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode call&&call.owner.equals("forge/localinstance/achievements/Achievement")&&call.name.equals("update")){
    m.instructions.insertBefore(n,new VarInsnNode(Opcodes.ALOAD,0));call.setOpcode(Opcodes.INVOKESTATIC);call.owner=helper;call.desc="(Lforge/localinstance/achievements/Achievement;Lforge/game/player/Player;Lforge/localinstance/achievements/AchievementCollection;)I";changes++;
   }
  }else if(c.name.endsWith("/GameHUD")){
   for(MethodNode m:c.methods)if(m.name.equals("enter")||m.name.equals("act")){
    boolean exists=false;for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode call&&call.owner.equals(helper))exists=true;
    if(!exists)for(AbstractInsnNode n:m.instructions.toArray())if(n.getOpcode()==Opcodes.RETURN){m.instructions.insertBefore(n,new MethodInsnNode(Opcodes.INVOKESTATIC,helper,"deliver","()V",false));changes++;}
   }
  }else throw new IllegalStateException(c.name);
  if(changes==0){System.exit(2);return;}
  ClassWriter out=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(out);Files.write(Paths.get(args[1]),out.toByteArray());
 }
}
