import java.nio.file.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
/** Adds an observer without replacing native life loss, costs, replacements or event ordering. */
public class PatchRogueLifeLoss {
 public static void main(String[] args)throws Exception{
  byte[] bytes=Files.readAllBytes(Path.of(args[0]));ClassNode c=new ClassNode();new ClassReader(bytes).accept(c,0);
  String helper="forge/adventure/scene/RogueCommandersScene";int found=0;
  for(MethodNode m:c.methods){
   if(!m.name.equals("loseLife")||!m.desc.equals("(IZZLforge/game/spellability/SpellAbility;)I"))continue;
   for(var n:m.instructions.toArray())if(n instanceof MethodInsnNode call&&call.owner.equals(helper)&&call.name.equals("recordLifeLoss")){Files.write(Path.of(args[1]),bytes);System.out.println("Life loss observer already installed");return;}
   for(var n:m.instructions.toArray())if(n.getOpcode()==Opcodes.IRETURN){
    InsnList code=new InsnList();code.add(new InsnNode(Opcodes.DUP));code.add(new VarInsnNode(Opcodes.ALOAD,0));code.add(new VarInsnNode(Opcodes.ILOAD,2));code.add(new VarInsnNode(Opcodes.ILOAD,3));code.add(new VarInsnNode(Opcodes.ALOAD,4));
    code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,helper,"recordLifeLoss","(ILforge/game/player/Player;ZZLforge/game/spellability/SpellAbility;)V",false));m.instructions.insertBefore(n,code);found++;
   }
  }
  if(found==0)throw new IllegalStateException("Expected native life loss returns missing");
  ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(writer);Files.write(Path.of(args[1]),writer.toByteArray());System.out.println("Life loss observer: "+found+" return sites");
 }
}
