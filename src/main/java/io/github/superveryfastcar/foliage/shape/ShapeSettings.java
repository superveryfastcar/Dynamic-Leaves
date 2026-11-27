package io.github.superveryfastcar.foliage.shape;

/**
 * The numbers the leaf shapes are built from. One unchanging value: the
 * settings in use are swapped as a whole when the player changes them, so
 * the threads that build chunks can read them at any time.
 *
 * <p>Sizes are in pixels of a block (a block is 16 wide).</p>
 *
 * @param shape      the shape of the crowns
 * @param fastLeaves true: outer faces and one inner plane only, whatever
 *                   the shape. For weak computers.
 * @param maxFaces   the most faces the leaves of one leaf block may be drawn
 *                   from. The unmodded game draws 6 for every leaf block;
 *                   with 6 here the leaves of no block ever cost more than
 *                   that. Higher values allow richer tips (a second card, a
 *                   stick under a clump). The mini branch of a bare tree is
 *                   not leaves and is counted apart ({@link #twigForks}).
 * @param tipSides   from how many open sides (of 6) a leaf block counts as
 *                   a tip of the crown: bushy gives it a second card, tufts
 *                   turns it into a clump
 * @param clumpSides from how many open sides a leaf block counts as an
 *                   outermost tip: mixed turns it into a clump on a twig,
 *                   tufts puts its clump on a twig
 * @param cardWidth  how wide a leaf card is
 * @param cardHeight how tall a leaf card is
 * @param tuftSize   the side of a clump at an ordinary tip (tufts)
 * @param tipSize    the side of a clump at an outermost tip (tufts)
 * @param clumpSize  the side of a clump at an outermost tip (mixed)
 * @param winterTwigs on which leaf spots of a bare tree a mini branch is drawn
 * @param twigDepth  with {@link WinterTwigs#MORE}: from which supply number
 *                   of Dynamic Trees (1 at the far edge of a crown, higher
 *                   toward the twig) a leaf spot that touches no branch
 *                   gets a twig as well
 * @param firstSize  the side of the clump of first leaves in spring
 * @param halfSize   the side of the clump of half-grown leaves in spring
 * @param twigForks  how many thinner twigs leave the main twig of a mini
 *                   branch, 0 to 3. Each costs four faces on every leaf
 *                   spot that has a mini branch.
 * @param budSize    the side of the bud at the end of a mini branch in the
 *                   days before the first leaves; 0 for no buds
 * @param fallSize   the side of the clump a leaf block shrinks to in autumn
 *                   when most of its leaves are gone
 * @param lastSize   the side of the clump of last leaves in autumn
 */
public record ShapeSettings(LeafShape shape, boolean fastLeaves, int maxFaces, int tipSides, int clumpSides,
                            int cardWidth, int cardHeight, int tuftSize, int tipSize, int clumpSize,
                            WinterTwigs winterTwigs, int twigDepth, int firstSize, int halfSize,
                            int twigForks, int budSize, int fallSize, int lastSize) {

    /**
     * The smallest and largest allowed number of faces per leaf block. A
     * leaf block that stands alone needs its six outer faces, so the limit
     * cannot be lower than 6. The richest recipe has 12.
     */
    public static final int MIN_FACES = 6;
    public static final int MAX_FACES = 12;

    /** The smallest and largest side of a clump. */
    public static final int MIN_CLUMP = 4;
    public static final int MAX_CLUMP = 14;

    /** The smallest and largest size of a card. */
    public static final int MIN_CARD = 16;
    public static final int MAX_CARD = 32;

    /** The smallest and largest supply number of Dynamic Trees. */
    public static final int MIN_DEPTH = 1;
    public static final int MAX_DEPTH = 7;

    /**
     * The smallest and largest side of a bud. A bud two pixels wide would
     * have its sides in the planes of the main twig's sides, and the two
     * would flicker; so the smallest is 3.
     */
    public static final int MIN_BUD = 3;
    public static final int MAX_BUD = 8;

    /**
     * The defaults: mixed crowns; at most 6 faces per leaf block; tips from
     * 3 open sides and outermost tips from 4; cards 30 wide and 26 tall;
     * clumps of 12, 8 and 10; in winter a mini branch with three thinner
     * twigs on every leaf spot that touches a branch (with "more twigs":
     * also from supply number 3); buds of 4; in spring clumps of 8, then
     * 12; in autumn clumps of 12, then 8.
     */
    public static final ShapeSettings DEFAULT = new ShapeSettings(LeafShape.MIXED, false, 6, 3, 4, 30, 26, 12, 8, 10,
            WinterTwigs.NEAR, 3, 8, 12, MiniBranch.MAX_FORKS, 4, 12, 8);

    /** Forces every number into its allowed range. */
    public ShapeSettings {
        if (shape == null) {
            shape = LeafShape.MIXED;
        }
        maxFaces = clamp(maxFaces, MIN_FACES, MAX_FACES);
        tipSides = clamp(tipSides, 1, 6);
        clumpSides = clamp(clumpSides, tipSides, 6);
        cardWidth = clamp(cardWidth, MIN_CARD, MAX_CARD);
        cardHeight = clamp(cardHeight, MIN_CARD, MAX_CARD);
        tuftSize = clamp(tuftSize, MIN_CLUMP, MAX_CLUMP);
        tipSize = clamp(tipSize, MIN_CLUMP, MAX_CLUMP);
        clumpSize = clamp(clumpSize, MIN_CLUMP, MAX_CLUMP);
        if (winterTwigs == null) {
            winterTwigs = WinterTwigs.NEAR;
        }
        twigDepth = clamp(twigDepth, MIN_DEPTH, MAX_DEPTH);
        firstSize = clamp(firstSize, MIN_CLUMP, MAX_CLUMP);
        halfSize = clamp(halfSize, firstSize, MAX_CLUMP);
        twigForks = clamp(twigForks, 0, MiniBranch.MAX_FORKS);
        budSize = budSize <= 0 ? 0 : clamp(budSize, MIN_BUD, MAX_BUD);
        lastSize = clamp(lastSize, MIN_CLUMP, MAX_CLUMP);
        fallSize = clamp(fallSize, lastSize, MAX_CLUMP);
    }

    /**
     * How many thinner twigs a mini branch is drawn with: the number the
     * player chose, or none with fast leaves, which are for the fewest
     * faces whatever else is set.
     */
    public int forks() {
        return fastLeaves ? 0 : twigForks;
    }

    /** The same settings with another shape. */
    public ShapeSettings withShape(LeafShape newShape) {
        return new ShapeSettings(newShape, fastLeaves, maxFaces, tipSides, clumpSides, cardWidth, cardHeight,
                tuftSize, tipSize, clumpSize, winterTwigs, twigDepth, firstSize, halfSize, twigForks, budSize, fallSize, lastSize);
    }

    /** The same settings with fast leaves switched on or off. */
    public ShapeSettings withFastLeaves(boolean fast) {
        return new ShapeSettings(shape, fast, maxFaces, tipSides, clumpSides, cardWidth, cardHeight,
                tuftSize, tipSize, clumpSize, winterTwigs, twigDepth, firstSize, halfSize, twigForks, budSize, fallSize, lastSize);
    }

    /** The same settings with another limit on the faces per leaf block. */
    public ShapeSettings withMaxFaces(int faces) {
        return new ShapeSettings(shape, fastLeaves, faces, tipSides, clumpSides, cardWidth, cardHeight,
                tuftSize, tipSize, clumpSize, winterTwigs, twigDepth, firstSize, halfSize, twigForks, budSize, fallSize, lastSize);
    }

    /** The same settings with another number of thinner twigs on a mini branch. */
    public ShapeSettings withTwigForks(int forks) {
        return new ShapeSettings(shape, fastLeaves, maxFaces, tipSides, clumpSides, cardWidth, cardHeight,
                tuftSize, tipSize, clumpSize, winterTwigs, twigDepth, firstSize, halfSize, forks, budSize, fallSize, lastSize);
    }

    /** The same settings with another size of bud; 0 for no buds. */
    public ShapeSettings withBudSize(int size) {
        return new ShapeSettings(shape, fastLeaves, maxFaces, tipSides, clumpSides, cardWidth, cardHeight,
                tuftSize, tipSize, clumpSize, winterTwigs, twigDepth, firstSize, halfSize, twigForks, size, fallSize, lastSize);
    }

    /** The same settings with other twigs on bare trees. */
    public ShapeSettings withWinterTwigs(WinterTwigs twigs) {
        return new ShapeSettings(shape, fastLeaves, maxFaces, tipSides, clumpSides, cardWidth, cardHeight,
                tuftSize, tipSize, clumpSize, twigs, twigDepth, firstSize, halfSize, twigForks, budSize, fallSize, lastSize);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
