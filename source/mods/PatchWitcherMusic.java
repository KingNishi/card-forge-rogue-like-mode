import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import java.nio.file.*;
public class PatchWitcherMusic {
 public static void main(String[] args)throws Exception{
  ClassNode cn=new ClassNode();new ClassReader(Files.readAllBytes(Paths.get(args[0]))).accept(cn,0);int changes=0;
  for(MethodNode m:cn.methods)for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode c){
   if(c.owner.equals("forge/adventure/util/WitcherMusicMod"))System.exit(2);
   if(c.owner.equals("forge/sound/SoundSystem")&&c.name.equals("findMusicDirectory")){c.owner="forge/adventure/util/WitcherMusicMod";c.name="directory";changes++;}
  }
  if(changes!=1)throw new IllegalStateException("Expected one playlist lookup: "+changes);
  ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);cn.accept(w);Files.write(Paths.get(args[1]),w.toByteArray());
 }
}
