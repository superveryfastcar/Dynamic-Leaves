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
