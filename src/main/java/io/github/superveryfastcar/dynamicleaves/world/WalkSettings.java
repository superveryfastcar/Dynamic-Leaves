package io.github.superveryfastcar.dynamicleaves.world;

/**
 * The settings of {@link LeafWalker}: whether leaf spots that go bare
 * really hold a bare block, and how much of that work the server does per
 * tick. Kept in {@code config/dynamicleaves-common.toml}, section
 * {@code bareBranches}. Only the server reads them.
 *
 * @param enabled        the setting {@code bareBlocks}. false: no leaf
 *                       block is ever swapped, and bare
 *                       blocks already in the world are turned back into
 *                       leaves. The trees then only look bare.
 * @param chunksPerTick  the most chunks looked at in one tick
 * @param changesPerTick the most blocks swapped in one tick
 * @param millisPerTick  the most time spent in one tick, in thousandths
 *                       of a second
 */
public record WalkSettings(boolean enabled, int chunksPerTick, int changesPerTick, double millisPerTick) {

    /** The limits of the numbers, for the settings file. */
    public static final int MIN_CHUNKS = 1;
    public static final int MAX_CHUNKS = 4096;
    public static final int MIN_CHANGES = 1;
    public static final int MAX_CHANGES = 65536;
    public static final double MIN_MILLIS = 0.05;
    public static final double MAX_MILLIS = 50.0;

    /**
     * The values the mod ships with. A tick lasts 50 thousandths of a
     * second; the walker may take one of them. 300 swaps a tick are 6000 a
     * second: a forest in view is brought up to date within seconds, the
     * trees nearest to the players first.
     */
    public static final WalkSettings DEFAULT = new WalkSettings(true, 64, 300, 1.0);

    /** The time limit of one tick in billionths of a second. */
    public long nanosPerTick() {
        return (long) (millisPerTick * 1_000_000.0);
    }
}
