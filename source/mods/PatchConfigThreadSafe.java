import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import java.nio.file.*;
/** Forge bug (2.0.16-SNAPSHOT): forge.adventure.util.Config.langFilePath shares one static StringBuilder and getFile one
 *  HashMap, but the new-world generation calls them from several threads at once. Now and then two threads write into
 *  the builder together, the path becomes "./res/.../|./res/...ring.png-en-US.png", the structure thread dies with
 *  InvalidPathException and World.generateNew waits forever: a new player's game freezes on "Start new game"
 *  (found 2026-10-16). Fix: both methods synchronized on the Config instance (a singleton). */
public class PatchConfigThreadSafe {
 public static void main(String[] args)throws Exception{
  ClassNode c=new ClassNode();new ClassReader(Files.readAllBytes(Paths.get(args[0]))).accept(c,0);
  int changed=0,found=0;
  for(MethodNode m:c.methods){
   boolean target=(m.name.equals("langFilePath")&&m.desc.equals("(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"))
                ||(m.name.equals("getFile")&&m.desc.equals("(Ljava/lang/String;)Lcom/badlogic/gdx/files/FileHandle;"));
   if(!target||(m.access&Opcodes.ACC_STATIC)!=0)continue;
   found++;
   if((m.access&Opcodes.ACC_SYNCHRONIZED)==0){m.access|=Opcodes.ACC_SYNCHRONIZED;changed++;}
  }
  if(found!=2){System.err.println("Config methods not found ("+found+"/2)");System.exit(1);}
  if(changed==0){System.exit(2);return;}
  ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);Files.write(Paths.get(args[1]),w.toByteArray());
 }
}
