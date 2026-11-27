package io.github.superveryfastcar.foliage.client;

import io.github.superveryfastcar.seasons.client.leaves.BareTrees;

/**
 * What Foliage asks of the Seasons mod on the client, kept in one place so
 * that a change in Seasons is answered here and nowhere else.
 */
public final class SeasonsLink {

    private SeasonsLink() {
    }

    /**
     * Whether Seasons shows leaves thinning out right now: from the start
     * of the leaf fall in autumn until the trees are in full leaf again in
     * spring.
     *
     * <p>During that time Seasons swaps the pictures on the plain leaf
     * cubes, and the new leaf shapes step aside so that its look stays
     * whole. This holds until Foliage draws the bare months itself.</p>
     *
     * <p>Read on the chunk-building threads; it is one read of a number
     * that Seasons updates on the game's main thread.</p>
     */
    public static boolean thinning() {
        return BareTrees.bareness() > 0.0F;
    }
}
