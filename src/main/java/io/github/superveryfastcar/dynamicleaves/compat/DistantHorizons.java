package io.github.superveryfastcar.dynamicleaves.compat;

import io.github.superveryfastcar.dynamicleaves.DynamicLeaves;
import net.neoforged.fml.ModList;

/**
 * The link to Distant Horizons, the mod that draws the land far beyond
 * the view of the game.
 *
 * <h2>What the link is for</h2>
 * Distant Horizons keeps its own picture of the land, built from the
 * blocks of a chunk when that chunk is loaded or saved, and it keeps it
 * for land nobody has been near for months. Bare blocks would make a
 * patchwork of it: a forest would be bare far away where a player
 * happened to be in winter, and leafy everywhere else, and it would stay
 * so until the next visit, summer or not.
 *
 * <p>So Distant Horizons is told to take the leaf block wherever a bare
 * block stands ({@link DistantHorizonsLink}). Far crowns are whole all
 * year, and only their color changes with the season, which Seasons
 * already does. Up close the crowns are bare as they should be. The
 * hand-over between the two is a matter for the eye, and a season color
 * of its own for far crowns is a later step (the design doc, "Other
 * mods").</p>
 *
 * <h2>Why two classes</h2>
 * This one is always loaded and only looks whether Distant Horizons is
 * there. {@link DistantHorizonsLink}, which names its classes, is loaded
 * by name and only then. A game without Distant Horizons never loads it,
 * so nothing of it can fail there.
 */
public final class DistantHorizons {

    /** The mod id of Distant Horizons. */
    public static final String MOD_ID = "distanthorizons";

    /** The class that names Distant Horizons' own classes; never loaded without the mod. */
    private static final String LINK_CLASS = "io.github.superveryfastcar.dynamicleaves.compat.DistantHorizonsLink";

    /** Whether the link is at work. */
    private static volatile boolean linked;

    private DistantHorizons() {
    }

    /**
     * Puts the link in place if Distant Horizons is installed. Called once
     * while the game starts, after the list of mods is known.
     */
    public static void init() {
        if (!ModList.get().isLoaded(MOD_ID)) {
            return;
        }
        try {
            Class.forName(LINK_CLASS).getDeclaredMethod("hook").invoke(null);
            linked = true;
            DynamicLeaves.LOGGER.info("Distant Horizons is installed: far crowns keep their leaves all year.");
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            // A version of Distant Horizons whose doors are not the ones
            // this link was written for. Everything else keeps working;
            // far forests then go bare in patches where someone has been
            // in winter.
            DynamicLeaves.LOGGER.warn("Distant Horizons is installed, but Dynamic Leaves' link to it does not fit this version. "
                    + "Far trees may show bare crowns where a player has been in winter, and leafy ones elsewhere.", e);
        }
    }

    /** Whether the link is at work. */
    public static boolean linked() {
        return linked;
    }
}
