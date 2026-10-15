import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import java.nio.file.*;
public class PatchDraftRating {
 public static void main(String[] args)throws Exception{
  ClassNode cn=new ClassNode();new ClassReader(Files.readAllBytes(Paths.get(args[0]))).accept(cn,0);int changes=0;
  for(MethodNode m:cn.methods)for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode c){
   if(c.owner.equals("forge/adventure/util/DraftRatingMod"))System.exit(2);
   if(c.owner.equals("forge/itemmanager/CardManager")&&c.name.equals("setup")&&c.desc.contains("Ljava/util/Map;")){
    c.setOpcode(Opcodes.INVOKESTATIC);c.owner="forge/adventure/util/DraftRatingMod";c.desc="(Lforge/itemmanager/CardManager;Lforge/itemmanager/ItemManagerConfig;Ljava/util/Map;)V";changes++;
   }else if(c.owner.equals("forge/itemmanager/CardManager")&&c.name.equals("setShowRanking")){
    c.setOpcode(Opcodes.INVOKESTATIC);c.owner="forge/adventure/util/DraftRatingMod";c.name="showRanking";c.desc="(Lforge/itemmanager/CardManager;Z)V";changes++;
   }
  }
  if(changes!=2)throw new IllegalStateException("Expected two deck-page hooks: "+changes);
  ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);cn.accept(w);Files.write(Paths.get(args[1]),w.toByteArray());
 }
}
