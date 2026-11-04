import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import java.nio.file.*;
public class PatchArenaMotion {
 static final String H="forge/adventure/util/ArenaMotionMod";
 public static void main(String[] args)throws Exception{
  ClassNode c=new ClassNode();new ClassReader(Files.readAllBytes(Paths.get(args[0]))).accept(c,0);int changes=0;
  for(MethodNode m:c.methods){
   String method=c.name.equals("forge/screens/match/views/VZoneDisplay")&&m.name.equals("update")&&m.desc.equals("()V")?"handUpdated":
    c.name.equals("forge/screens/match/views/VCardDisplayArea$CardAreaPanel")&&m.name.equals("draw")&&m.desc.equals("(Lforge/Graphics;)V")?"handPanel":null;
   if(method==null)continue;
   boolean exists=false;for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode call&&call.owner.equals(H)&&call.name.equals(method))exists=true;
   if(exists)continue;
   String desc=method.equals("handUpdated")?"(Lforge/screens/match/views/VZoneDisplay;)V":"(Lforge/screens/match/views/VCardDisplayArea$CardAreaPanel;)V";
   if(method.equals("handPanel")){InsnList a=new InsnList();a.add(new VarInsnNode(Opcodes.ALOAD,0));a.add(new MethodInsnNode(Opcodes.INVOKESTATIC,H,method,desc,false));m.instructions.insert(a);changes++;}
   else for(AbstractInsnNode n:m.instructions.toArray())if(n.getOpcode()==Opcodes.RETURN){InsnList a=new InsnList();a.add(new VarInsnNode(Opcodes.ALOAD,0));a.add(new MethodInsnNode(Opcodes.INVOKESTATIC,H,method,desc,false));m.instructions.insertBefore(n,a);changes++;}
  }
  if(c.name.equals("forge/screens/match/views/VCardDisplayArea$CardAreaPanel"))   // planeswalker ready glow after the card is drawn
   for(MethodNode m:c.methods){
    if(!m.name.equals("draw")||!m.desc.equals("(Lforge/Graphics;)V"))continue;
    boolean has=false;for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode call&&call.owner.equals(H)&&call.name.equals("panelDrawn"))has=true;
    if(has)continue;
    for(AbstractInsnNode n:m.instructions.toArray())if(n.getOpcode()==Opcodes.RETURN){InsnList a=new InsnList();a.add(new VarInsnNode(Opcodes.ALOAD,0));a.add(new VarInsnNode(Opcodes.ALOAD,1));
     a.add(new MethodInsnNode(Opcodes.INVOKESTATIC,H,"panelDrawn","(Lforge/screens/match/views/VCardDisplayArea$CardAreaPanel;Lforge/Graphics;)V",false));m.instructions.insertBefore(n,a);changes++;}
   }
  if(c.name.equals("forge/screens/match/CardFlightOverlay")){
   for(String name:new String[]{"drawEnter","drawLeave"}){
    String original="arenaOriginal"+Character.toUpperCase(name.charAt(0))+name.substring(1);
    if(c.methods.stream().anyMatch(m->m.name.equals(original)))continue;
    MethodNode old=c.methods.stream().filter(m->m.name.equals(name)).findFirst().orElseThrow();old.name=original;
    MethodNode bridge=new MethodNode(Opcodes.ACC_PRIVATE|Opcodes.ACC_STATIC,name,old.desc,null,null);
    bridge.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));bridge.instructions.add(new VarInsnNode(Opcodes.ALOAD,1));bridge.instructions.add(new VarInsnNode(Opcodes.FLOAD,2));
    bridge.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,H,name.equals("drawEnter")?"enter":"leave","(Lforge/Graphics;Ljava/lang/Object;F)V",false));bridge.instructions.add(new InsnNode(Opcodes.RETURN));c.methods.add(bridge);changes++;
   }
  }
  if(c.name.equals("forge/screens/match/CardFlightOverlay"))for(MethodNode m:c.methods)
   if(m.name.equals("draw")&&m.desc.equals("(Lforge/Graphics;Lforge/game/player/PlayerView;F)V")){
    boolean exists=false;for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode call&&call.owner.equals(H)&&call.name.equals("effects"))exists=true;
    if(!exists){InsnList a=new InsnList();a.add(new VarInsnNode(Opcodes.ALOAD,0));a.add(new MethodInsnNode(Opcodes.INVOKESTATIC,H,"effects","(Lforge/Graphics;)V",false));m.instructions.insert(a);changes++;}
   }
  for(MethodNode m:c.methods){
   boolean isTap=c.name.endsWith("CardTapAnimation")||c.name.endsWith("CardUnTapAnimation");
   int transformIndex=0;
   for(AbstractInsnNode n:m.instructions.toArray()){
    if(n instanceof FieldInsnNode f&&f.getOpcode()==Opcodes.PUTFIELD&&f.name.equals("durationNanos")&&f.desc.equals("F")){
     if(n.getPrevious() instanceof MethodInsnNode call&&call.owner.equals(H))continue;
     m.instructions.insertBefore(n,new MethodInsnNode(Opcodes.INVOKESTATIC,H,"duration","(F)F",false));changes++;
    }
    if(isTap&&n instanceof LdcInsnNode ldc&&ldc.cst instanceof Float value&&value==.18f){
     if(n.getNext() instanceof MethodInsnNode call&&call.owner.equals(H))continue;
     m.instructions.insert(n,new MethodInsnNode(Opcodes.INVOKESTATIC,H,"tapDuration","(F)F",false));changes++;
    }
    if(isTap&&m.name.equals("drawCard")&&n.getOpcode()==Opcodes.FDIV){
     if(n.getNext() instanceof MethodInsnNode call&&call.owner.equals(H))continue;
     m.instructions.insert(n,new MethodInsnNode(Opcodes.INVOKESTATIC,H,"eased","(F)F",false));changes++;
    }
    if(c.name.endsWith("CardTransformAnimation")&&n instanceof LdcInsnNode ldc&&ldc.cst instanceof Float value&&n.getNext() instanceof FieldInsnNode f&&f.getOpcode()==Opcodes.PUTFIELD&&f.name.equals("DURATION")){
     if(value==.001f)ldc.cst=m.name.equals("<init>")?.18f:new float[]{.16f,.18f,.18f,.25f}[Math.min(3,transformIndex)];transformIndex++;
     m.instructions.insert(n,new MethodInsnNode(Opcodes.INVOKESTATIC,H,"transformDuration","(F)F",false));changes++;
    }
   }
  }
  if(changes==0){System.exit(2);return;}ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);Files.write(Paths.get(args[1]),w.toByteArray());
 }
}
