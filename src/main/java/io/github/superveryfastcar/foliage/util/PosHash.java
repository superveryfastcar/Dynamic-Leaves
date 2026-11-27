package io.github.superveryfastcar.foliage.util;

/**
 * Fixed random numbers per place: the same position always gives the same
 * number, and neighboring positions give unrelated numbers, so patterns
 * built from them show no stripes or grid.
 *
 * <p>Plain arithmetic with no state, so it gives the same answers on a
 * server, in every player's game, and on any thread.</p>
 */
public final class PosHash {

    private PosHash() {
    }

    /**
     * Scrambles a salt and two whole numbers into 64 well-mixed bits. The
     * numbers are first spread with two large odd constants, then mixed
     * with the finishing steps of the SplitMix64 generator, so that every
     * input bit affects every output bit.
     *
     * @param salt tells different uses apart, so that two patterns made
     *             from the same positions are unrelated
     */
    public static long hash(long salt, int x, int y) {
        long z = salt + x * 0x9E3779B97F4A7C15L + y * 0xC2B2AE3D27D4EB4FL;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** The same for a block position: three whole numbers. */
    public static long hash(long salt, int x, int y, int z) {
        return hash(hash(salt, y, 0), x, z);
    }

    /** The top 24 bits of a hash as a number from 0 to just under 1, evenly spread. */
    public static float unit(long hash) {
        return (hash >>> 40) / (float) (1L << 24);
    }

    /**
     * A smooth pattern over the map: a number from 0 to 255 that changes
     * gently from one block to the next and has no visible grid. The map is
     * cut into square cells, each corner gets a fixed random number, and a
     * place takes a mix of the four corners around it, weighted by an
     * S-shaped curve so that the pattern has no creases at the cell edges.
     *
     * <p>Whole-number arithmetic only, so a server and every player's game
     * get exactly the same pattern.</p>
     *
     * @param salt tells different patterns apart
     * @param x    the block's x
     * @param z    the block's z
     * @param cell the width of a cell in blocks, at least 1; over about
     *             this distance the pattern changes from one random value
     *             to the next
     * @return 0 to 255
     */
    public static int smooth(long salt, int x, int z, int cell) {
        int cellX = Math.floorDiv(x, cell);
        int cellZ = Math.floorDiv(z, cell);
        int weightX = ease(Math.floorMod(x, cell) * 256 / cell);
        int weightZ = ease(Math.floorMod(z, cell) * 256 / cell);
        int a = bits(hash(salt, cellX, cellZ), 8);
        int b = bits(hash(salt, cellX + 1, cellZ), 8);
        int c = bits(hash(salt, cellX, cellZ + 1), 8);
        int d = bits(hash(salt, cellX + 1, cellZ + 1), 8);
        int near = a * (256 - weightX) + b * weightX;
        int far = c * (256 - weightX) + d * weightX;
        return (near * (256 - weightZ) + far * weightZ) >> 16;
    }

    /** An S-shaped curve from 0 to 256 over 0 to 256: slow at both ends, fastest in the middle. */
    private static int ease(int t) {
        return t * t * (768 - 2 * t) >> 16;
    }

    /**
     * The top bits of a hash as a whole number.
     *
     * @param hash  a hash from this class
     * @param count how many bits, 1 to 31
     * @return a number from 0 to 2^count - 1, evenly spread
     */
    public static int bits(long hash, int count) {
        return (int) (hash >>> (64 - count));
    }
}
