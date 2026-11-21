package forge.screens.match.views;

import com.badlogic.gdx.graphics.Color;
import forge.Forge;
import forge.Graphics;
import forge.assets.FSkinFont;
import forge.assets.TextRenderer;
import forge.game.GameView;
import forge.screens.match.MatchController;
import forge.menu.FDropDown;
import forge.toolbox.FButton;
import forge.toolbox.FDisplayObject;
import forge.toolbox.FOverlay;
import forge.toolbox.FScrollPane;
import forge.util.TextBounds;
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
    private static final Color GLASS = rgba(0x0d121b, .66f);
    private static final Color GOLD = rgba(0xe0b24f, 1f);
    private static final Color HAIRLINE = rgba(0xe0b24f, .40f);
    private static final Color TEXT = rgba(0xf5f3ed, 1f);
    private static final Color MUTED = rgba(0xb8c2d1, 1f);
    private static final Color CHIP = rgba(0x1c2635, .82f);
    private static final Color PANEL = rgba(0x0d121b, .84f);
    private static final Color PANEL_STRONG = rgba(0x0b0f17, .93f);
    private static final Color NAME = rgba(0xf2c96b, 1f), HARM = rgba(0xff8a78, 1f), AIM = rgba(0xffe07a, 1f),
            GOOD = rgba(0x8fe39a, 1f), ZONE = rgba(0x8fc8ff, 1f);
    // keyword groups: 1 harmful, 2 targets / optional, 3 gains, 4 zones
    private static final String KEYWORDS = "(damage|destroy(?:s|ed)?|exile(?:s|d)?|sacrific(?:e|es|ed)|discard(?:s|ed)?|dies|die|loses?)"
            + "|(any target|targets?|you may|up to (?:one|two|three|four|five|X))"
            + "|(draws?|gains?|create(?:s|d)?|tokens?)"
            + "|(battlefield|graveyard|library|hand|command zone)";
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
                && (Forge.getCurrentScene() instanceof forge.adventure.scene.DuelScene || (System.getProperty("forge.duo")!=null&&forge.Forge.getCurrentScreen() instanceof forge.screens.match.MatchScreen));   // forge.duo: 1v1 browser mode
    }

    private static float scale(VPrompt p) {
        // current width AND height: follows window/monitor switches (DevModeMod.followWindowSize)
        float s = Math.min(p.getWidth() / 1600f, Forge.getScreenHeight() / 900f);
        if (touch()) s *= 1.45f;   // a phone has desktop pixels on a few centimetres: bigger buttons and text (user 2026-11-05)
        return Math.max(.75f, Math.min(touch() ? 3.2f : 2.4f, s));
    }
    static boolean touch() { if (System.getProperty("forge.touchtest") != null) return true; try { return forge.gui.GuiBase.getInterface() != null && forge.gui.GuiBase.isAndroid(); } catch (Throwable t) { return false; } }
    /** a real decision on a phone (targets, attackers, blockers, "use this trigger?", choices) - not plain priority passing */
    private static boolean decisionPrompt(VPrompt p) {
        try {
            if (!touch() || !p.isVisible()) return false;
            FButton ok = p.getBtnOk(), no = p.getBtnCancel();
            if (!(ok.isVisible() && ok.isEnabled() || no.isVisible() && no.isEnabled())) return false;
            MatchController c = MatchController.instance;
            if (c == null || !(c.getGameController() instanceof forge.player.PlayerControllerHuman h)) return false;
            Object in = h.getInputProxy().getInput();
            // not while the game thread works (InputLockUI) - that made the panel open and close in a loop with the
            // priority text while Android's own confirm dialog ("pay 1 life?") was up (user 2026-11-06: "vibriert")
            return in != null && !(in instanceof forge.gamemodes.match.input.InputPassPriority)
                    && !(in instanceof forge.gamemodes.match.input.InputLockUI);
        } catch (Throwable t) { return false; }
    }
    /** the decision is about cards in the own hand (London mulligan "put N on the bottom", discard ...): the panel then
     *  sits in the middle so the hand stays tappable (user 2026-11-06: "direkt ueber der Hand ... in der Mitte anzeigen") */
    /** a plain question (yes/no, use this trigger?, optional cost): a big popup in the middle of the screen on touch
     *  (user 2026-11-13: "alle confirmation buttons als groessere pop ups") */
    private static boolean confirmQuestion() {
        try {
            MatchController c = MatchController.instance;
            if (c == null || !(c.getGameController() instanceof forge.player.PlayerControllerHuman h)) return false;
            return h.getInputProxy().getInput() instanceof forge.gamemodes.match.input.InputConfirm;
        } catch (Throwable t) { return false; }
    }
    private static boolean popupCentered() { return handChoice() || confirmQuestion(); }
    private static boolean handChoice() {
        try {
            MatchController c = MatchController.instance;
            if (c == null || !(c.getGameController() instanceof forge.player.PlayerControllerHuman h)) return false;
            Object in = h.getInputProxy().getInput();
            if (in instanceof forge.gamemodes.match.input.InputLondonMulligan) return true;
            if (in instanceof forge.gamemodes.match.input.InputSelectEntitiesFromList<?> sel)
                for (Object o : sel.getValidChoices())
                    if (o instanceof forge.game.card.Card card && card.isInZone(forge.game.zone.ZoneType.Hand)) return true;
            return false;
        } catch (Throwable t) { return false; }
    }
    /** another overlay (Android confirm dialog with card picture, card zoom, option list ...) is open: keep out of its way */
    private static boolean otherOverlay(FOverlay self) {
        try {
            for (FOverlay o : FOverlay.getOverlays())
                if (o != self && o.isVisible() && !(o instanceof MulliganPanel)) return true;
        } catch (Throwable t) { }
        return false;
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
        // no band at all (user 2026-10-17): chips, pills and buttons carry their own dark backgrounds
        return true;
    }

    /** MessageLabel keeps its tap/magnify, fling and card-zoom behaviour. */
    public static boolean drawMessage(VPrompt p, FDisplayObject label, Graphics g) {
        promptTop = label.screenPos == null ? -1f : label.screenPos.y;
        boolean r = drawMessage0(p, label, g);
        if (r) clampStack(promptTop);
        return r;
    }

    // stack vs. long prompt text (user 2026-11-17: "wenn zuviele trigger auf dem stack sind und man einen langen auswahl text
    // hat ueberlappen sich die texte unten mit dem stack" - "beim click auf den unteren text den wieder in vordergrund, beim
    // click auf den stack ist dieser im vordergrund"): the open stack list ends above the prompt panel and scrolls; when
    // there is not even room for about two entries, tapping the prompt closes the stack and the Stack tab opens it again
    private static float promptTop = -1f;
    private static final Map<FDropDown, Float> STACK_NATURAL = new WeakHashMap<>(), STACK_CLAMPED = new WeakHashMap<>();
    private static VStack openStack() {
        try { var v = MatchController.getView(); VStack st = v == null ? null : v.getStack(); return st != null && st.isVisible() ? st : null; }
        catch (Throwable t) { return null; }
    }
    private static void clampStack(float top) {
        if (top <= 0f) return;
        VStack st = openStack(); if (st == null) return;
        try {
            float h = st.getHeight();
            Float done = STACK_CLAMPED.get(st);
            if (done == null || Math.abs(h - done) > .5f) STACK_NATURAL.put(st, h);   // Forge laid the list out anew
            float natural = STACK_NATURAL.getOrDefault(st, h);
            float min = Math.max(90f, Forge.getScreenHeight() * .16f);
            float target = Math.min(natural, Math.max(min, top - st.getTop() - 6f));
            if (Math.abs(target - h) > .5f) st.setHeight(Math.round(target));
            STACK_CLAMPED.put(st, st.getHeight());
        } catch (Throwable t) { System.err.println("[PromptStyle] stack clamp " + t); }
    }
    /** the prompt panel still lies under the open stack (not enough room above it) */
    private static boolean stackCovers() {
        VStack st = openStack();
        return st != null && promptTop > 0f && st.getTop() + st.getHeight() > promptTop + 2f;
    }

    private static boolean drawMessage0(VPrompt p, FDisplayObject label, Graphics g) {
        State state = STATES.get(p);
        if (!active(p) || state == null || !state.styled) return false;
        if (behindDialog(p)) return true;
        if (mulliganPrompt(p)) { MulliganPanel.ensure(p); return true; }   // keep / mulligan and play / draw: big, in the middle (tester 2026-10-19)
        if (decisionPrompt(p)) DecisionPanel.ensure(p);   // Android: confirmations pop up big, bottom right (user 2026-11-05)
        if (DecisionPanel.showing(p)) return true;   // the panel shows the question - no second text box over the hand (user 2026-11-06)
        String message = p.getMessage();
        state.clipped = false;
        if (message == null || message.isBlank()) return true;
        float w = label.getWidth(), h = label.getHeight(), s = scale(p);
        if (!message.equals(state.message)) {
            state.message = message;
            state.status = parseStatus(message);
        }
        if (state.status != null) {
            state.clipped = drawChips(p, g, state.status, w, h, s);
        } else {
            // TextRenderer preserves mana symbols and Forge reminder/markup rendering.
            // Multi-line prompts ("X is going first / Do you want to keep your hand?") become one readable line
            // on a dark pill instead of three tiny lines squeezed into the slim transparent bar.
            // Trigger / choice texts (user 2026-10-18: "Schrift viel zu klein - ruhig mehr Platz und groessere Schrift, auch
            // wenn es ueber die Hand geht; Kartenname in Farbe, Woerter wie damage / target hervorheben"): one line when it
            // fits at full size, otherwise a big panel that grows UP from the bar over the hand. Colours via TextRenderer
            // markup, so mana symbols and wrapping keep working.
            String one = highlight(p, message.replaceAll("\\s*\\R+\\s*", "   \u00b7   ").trim());
            float usable = Math.max(1f, w - 16f * s);
            FSkinFont f = font(20f * s, h - 6f * s);
            float tw = state.renderer.getBounds(one, f).width;
            if (tw <= usable) {
                float ph = Math.min(h - 2f * s, f.getLineHeight() + 12f * s), pw = Math.min(w, tw + 36f * s);
                pill(g, CHIP, EDGE, (w - pw) / 2f, (h - ph) / 2f, pw, ph, Math.max(1f, s * .7f));
                state.renderer.drawText(g, one, f, TEXT, 8f * s, 0f, usable, h, 0f, h, false, 1, true);
            } else {
                // long texts: a big panel that grows UP from the bar over the hand (user 2026-10-18: tried the middle of
                // the screen - "nee, wir lassen den Trigger unten" - but big: "noch groesser")
                String multi = highlight(p, message.trim());
                float shh = Forge.getScreenHeight(), pw = w, px = 0f;
                float pad = 18f * s, inner = Math.max(1f, pw - 2f * pad), maxH = shh * .5f;
                float ts = forge.adventure.util.BattleUiSettings.size(3);   // Settings > Battle UI: Trigger / choice text
                float room = manaRoom(label, h);   // open mana pool: stay below its circles (user 2026-10-19)
                if (room > 0f) maxH = Math.max(1f, Math.min(maxH, room - 2f * pad * .8f));
                f = FSkinFont._get(Math.max(12, Math.round(21f * s * ts)));
                for (int size = Math.round(21f * s * ts) - 1; size >= Math.max(10, Math.round((room > 0f ? 12f : 15f) * s * ts))
                        && state.renderer.getWrappedBounds(multi, f, inner).height > maxH; size--)
                    f = FSkinFont._get(size);
                float th = state.renderer.getWrappedBounds(multi, f, inner).height;
                state.clipped = th > maxH;
                th = Math.min(th, maxH);
                float ph = th + 2f * pad * .8f, top = h - ph;   // bottom on the bar, the rest above it
                if (label.screenPos != null) promptTop = label.screenPos.y + top;
                pill(g, PANEL_STRONG, HAIRLINE, px, top, pw, ph, Math.max(1f, s * .9f), Math.min(18f * s, ph / 2f));
                g.fillRect(GOLD, px + pw * .08f, top + 1.5f * s, pw * .84f, Math.max(1f, s));   // thin gold rule on top
                state.renderer.drawText(g, multi, f, TEXT, px + pad, top + pad * .8f, inner, th, top + pad * .8f, th, true, 8, false);
            }
        }
        return true;
    }

    /** the bottom player's open mana pool (paying mana): its circles fill the upper 60 % of the display area right above
     *  the bar (ManaPoolLayoutMod.ROW); returns how high a panel growing up from the label may be, or -1 when no pool
     *  is open (user 2026-10-19: the payment text covered the circles) */
    private static float manaRoom(FDisplayObject label, float h) {
        try {
            forge.screens.match.MatchScreen ms = MatchController.getView();
            VPlayerPanel panel = ms == null ? null : ms.getBottomPlayerPanel();
            if (panel == null || panel.getManaPoolTab() == null || panel.getSelectedTab() != panel.getManaPoolTab()) return -1f;
            VDisplayArea da = panel.getManaPoolTab().getDisplayArea();
            if (da == null || !da.isVisible() || da.getHeight() <= 0f) return -1f;
            float circlesBottom = da.screenPos.y + da.getHeight() * .6f;
            return Math.max(h, label.screenPos.y + h - circlesBottom - 4f);
        } catch (Throwable t) { return -1f; }
    }

    /** "Keep / Mulligan" (InputConfirmMulligan) or "Play / Draw" (who starts) */
    private static boolean mulliganPrompt(VPrompt p) {
        try {
            FButton ok = p.getBtnOk(), no = p.getBtnCancel();
            if (!ok.isVisible() || !ok.isEnabled()) return false;
            forge.util.Localizer l = forge.util.Localizer.getInstance();
            String a = ok.getText(), b = no.getText();
            return l.getMessage("lblKeep").equals(a) && l.getMessage("lblMulligan").equals(b)
                    || l.getMessage("lblPlay").equals(a) && l.getMessage("lblDraw").equals(b);
        } catch (Throwable t) { return false; }
    }

    /** the opening-hand decision as a big panel in the middle of the battlefield (tester 2026-10-19: "zu klein, mach es in
     *  der Mitte"). Only the panel takes clicks: the hand below stays clickable for a closer look. */
    private static final class MulliganPanel extends FOverlay {
        private static MulliganPanel current;
        private final VPrompt prompt;
        private final Big keep, mull;
        private float s = 1f, px, py, pw, ph;
        static boolean showing(VPrompt p) { return current != null && current.prompt == p && current.isVisible(); }
        static void ensure(VPrompt p) {
            if (showing(p)) return;
            com.badlogic.gdx.Gdx.app.postRunnable(() -> {
                if (showing(p) || !mulliganPrompt(p)) return;
                if (current != null) current.close();
                current = new MulliganPanel(p);
                current.show();
            });
        }
        MulliganPanel(VPrompt p) { prompt = p; keep = add(new Big(true)); mull = add(new Big(false)); }
        void close() { if (current == this) current = null; if (isVisible()) hide(); }
        @Override public boolean preventInputBehindOverlay() { return false; }
        @Override public void buildTouchListeners(float screenX, float screenY, java.util.List<FDisplayObject> listeners) {
            if (screenX >= px && screenX <= px + pw && screenY >= py && screenY <= py + ph) super.buildTouchListeners(screenX, screenY, listeners);
        }
        @Override public boolean keyDown(int keyCode) { return false; }   // Enter / Esc and the battle keys still reach the prompt
        @Override protected void doLayout(float w, float h) {
            // only the two buttons, no box and no text around them (user 2026-11-15 "die box verdeckt teilweise die karten
            // ... nur die buttons ohne outer box"); they stay where they were inside the old panel
            s = Math.max(.7f, Math.min(2.4f, Math.min(w / 1600f, h / 900f)));
            float bw = Math.min(300f * s, (Math.min(w * .52f, 860f * s) - 90f * s) / 2f), bh = 66f * s, gap = 28f * s;
            pw = 2f * bw + gap; ph = bh; px = (w - pw) / 2f; py = h * .40f + 28f * s;
            mull.setBounds(px, py, bw, bh);
            keep.setBounds(px + bw + gap, py, bw, bh);
        }
        @Override protected void drawBackground(Graphics g) {
            if (!prompt.isVisible() || !mulliganPrompt(prompt)) com.badlogic.gdx.Gdx.app.postRunnable(this::close);
        }
        private final class Big extends FDisplayObject {
            private final boolean ok;
            Big(boolean ok) { this.ok = ok; }
            private FButton button() { return ok ? prompt.getBtnOk() : prompt.getBtnCancel(); }
            @Override public boolean tap(float x, float y, int count) { FButton b = button(); close(); b.trigger(); return true; }
            @Override public void draw(Graphics g) {
                float w = getWidth(), h = getHeight();
                Color fill = ok ? (isHovered() ? GOLD_HOVER : GOLD) : (isHovered() ? HOVER : SECONDARY);
                pill(g, fill, ok ? GOLD_DOWN : EDGE, 0, 0, w, h, Math.max(1f, 1.5f * s), 14f * s);
                g.drawText(button().getText(), FSkinFont._get(Math.max(12, Math.round(26f * s))), ok ? INK : TEXT, 0, 0, w, h, false, 1, true);
            }
        }
    }

    /** Android: a decision (targets, attackers, "use this trigger?", choices ...) as a big card above the bottom-right corner:
     *  the question and two large buttons (user 2026-11-05: "OK und Cancel ... ultra klein", "unten rechts ... aufploppen").
     *  Only the card takes touches - cards on the board stay selectable. */
    private static final class DecisionPanel extends FOverlay {
        private static DecisionPanel current;
        private final VPrompt prompt;
        private final Big ok, no;
        private float s = 1f, px, py, pw, ph, textH;
        private boolean centered, suspended;   // centered: hand choice in the middle; suspended: another overlay is open
        private long falseSince;               // close only after the decision is really over (no flicker)
        private String laidOut = "";
        /** the bar buttons hide while the panel stands for them - not while it steps aside for a dialog */
        static boolean showing(VPrompt p) { return current != null && current.prompt == p && current.isVisible() && !current.suspended; }
        static void ensure(VPrompt p) {
            if (current != null && current.prompt == p && current.isVisible()) return;
            com.badlogic.gdx.Gdx.app.postRunnable(() -> {
                if (current != null && current.prompt == p && current.isVisible() || !decisionPrompt(p) || mulliganPrompt(p)) return;
                if (current != null) current.close();
                current = new DecisionPanel(p);
                current.show();
            });
        }
        DecisionPanel(VPrompt p) { prompt = p; no = add(new Big(false)); ok = add(new Big(true)); }
        void close() { if (current == this) current = null; if (isVisible()) hide(); }
        @Override public boolean preventInputBehindOverlay() { return false; }
        @Override public void buildTouchListeners(float screenX, float screenY, java.util.List<FDisplayObject> listeners) {
            if (!suspended && screenX >= px && screenX <= px + pw && screenY >= py && screenY <= py + ph) super.buildTouchListeners(screenX, screenY, listeners);
        }
        @Override public void draw(Graphics g) {
            if (!prompt.isVisible() || mulliganPrompt(prompt) || !decisionPrompt(prompt)) {
                long now = System.currentTimeMillis();
                if (falseSince == 0) falseSince = now;
                if (now - falseSince > 300 || !prompt.isVisible() || mulliganPrompt(prompt)) com.badlogic.gdx.Gdx.app.postRunnable(this::close);
                return;   // a short gap between two inputs: draw nothing, keep the panel (no open / close loop)
            }
            falseSince = 0;
            suspended = otherOverlay(this);
            if (suspended) return;
            if (popupCentered() != centered || !question().equals(laidOut)) revalidate();   // new question: new height, same anchor
            forge.adventure.util.BattleUiSettings.decisionAt = System.currentTimeMillis();   // the turn panel steps aside
            super.draw(g);
        }
        @Override public boolean keyDown(int keyCode) { return false; }
        private String question() {
            String m = prompt.getMessage() == null ? "" : prompt.getMessage().trim();
            return m.replaceAll("\\s*\\R+\\s*", "  ");
        }
        @Override protected void doLayout(float w, float h) {
            s = Math.max(.9f, Math.min(3.2f, Math.min(w / 1600f, h / 900f) * 1.3f));
            pw = Math.min(w * .38f, 600f * s);
            if (popupCentered()) pw = Math.min(w * .62f, 820f * s);
            centered = popupCentered();
            // right edge left of the portrait column: the opponent's portrait stays tappable as a target
            float right = w - 14f * s;
            try { VPlayerPanel top = MatchController.getView().getTopPlayerPanel(); if (top != null && top.getAvatar().screenPos.x > w * .5f) right = top.getAvatar().screenPos.x - 12f * s; } catch (Throwable t) { }
            if (!centered) pw = Math.min(pw, right - w * .3f);
            laidOut = question();
            FSkinFont f = FSkinFont._get(Math.max(12, Math.round(19f * s)));
            textH = Math.min(f.getLineHeight() * 5.2f, Math.max(f.getLineHeight() * 1.2f, f.getWrappedBounds(laidOut, pw - 36f * s).height + 6f * s));
            float bh = Math.max(58f * s, forge.util.Utils.AVG_FINGER_HEIGHT * 1.35f);   // at least a fingertip on small phones
            ph = 18f * s + textH + 16f * s + bh + 16f * s;
            // upper right, standing on the middle line where the turn panel sits (which steps aside meanwhile): the hand,
            // the zone strip and an open graveyard stay free (user 2026-11-06: "die Menus sind im Weg, weiter oben");
            // the bottom edge is fixed, a longer question grows upwards. Hand choices: the middle of the screen.
            float middle = h * .45f;
            try { VPlayerPanel b = MatchController.getView().getBottomPlayerPanel(); if (b != null && b.screenPos.y > h * .2f) middle = b.screenPos.y; } catch (Throwable t) { }
            if (centered) { px = (w - pw) / 2f; py = Math.max(8f * s, h * .40f - ph / 2f); }
            else { px = right - pw; py = Math.max(8f * s, middle - ph - 8f * s); }
            float bw = (pw - 3 * 16f * s) / 2f, by = py + ph - bh - 16f * s;
            no.setBounds(px + 16f * s, by, bw, bh);
            ok.setBounds(px + pw - 16f * s - bw, by, bw, bh);
        }
        @Override protected void drawBackground(Graphics g) {
            pill(g, PANEL_STRONG, GOLD, px, py, pw, ph, Math.max(1.5f, 2f * s), 16f * s);
            g.fillRect(GOLD, px + pw * .1f, py + 3f * s, pw * .8f, Math.max(1f, s));
            g.drawText(question(), FSkinFont._get(Math.max(12, Math.round(19f * s))), TEXT, px + 18f * s, py + 18f * s, pw - 36f * s, textH, true, 8, false);
        }
        private final class Big extends FDisplayObject {
            private final boolean primary;
            Big(boolean primary) { this.primary = primary; }
            private FButton button() { return primary ? prompt.getBtnOk() : prompt.getBtnCancel(); }
            private boolean usable() { FButton b = button(); return b.isVisible() && b.isEnabled(); }
            @Override public boolean tap(float x, float y, int count) { if (!usable()) return true; FButton b = button(); close(); b.trigger(); return true; }
            @Override public void draw(Graphics g) {
                float w = getWidth(), h = getHeight();boolean on = usable();
                Color fill = !on ? DISABLED : primary ? (isHovered() ? GOLD_HOVER : GOLD) : (isHovered() ? HOVER : SECONDARY);
                pill(g, fill, primary && on ? GOLD_DOWN : EDGE, 0, 0, w, h, Math.max(1f, 1.5f * s), 14f * s);
                g.drawText(button().getText(), FSkinFont._get(Math.max(12, Math.round(Math.max(25f * s, h * .42f)))), !on ? DISABLED_TEXT : primary ? INK : TEXT, 0, 0, w, h, false, 1, true);
            }
        }
    }

    private static final Field CARD = cardField();
    private static Field cardField() {
        try { Field f = VPrompt.class.getDeclaredField("card"); f.setAccessible(true); return f; }
        catch (ReflectiveOperationException ex) { return null; }
    }
    /** colour markup in one pass (a card name containing a keyword is coloured once): the prompt's card name (full,
     *  before the comma, first word) gold, harmful words red, targets / "you may" yellow, gains green, zones blue */
    private static String highlight(VPrompt p, String text) {
        try {
            StringBuilder names = new StringBuilder();
            Object card = CARD == null ? null : CARD.get(p);
            if (card instanceof forge.game.card.CardView cv && cv.getName() != null && !cv.getName().isBlank()) {
                String n = cv.getName().trim();
                java.util.List<String> list = new java.util.ArrayList<>(java.util.List.of(n));
                int comma = n.indexOf(','); if (comma > 2) list.add(n.substring(0, comma).trim());
                String first = n.split("[ ,]")[0]; if (first.length() >= 4 && !first.equals(n)) list.add(first);
                for (String x : list) { if (names.length() > 0) names.append('|'); names.append(Pattern.quote(x)); }
            }
            Matcher lead = Pattern.compile("^\\s*([A-Z][^\\n\\-\u2013(]{1,44}?)\\s+[-\u2013]\\s").matcher(text);
            if (lead.find()) { if (names.length() > 0) names.append('|'); names.append(Pattern.quote(lead.group(1).trim())); }
            Matcher withId = Pattern.compile("([A-Z][A-Za-z',\\- ]{1,44}?) \\((\\d+)\\)").matcher(text);
            while (withId.find()) { if (names.length() > 0) names.append('|'); names.append(Pattern.quote(withId.group(1).trim())); }
            boolean withName = names.length() > 0;
            Pattern all = Pattern.compile("(?i)\\b(?:" + (withName ? "(" + names + ")|" : "") + KEYWORDS + ")\\b");
            Matcher m = all.matcher(text);
            StringBuilder out = new StringBuilder(); int last = 0, o = withName ? 1 : 0;
            while (m.find()) {
                if (inBraces(text, m.start())) continue;   // never inside {mana} or existing markup
                Color c = withName && m.group(1) != null ? NAME : m.group(1 + o) != null ? HARM : m.group(2 + o) != null ? AIM
                        : m.group(3 + o) != null ? GOOD : m.group(4 + o) != null ? ZONE : null;
                if (c == null) continue;
                out.append(text, last, m.start()).append(TextRenderer.startColor(c)).append(m.group()).append(TextRenderer.endColor());
                last = m.end();
            }
            return out.append(text.substring(last)).toString();
        } catch (Throwable t) { return text; }
    }
    private static boolean inBraces(String t, int at) {
        return t.lastIndexOf('{', at) > t.lastIndexOf('}', at) || t.lastIndexOf('<', at) > t.lastIndexOf('>', at);
    }

    private static boolean drawChips(VPrompt p, Graphics g, String[] status, float w, float h, float s) {
        String turn = status[1];
        Matcher m = TURN.matcher(turn);
        String owner = m.matches() ? m.group(2) : turn;
        String number = m.matches() ? m.group(1) : "";
        MatchController controller = MatchController.instance;
        GameView game = controller == null ? null : controller.getGameView();
        String title = owner;
        if (game != null && game.getPlayerTurn() != null
                && game.getPlayerTurn().getName().equals(owner)) {
            title = forge.adventure.util.BattleUiSettings.tr(game.getPlayerTurn().isAI() || controller != null && !controller.isLocalPlayer(game.getPlayerTurn()) ? "Enemy turn" : "Your turn");
        }
        String[] chips = {title + " · " + phase(status[2]) + (number.isEmpty() ? "" : " · T" + number),
                forge.adventure.util.BattleUiSettings.tr("Priority") + ": " + status[0], forge.adventure.util.BattleUiSettings.tr("Stack") + ": " + (status[3].equalsIgnoreCase("empty") || status[3].equalsIgnoreCase("leer") ? forge.adventure.util.BattleUiSettings.tr("empty") : status[3])};
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
        return ratio < 1f;
    }

    private static float chipTotal(FSkinFont f, String[] chips, float s, float gap) {
        float total = 2f * gap;
        for (int i = 0; i < chips.length; i++) total += width(f, chips[i]) + (i == 0 ? 36f : 24f) * s;
        return total;
    }

    /** MessageLabel.tap: no huge opaque full-width magnify (user 2026-10-17). Chips and one-line prompts already show
     *  everything; only text that really is cut off opens a small translucent popup above the bar. */
    public static boolean tapMessage(VPrompt p, FDisplayObject label) {
        State state = STATES.get(p);
        if (!active(p) || state == null || !state.styled) return false;
        if (stackCovers()) { VStack st = openStack(); if (st != null) st.hide(); return true; }   // prompt to the front (user 2026-11-17)
        String message = p.getMessage();
        if (state.clipped && message != null && !message.isBlank()) new Popup(label, message.trim(), scale(p)).show();
        return true;
    }

    /** the battlefield's own prompt while an open (not swiped-away) choice dialog shows its prompt bar on top of it:
     *  drawn as nothing, otherwise "Auto / Cancel" and "OK / OK" lie on top of each other (user screenshot 2026-10-18) */
    private static boolean behindDialog(VPrompt p) {
        try {
            forge.toolbox.FOverlay top = forge.toolbox.FOverlay.getTopOverlay();
            return top instanceof forge.toolbox.FDialog d && d.isVisible() && d.preventInputBehindOverlay()
                    && Forge.getCurrentScreen() instanceof forge.screens.match.MatchScreen ms && ms.getActivePrompt() == p;
        } catch (Throwable t) { return false; }
    }

    private static final class Popup extends FDropDown {
        private final FDisplayObject owner;
        private final String text;
        private final float s, pad;
        private final FSkinFont font;
        private final TextRenderer renderer = new TextRenderer(true);
        Popup(FDisplayObject owner, String text, float s) {
            this.owner = owner; this.text = text; this.s = s; pad = 10f * s; font = FSkinFont._get(Math.max(8, Math.round(14f * s)));
        }
        @Override protected boolean autoHide() { return true; }
        @Override protected FDisplayObject getDropDownOwner() { return owner; }
        @Override protected FScrollPane.ScrollBounds updateAndGetPaneSize(float maxWidth, float maxHeight) {
            TextBounds b = renderer.getWrappedBounds(text, font, maxWidth - 2f * pad);
            return new FScrollPane.ScrollBounds(Math.min(maxWidth, b.width + 2f * pad), b.height + 2f * pad);
        }
        @Override protected void updateSizeAndPosition() {
            float top = Math.max(1f, owner.screenPos.y - 6f * s);
            paneSize = updateAndGetPaneSize(Math.max(80f * s, Math.min(owner.getWidth(), Forge.getScreenWidth() * .45f)), top);
            float w = paneSize.getWidth(), h = Math.min(paneSize.getHeight(), top);
            float x = Math.max(0f, owner.screenPos.x + (owner.getWidth() - w) / 2f);
            setBounds(Math.round(x), Math.round(top - h), Math.round(w), Math.round(h));
        }
        @Override protected void drawBackground(Graphics g) {
            float w = getWidth(), h = getHeight();
            pill(g, GLASS, EDGE, 0f, 0f, w, h, Math.max(1f, s * .7f), Math.min(12f * s, h / 2f));
            renderer.drawText(g, text, font, TEXT, pad - getScrollLeft(), pad - getScrollTop(),
                    getScrollWidth() - 2f * pad, getScrollHeight() - 2f * pad, 0f, h, true, 8, false);
        }
        @Override protected void drawOverlay(Graphics g) {}   // no square Forge border
    }

    /** Guarded FButton.draw hook; pressed is read by bytecode inside FButton itself. */
    public static boolean drawButton(FButton b, Graphics g, boolean pressed) {
        WeakReference<VPrompt> ref = BUTTONS.get(b);
        VPrompt p = ref == null ? null : ref.get();
        State state = p == null ? null : STATES.get(p);
        if (p == null || !active(p) || state == null || !state.styled) return false;
        if (behindDialog(p)) return true;
        if (mulliganPrompt(p) && MulliganPanel.showing(p)) return true;
        if (DecisionPanel.showing(p)) return true;   // the big panel shows these two buttons
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
            // painted fantasy button sheet (user concept 2026-10-17); OK keeps its primary glow, text clears the ornaments
            int art = !enabled ? 3 : down ? 2 : hover ? 1 : 0;
            if (forge.adventure.util.FantasyButtonArt.drawGraphics(g, art, primary, inset, y, w - 2f * inset, bh)) {
                float pad = forge.adventure.util.FantasyButtonArt.textInset(w - 2f * inset, bh);
                g.drawText(b.getText(), font(16f * s, bh - 6f * s), enabled ? TEXT : DISABLED_TEXT,
                        inset + pad, y, Math.max(1f, w - 2f * inset - 2f * pad), bh, false, 1, true);
                return true;
            }
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
        String[][] keys = {{"priority:", "priorität:"}, {"turn:", "zug:"}, {"phase:"}, {"stack:", "stapel:"}};   // German: 1v1 LAN (user 2026-11-14)
        int count = 0;
        for (String part : message.split("\\s*(?:\\R|\\|)\\s*")) {
            String value = part.trim();
            if (value.isEmpty()) continue;
            String lower = value.toLowerCase(Locale.ROOT);
            int index = -1, len = 0;
            for (int i = 0; i < keys.length && index < 0; i++) for (String k : keys[i]) if (lower.startsWith(k)) { index = i; len = k.length(); break; }
            if (index < 0 || values[index] != null) return null;
            values[index] = value.substring(len).trim();
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
        pill(g, fill, rim, x, y, w, h, line, Math.min(w, h) / 2f);
    }
    private static void pill(Graphics g, Color fill, Color rim, float x, float y, float w, float h, float line, float r) {
        if (rim != null && w > 2f * line && h > 2f * line) {
            g.fillRoundRect(rim, x, y, w, h, r);
            x += line; y += line; w -= 2f * line; h -= 2f * line; r = Math.max(0f, r - line);
        }
        g.fillRoundRect(fill, x, y, w, h, Math.min(r, Math.min(w, h) / 2f));
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
        boolean styled, clipped;
        String message;
        String[] status;
        State(VPrompt p) {
            labelBounds = bounds(label(p)); okBounds = bounds(p.getBtnOk()); cancelBounds = bounds(p.getBtnCancel());
            okCorner = p.getBtnOk().getCorner(); cancelCorner = p.getBtnCancel().getCorner();
        }
    }
}
