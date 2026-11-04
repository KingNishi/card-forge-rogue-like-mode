import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import java.nio.file.*;
/** VLog.getRowColor/getAltRowColor/getForeColor start with: FSkinColor c=LogStyleMod.<same>();if(c!=null)return c;
 *  VLog$LogEntryDisplay.draw starts with: if(LogStyleMod.drawEntry(this,g,text))return;   (turn bands, 2026-10-18)
 *  VLog$LogEntryDisplay.getMinHeight returns LogStyleMod.height(h,text). */
public class PatchLogStyle {
 public static void main(String[] args)throws Exception{
  ClassNode c=new ClassNode();new ClassReader(Files.readAllBytes(Paths.get(args[0]))).accept(c,0);
  String H="forge/screens/match/views/LogStyleMod",D="()Lforge/assets/FSkinColor;";int count=0;
  if(c.name.equals("forge/screens/match/views/VLog$LogEntryDisplay")){
   for(MethodNode m:c.methods){
    for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode call&&call.owner.equals(H)){System.exit(2);return;}
    if(m.name.equals("draw")&&m.desc.equals("(Lforge/Graphics;)V")){
     LabelNode go=new LabelNode();InsnList a=new InsnList();
     a.add(new VarInsnNode(Opcodes.ALOAD,0));a.add(new VarInsnNode(Opcodes.ALOAD,1));
     a.add(new VarInsnNode(Opcodes.ALOAD,0));a.add(new FieldInsnNode(Opcodes.GETFIELD,c.name,"text","Ljava/lang/String;"));
     a.add(new MethodInsnNode(Opcodes.INVOKESTATIC,H,"drawEntry","(Lforge/toolbox/FDisplayObject;Lforge/Graphics;Ljava/lang/String;)Z",false));
     a.add(new JumpInsnNode(Opcodes.IFEQ,go));a.add(new InsnNode(Opcodes.RETURN));
     a.add(go);a.add(new FrameNode(Opcodes.F_SAME,0,null,0,null));
     m.instructions.insert(a);count++;
    }else if(m.name.equals("getMinHeight")&&m.desc.equals("(F)F")){
     for(AbstractInsnNode n:m.instructions.toArray())if(n.getOpcode()==Opcodes.FRETURN){
      InsnList a=new InsnList();
      a.add(new VarInsnNode(Opcodes.ALOAD,0));a.add(new FieldInsnNode(Opcodes.GETFIELD,c.name,"text","Ljava/lang/String;"));
      a.add(new MethodInsnNode(Opcodes.INVOKESTATIC,H,"height","(FLjava/lang/String;)F",false));
      m.instructions.insertBefore(n,a);count++;}
    }
   }
   if(count<2){System.err.println("VLog$LogEntryDisplay: draw/getMinHeight not found ("+count+")");System.exit(1);return;}
   ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);Files.write(Paths.get(args[1]),w.toByteArray());
   return;
  }
  for(MethodNode m:c.methods){
   if(!D.equals(m.desc)||!(m.name.equals("getRowColor")||m.name.equals("getAltRowColor")||m.name.equals("getForeColor")))continue;
   for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode call&&call.owner.equals(H)){System.exit(2);return;}
   LabelNode go=new LabelNode();InsnList a=new InsnList();
   a.add(new MethodInsnNode(Opcodes.INVOKESTATIC,H,m.name,D,false));
   a.add(new InsnNode(Opcodes.DUP));a.add(new JumpInsnNode(Opcodes.IFNULL,go));a.add(new InsnNode(Opcodes.ARETURN));
   a.add(go);a.add(new FrameNode(Opcodes.F_SAME1,0,null,1,new Object[]{"forge/assets/FSkinColor"}));a.add(new InsnNode(Opcodes.POP));
   m.instructions.insert(a);count++;
  }
  if(count!=3){System.err.println("VLog colour getters: found "+count+" of 3");System.exit(1);return;}
  ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);Files.write(Paths.get(args[1]),w.toByteArray());
 }
}
