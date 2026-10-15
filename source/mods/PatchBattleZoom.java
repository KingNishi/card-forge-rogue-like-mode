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
  if(edits==0){System.exit(2);return;}
  ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);Files.write(Paths.get(args[1]),w.toByteArray());
 }
}
