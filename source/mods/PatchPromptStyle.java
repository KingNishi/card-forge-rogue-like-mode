import java.nio.file.Files;
import java.nio.file.Path;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Patch extracted classes individually: in.class out.class. Exit 2: already patched. */
public final class PatchPromptStyle implements Opcodes {
    private static final String HOOK = "forge/screens/match/views/PromptStyleMod";
    private static final String PROMPT = "forge/screens/match/views/VPrompt";
    private static final String MESSAGE = PROMPT + "$MessageLabel";
    private static final String BUTTON = "forge/toolbox/FButton";

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: PatchPromptStyle in.class out.class");
            System.exit(1); return;
        }
        ClassNode c = new ClassNode();
        new ClassReader(Files.readAllBytes(Path.of(args[0]))).accept(c, 0);
        int count = 0, targets = 0;
        for (MethodNode m : c.methods) {
            String hook = null, descriptor = null;
            boolean bool = false;
            InsnList prefix = new InsnList();
            if (c.name.equals(PROMPT) && m.name.equals("doLayout") && m.desc.equals("(FF)V")) {
                targets++;
                if (hasHook(m, "prepareLayout")) continue;
                prefix.add(new VarInsnNode(ALOAD, 0));
                prefix.add(new MethodInsnNode(INVOKESTATIC, HOOK, "prepareLayout", "(L" + PROMPT + ";)V", false));
                m.instructions.insert(prefix); count++; continue;
            }
            if (c.name.equals(PROMPT) && m.name.equals("drawBackground") && m.desc.equals("(Lforge/Graphics;)V")) {
                hook = "drawBackground";
                descriptor = "(L" + PROMPT + ";Lforge/Graphics;)Z";
                prefix.add(new VarInsnNode(ALOAD, 0));
                prefix.add(new VarInsnNode(ALOAD, 1));
            } else if (c.name.equals(MESSAGE) && m.name.equals("draw") && m.desc.equals("(Lforge/Graphics;)V")) {
                hook = "drawMessage";
                descriptor = "(L" + PROMPT + ";Lforge/toolbox/FDisplayObject;Lforge/Graphics;)Z";
                FieldNode outer = c.fields.stream().filter(f -> f.name.equals("this$0") && f.desc.equals("L" + PROMPT + ";"))
                        .findFirst().orElseThrow(() -> new IllegalStateException("MessageLabel.this$0 missing"));
                prefix.add(new VarInsnNode(ALOAD, 0));
                prefix.add(new FieldInsnNode(GETFIELD, c.name, outer.name, outer.desc));
                prefix.add(new VarInsnNode(ALOAD, 0));
                prefix.add(new VarInsnNode(ALOAD, 1));
            } else if (c.name.equals(MESSAGE) && m.name.equals("tap") && m.desc.equals("(FFI)Z")) {
                // small translucent popup instead of the full-width opaque FMagnifyView
                hook = "tapMessage"; bool = true;
                descriptor = "(L" + PROMPT + ";Lforge/toolbox/FDisplayObject;)Z";
                prefix.add(new VarInsnNode(ALOAD, 0));
                prefix.add(new FieldInsnNode(GETFIELD, c.name, "this$0", "L" + PROMPT + ";"));
                prefix.add(new VarInsnNode(ALOAD, 0));
            } else if (c.name.equals(BUTTON) && m.name.equals("draw") && m.desc.equals("(Lforge/Graphics;)V")) {
                hook = "drawButton";
                descriptor = "(L" + BUTTON + ";Lforge/Graphics;Z)Z";
                if (c.fields.stream().noneMatch(f -> f.name.equals("pressed") && f.desc.equals("Z")))
                    throw new IllegalStateException("FButton.pressed missing");
                prefix.add(new VarInsnNode(ALOAD, 0));
                prefix.add(new VarInsnNode(ALOAD, 1));
                prefix.add(new VarInsnNode(ALOAD, 0));
                prefix.add(new FieldInsnNode(GETFIELD, BUTTON, "pressed", "Z"));
            }
            if (hook == null) continue;
            targets++;
            if (hasHook(m, hook)) continue;
            // Prefix changes no locals. Keep earlier code/frames and supply the new branch frame.
            LabelNode original = new LabelNode();
            prefix.add(new MethodInsnNode(INVOKESTATIC, HOOK, hook, descriptor, false));
            prefix.add(new JumpInsnNode(IFEQ, original));
            if (bool) { prefix.add(new InsnNode(ICONST_1)); prefix.add(new InsnNode(IRETURN)); }
            else prefix.add(new InsnNode(RETURN));
            prefix.add(original);
            prefix.add(new FrameNode(F_SAME, 0, null, 0, null));
            m.instructions.insert(prefix); count++;
        }
        int expected = c.name.equals(PROMPT) || c.name.equals(MESSAGE) ? 2 : c.name.equals(BUTTON) ? 1 : 0;
        if (expected == 0 || targets != expected) throw new IllegalArgumentException("Unexpected class/methods: " + c.name);
        if (count == 0) { System.exit(2); return; }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        c.accept(writer);
        Files.write(Path.of(args[1]), writer.toByteArray());
        System.out.println(c.name + ": " + count + " hook(s) added");
    }

    private static boolean hasHook(MethodNode m, String name) {
        for (AbstractInsnNode n : m.instructions.toArray())
            if (n instanceof MethodInsnNode call && call.owner.equals(HOOK) && call.name.equals(name)) return true;
        return false;
    }
}
