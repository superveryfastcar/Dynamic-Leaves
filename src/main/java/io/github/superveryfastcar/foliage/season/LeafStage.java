package io.github.superveryfastcar.foliage.season;

/**
 * What a leaf spot of a tree that sheds its leaves shows at one moment of
 * the year. A spot goes through the stages in this order, once around per
 * year:
 *
 * <pre>
 * summer   FULL
 * autumn   FULL, THIN, THINNER, LAST, BARE
 * winter   BARE
 * spring   BARE, BUDS, FIRST, HALF, FULL
 * </pre>
 *
 * <p>The stage is worked out from the date and the place by
 * {@link LeafClock}, the same on the server and in every player's game.
 * How a stage is drawn is the business of the client
 * ({@code shape.ShapeRules}).</p>
 */
public enum LeafStage {

    /** In full leaf. */
    FULL,

    /** About a third of the leaves have fallen. */
    THIN,

    /** Most of the leaves have fallen: what is left has drawn back toward the branch. */
    THINNER,

    /** The last few leaves hang on. */
    LAST,

    /** No leaves. Where a twig is drawn, it is bare. */
    BARE,

    /** No leaves yet, but the tree is about to sprout: twigs carry buds. */
    BUDS,

    /** The first small leaves are out. */
    FIRST,

    /** The leaves are half grown. */
    HALF;

    private static final LeafStage[] VALUES = values();

    /** The stage with a given number ({@link #ordinal()}); FULL for a number that is none. */
    public static LeafStage byNumber(int number) {
        return number >= 0 && number < VALUES.length ? VALUES[number] : FULL;
    }

    /**
     * Whether a leaf block at this stage hides what lies behind it well
     * enough that a neighboring leaf block need not draw its face toward
     * it: only in full leaf or nearly so.
     */
    public boolean covers() {
        return this == FULL || this == THIN;
    }

    /**
     * Whether there are no leaves at all at this stage: nothing but a twig
     * (bare or with buds), or nothing.
     */
    public boolean leafless() {
        return this == BARE || this == BUDS;
    }

    /**
     * Whether a spot at this stage holds the bare block instead of the
     * leaf block ({@code block.BareLeavesBlock}): from the last few leaves
     * of autumn to the first small leaves of spring. A spot with a good
     * part of its leaves holds its leaf block.
     *
     * <p>The last leaves and the first ones go with the bare block so
     * that they get the light of the open sky and stop nobody: a leaf
     * block dims the light for everything behind it, however few leaves
     * are drawn on it.</p>
     */
    public boolean bareBlock() {
        return this == LAST || this == BARE || this == BUDS || this == FIRST;
    }
}
