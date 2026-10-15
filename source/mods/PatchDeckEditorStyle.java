import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/**
 * Compile: javac --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED
 *   --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -d BUILD PatchDeckEditorStyle.java
 * Run with those same exports: PatchDeckEditorStyle in.class out.class (exit 2 = already patched).
 * --list prints the exact installer target entries. Compile DeckEditorStyleMod.java separately with --release 17.
 * Original instructions/frames and earlier mod hooks are preserved; new return guards add an explicit F_SAME frame.
 * No constructor, input handler, layout method, or card rendering call is replaced.
 */
public final class PatchDeckEditorStyle implements Opcodes {
    private static final String H = "forge/adventure/scene/DeckEditorStyleMod";
    private static final String A = "forge/adventure/scene/AdventureDeckEditor";
    private static final String D = "forge/toolbox/FDisplayObject";
    private static final String G = "forge/Graphics";
    private static final String C = "Lforge/assets/FSkinColor;";
    private static final String DRAW = "(Lforge/Graphics;)V";
    private static final Map<String, Integer> BACKGROUNDS = new LinkedHashMap<>();
    private static final Map<String, Integer> TARGETS = new LinkedHashMap<>();
    static {
        BACKGROUNDS.put(A, 0);
        BACKGROUNDS.put(A + "$AdventureDeckHeader", 1);
        for (String page : new String[]{"AdventureDeckSectionPage", "AdventureDraftPackPage", "CollectionAutoSellPage",
                "CollectionCatalogPage", "ContentPreviewPage", "StoreCatalogPage"}) BACKGROUNDS.put(A + "$" + page, 2);
        BACKGROUNDS.put("forge/screens/TabPageScreen$TabHeader", 3);
        BACKGROUNDS.forEach((name, kind) -> TARGETS.put(name, kind == 3 ? 2 : 1));
        TARGETS.put("forge/deck/FDeckEditor$DeckHeader", 1);
        TARGETS.put("forge/screens/TabPageScreen$TabPage$Tab", 1);
        TARGETS.put("forge/toolbox/FLabel", 1);
        TARGETS.put("forge/toolbox/FComboBox", 1);
        TARGETS.put("forge/toolbox/FTextField", 2); // draw + private drawText, caret code kept intact
        TARGETS.put("forge/itemmanager/filters/TextSearchFilter$SearchField", 1);
        TARGETS.put("forge/itemmanager/views/ImageView$Group", 1);
        TARGETS.put("forge/itemmanager/views/ImageView$ItemInfo", 1);
        TARGETS.put("forge/itemmanager/views/ItemView$Scroller", 1);
        TARGETS.put("forge/itemmanager/views/ImageView", 2);
    }
    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("--list")) {
            TARGETS.keySet().forEach(name -> System.out.println(name + ".class")); return;
        }
        if (args.length != 2) throw new IllegalArgumentException("PatchDeckEditorStyle in.class out.class | --list");
        ClassNode c = new ClassNode();
        new ClassReader(Files.readAllBytes(Path.of(args[0]))).accept(c, 0);
        Integer expected = TARGETS.get(c.name);
        if (expected == null) throw new IllegalArgumentException("Unsupported target: " + c.name);
        for (MethodNode m : c.methods) for (AbstractInsnNode n : m.instructions.toArray()) {
            if (n instanceof MethodInsnNode call && call.owner.equals(H)) { System.exit(2); return; }
        }
        int changed = 0;
        for (MethodNode m : c.methods) {
            if (m.name.equals("drawBackground") && m.desc.equals(DRAW) && BACKGROUNDS.containsKey(c.name)) {
                guard(m, "background", BACKGROUNDS.get(c.name)); changed++;
            } else if (m.name.equals("drawOverlay") && m.desc.equals(DRAW)
                    && (c.name.equals("forge/deck/FDeckEditor$DeckHeader") || c.name.equals("forge/screens/TabPageScreen$TabHeader"))) {
                guard(m, "overlay", c.name.endsWith("$DeckHeader") ? 1 : 3); changed++;
            } else if (m.name.equals("draw") && m.desc.equals(DRAW) && c.name.equals("forge/toolbox/FLabel")) {
                widgetGuard(m, "label", "forge/toolbox/FLabel"); changed++;
            } else if (m.name.equals("draw") && m.desc.equals(DRAW) && c.name.equals("forge/toolbox/FComboBox")) {
                widgetGuard(m, "combo", "forge/toolbox/FComboBox"); changed++;
            } else if (m.name.equals("draw") && m.desc.equals(DRAW) && c.name.equals("forge/screens/TabPageScreen$TabPage$Tab")) {
                FieldNode outer = c.fields.stream().filter(f -> f.name.equals("this$0") && f.desc.equals("Lforge/screens/TabPageScreen$TabPage;"))
                        .findFirst().orElseThrow(() -> new IllegalStateException("Missing Tab.this$0"));
                InsnList prefix = selfAndGraphics();
                prefix.add(new VarInsnNode(ALOAD, 0));
                prefix.add(new FieldInsnNode(GETFIELD, c.name, outer.name, outer.desc));
                earlyReturn(m, prefix, "tab", "(L" + D + ";L" + G + ";Lforge/screens/TabPageScreen$TabPage;)Z"); changed++;
            } else if (c.name.equals("forge/toolbox/FTextField") && m.name.equals("draw") && m.desc.equals(DRAW)) {
                int backgrounds = 0, borders = 0;
                for (AbstractInsnNode n : m.instructions.toArray()) if (n instanceof MethodInsnNode call) {
                    palette(m, call);
                    if (call.owner.equals(G) && call.name.equals("fillRect") && call.desc.equals("(" + C + "FFFF)V") && backgrounds == 0) {
                        m.instructions.insertBefore(call, new VarInsnNode(ALOAD, 0));
                        replace(m, call, "fieldBackground", "(L" + G + ";" + C + "FFFFLforge/toolbox/FTextField;)V"); backgrounds++;
                    } else if (call.owner.equals(G) && call.name.equals("drawRect") && call.desc.equals("(F" + C + "FFFF)V")) {
                        bridge(m, call, "fieldBorder"); borders++;
                    }
                }
                require(backgrounds == 1 && borders == 1, "FTextField background/border changed"); changed++;
            } else if (c.name.equals("forge/toolbox/FTextField") && m.name.equals("drawText") && m.desc.equals("(Lforge/Graphics;FF)V")) {
                int count = 0;
                for (AbstractInsnNode n : m.instructions.toArray()) if (n instanceof MethodInsnNode call && palette(m, call)) count++;
                require(count == 2, "FTextField text/placeholder colors changed"); changed++;
            } else if (c.name.equals("forge/itemmanager/filters/TextSearchFilter$SearchField") && m.name.equals("draw") && m.desc.equals(DRAW)) {
                int count = 0;
                for (AbstractInsnNode n : m.instructions.toArray()) if (n instanceof MethodInsnNode call && palette(m, call)) count++;
                require(count == 1, "Search ratio color changed"); changed++;
            } else if (c.name.equals("forge/itemmanager/views/ImageView$Group") && m.name.equals("draw") && m.desc.equals(DRAW)) {
                int texts = 0, lines = 0;
                for (AbstractInsnNode n : m.instructions.toArray()) if (n instanceof MethodInsnNode call) {
                    if (call.owner.equals(G) && call.name.equals("drawText")
                            && call.desc.equals("(Ljava/lang/String;Lforge/assets/FSkinFont;" + C + "FFFFZIZ)V")) {
                        bridge(m, call, "groupText"); texts++;
                    } else if (call.owner.equals(G) && call.name.equals("drawLine") && call.desc.equals("(F" + C + "FFFF)V")) {
                        bridge(m, call, "groupLine"); lines++;
                    } else if (call.name.equals("getGroupHeaderLineColor") && call.desc.equals("()" + C)) {
                        m.instructions.insert(call, hook("accent", "(" + C + ")" + C));
                    }
                }
                require(texts == 1 && lines == 1, "ImageView group header changed"); changed++;
            } else if (c.name.equals("forge/itemmanager/views/ImageView$ItemInfo") && m.name.equals("draw") && m.desc.equals(DRAW)) {
                int count = 0;
                for (AbstractInsnNode n : m.instructions.toArray()) if (n instanceof FieldInsnNode f && f.getOpcode() == GETSTATIC
                        && f.owner.equals("com/badlogic/gdx/graphics/Color") && f.name.equals("GREEN")) {
                    m.instructions.insert(f, hook("cardSelection", "(Lcom/badlogic/gdx/graphics/Color;)Lcom/badlogic/gdx/graphics/Color;")); count++;
                }
                require(count >= 3, "ImageView selected-card rim changed"); changed++;
            } else if (c.name.equals("forge/itemmanager/views/ItemView$Scroller") && m.name.equals("drawOverlay") && m.desc.equals(DRAW)) {
                int count = 0;
                for (AbstractInsnNode n : m.instructions.toArray()) if (n instanceof MethodInsnNode call && call.owner.equals(G)
                        && call.name.equals("drawRect") && call.desc.equals("(F" + C + "FFFF)V")) {
                    bridge(m, call, "gridBorder"); count++;
                }
                require(count == 1, "Scroller border changed"); changed++;
            } else if (c.name.equals("forge/itemmanager/views/ImageView") && m.desc.equals("()" + C)
                    && (m.name.equals("getGroupHeaderForeColor") || m.name.equals("getGroupHeaderLineColor"))) {
                for (AbstractInsnNode n : m.instructions.toArray()) if (n.getOpcode() == ARETURN)
                    m.instructions.insertBefore(n, hook("accent", "(" + C + ")" + C));
                changed++;
            }
        }
        require(changed == expected, c.name + ": expected " + expected + " methods, found " + changed);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        c.accept(writer);
        Files.write(Path.of(args[1]), writer.toByteArray());
        System.out.println(c.name + ": " + changed + " method(s) patched");
    }
    private static void require(boolean ok, String message) { if (!ok) throw new IllegalStateException(message); }
    private static MethodInsnNode hook(String name, String descriptor) { return new MethodInsnNode(INVOKESTATIC, H, name, descriptor, false); }
    private static void replace(MethodNode m, MethodInsnNode old, String name, String descriptor) {
        m.instructions.set(old, hook(name, descriptor));
    }
    private static void bridge(MethodNode m, MethodInsnNode call, String name) {
        replace(m, call, name, "(L" + G + ";" + call.desc.substring(1));
    }
    private static boolean palette(MethodNode m, MethodInsnNode call) {
        if (call.getOpcode() != INVOKESTATIC || !call.desc.equals("()" + C)) return false;
        String name = switch (call.name) {
            case "getForeColor" -> "foreground"; case "getGhostTextColor" -> "muted"; case "getSelColor" -> "selection"; default -> null;
        };
        if (name == null) return false;
        m.instructions.insert(call, hook(name, "(" + C + ")" + C)); return true;
    }
    private static InsnList selfAndGraphics() {
        InsnList list = new InsnList(); list.add(new VarInsnNode(ALOAD, 0)); list.add(new VarInsnNode(ALOAD, 1)); return list;
    }
    private static void guard(MethodNode m, String hook, int kind) {
        InsnList prefix = selfAndGraphics(); prefix.add(new InsnNode(ICONST_0 + kind));
        earlyReturn(m, prefix, hook, "(L" + D + ";L" + G + ";I)Z");
    }
    private static void widgetGuard(MethodNode m, String hook, String widget) {
        earlyReturn(m, selfAndGraphics(), hook, "(L" + widget + ";L" + G + ";)Z");
    }
    private static void earlyReturn(MethodNode m, InsnList prefix, String name, String descriptor) {
        LabelNode original = new LabelNode();
        prefix.add(hook(name, descriptor)); prefix.add(new JumpInsnNode(IFEQ, original)); prefix.add(new InsnNode(RETURN));
        prefix.add(original); prefix.add(new FrameNode(F_SAME, 0, null, 0, null));
        m.instructions.insert(prefix);
    }
}
