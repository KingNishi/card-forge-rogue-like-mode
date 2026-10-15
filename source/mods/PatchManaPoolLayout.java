import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import java.nio.file.*;
/** VManaPool.layoutAndGetScrollBounds(w,h) becomes: return ManaPoolLayoutMod.layout(this.manaLabels,w,h); */
public class PatchManaPoolLayout {
 public static void main(String[] args)throws Exception{
  ClassNode c=new ClassNode();new ClassReader(Files.readAllBytes(Paths.get(args[0]))).accept(c,0);
  String H="forge/screens/match/views/ManaPoolLayoutMod",SB="Lforge/toolbox/FScrollPane$ScrollBounds;";int count=0;
  for(MethodNode m:c.methods){
   if(!m.name.equals("layoutAndGetScrollBounds")||!m.desc.equals("(FF)"+SB))continue;
   for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode call&&call.owner.equals(H)){System.exit(2);return;}
   m.instructions.clear();m.tryCatchBlocks.clear();m.localVariables=null;
   m.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));
   m.instructions.add(new FieldInsnNode(Opcodes.GETFIELD,"forge/screens/match/views/VManaPool","manaLabels","Ljava/util/List;"));
   m.instructions.add(new VarInsnNode(Opcodes.FLOAD,1));m.instructions.add(new VarInsnNode(Opcodes.FLOAD,2));
   m.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,H,"layout","(Ljava/util/List;FF)"+SB,false));
   m.instructions.add(new InsnNode(Opcodes.ARETURN));count++;
  }
  if(count==0){System.err.println("VManaPool.layoutAndGetScrollBounds not found");System.exit(1);return;}
  ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);Files.write(Paths.get(args[1]),w.toByteArray());
 }
}
