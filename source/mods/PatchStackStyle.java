import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import java.nio.file.*;
/** VStack / VStack$StackInstanceDisplay / FDropDown backgrounds: Graphics.fillRect(..) -> StackStyleMod.fillRect(Graphics,..)
 *  (same operand stack, receiver becomes the first argument). Only in the stack classes. */
public class PatchStackStyle {
 public static void main(String[] args)throws Exception{
  ClassNode c=new ClassNode();new ClassReader(Files.readAllBytes(Paths.get(args[0]))).accept(c,0);
  String H="forge/screens/match/views/StackStyleMod";int count=0;
  for(MethodNode m:c.methods){
   for(AbstractInsnNode n:m.instructions.toArray()){
    if(n instanceof MethodInsnNode call&&call.owner.equals(H)){System.exit(2);return;}
    if(n.getOpcode()==Opcodes.INVOKEVIRTUAL&&n instanceof MethodInsnNode call&&call.owner.equals("forge/Graphics")&&call.name.equals("fillRect")
       &&(call.desc.equals("(Lcom/badlogic/gdx/graphics/Color;FFFF)V")||call.desc.equals("(Lforge/assets/FSkinColor;FFFF)V"))){
     m.instructions.set(n,new MethodInsnNode(Opcodes.INVOKESTATIC,H,"fillRect","(Lforge/Graphics;"+call.desc.substring(1),false));count++;}
   }
  }
  if(c.name.equals("forge/screens/match/views/VStack")&&c.methods.stream().noneMatch(m->m.name.equals("drawBackground"))){
   MethodNode bg=new MethodNode(Opcodes.ACC_PROTECTED,"drawBackground","(Lforge/Graphics;)V",null,null);
   bg.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));bg.instructions.add(new VarInsnNode(Opcodes.ALOAD,1));
   bg.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,H,"drawBackground","(Lforge/screens/match/views/VStack;Lforge/Graphics;)V",false));
   bg.instructions.add(new InsnNode(Opcodes.RETURN));c.methods.add(bg);count++;}
  if(count==0){System.exit(2);return;}
  ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);Files.write(Paths.get(args[1]),w.toByteArray());
 }
}
