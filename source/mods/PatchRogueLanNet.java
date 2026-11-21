import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import java.nio.file.*;
/** 1v1 LAN (user 2026-11-14 "kein Unterschied zwischen Host und Client"): the joining player's NetGameController
 *  - selectCard: RogueLan.netSelect decides first - cards you cannot use right now open the big view (as on the host)
 *  - cheat(): the dev menu sends its commands to the host instead of doing nothing (RogueLan.netCheat) */
public class PatchRogueLanNet {
 public static void main(String[] args)throws Exception{
  ClassNode c=new ClassNode();new ClassReader(Files.readAllBytes(Paths.get(args[0]))).accept(c,0);
  String H="forge/adventure/util/RogueLan";int changes=0;
  for(MethodNode m:c.methods){
   boolean done=false;for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode call&&call.owner.equals(H))done=true;
   if(done)continue;
   if(m.name.equals("selectCard")&&m.desc.startsWith("(Lforge/game/card/CardView;")&&m.desc.endsWith(")Z")){
    InsnList a=new InsnList();LabelNode go=new LabelNode();
    a.add(new VarInsnNode(Opcodes.ALOAD,1));a.add(new MethodInsnNode(Opcodes.INVOKESTATIC,H,"netSelect","(Lforge/game/card/CardView;)Z",false));
    a.add(new JumpInsnNode(Opcodes.IFNE,go));a.add(new InsnNode(Opcodes.ICONST_0));a.add(new InsnNode(Opcodes.IRETURN));a.add(go);a.add(new FrameNode(Opcodes.F_SAME,0,null,0,null));   // branch target: same locals as on entry
    m.instructions.insert(a);changes++;
   }
   // German card pictures in LAN games (user 2026-11-14): every picture lookup may be swapped for a German scan
   if(c.name.equals("forge/ImageKeys")&&m.name.equals("getImageFile")&&m.desc.equals("(Ljava/lang/String;)Ljava/io/File;"))
    for(AbstractInsnNode n:m.instructions.toArray())if(n.getOpcode()==Opcodes.ARETURN){InsnList a=new InsnList();
     a.add(new VarInsnNode(Opcodes.ALOAD,0));a.add(new MethodInsnNode(Opcodes.INVOKESTATIC,H,"germanImage","(Ljava/io/File;Ljava/lang/String;)Ljava/io/File;",false));
     m.instructions.insertBefore(n,a);changes++;}
   // simultaneous mulligan (user 2026-11-14): RogueLan.parallelMulligan runs the round itself in LAN games
   if(c.name.equals("forge/game/mulligan/MulliganService")&&m.name.equals("runPlayerMulligans")&&m.desc.equals("()V")){
    InsnList a=new InsnList();LabelNode go=new LabelNode();
    a.add(new VarInsnNode(Opcodes.ALOAD,0));a.add(new MethodInsnNode(Opcodes.INVOKESTATIC,H,"parallelMulligan","(Lforge/game/mulligan/MulliganService;)Z",false));
    a.add(new JumpInsnNode(Opcodes.IFEQ,go));a.add(new InsnNode(Opcodes.RETURN));a.add(go);a.add(new FrameNode(Opcodes.F_SAME,0,null,0,null));
    m.instructions.insert(a);changes++;}
   // 1v1 vs AI (user 2026-11-15 screenshot): no "the AI has problems with these cards" list - it blocks the Rogue battle screen
   if(c.name.equals("forge/game/GameRules")&&m.name.equals("warnAboutAICards")&&m.desc.equals("()Z"))
    for(AbstractInsnNode n:m.instructions.toArray())if(n.getOpcode()==Opcodes.IRETURN){
     m.instructions.insertBefore(n,new MethodInsnNode(Opcodes.INVOKESTATIC,H,"aiWarn","(Z)Z",false));changes++;}
   // big card view (user 2026-11-15): arrow down / up switches between the picture and the card text (German in German games)
   if(c.name.equals("forge/card/CardZoom")&&m.name.equals("keyDown")&&m.desc.equals("(I)Z")){
    InsnList a=new InsnList();LabelNode go=new LabelNode(),flip=new LabelNode();
    a.add(new VarInsnNode(Opcodes.ILOAD,1));a.add(new IntInsnNode(Opcodes.BIPUSH,20));a.add(new JumpInsnNode(Opcodes.IF_ICMPEQ,flip));
    a.add(new VarInsnNode(Opcodes.ILOAD,1));a.add(new IntInsnNode(Opcodes.BIPUSH,19));a.add(new JumpInsnNode(Opcodes.IF_ICMPNE,go));
    a.add(flip);a.add(new FrameNode(Opcodes.F_SAME,0,null,0,null));
    a.add(new VarInsnNode(Opcodes.ALOAD,0));a.add(new InsnNode(Opcodes.FCONST_0));a.add(new LdcInsnNode(300f));
    a.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"forge/card/CardZoom","fling","(FF)Z",false));a.add(new InsnNode(Opcodes.IRETURN));
    a.add(go);a.add(new FrameNode(Opcodes.F_SAME,0,null,0,null));
    m.instructions.insert(a);changes++;}
   if(m.name.equals("cheat")&&m.desc.equals("()Lforge/interfaces/IDevModeCheats;"))
    for(AbstractInsnNode n:m.instructions.toArray())if(n.getOpcode()==Opcodes.ARETURN){
     m.instructions.insertBefore(n,new MethodInsnNode(Opcodes.INVOKESTATIC,H,"netCheat","(Lforge/interfaces/IDevModeCheats;)Lforge/interfaces/IDevModeCheats;",false));changes++;}
  }
  if(changes==0){System.exit(2);return;}
  ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
  c.accept(w);Files.write(Paths.get(args[1]),w.toByteArray());
 }
}
