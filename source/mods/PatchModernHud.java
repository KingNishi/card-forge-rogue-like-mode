import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import java.nio.file.*;
public class PatchModernHud {
 public static void main(String[] args)throws Exception{
  ClassNode cn=new ClassNode();new ClassReader(Files.readAllBytes(Paths.get(args[0]))).accept(cn,0);
  String[] inputs={"touchDown","touchDragged","touchUp","keyDown","keyUp"};
  for(String input:inputs){
   if(cn.methods.stream().anyMatch(m->m.name.equals("hudOriginal_"+input)))cn.methods.removeIf(m->m.name.equals(input));
  }
  for(MethodNode m:cn.methods)for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode c&&c.owner.equals("forge/adventure/util/ModernHudMod")){
   AbstractInsnNode previous=n.getPrevious();if(!(previous instanceof VarInsnNode v)||v.getOpcode()!=Opcodes.ALOAD||v.var!=0)throw new IllegalStateException("Unexpected HUD hook");
   m.instructions.remove(previous);m.instructions.remove(n);
  }
  int changes=0;
  for(MethodNode m:cn.methods)if(m.name.equals("<init>"))for(AbstractInsnNode n:m.instructions.toArray())if(n.getOpcode()==Opcodes.RETURN){
   InsnList add=new InsnList();add.add(new VarInsnNode(Opcodes.ALOAD,0));add.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"forge/adventure/util/ModernHudMod","install","(Lforge/adventure/stage/GameHUD;)V",false));m.instructions.insertBefore(n,add);changes++;
  }
  if(changes!=1)throw new IllegalStateException("Expected one HUD constructor: "+changes);
  for(MethodNode m:cn.methods)if(m.name.equals("enter")||m.name.equals("act"))for(AbstractInsnNode n:m.instructions.toArray())if(n.getOpcode()==Opcodes.RETURN){
   InsnList layout=new InsnList();layout.add(new VarInsnNode(Opcodes.ALOAD,0));layout.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"forge/adventure/util/ModernHudMod","layout","(Lforge/adventure/stage/GameHUD;)V",false));m.instructions.insertBefore(n,layout);
  }
  MethodNode abilities=cn.methods.stream().filter(m->m.name.equals("updateAbility")).findFirst().orElseThrow();
  for(AbstractInsnNode n:abilities.instructions.toArray())if(n.getOpcode()==Opcodes.RETURN){
   InsnList layout=new InsnList();layout.add(new VarInsnNode(Opcodes.ALOAD,0));layout.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"forge/adventure/util/ModernHudMod","layoutAbilities","(Lforge/adventure/stage/GameHUD;)V",false));abilities.instructions.insertBefore(n,layout);
  }
  MethodNode dispose=cn.methods.stream().filter(m->m.name.equals("dispose")&&m.desc.equals("()V")).findFirst().orElseThrow();
  InsnList cleanup=new InsnList();cleanup.add(new VarInsnNode(Opcodes.ALOAD,0));cleanup.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"forge/adventure/util/ModernHudMod","dispose","(Lforge/adventure/stage/GameHUD;)V",false));dispose.instructions.insert(cleanup);
  for(String input:inputs){
   MethodNode original=cn.methods.stream().filter(m->m.name.equals("hudOriginal_"+input)||m.name.equals(input)).findFirst().orElseThrow();
   original.name="hudOriginal_"+input;
   MethodNode bridge=new MethodNode(Opcodes.ACC_PUBLIC,input,original.desc,null,null);
   bridge.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));int slot=1;
   for(Type t:Type.getArgumentTypes(original.desc))bridge.instructions.add(new VarInsnNode(Opcodes.ILOAD,slot++));
   bridge.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"forge/adventure/util/ModernHudMod",input,"(Lforge/adventure/stage/GameHUD;"+original.desc.substring(1),false));
   bridge.instructions.add(new InsnNode(Opcodes.IRETURN));cn.methods.add(bridge);
  }
  ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);cn.accept(w);Files.write(Paths.get(args[1]),w.toByteArray());
 }
}
