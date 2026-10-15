import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import java.nio.file.*;

/** Adventure button captions use the fantasy font; labels still use the body font. */
public final class PatchModernSkinButtons {
    public static void main(String[] args) throws Exception {
        ClassNode node = new ClassNode();
        new ClassReader(Files.readAllBytes(Path.of(args[0]))).accept(node, 0);
        if (!node.name.equals("forge/adventure/util/Controls$TextButtonFix") &&
            !node.name.equals("forge/adventure/util/Controls$TypingButtonFix")) throw new IllegalArgumentException(node.name);
        int changed = 0, installed = 0;
        for (MethodNode method : node.methods) for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode call) || call.getOpcode() != Opcodes.INVOKESTATIC ||
                !call.desc.equals("()Lcom/github/tommyettinger/textra/Font;")) continue;
            if (call.owner.equals("forge/adventure/util/ModernSkinMod") && call.name.equals("getButtonFont")) installed++;
            if (call.owner.equals("forge/adventure/util/Controls") && call.name.equals("getTextraFont")) {
                call.owner = "forge/adventure/util/ModernSkinMod"; call.name = "getButtonFont"; changed++;
            }
        }
        if (changed == 0 && installed > 0) { System.exit(2); return; }
        if (changed == 0 || installed > 0) throw new IllegalStateException("Missing/partial Adventure button font hooks");
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer); Files.write(Path.of(args[1]), writer.toByteArray());
    }
}
