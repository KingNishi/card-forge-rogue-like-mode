import java.nio.file.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
public class PatchAttackTriggerRemoval {
 public static void main(String[] args)throws Exception{
  ClassNode c=new ClassNode();new ClassReader(Files.readAllBytes(Path.of(args[0]))).accept(c,0);
  String helper="forge/ai/AttackTriggerRemovalMod";boolean found=false;
  for(MethodNode m:c.methods){
   if(!m.name.equals("useRemovalNow")||!m.desc.equals("(Lforge/game/spellability/SpellAbility;Lforge/game/card/Card;ILforge/game/zone/ZoneType;)Z"))continue;
   found=true;for(var n:m.instructions.toArray())if(n instanceof MethodInsnNode call&&call.owner.equals(helper)){if(args.length>1)Files.write(Path.of(args[1]),Files.readAllBytes(Path.of(args[0])));System.out.println("Already patched");return;}
   InsnList code=new InsnList();LabelNode original=new LabelNode();
   code.add(new VarInsnNode(Opcodes.ALOAD,0));code.add(new VarInsnNode(Opcodes.ALOAD,1));code.add(new VarInsnNode(Opcodes.ILOAD,2));code.add(new VarInsnNode(Opcodes.ALOAD,3));
   code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,helper,"beforeAttack",m.desc,false));
   code.add(new JumpInsnNode(Opcodes.IFEQ,original));code.add(new InsnNode(Opcodes.ICONST_1));code.add(new InsnNode(Opcodes.IRETURN));code.add(original);code.add(new FrameNode(Opcodes.F_SAME,0,null,0,null));
   m.instructions.insert(code);
  }
  if(!found)throw new IllegalStateException("Expected removal timing API missing");
  ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(writer);
  Files.write(Path.of(args.length>1?args[1]:args[0]),writer.toByteArray());System.out.println("Patched native removal timing once");
 }
}
