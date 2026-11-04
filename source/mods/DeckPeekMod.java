package forge.screens.match;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import forge.Forge;
import forge.Graphics;
import forge.assets.FSkinFont;
import forge.card.CardRenderer;
import forge.card.CardTypeView;
import forge.card.CardZoom;
import forge.deck.CardPool;
import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.game.GameView;
import forge.game.card.CardView;
import forge.game.player.PlayerView;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;
import forge.toolbox.FDisplayObject;
import forge.toolbox.FOptionPane;
import forge.toolbox.FOverlay;
import forge.toolbox.FScrollPane;
import forge.util.ImageUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A GUI-only snapshot. Never changes visibility permissions, card order, or game state. */
public final class DeckPeekMod {
    private DeckPeekMod() {}

    private static final Color BACK = new Color(0x0d121bf5);
    private static final Color PANEL = new Color(0x202b3cff);
    private static final Color HOVER = new Color(0x304157ff);
    private static final Color GOLD = new Color(0xe0b24fff);
    private static final Color TEXT = new Color(0xf5f3edff);
    private static final Color MUTED = new Color(0xb8c2d1ff);
    private static final Color DIM = new Color(0x0d121b80);
    private static final String[] GROUPS = {"Creatures", "Instants / Sorceries", "Artifacts",
            "Enchantments", "Planeswalkers", "Lands", "Other"};

    // No physical card IDs or original positions are used as sorting tie-breakers.
    static final Comparator<Entry> SORT = Comparator.comparingInt((Entry e) -> e.group)
            .thenComparingInt(e -> e.manaValue).thenComparing(e -> e.name)
            .thenComparing(e -> e.cost)
            .thenComparing(e -> e.paper == null ? "" : e.paper.getEdition())
            .thenComparingInt(e -> e.paper == null ? 0 : e.paper.getArtIndex())
            .thenComparing(e -> e.paper == null ? "" : e.paper.getFunctionalVariant(), Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(e -> e.paper != null && e.paper.isFoil());

    public static void showDeck() {
        if (!(Forge.getCurrentScreen() instanceof MatchScreen)) return;
        MatchController controller = MatchController.instance;
        GameView game = controller.getGameView();
        PlayerView player = localHuman(controller);
        if (game == null || game.isGameOver() || player == null) {
            FOptionPane.showMessageDialog("Your deck is available during your match.", "View deck");
            return;
        }
        new Viewer(game, player).show();
    }

    private static PlayerView localHuman(MatchController controller) {
        PlayerView current = controller.getCurrentPlayer();
        if (current != null && !current.isAI() && controller.isLocalPlayer(current)) return current;
        PlayerView only = null;
        for (PlayerView p : controller.getLocalPlayers()) {
            if (p.isAI()) continue;
            if (only != null) return null; // Never guess another hot-seat player's identity.
            only = p;
        }
        return only;
    }

    private static int group(CardTypeView type) {
        if (type.isCreature()) return 0;
        if (type.isInstant() || type.isSorcery()) return 1;
        if (type.isArtifact()) return 2;
        if (type.isEnchantment()) return 3;
        if (type.isPlaneswalker()) return 4;
        if (type.isLand()) return 5;
        return 6;
    }

    static final class Entry {
        final PaperCard paper;
        final String name, cost;
        final int group, manaValue;
        boolean outsideLibrary;

        Entry(PaperCard paper) {
            this.paper = paper;
            name = paper.getName();
            cost = paper.getRules().getManaCost().toString();
            manaValue = paper.getRules().getManaCost().getCMC();
            group = group(paper.getRules().getType());
        }

        Entry(CardView card) {
            paper = null; // A readable fallback when a card has no resolvable paper printing.
            CardView.CardStateView state = card.getCurrentState();
            name = state.getName();
            cost = state.getManaCost().toString();
            manaValue = state.getManaCost().getCMC();
            group = group(state.getType());
        }
    }

    private static void addPool(List<Entry> target, CardPool pool) {
        if (pool == null) return;
        for (Map.Entry<PaperCard, Integer> item : pool) {
            for (int i = 0; i < item.getValue(); i++) target.add(new Entry(item.getKey()));
        }
    }

    static void markOutsideLibrary(List<Entry> whole, List<Entry> library) {
        Map<String, Integer> left = new HashMap<>();
        Map<String, Integer> prints = new HashMap<>();
        for (Entry entry : library) {
            left.merge(entry.name, 1, Integer::sum);
            if (entry.paper != null) prints.merge(printKey(entry), 1, Integer::sum);
        }
        for (Entry entry : whole) {
            String key = printKey(entry);
            int n = prints.getOrDefault(key, 0);
            entry.outsideLibrary = n == 0;
            if (n > 0) {
                prints.put(key, n - 1);
                left.put(entry.name, left.get(entry.name) - 1);
            }
        }
        // Prefer the matching printing/art before assigning copies with missing metadata.
        for (Entry entry : whole) {
            if (!entry.outsideLibrary) continue;
            int n = left.getOrDefault(entry.name, 0);
            entry.outsideLibrary = n == 0;
            if (n > 0) left.put(entry.name, n - 1);
        }
    }

    private static String printKey(Entry entry) {
        return entry.paper == null ? "" : entry.paper.getImageKey(false) + "|" + entry.paper.isFoil()
                + "|" + entry.paper.getFunctionalVariant();
    }

    private static void render() {
        if (Gdx.graphics != null) Gdx.graphics.requestRendering();
    }

    private static final class Viewer extends FOverlay {
        final GameView game;
        final PlayerView player;
        final List<Entry> library = new ArrayList<>(), whole = new ArrayList<>();
        final List<PaperCard> zoomCards = new ArrayList<>();
        final Grid grid = add(new Grid());
        final Control libraryTab = add(new Control("Library", () -> select(false)));
        final Control wholeTab = add(new Control("Whole deck", () -> select(true)));
        final Control refresh = add(new Control("Refresh", this::refresh));
        final Control close = add(new Control("Close", this::hide));
        boolean all, startingDeckAvailable;
        float scale = 1f, margin;

        Viewer(GameView game, PlayerView player) {
            this.game = game;
            this.player = player;
            refresh();
        }

        private void refresh() {
            if (MatchController.instance.getGameView() != game || game.isGameOver()
                    || !player.equals(localHuman(MatchController.instance))) {
                hide();
                return;
            }
            whole.clear();
            Deck deck = game.getDeck(player);
            startingDeckAvailable = deck != null;
            if (deck != null) {
                addPool(whole, deck.get(DeckSection.Main));
                addPool(whole, deck.get(DeckSection.Commander));
            }
            library.clear();
            Iterable<CardView> cards = player.getCards(ZoneType.Library);
            if (cards != null) {
                for (CardView card : cards) {
                    // Resolve immutable print data instead of granting may-look access to a live card.
                    PaperCard paper = ImageUtil.getPaperCardFromImageKey(card.getCurrentState().getTrackableImageKey());
                    library.add(paper == null ? new Entry(card) : new Entry(paper));
                }
            }
            library.sort(SORT);
            whole.sort(SORT);
            markOutsideLibrary(whole, library);
            select(all);
        }

        private void select(boolean wholeDeck) {
            all = wholeDeck;
            libraryTab.label = "Library (" + library.size() + ")";
            wholeTab.label = "Whole deck (" + whole.size() + ")";
            libraryTab.selected = !all;
            wholeTab.selected = all;
            grid.clear();
            zoomCards.clear();
            List<Entry> entries = all ? whole : library;
            for (int group = 0; group < GROUPS.length; group++) {
                int count = 0;
                for (Entry entry : entries) if (entry.group == group) count++;
                if (count == 0) continue;
                grid.add(new Heading(GROUPS[group] + " (" + count + ")"));
                for (Entry entry : entries) {
                    if (entry.group != group) continue;
                    int index = entry.paper == null ? -1 : zoomCards.size();
                    if (entry.paper != null) zoomCards.add(entry.paper);
                    grid.add(new Tile(entry, index));
                }
            }
            if (entries.isEmpty()) grid.add(new Heading(all
                    ? startingDeckAvailable ? "Your starting deck is empty." : "Starting deck unavailable."
                    : "Your library is empty."));
            grid.revalidate();
            grid.setScrollTop(0);
            render();
        }

        @Override protected void doLayout(float w, float h) {
            scale = Math.max(.65f, Math.min(2.5f, Math.min(w / 1600f, h / 900f)));
            margin = 24f * scale;
            float buttonHeight = 44f * scale, gap = 12f * scale;
            float buttonWidth = Math.min(215f * scale, (w - 2 * margin - 3 * gap) / 4);
            float y = 60f * scale;
            libraryTab.setBounds(margin, y, buttonWidth, buttonHeight);
            wholeTab.setBounds(margin + buttonWidth + gap, y, buttonWidth, buttonHeight);
            close.setBounds(w - margin - buttonWidth, y, buttonWidth, buttonHeight);
            refresh.setBounds(w - margin - 2 * buttonWidth - gap, y, buttonWidth, buttonHeight);
            float top = y + buttonHeight + 45f * scale;
            grid.setBounds(margin, top, Math.max(1, w - 2 * margin), Math.max(1, h - top - margin));
        }

        @Override protected void drawBackground(Graphics g) {
            g.fillRect(BACK, 0, 0, getWidth(), getHeight());
            g.drawText("View deck - " + player.getName(), font(24), TEXT,
                    margin, 8f * scale, getWidth() - 2 * margin, 44f * scale, false, 8, true);
            String note = all ? "Starting deck; dimmed copies are outside your library."
                    : "Remaining cards sorted by type, mana value, then name. Never library order.";
            g.drawText(note + "  Snapshot - Refresh to update. Tap a card to zoom.", font(14), MUTED,
                    margin, 108f * scale, getWidth() - 2 * margin, 36f * scale, true, 8, true);
        }

        @Override public boolean keyDown(int key) {
            if (key == Input.Keys.ESCAPE || key == Input.Keys.BACK) hide();
            return true; // No keyboard shortcuts leak through to the match.
        }

        // Layout already scales in screen pixels; get() would apply Utils.scale a second time.
        FSkinFont font(int size) { return FSkinFont._get(Math.max(10, Math.round(size * scale))); }

        private final class Control extends FDisplayObject {
            String label;
            final Runnable action;
            boolean selected;
            Control(String label, Runnable action) { this.label = label; this.action = action; }
            @Override public boolean tap(float x, float y, int count) { action.run(); return true; }
            @Override public void draw(Graphics g) {
                g.fillRoundRect(isHovered() ? HOVER : PANEL, 0, 0, getWidth(), getHeight(), 8f * scale);
                if (selected) g.drawRoundRect(2f * scale, GOLD, 0, 0, getWidth(), getHeight(), 8f * scale);
                g.drawText(label, font(17), selected ? GOLD : TEXT, 4f * scale, 0,
                        getWidth() - 8f * scale, getHeight(), false, 1, true);
            }
        }

        private final class Heading extends FDisplayObject {
            final String label;
            Heading(String label) { this.label = label; }
            @Override public void draw(Graphics g) {
                g.drawText(label, font(20), GOLD, 0, 0, getWidth(), getHeight(), false, 8, true);
            }
        }

        private final class Tile extends FDisplayObject {
            final Entry entry;
            final int zoomIndex;
            Tile(Entry entry, int zoomIndex) { this.entry = entry; this.zoomIndex = zoomIndex; }
            @Override public boolean tap(float x, float y, int count) {
                if (zoomIndex >= 0) CardZoom.show(new ArrayList<>(zoomCards), zoomIndex, null);
                return true;
            }
            @Override public void draw(Graphics g) {
                float w = getWidth(), imageHeight = w * 1.4f;
                boolean dim = all && entry.outsideLibrary;
                if (entry.paper != null) {
                    CardRenderer.drawCard(g, entry.paper, 0, 0, w, imageHeight, CardRenderer.CardStackPosition.Top);
                    if (dim) g.fillRect(DIM, 0, 0, w, imageHeight);
                } else {
                    g.fillRoundRect(PANEL, 0, 0, w, imageHeight, 8f * scale);
                    g.drawText(entry.name + "\n" + entry.cost, font(22), TEXT,
                            10f * scale, 0, w - 20f * scale, imageHeight, true, 1, true);
                }
                g.drawText(entry.name, font(16), dim ? MUTED : TEXT, 0, imageHeight,
                        w, 48f * scale, true, 1, true);
                String detail = "MV " + entry.manaValue + (dim ? " - Outside library" : " - Library");
                g.drawText(detail, font(13), dim ? MUTED : GOLD, 0, imageHeight + 48f * scale,
                        w, 25f * scale, false, 1, true);
            }
        }

        private final class Grid extends FScrollPane {
            @Override protected ScrollBounds layoutAndGetScrollBounds(float w, float h) {
                float gap = 18f * scale;
                int columns = Math.max(1, (int) ((w + gap) / (210f * scale + gap)));
                float cardWidth = Math.max(1, (w - gap * (columns - 1) - getIndicatorMargin()) / columns);
                float tileHeight = cardWidth * 1.4f + 78f * scale;
                float y = 0;
                int column = 0;
                for (FDisplayObject child : getChildren()) {
                    if (child instanceof Heading) {
                        if (column > 0) { y += tileHeight + gap; column = 0; }
                        child.setBounds(0, y, w, 46f * scale);
                        y += 46f * scale;
                    } else {
                        child.setBounds(column * (cardWidth + gap), y, cardWidth, tileHeight);
                        if (++column == columns) { column = 0; y += tileHeight + gap; }
                    }
                }
                if (column > 0) y += tileHeight + gap;
                return new ScrollBounds(w, y);
            }
        }
    }
}
