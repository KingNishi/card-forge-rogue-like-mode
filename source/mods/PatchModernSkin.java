import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import java.nio.file.*;

/** Hook skin loading and preserve Textra line heights; fail closed on unfamiliar classes. */
public final class PatchModernSkin {
    public static void main(String[] args) throws Exception {
        ClassNode node = new ClassNode();
        new ClassReader(Files.readAllBytes(Path.of(args[0]))).accept(node, 0);
        if (!node.name.equals("forge/adventure/util/Controls")) throw new IllegalArgumentException(node.name);
        String skin = "Lcom/badlogic/gdx/scenes/scene2d/ui/Skin;", font = "Lcom/github/tommyettinger/textra/Font;";
        String[][] methods = {{"getSkin", "()" + skin}, {"getTextraFont", "()" + font},
            {"getTextraFont", "(Ljava/lang/String;)" + font}, {"getKeysFont", "()" + font},
            {"getRewardHeaderFont", "()" + font}};
        int changed = 0;
        for (String[] target : methods) {
            MethodNode method = node.methods.stream().filter(m -> m.name.equals(target[0]) && m.desc.equals(target[1]))
                .findFirst().orElseThrow();
            boolean isSkin = target[0].equals("getSkin");
            String hook = isSkin ? "apply" : "metrics";
            String hookDesc = isSkin ? "(" + skin + ")" + skin : "(" + font + "F)" + font;
            int returns = 0, installed = 0;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction.getOpcode() != Opcodes.ARETURN) continue;
                returns++;
                AbstractInsnNode previous = instruction.getPrevious();
                while (previous != null && previous.getOpcode() < 0) previous = previous.getPrevious();
                if (previous instanceof MethodInsnNode call && call.owner.equals("forge/adventure/util/ModernSkinMod")
                        && call.name.equals(hook) && call.desc.equals(hookDesc)) installed++;
            }
            if (returns == 0 || installed != 0 && installed != returns)
                throw new IllegalStateException("Missing/partial modern skin hook: " + target[0]);
            if (installed == returns) continue;
            for (AbstractInsnNode instruction : method.instructions.toArray())
                if (instruction.getOpcode() == Opcodes.ARETURN) {
                    InsnList added = new InsnList();
                    if (!isSkin) added.add(new LdcInsnNode(target[0].equals("getKeysFont") ? 0f :
                        target[0].equals("getRewardHeaderFont") ? -2.5f : 1f));
                    added.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                        "forge/adventure/util/ModernSkinMod", hook, hookDesc, false));
                    method.instructions.insertBefore(instruction, added); changed++;
                }
        }
        if (changed == 0) { System.exit(2); return; }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer); Files.write(Path.of(args[1]), writer.toByteArray());
    }
}
