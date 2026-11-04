import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import java.nio.file.*;
/** VPlayers$PlayerInfoPanel.drawBackground(g) starts with: if(PlayersStyleMod.draw(this,g))return; (user 2026-10-16) */
public class PatchPlayersStyle {
 public static void main(String[] args)throws Exception{
  ClassNode c=new ClassNode();new ClassReader(Files.readAllBytes(Paths.get(args[0]))).accept(c,0);
  String H="forge/screens/match/views/PlayersStyleMod";int count=0;
  for(MethodNode m:c.methods){
   if(!m.name.equals("drawBackground")||!m.desc.equals("(Lforge/Graphics;)V"))continue;
   for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode call&&call.owner.equals(H)){System.exit(2);return;}
   LabelNode go=new LabelNode();InsnList a=new InsnList();
   a.add(new VarInsnNode(Opcodes.ALOAD,0));a.add(new VarInsnNode(Opcodes.ALOAD,1));
   a.add(new MethodInsnNode(Opcodes.INVOKESTATIC,H,"draw","(Ljava/lang/Object;Lforge/Graphics;)Z",false));
   a.add(new JumpInsnNode(Opcodes.IFEQ,go));a.add(new InsnNode(Opcodes.RETURN));
   a.add(go);a.add(new FrameNode(Opcodes.F_SAME,0,null,0,null));
   m.instructions.insert(a);count++;
  }
  if(count==0){System.err.println("PlayerInfoPanel.drawBackground not found");System.exit(1);return;}
  ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);Files.write(Paths.get(args[1]),w.toByteArray());
 }
}
