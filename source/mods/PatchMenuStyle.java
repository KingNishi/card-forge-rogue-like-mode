import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import java.nio.file.*;
/** FMenuItem.draw starts with: if(MenuStyleMod.drawItem(this,g))return;
 *  FPopupMenu.drawBackground / drawOverlay start with: if(MenuStyleMod.drawMenu(this,g,overlay))return;   (2026-10-18) */
public class PatchMenuStyle {
 public static void main(String[] args)throws Exception{
  ClassNode c=new ClassNode();new ClassReader(Files.readAllBytes(Paths.get(args[0]))).accept(c,0);
  String H="forge/menu/MenuStyleMod";int count=0;
  for(MethodNode m:c.methods){
   for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode call&&call.owner.equals(H)){System.exit(2);return;}
   if(!m.desc.equals("(Lforge/Graphics;)V"))continue;
   InsnList a=new InsnList();LabelNode go=new LabelNode();
   if(c.name.equals("forge/menu/FMenuItem")&&m.name.equals("draw")){
    a.add(new VarInsnNode(Opcodes.ALOAD,0));a.add(new VarInsnNode(Opcodes.ALOAD,1));
    a.add(new MethodInsnNode(Opcodes.INVOKESTATIC,H,"drawItem","(Lforge/menu/FMenuItem;Lforge/Graphics;)Z",false));
   }else if(c.name.equals("forge/menu/FPopupMenu")&&(m.name.equals("drawBackground")||m.name.equals("drawOverlay"))){
    a.add(new VarInsnNode(Opcodes.ALOAD,0));a.add(new VarInsnNode(Opcodes.ALOAD,1));
    a.add(new InsnNode(m.name.equals("drawOverlay")?Opcodes.ICONST_1:Opcodes.ICONST_0));
    a.add(new MethodInsnNode(Opcodes.INVOKESTATIC,H,"drawMenu","(Lforge/menu/FPopupMenu;Lforge/Graphics;Z)Z",false));
   }else continue;
   a.add(new JumpInsnNode(Opcodes.IFEQ,go));a.add(new InsnNode(Opcodes.RETURN));a.add(go);a.add(new FrameNode(Opcodes.F_SAME,0,null,0,null));
   m.instructions.insert(a);count++;
  }
  if(count==0){System.err.println("menu draw methods not found in "+c.name);System.exit(1);return;}
  ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);Files.write(Paths.get(args[1]),w.toByteArray());
 }
}
