package forge.screens.match.views;

import com.badlogic.gdx.graphics.Color;
import forge.Forge;
import forge.Graphics;
import forge.assets.FSkinFont;
import forge.assets.TextRenderer;
import forge.game.GameView;
import forge.screens.match.MatchController;
import forge.toolbox.FButton;
import forge.toolbox.FDisplayObject;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Adventure landscape prompt skin. Original controls and message interactions stay intact. */
public final class PromptStyleMod {
    private PromptStyleMod() {}

    private static final Color INK = rgba(0x0d121b, .92f);
    private static final Color CLEAR = rgba(0x0d121b, 0f);
    private static final Color SHADE = rgba(0x0d121b, .35f);
    private static final Color GOLD = rgba(0xe0b24f, 1f);
    private static final Color HAIRLINE = rgba(0xe0b24f, .40f);
    private static final Color TEXT = rgba(0xf5f3ed, 1f);
    private static final Color MUTED = rgba(0xb8c2d1, 1f);
    private static final Color CHIP = rgba(0x1c2635, .82f);
    private static final Color PANEL = rgba(0x0d121b, .84f);
    private static final Color EDGE = rgba(0x8a9bb5, .24f);
    private static final Color SECONDARY = rgba(0x202b3c, .96f);
    private static final Color HOVER = rgba(0x304157, 1f);
    private static final Color GOLD_HOVER = rgba(0xf2c96b, 1f);
    private static final Color GOLD_DOWN = rgba(0xbd913a, 1f);
    private static final Color DISABLED = rgba(0x28303d, 1f);
    private static final Color DISABLED_TEXT = rgba(0x7f8b9d, 1f);
    private static final Pattern TURN = Pattern.compile("(\\d+)\\s*\\((.*)\\)");
    // Weak values as well as weak keys: neither the label nor its outer VPrompt is retained.
    private static final Map<VPrompt, State> STATES = new WeakHashMap<>();
    private static final Map<FButton, WeakReference<VPrompt>> BUTTONS = new WeakHashMap<>();
    private static final Field MESSAGE_LABEL = messageField();

    private static Field messageField() {
        try {
            Field f = VPrompt.class.getDeclaredField("lblMessage");
            f.setAccessible(true);
            return f;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("VPrompt.lblMessage is required", ex);
        }
    }

    private static FDisplayObject label(VPrompt p) {
        try { return (FDisplayObject) MESSAGE_LABEL.get(p); }
        catch (IllegalAccessException ex) { throw new IllegalStateException(ex); }
    }

    private static boolean active(VPrompt p) {
        return Forge.isLandscapeMode() && p.getWidth() >= 900f
                && Forge.getCurrentScene() instanceof forge.adventure.scene.DuelScene;
    }

    private static float scale(VPrompt p) {
        return Math.max(.75f, Math.min(2.4f, p.getWidth() / 1600f));
    }

    /** Called by ArenaBattleHudMod.prompt in place of its old layout body. */
    public static void layout(VPrompt p, float w, float h) {
        if (!active(p) || w < 900f || h <= 0f) return;
        State state = STATES.computeIfAbsent(p, k -> new State(p));
        state.styled = true;
        BUTTONS.put(p.getBtnOk(), new WeakReference<>(p));
        BUTTONS.put(p.getBtnCancel(), new WeakReference<>(p));
        place(p, w, h);
    }

    /** Patcher calls this before native doLayout, allowing a clean return to portrait. */
    public static void prepareLayout(VPrompt p) {
        State s = STATES.get(p);
        if (s == null || !s.styled) return;
        restore(label(p), s.labelBounds);
        restore(p.getBtnOk(), s.okBounds);
        restore(p.getBtnCancel(), s.cancelBounds);
        p.getBtnOk().setCorner(s.okCorner);
        p.getBtnCancel().setCorner(s.cancelCorner);
        s.styled = false;
    }

    private static void place(VPrompt p, float w, float h) {
        float s = scale(p), gap = 10f * s, margin = 16f * s;
        float bh = Math.max(1f, h - 12f * s), y = (h - bh) / 2f;
        FSkinFont f = font(16f * s, bh - 6f * s);
        float max = w * .19f;
        float ok = Math.min(max, Math.max(132f * s, width(f, p.getBtnOk().getText()) + 38f * s));
        float cancel = Math.min(max, Math.max(112f * s, width(f, p.getBtnCancel().getText()) + 38f * s));
        float primaryX = w - margin - ok;
        float secondaryX = primaryX - gap - cancel;
        p.getBtnOk().setBounds(primaryX, y, ok, bh);
        p.getBtnCancel().setBounds(secondaryX, y, cancel, bh);
        label(p).setBounds(margin, 2f * s, Math.max(1f, secondaryX - gap - margin),
                Math.max(1f, h - 4f * s));
    }

    /** true means handled; false leaves every earlier background patch in place. */
    public static boolean drawBackground(VPrompt p, Graphics g) {
        State state = STATES.get(p);
        if (!active(p) || state == null || !state.styled) return false;
        // Labels change without doLayout (Undo counts, mulligans, End Turn).
        place(p, p.getWidth(), p.getHeight());
        // transparent bar over the board (user request): only a faint shade so the text stays readable
        g.fillGradientRect(CLEAR, SHADE, true, 0f, 0f, p.getWidth(), p.getHeight());
        return true;
    }

    /** MessageLabel keeps its tap/magnify, fling and card-zoom behaviour. */
    public static boolean drawMessage(VPrompt p, FDisplayObject label, Graphics g) {
        State state = STATES.get(p);
        if (!active(p) || state == null || !state.styled) return false;
        String message = p.getMessage();
        if (message == null || message.isBlank()) return true;
        float w = label.getWidth(), h = label.getHeight(), s = scale(p);
        if (!message.equals(state.message)) {
            state.message = message;
            state.status = parseStatus(message);
        }
        if (state.status != null) {
            drawChips(p, g, state.status, w, h, s);
        } else {
            // TextRenderer preserves mana symbols and Forge reminder/markup rendering.
            // Multi-line prompts ("X is going first / Do you want to keep your hand?") become one readable line
            // on a dark pill instead of three tiny lines squeezed into the slim transparent bar.
            String one = message.replaceAll("\\s*\\R+\\s*", "   ·   ").trim();
            float usable = Math.max(1f, w - 16f * s);
            FSkinFont f = font(20f * s, h - 6f * s);
            float tw = f.getBounds(one).width;
            for (int size = Math.round(20f * s) - 1; tw > usable && size >= Math.max(10, Math.round(13f * s)); size--) {
                f = font(size, h - 6f * s);
                tw = f.getBounds(one).width;
            }
            if (tw <= usable) {
                float ph = Math.min(h - 2f * s, f.getLineHeight() + 12f * s), pw = Math.min(w, tw + 36f * s);
                pill(g, CHIP, EDGE, (w - pw) / 2f, (h - ph) / 2f, pw, ph, Math.max(1f, s * .7f));
                state.renderer.drawText(g, one, f, TEXT, 8f * s, 0f, usable, h, 0f, h, false, 1, true);
            } else {
                for (int size = Math.round(13f * s) - 1;
                        size >= Math.max(8, Math.round(10f * s))
                        && state.renderer.getWrappedBounds(message, f, usable).height > h; size--)
                    f = font(size, h - 4f * s);
                // long prompts (trigger texts) wrap; a dark panel behind them keeps them readable over bright boards
                float th = Math.min(h, state.renderer.getWrappedBounds(message, f, usable).height + 10f * s);
                g.fillRoundRect(PANEL, 0f, (h - th) / 2f, w, th, Math.min(14f * s, th / 2f));
                state.renderer.drawText(g, message, f, TEXT, 8f * s, 0f, usable, h, 0f, h, true, 1, true);
            }
        }
        return true;
    }

    private static void drawChips(VPrompt p, Graphics g, String[] status, float w, float h, float s) {
        String turn = status[1];
        Matcher m = TURN.matcher(turn);
        String owner = m.matches() ? m.group(2) : turn;
        String number = m.matches() ? m.group(1) : "";
        MatchController controller = MatchController.instance;
        GameView game = controller == null ? null : controller.getGameView();
        String title = owner;
        if (game != null && game.getPlayerTurn() != null
                && game.getPlayerTurn().getName().equals(owner)) {
            title = game.getPlayerTurn().isAI() ? "Enemy turn" : "Your turn";
        }
        String[] chips = {title + " · " + phase(status[2]) + (number.isEmpty() ? "" : " · T" + number),
                "Priority: " + status[0], "Stack: " + (status[3].equalsIgnoreCase("empty") ? "empty" : status[3])};
        float ch = Math.max(1f, Math.min(30f * s, h - 4f * s)), gap = 8f * s;
        FSkinFont f = font(14f * s, ch - 6f * s);
        float total = chipTotal(f, chips, s, gap);
        for (int size = Math.round(14f * s) - 1; total > w && size >= Math.max(8, Math.round(10f * s)); size--) {
            f = font(size, ch - 6f * s);
            total = chipTotal(f, chips, s, gap);
        }
        // Unusually long player names/stacks stay bounded; magnify still shows the full message.
        float ratio = Math.min(1f, w / Math.max(1f, total));
        float x = Math.max(0f, (w - total) / 2f), y = (h - ch) / 2f;
        for (int i = 0; i < chips.length; i++) {
            float cw = (width(f, chips[i]) + (i == 0 ? 36f : 24f) * s) * ratio;
            pill(g, CHIP, i == 0 ? HAIRLINE : EDGE, x, y, cw, ch, Math.max(1f, s * .7f));
            float pad = 12f * s;
            if (i == 0) {
                g.fillCircle(GOLD, x + 12f * s, y + ch / 2f, 2.5f * s);
                pad = 24f * s;
            }
            g.drawText(chips[i], f, i == 0 ? TEXT : MUTED, x + pad, y,
                    Math.max(1f, cw - pad - 10f * s), ch, false, 1, true);
            x += cw + gap * ratio;
        }
    }

    private static float chipTotal(FSkinFont f, String[] chips, float s, float gap) {
        float total = 2f * gap;
        for (int i = 0; i < chips.length; i++) total += width(f, chips[i]) + (i == 0 ? 36f : 24f) * s;
        return total;
    }

    /** Guarded FButton.draw hook; pressed is read by bytecode inside FButton itself. */
    public static boolean drawButton(FButton b, Graphics g, boolean pressed) {
        WeakReference<VPrompt> ref = BUTTONS.get(b);
        VPrompt p = ref == null ? null : ref.get();
        State state = p == null ? null : STATES.get(p);
        if (p == null || !active(p) || state == null || !state.styled) return false;
        boolean enabled = b.isEnabled(), primary = b == p.getBtnOk();
        boolean down = pressed || b.isToggled(), hover = b.isHovered();
        Color fill = !enabled ? DISABLED : primary ? (down ? GOLD_DOWN : hover ? GOLD_HOVER : GOLD)
                : down ? INK : hover ? HOVER : SECONDARY;
        Color text = !enabled ? DISABLED_TEXT : primary ? INK : TEXT;
        float w = b.getWidth(), h = b.getHeight(), s = scale(p);
        float oldAlpha = g.getfloatAlphaComposite();
        // FContainer fades disabled children to .25; use deliberate disabled palette instead.
        if (!enabled) g.setAlphaComposite(1f);
        try {
            float inset = Math.max(1f, s * .6f), y = inset + (enabled && down ? s : 0f);
            float bh = Math.max(1f, h - 2f * inset - (enabled && down ? s : 0f));
            pill(g, fill, !primary || !enabled ? (enabled && hover ? HAIRLINE : EDGE) : null,
                    inset, y, w - 2f * inset, bh, Math.max(1f, s * .7f));
            g.drawText(b.getText(), font(16f * s, bh - 6f * s), text,
                    14f * s, y, Math.max(1f, w - 28f * s), bh, false, 1, true);
        } finally { if (!enabled) g.setAlphaComposite(oldAlpha); }
        return true;
    }

    // Strict recognition prevents instructions mentioning a phase/stack from becoming status chips.
    private static String[] parseStatus(String message) {
        String[] values = new String[4];
        String[] keys = {"priority:", "turn:", "phase:", "stack:"};
        int count = 0;
        for (String part : message.split("\\s*(?:\\R|\\|)\\s*")) {
            String value = part.trim();
            if (value.isEmpty()) continue;
            String lower = value.toLowerCase(Locale.ROOT);
            int index = -1;
            for (int i = 0; i < keys.length; i++) if (lower.startsWith(keys[i])) { index = i; break; }
            if (index < 0 || values[index] != null) return null;
            values[index] = value.substring(keys[index].length()).trim();
            if (values[index].isEmpty()) return null;
            count++;
        }
        return count == 4 ? values : null;
    }

    private static String phase(String phase) {
        String lower = phase.toLowerCase(Locale.ROOT);
        if (lower.contains("precombat") || lower.equals("main1") || lower.equals("main 1")) return "Main 1";
        if (lower.contains("postcombat") || lower.equals("main2") || lower.equals("main 2")) return "Main 2";
        return phase;
    }

    private static FSkinFont font(float size, float height) {
        FSkinFont result = FSkinFont._get(Math.max(8, Math.round(size)));
        for (int n = Math.round(size) - 1; result.getLineHeight() > height && n >= 8; n--)
            result = FSkinFont._get(n);
        return result;
    }

    private static float width(FSkinFont f, String text) {
        return text == null || text.isEmpty() ? 0f : f.getBounds(text).width;
    }
    // Forge.drawRoundRect outlines its component rectangles too; nested fills give a clean rim.
    private static void pill(Graphics g, Color fill, Color rim, float x, float y, float w, float h, float line) {
        if (rim != null && w > 2f * line && h > 2f * line) {
            g.fillRoundRect(rim, x, y, w, h, Math.min(w, h) / 2f);
            x += line; y += line; w -= 2f * line; h -= 2f * line;
        }
        g.fillRoundRect(fill, x, y, w, h, Math.min(w, h) / 2f);
    }
    private static Color rgba(int rgb, float alpha) {
        return new Color(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, alpha);
    }
    private static float[] bounds(FDisplayObject o) { return new float[]{o.getLeft(), o.getTop(), o.getWidth(), o.getHeight()}; }
    private static void restore(FDisplayObject o, float[] b) { o.setBounds(b[0], b[1], b[2], b[3]); }

    private static final class State {
        final float[] labelBounds, okBounds, cancelBounds;
        final FButton.Corner okCorner, cancelCorner;
        final TextRenderer renderer = new TextRenderer();
        boolean styled;
        String message;
        String[] status;
        State(VPrompt p) {
            labelBounds = bounds(label(p)); okBounds = bounds(p.getBtnOk()); cancelBounds = bounds(p.getBtnCancel());
            okCorner = p.getBtnOk().getCorner(); cancelCorner = p.getBtnCancel().getCorner();
        }
    }
}
