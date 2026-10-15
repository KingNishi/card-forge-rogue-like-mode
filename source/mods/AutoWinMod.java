package forge.adventure.stage;

import forge.adventure.character.EnemySprite;
import forge.adventure.data.EnemyData;
import forge.deck.Deck;
import forge.adventure.character.MapActor;
import forge.adventure.character.PlayerSprite;
import forge.adventure.util.Current;
import org.apache.commons.lang3.tuple.Pair;

import java.lang.reflect.Field;

/**
 * Mod: once an enemy has been beaten REQUIRED_WINS times, touching it defeats it right away -
 * no duel. Forge's normal after-win flow runs (win counted, death animation, reward screen,
 * enemy removed, quest progress). Arena and event fights are not affected.
 *
 * Hooked in by PatchAutoWin.java:
 *   WorldStage: player.collideWith(mob)  ->  AutoWinMod.worldCollide(player, mob)
 *   MapStage:   beginDuel(mob)           ->  AutoWinMod.mapBeginDuel(this, mob)
 * Any error falls back to the original behaviour (normal duel).
 */
public final class AutoWinMod {
    public static final int REQUIRED_WINS = 10;   // replaced by reapply_mods.py when compiling
    private static final long BUSY_MS = 6000;     // ignore encounters while an auto-win plays out

    private static long busyUntil;

    static int wins(EnemySprite mob) {
        Pair<Integer, Integer> r = Current.player().getStatistic().getWinLossRecord().get(mob.getName());
        return r == null || r.getLeft() == null ? 0 : r.getLeft();
    }

    static boolean eligible(EnemySprite mob) {
        return mob != null && mob.getData() != null && wins(mob) >= REQUIRED_WINS;
    }

    static boolean busy() {
        return System.currentTimeMillis() < busyUntil;
    }

    /**
     * The reward code (EnemySprite.getRewards) reads the enemy's deck from Current.latestDeck(),
     * which DuelScene normally sets when the match starts. Without a match it would be null
     * (crash) or still hold the previous opponent's deck, so build the deck the same way DuelScene does.
     */
    static void prepareEnemyDeck(EnemySprite mob) {
        Deck deck = null;
        try {
            EnemyData d = mob.getData();
            deck = d.copyPlayerDeck ? Current.player().getSelectedDeck()
                                    : d.generateDeck(Current.player().isFantasyMode(), false);
        } catch (Throwable t) {
            t.printStackTrace();
        }
        if (deck == null) deck = Current.player().getSelectedDeck();
        Current.setLatestDeck(deck);
    }

    // ------------------------------------------------------------------ world map
    public static boolean worldCollide(PlayerSprite player, MapActor actor) {
        boolean hit = player.collideWith(actor);
        if (!hit || !(actor instanceof EnemySprite)) return hit;
        try {
            if (busy()) return false;                    // previous auto-win still animating
            EnemySprite mob = (EnemySprite) actor;
            if (!eligible(mob)) return true;             // normal duel
            busyUntil = System.currentTimeMillis() + BUSY_MS;
            WorldStage stage = WorldStage.getInstance();
            stage.currentMob = mob;
            prepareEnemyDeck(mob);
            Current.player().getStatistic().setResult(mob.getName(), true);
            stage.setWinner(true, false);
            return false;
        } catch (Throwable t) {
            t.printStackTrace();
            busyUntil = 0;
            return hit;
        }
    }

    // ------------------------------------------------------------------ dungeons / towns / caves
    public static void mapBeginDuel(MapStage stage, EnemySprite mob) {
        try {
            if (busy()) return;
            if (!eligible(mob)) { stage.beginDuel(mob); return; }
            busyUntil = System.currentTimeMillis() + BUSY_MS;
            setField(stage, "currentMob", mob);
            prepareEnemyDeck(mob);
            Current.player().getStatistic().setResult(mob.getName(), true);
            stage.setWinner(true, false);
        } catch (Throwable t) {
            t.printStackTrace();
            busyUntil = 0;
            stage.beginDuel(mob);
        }
    }

    private static void setField(Object o, String name, Object value) {
        try {
            Field f = MapStage.class.getDeclaredField(name);
            f.setAccessible(true);
            f.set(o, value);
        } catch (Throwable ignored) {
        }
    }
}
