import java.nio.file.Files;
import java.nio.file.Path;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** args: in.class out.class. Apply to both documented targets; never edits the JAR. */
public final class PatchMatchMenu {
    private static final String HELPER = "forge/screens/match/MatchMenuMod";
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: PatchMatchMenu in.class out.class");
        ClassNode c = new ClassNode();
        new ClassReader(Files.readAllBytes(Path.of(args[0]))).accept(c, 0);
        String method, desc, hook;
        int returnOpcode;
        if (c.name.equals("forge/screens/match/MatchScreen")) {
            method = "doLayout"; desc = "(FFF)V"; hook = "layout"; returnOpcode = Opcodes.RETURN;
        } else if (c.name.equals("forge/menu/FDropDown")) {
            method = "updateSizeAndPosition"; desc = "()V";
            hook = "dropDownOwner"; returnOpcode = -1;
        } else {
            throw new IllegalArgumentException("Unsupported class: " + c.name);
        }
        MethodNode target = c.methods.stream().filter(m -> m.name.equals(method) && m.desc.equals(desc))
                .findFirst().orElseThrow(() -> new IllegalStateException("Target method missing: " + c.name + "." + method + desc));
        for (AbstractInsnNode n : target.instructions.toArray()) {
            if (n instanceof MethodInsnNode call && call.owner.equals(HELPER) && call.name.equals(hook)) {
                System.exit(2);
                return;
            }
        }
        int count = 0;
        for (AbstractInsnNode n : target.instructions.toArray()) {
            boolean ownerQuery = n instanceof MethodInsnNode call && call.name.equals("getDropDownOwner")
                    && call.desc.equals("()Lforge/toolbox/FDisplayObject;");
            if (hook.equals("layout") ? n.getOpcode() != returnOpcode : !ownerQuery) continue;
            InsnList add = new InsnList();
            add.add(new VarInsnNode(Opcodes.ALOAD, 0));
            String hookDesc = hook.equals("layout") ? "(Lforge/screens/match/MatchScreen;)V"
                    : "(Lforge/toolbox/FDisplayObject;Lforge/menu/FDropDown;)Lforge/toolbox/FDisplayObject;";
            add.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, hook, hookDesc, false));
            if (hook.equals("layout")) target.instructions.insertBefore(n, add);
            else target.instructions.insert(n, add);
            count++;
        }
        if (count == 0) throw new IllegalStateException("No return to patch");
        // Straight-line return hooks leave all original stack-map frames valid.
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        c.accept(writer);
        Files.write(Path.of(args[1]), writer.toByteArray());
        System.out.println(c.name + ": " + count + " hook(s)");
    }
}
