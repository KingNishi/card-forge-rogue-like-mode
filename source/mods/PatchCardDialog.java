import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import java.nio.file.*;
/** FDialog.drawBackground / drawOverlay: prefix "if(CardDialogMod.draw(this,g,overlay,btnMiddle,title,prompt,totalHeight,revealPercent))return;"
 *  -> card yes/no dialogs show only the card, match choice lists are semi-transparent. Other dialogs run the original code.
 *  FChoiceList.getItemFillColor: every returned colour goes through CardDialogMod.item(color) (translucent rows). */
public class PatchCardDialog {
 public static void main(String[] args)throws Exception{
  ClassNode c=new ClassNode();new ClassReader(Files.readAllBytes(Paths.get(args[0]))).accept(c,0);
  String H="forge/toolbox/CardDialogMod",D="forge/toolbox/FDialog";int count=0;
  if(c.name.equals("forge/toolbox/DualListBox")||c.name.equals("forge/toolbox/FOptionPane")){   // Enter on a single candidate takes it (2026-10-18)
   for(MethodNode m:c.methods){
    if(c.name.equals("forge/toolbox/DualListBox")&&m.name.startsWith("lambda$new$")&&m.desc.equals("(Ljava/util/function/Consumer;Lforge/toolbox/FEvent;)V")){
     for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode call&&call.owner.equals(H)){System.exit(2);return;}
     LabelNode go=new LabelNode();InsnList a=new InsnList();a.add(new VarInsnNode(Opcodes.ALOAD,0));
     a.add(new MethodInsnNode(Opcodes.INVOKESTATIC,H,"okBlocked","(Lforge/toolbox/DualListBox;)Z",false));
     a.add(new JumpInsnNode(Opcodes.IFEQ,go));a.add(new InsnNode(Opcodes.RETURN));a.add(go);a.add(new FrameNode(Opcodes.F_SAME,0,null,0,null));
     m.instructions.insert(a);count++;continue;
    }
    if(!m.name.equals("keyDown")||!m.desc.equals("(I)Z"))continue;
    for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode call&&call.owner.equals(H)){System.exit(2);return;}
    LabelNode go=new LabelNode();InsnList a=new InsnList();a.add(new VarInsnNode(Opcodes.ALOAD,0));a.add(new VarInsnNode(Opcodes.ILOAD,1));
    a.add(new MethodInsnNode(Opcodes.INVOKESTATIC,H,"enterKey","(Lforge/toolbox/FDialog;I)Z",false));
    a.add(new JumpInsnNode(Opcodes.IFEQ,go));a.add(new InsnNode(Opcodes.ICONST_1));a.add(new InsnNode(Opcodes.IRETURN));
    a.add(go);a.add(new FrameNode(Opcodes.F_SAME,0,null,0,null));
    m.instructions.insert(a);count++;
   }
   if(count==0){System.exit(2);return;}
   ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);Files.write(Paths.get(args[1]),w.toByteArray());return;
  }
  for(MethodNode m:c.methods){
   if(c.name.equals("forge/toolbox/FChoiceList")){
    if(!m.name.equals("getItemFillColor"))continue;
    for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode call&&call.owner.equals(H)){System.exit(2);return;}
    for(AbstractInsnNode n:m.instructions.toArray())if(n.getOpcode()==Opcodes.ARETURN){
     m.instructions.insertBefore(n,new MethodInsnNode(Opcodes.INVOKESTATIC,H,"item","(Lforge/assets/FSkinColor;)Lforge/assets/FSkinColor;",false));count++;}
    continue;
   }
   if(!m.desc.equals("(Lforge/Graphics;)V")||!(m.name.equals("drawBackground")||m.name.equals("drawOverlay")))continue;
   for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode call&&call.owner.equals(H)&&call.name.equals("draw")){System.exit(2);return;}
   LabelNode orig=new LabelNode();InsnList a=new InsnList();
   a.add(new VarInsnNode(Opcodes.ALOAD,0));a.add(new VarInsnNode(Opcodes.ALOAD,1));
   a.add(new InsnNode(m.name.equals("drawOverlay")?Opcodes.ICONST_1:Opcodes.ICONST_0));
   a.add(new VarInsnNode(Opcodes.ALOAD,0));a.add(new FieldInsnNode(Opcodes.GETFIELD,D,"btnMiddle","Lforge/toolbox/FButton;"));
   a.add(new VarInsnNode(Opcodes.ALOAD,0));a.add(new FieldInsnNode(Opcodes.GETFIELD,D,"title","Ljava/lang/String;"));
   a.add(new VarInsnNode(Opcodes.ALOAD,0));a.add(new FieldInsnNode(Opcodes.GETFIELD,D,"prompt","Lforge/screens/match/views/VPrompt;"));
   a.add(new VarInsnNode(Opcodes.ALOAD,0));a.add(new FieldInsnNode(Opcodes.GETFIELD,D,"totalHeight","F"));
   a.add(new VarInsnNode(Opcodes.ALOAD,0));a.add(new FieldInsnNode(Opcodes.GETFIELD,D,"revealPercent","F"));
   a.add(new MethodInsnNode(Opcodes.INVOKESTATIC,H,"draw","(Lforge/toolbox/FDialog;Lforge/Graphics;ZLforge/toolbox/FButton;Ljava/lang/String;Lforge/toolbox/FDisplayObject;FF)Z",false));
   a.add(new JumpInsnNode(Opcodes.IFEQ,orig));
   a.add(new InsnNode(Opcodes.RETURN));a.add(orig);a.add(new FrameNode(Opcodes.F_SAME,0,null,0,null));
   m.instructions.insert(a);count++;
  }
  if(count==0){System.exit(2);return;}
  ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);Files.write(Paths.get(args[1]),w.toByteArray());
 }
}
