package forge.adventure.stage;

import forge.adventure.pointofintrest.PointOfInterestChanges;
import forge.adventure.world.World;

import java.util.Random;

/**
 * Mod: every shop building in a town re-rolls WHICH shop it is every PERIOD_MS (default 1 hour),
 * using the town's normal rules (common/uncommon/rare/mythic lists, no duplicates).
 * Forge itself rolls this once per town from a fixed random chain, so towns never change.
 *
 * Like Forge's own rotating shops, the hourly seed also becomes the shop's stock seed, so the
 * stock and the "already bought" marks reset when the shop changes.
 *
 * Hooked in by PatchShopHour.java: in MapStage.loadObjects the World.getRandom() call before the
 * shop rarity roll becomes ShopHourMod.shopRandom(world, this, id).
 */
public final class ShopHourMod {
    public static final long PERIOD_MS = 3_600_000L;   // replaced by reapply_mods.py when compiling

    public static Random shopRandom(World world, MapStage stage, int id) {
        Random r = world.getRandom();
        try {
            long seed = r.nextLong() + System.currentTimeMillis() / PERIOD_MS;
            PointOfInterestChanges changes = stage.getChanges();
            if (changes != null) changes.setRotatingShopSeed(id, seed);
            r.setSeed(seed);
        } catch (Throwable t) {
            t.printStackTrace();
        }
        return r;
    }
}
