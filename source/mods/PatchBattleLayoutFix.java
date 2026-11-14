import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import java.nio.file.*;
public class PatchBattleLayoutFix {
 public static void main(String[] args)throws Exception{
  ClassNode c=new ClassNode();new ClassReader(Files.readAllBytes(Paths.get(args[0]))).accept(c,0);String H="forge/adventure/util/BattleLayoutFixMod";int changes=0;
  if(c.name.equals("forge/screens/match/views/VField")&&c.methods.stream().noneMatch(m->m.name.equals("layoutOriginalTryStackCard"))){
   MethodNode old=c.methods.stream().filter(m->m.name.equals("tryStackCard")).findFirst().orElseThrow();
   old.name="layoutOriginalTryStackCard";
   MethodNode bridge=new MethodNode(Opcodes.ACC_PRIVATE,"tryStackCard",old.desc,null,null);
   bridge.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));bridge.instructions.add(new VarInsnNode(Opcodes.ALOAD,1));bridge.instructions.add(new VarInsnNode(Opcodes.ALOAD,2));
   bridge.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,H,"stackCard","(Lforge/screens/match/views/VField;Lforge/game/card/CardView;Ljava/util/List;)Z",false));bridge.instructions.add(new InsnNode(Opcodes.IRETURN));c.methods.add(bridge);changes++;
  }
  for(MethodNode m:c.methods.toArray(new MethodNode[0])){
   boolean exists=false;for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode call&&call.owner.equals(H))exists=true;
   if(exists)continue;
   if(c.name.equals("forge/screens/match/views/VField$1")&&m.name.equals("run"))for(AbstractInsnNode n:m.instructions.toArray())if(n.getOpcode()==Opcodes.RETURN){InsnList a=new InsnList();a.add(new VarInsnNode(Opcodes.ALOAD,0));a.add(new FieldInsnNode(Opcodes.GETFIELD,c.name,"this$0","Lforge/screens/match/views/VField;"));a.add(new MethodInsnNode(Opcodes.INVOKESTATIC,H,"updated","(Lforge/screens/match/views/VField;)V",false));m.instructions.insertBefore(n,a);changes++;}
   if((c.name.endsWith("VCardDisplayArea")||c.name.endsWith("VZoneDisplay"))&&m.name.equals("layoutAndGetScrollBounds")){
    for(AbstractInsnNode n:m.instructions.toArray())if(n.getOpcode()==Opcodes.ARETURN){InsnList a=new InsnList();a.add(new VarInsnNode(Opcodes.ALOAD,0));a.add(new VarInsnNode(Opcodes.FLOAD,1));a.add(new VarInsnNode(Opcodes.FLOAD,2));a.add(new MethodInsnNode(Opcodes.INVOKESTATIC,H,"center","(Lforge/toolbox/FScrollPane$ScrollBounds;Lforge/screens/match/views/VCardDisplayArea;FF)Lforge/toolbox/FScrollPane$ScrollBounds;",false));m.instructions.insertBefore(n,a);changes++;}
   }
   // many attackers on a phone: one bundled arrow per defending player instead of 30 (user 2026-11-05: alpha strike lag)
   if(c.name.equals("forge/screens/match/TargetingOverlay")&&m.name.equals("assembleArrows"))for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode call&&call.name.equals("flushArrows")){m.instructions.insertBefore(n,new MethodInsnNode(Opcodes.INVOKESTATIC,H,"thinArrows","()V",false));changes++;}
   // compact lands (user 2026-11-13): tapped lands stay upright, a tap mark is drawn over them instead
   if(c.name.equals("forge/screens/match/views/VCardDisplayArea$CardAreaPanel")&&m.name.equals("getTappedAngle")&&m.desc.equals("()F"))for(AbstractInsnNode n:m.instructions.toArray())if(n.getOpcode()==Opcodes.FRETURN){InsnList a=new InsnList();a.add(new VarInsnNode(Opcodes.ALOAD,0));a.add(new MethodInsnNode(Opcodes.INVOKESTATIC,H,"tappedAngle","(FLforge/screens/match/views/VCardDisplayArea$CardAreaPanel;)F",false));m.instructions.insertBefore(n,a);changes++;}
   if(c.name.equals("forge/toolbox/FCardPanel")&&m.name.equals("rotateTransform")&&m.desc.equals("(Lforge/Graphics;FFFFFZ)V"))for(AbstractInsnNode n:m.instructions.toArray())if(n.getOpcode()==Opcodes.RETURN){InsnList a=new InsnList();a.add(new VarInsnNode(Opcodes.ALOAD,0));a.add(new VarInsnNode(Opcodes.ALOAD,1));for(int i=2;i<=5;i++)a.add(new VarInsnNode(Opcodes.FLOAD,i));a.add(new MethodInsnNode(Opcodes.INVOKESTATIC,H,"tapMark","(Lforge/toolbox/FCardPanel;Lforge/Graphics;FFFF)V",false));m.instructions.insertBefore(n,a);changes++;}
   if(c.name.endsWith("VField")&&m.name.equals("doLayout"))for(AbstractInsnNode n:m.instructions.toArray())if(n.getOpcode()==Opcodes.RETURN){InsnList a=new InsnList();a.add(new VarInsnNode(Opcodes.ALOAD,0));a.add(new VarInsnNode(Opcodes.FLOAD,1));a.add(new VarInsnNode(Opcodes.FLOAD,2));a.add(new MethodInsnNode(Opcodes.INVOKESTATIC,H,"field","(Lforge/screens/match/views/VField;FF)V",false));m.instructions.insertBefore(n,a);changes++;}
   if(c.name.equals("forge/screens/match/MatchScreen")&&m.name.equals("drawOverlay"))for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode call&&((call.owner.equals("forge/card/CardRenderer")&&call.name.equals("drawCard"))||(call.owner.equals("forge/card/CardImageRenderer")&&call.name.equals("drawDetails")))){
    m.instructions.insertBefore(n,new VarInsnNode(Opcodes.ALOAD,0));call.owner=H;call.name=call.name.equals("drawCard")?"preview":"details";call.desc=call.desc.substring(0,call.desc.indexOf(')'))+"Lforge/screens/match/MatchScreen;)V";changes++;
   }
  }
  // graveyard + flashback share one card panel: each zone row puts its own bounds back before it draws (user 2026-11-05)
  if(c.name.equals("forge/screens/match/views/VCardDisplayArea")&&c.methods.stream().noneMatch(m->m.name.equals("draw")&&m.desc.equals("(Lforge/Graphics;)V"))){
   MethodNode d=new MethodNode(Opcodes.ACC_PUBLIC,"draw","(Lforge/Graphics;)V",null,null);
   d.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));d.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,H,"beforeDraw","(Lforge/screens/match/views/VCardDisplayArea;)V",false));
   d.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));d.instructions.add(new VarInsnNode(Opcodes.ALOAD,1));d.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,"forge/toolbox/FScrollPane","draw","(Lforge/Graphics;)V",false));
   d.instructions.add(new InsnNode(Opcodes.RETURN));c.methods.add(d);changes++;
  }
  String method=c.name.equals("forge/toolbox/FButton")?"draw":c.name.equals("forge/toolbox/FList")?"drawBackground":null;
  if(method!=null&&c.methods.stream().noneMatch(m->m.name.equals(method.equals("draw")?"layoutOriginalDraw":"layoutOriginalBackground"))){
   MethodNode old=c.methods.stream().filter(m->m.name.equals(method)&&m.desc.equals("(Lforge/Graphics;)V")).findFirst().orElseThrow();old.name=method.equals("draw")?"layoutOriginalDraw":"layoutOriginalBackground";
   MethodNode bridge=new MethodNode(Opcodes.ACC_PUBLIC,method,old.desc,null,null);bridge.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));bridge.instructions.add(new VarInsnNode(Opcodes.ALOAD,1));bridge.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,H,method.equals("draw")?"button":"list","(L"+c.name+";Lforge/Graphics;)V",false));bridge.instructions.add(new InsnNode(Opcodes.RETURN));c.methods.add(bridge);changes++;
  }
  if(changes==0){System.exit(2);return;}ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);Files.write(Paths.get(args[1]),w.toByteArray());
 }
}
