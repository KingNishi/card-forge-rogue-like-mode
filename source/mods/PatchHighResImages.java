import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import java.nio.file.*;

/** Change only full-card Scryfall variants, preserving art_crop downloads. */
public class PatchHighResImages {
    static final String MOD="forge/util/HighResImagesMod";
    public static void main(String[] args)throws Exception {
        ClassNode c=new ClassNode();
        new ClassReader(Files.readAllBytes(Paths.get(args[0]))).accept(c,0);
        int count=0;
        for(MethodNode m:c.methods) {
            if(c.name.equals("forge/gui/download/CdnUuidCache") && m.name.equals("cdnUrl")) {
                for(AbstractInsnNode n:m.instructions.toArray())
                    if(n instanceof MethodInsnNode && ((MethodInsnNode)n).owner.equals(MOD))System.exit(2);
                for(AbstractInsnNode n:m.instructions.toArray()) if(n.getOpcode()==Opcodes.ARETURN) {
                    m.instructions.insertBefore(n,new MethodInsnNode(Opcodes.INVOKESTATIC,MOD,"cdnUrl","(Ljava/lang/String;)Ljava/lang/String;",false));count++;
                }
            }
            boolean targeted=(c.name.equals("forge/util/ImageFetcher") && m.name.equals("addScryfallUrl"))
                || (c.name.equals("forge/util/ImageUtil") && (m.name.equals("getScryfallDownloadUrl") || m.name.equals("getScryfallTokenDownloadUrl")));
            if(targeted) for(AbstractInsnNode n:m.instructions.toArray())
                if(n instanceof LdcInsnNode && "normal".equals(((LdcInsnNode)n).cst)) {((LdcInsnNode)n).cst="png";count++;}
        }
        if(count==0)System.exit(2);
        int expected=c.name.equals("forge/util/ImageUtil")?2:1;
        if(count!=expected)throw new IllegalStateException(c.name+": expected "+expected+", found "+count);
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);
        Files.write(Paths.get(args[1]),w.toByteArray());
        System.out.println(c.name+": "+count+" high-resolution hook(s)");
    }
}
