import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import java.nio.file.*;
/** TransitionScreen$BGAnimation.drawBackground: Forge logo (FSkinImage.LOGO / FSkin.getLogo()) and the progress bar
 *  are drawn by TransitionStyleMod (modern spinner and slim bar). */
public class PatchTransitionStyle {
 public static void main(String[] args)throws Exception{
  ClassNode c=new ClassNode();new ClassReader(Files.readAllBytes(Paths.get(args[0]))).accept(c,0);
  String H="forge/screens/TransitionStyleMod";int count=0;
  for(MethodNode m:c.methods){
   if(!m.name.equals("drawBackground")||!m.desc.equals("(Lforge/Graphics;)V"))continue;
   for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode call&&call.owner.equals(H)){System.exit(2);return;}
   for(AbstractInsnNode n:m.instructions.toArray()){
    if(!(n instanceof MethodInsnNode call)||call.getOpcode()!=Opcodes.INVOKEVIRTUAL||!call.owner.equals("forge/Graphics"))continue;
    if(call.name.equals("drawImage")&&call.desc.equals("(Lforge/assets/FImage;FFFF)V")&&after(n,"LOGO")){
     m.instructions.set(n,new MethodInsnNode(Opcodes.INVOKESTATIC,H,"logo","(Lforge/Graphics;Lforge/assets/FImage;FFFF)V",false));count++;}
    else if(call.name.equals("drawImage")&&call.desc.equals("(Lcom/badlogic/gdx/graphics/Texture;FFFF)V")&&after(n,"getLogo")){
     m.instructions.set(n,new MethodInsnNode(Opcodes.INVOKESTATIC,H,"logoTexture","(Lforge/Graphics;Lcom/badlogic/gdx/graphics/Texture;FFFF)V",false));count++;}
    else if(call.name.equals("draw")&&call.desc.equals("(Lforge/toolbox/FDisplayObject;)V")){
     m.instructions.set(n,new MethodInsnNode(Opcodes.INVOKESTATIC,H,"progress","(Lforge/Graphics;Lforge/toolbox/FDisplayObject;)V",false));count++;}
   }
  }
  if(count==0){System.err.println("nothing to patch in "+c.name);System.exit(1);return;}
  ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);Files.write(Paths.get(args[1]),w.toByteArray());
 }
 /** the image argument comes from FSkinImage.LOGO / FSkin.getLogo(): look back to the previous Graphics call */
 static boolean after(AbstractInsnNode n,String name){
  for(AbstractInsnNode q=n.getPrevious();q!=null;q=q.getPrevious()){
   if(q instanceof MethodInsnNode call&&call.owner.equals("forge/Graphics"))return false;
   if(q instanceof FieldInsnNode f&&f.name.equals(name))return true;
   if(q instanceof MethodInsnNode call&&call.name.equals(name))return true;
  }
  return false;
 }
}
