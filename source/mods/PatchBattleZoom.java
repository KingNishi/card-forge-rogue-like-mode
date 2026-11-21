import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import java.nio.file.*;
public class PatchBattleZoom {
 static final String H="forge/adventure/util/BattleZoomMod";
 public static void main(String[] args)throws Exception{
  ClassNode c=new ClassNode();new ClassReader(Files.readAllBytes(Paths.get(args[0]))).accept(c,0);int edits=0;
  if(c.name.equals("forge/screens/match/MatchScreen$FieldScroller")&&c.methods.stream().noneMatch(m->m.name.equals("battleOriginalZoom"))){
   MethodNode old=c.methods.stream().filter(m->m.name.equals("zoom")&&m.desc.equals("(FFF)Z")).findFirst().orElseThrow();old.name="battleOriginalZoom";
   MethodNode bridge=new MethodNode(Opcodes.ACC_PUBLIC,"zoom","(FFF)Z",null,null);
   bridge.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));for(int i=1;i<=3;i++)bridge.instructions.add(new VarInsnNode(Opcodes.FLOAD,i));
   bridge.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,H,"gesture","(Lforge/toolbox/FScrollPane;FFF)Z",false));bridge.instructions.add(new InsnNode(Opcodes.IRETURN));c.methods.add(bridge);edits++;
  }
  if(c.name.equals("forge/screens/match/MatchScreen")){
   if(c.methods.stream().noneMatch(m->m.name.equals("battleOriginalKeyDown"))){
    MethodNode old=c.methods.stream().filter(m->m.name.equals("keyDown")&&m.desc.equals("(I)Z")).findFirst().orElseThrow();old.name="battleOriginalKeyDown";
    MethodNode bridge=new MethodNode(Opcodes.ACC_PUBLIC,"keyDown","(I)Z",null,null);bridge.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));bridge.instructions.add(new VarInsnNode(Opcodes.ILOAD,1));bridge.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,H,"key","(Lforge/screens/match/MatchScreen;I)Z",false));bridge.instructions.add(new InsnNode(Opcodes.IRETURN));c.methods.add(bridge);edits++;
   }
   for(MethodNode m:c.methods)if(m.name.equals("doLayout")){
    boolean exists=false;for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode call&&call.owner.equals(H)&&call.name.equals("layout"))exists=true;
    if(!exists)for(AbstractInsnNode n:m.instructions.toArray())if(n.getOpcode()==Opcodes.RETURN){InsnList a=new InsnList();a.add(new VarInsnNode(Opcodes.ALOAD,0));a.add(new MethodInsnNode(Opcodes.INVOKESTATIC,H,"layout","(Lforge/screens/match/MatchScreen;)V",false));m.instructions.insertBefore(n,a);edits++;}
   }
  }
  // mouse wheel over a field in a 3-4 player game: zoom that field (BattleZoomMod.wheel), else Forge's own scrolling
  if(c.name.equals("forge/Forge$MainInputProcessor"))for(MethodNode m:c.methods)if(m.name.equals("scrolled")&&m.desc.equals("(FF)Z")){
   boolean exists=false;for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode call&&call.owner.equals(H)&&call.name.equals("wheel"))exists=true;
   if(!exists){InsnList a=new InsnList();LabelNode go=new LabelNode();
    a.add(new VarInsnNode(Opcodes.FLOAD,2));a.add(new MethodInsnNode(Opcodes.INVOKESTATIC,H,"wheel","(F)Z",false));
    a.add(new JumpInsnNode(Opcodes.IFEQ,go));a.add(new InsnNode(Opcodes.ICONST_1));a.add(new InsnNode(Opcodes.IRETURN));a.add(go);a.add(new FrameNode(Opcodes.F_SAME,0,null,0,null));
    m.instructions.insert(a);edits++;}
  }
  // first mulligan free in every Commander game (BattleZoomMod.freeMulligan instead of rules.hasAppliedVariant(Brawl))
  if(c.name.equals("forge/game/mulligan/MulliganService"))for(MethodNode m:c.methods)for(AbstractInsnNode n:m.instructions.toArray())
   if(n instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKEVIRTUAL&&call.owner.equals("forge/game/GameRules")&&call.name.equals("hasAppliedVariant")){
    m.instructions.set(n,new MethodInsnNode(Opcodes.INVOKESTATIC,H,"freeMulligan","(Lforge/game/GameRules;Lforge/game/GameType;)Z",false));edits++;}
  // pre-game option "all mulligans free" (RogueLan, user 2026-11-15): every result of tuckCardsDuringMulligan goes through
  // BattleZoomMod.tuck(n, this)
  if(c.name.equals("forge/game/mulligan/LondonMulligan"))for(MethodNode m:c.methods)
   if(m.name.equals("tuckCardsDuringMulligan")&&m.desc.equals("()I")){
    boolean done=false;for(AbstractInsnNode n:m.instructions.toArray())done|=n instanceof MethodInsnNode call&&call.owner.equals(H)&&call.name.equals("tuck");
    if(done)continue;
    for(AbstractInsnNode n:m.instructions.toArray())if(n.getOpcode()==Opcodes.IRETURN){
     InsnList a=new InsnList();a.add(new VarInsnNode(Opcodes.ALOAD,0));
     a.add(new MethodInsnNode(Opcodes.INVOKESTATIC,H,"tuck","(ILforge/game/mulligan/AbstractMulligan;)I",false));
     m.instructions.insertBefore(n,a);edits++;}
   }
  // no "return 0 card(s) to the bottom" prompt after a free mulligan (user 2026-11-16): mulliganDraw calls
  // BattleZoomMod.tuckVia(controller, hand, n) instead of controller.tuckCardsViaMulligan(hand, n)
  if(c.name.equals("forge/game/mulligan/LondonMulligan"))for(MethodNode m:c.methods)
   if(m.name.equals("mulliganDraw"))for(AbstractInsnNode n:m.instructions.toArray())
    if(n instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKEVIRTUAL&&call.name.equals("tuckCardsViaMulligan")){
     m.instructions.set(n,new MethodInsnNode(Opcodes.INVOKESTATIC,H,"tuckVia",
      "(Lforge/game/player/PlayerController;Lforge/game/card/CardCollectionView;I)Lforge/game/card/CardCollectionView;",false));edits++;}
  if(edits==0){System.exit(2);return;}
  ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);Files.write(Paths.get(args[1]),w.toByteArray());
 }
}
