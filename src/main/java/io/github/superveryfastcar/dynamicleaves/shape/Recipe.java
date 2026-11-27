package io.github.superveryfastcar.dynamicleaves.shape;

import io.github.superveryfastcar.dynamicleaves.season.LeafStage;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;

/**
 * Which pieces one leaf block is drawn from, packed into one whole number.
 *
 * <h2>The pieces</h2>
 * <ul>
 *   <li><b>Outer faces</b>: the sides of the block's cube, only where a
 *       side is open.</li>
 *   <li><b>Inner planes</b>: flat leaf pictures through the middle of the
 *       block, across the x, y or z axis, seen from both sides. They give
 *       depth behind the holes of the outer faces.</li>
 *   <li><b>Cards</b>: larger planes with a round leaf picture that stand
 *       at a slant and reach out past the block.</li>
 *   <li><b>A clump</b>: a small cube of leaves. It sits against one side of
 *       the block (its anchor): the side on which the block hangs on to its
 *       tree, so that it rests on its branch, or the opposite side when a
 *       twig carries it. The clump's face on the anchor side is left out
 *       when that side is covered, because nothing could see it.</li>
 *   <li><b>A mini branch</b>: a small branch of boxes in the bark of the
 *       tree ({@link MiniBranch}), growing in one of the six directions
 *       from the side the block hangs on to: a main twig and, on a bare
 *       tree, thinner twigs that leave it. Where the block is drawn as a
 *       clump at its far end, only the main twig is drawn, as the stick
 *       that carries the clump.</li>
 * </ul>
 *
 * <h2>The packing</h2>
 * <ul>
 *   <li>bits 0 to 5: the open sides of the block, always kept, one bit per
 *       direction in the order of {@link Direction#ordinal()};</li>
 *   <li>bit 6: the outer faces of the open sides are drawn;</li>
 *   <li>bits 7 to 9: the inner planes across x, y and z;</li>
 *   <li>bits 10 and 11: the number of cards, 0 to 2;</li>
 *   <li>bits 12 to 15: the side of the clump in pixels, 0 for none;</li>
 *   <li>bits 16 to 18: the mini branch, as 1 plus the number of the
 *       direction it grows in, or 0 for none;</li>
 *   <li>bits 19 to 21: the clump's anchor, as 1 plus the number of the
 *       side it sits against, or 0 for a clump without one: in the middle
 *       of the block, or, on a spot with a mini branch, on the end of its
 *       main twig ({@link MiniBranch#tuftEnd});</li>
 *   <li>bits 22 to 25: the variant, a number from 0 to 15 that is fixed for
 *       the position and turns and shifts the pieces, so that neighbors do
 *       not look alike;</li>
 *   <li>bits 26 to 28: the stage of the leaf calendar the block is drawn
 *       at ({@link LeafStage}). It picks the pictures: thinned leaves in
 *       autumn, young ones in spring;</li>
 *   <li>bits 29 and 30: where the mini branch starts
 *       ({@link MiniBranch#FLUSH}, {@link MiniBranch#REACH} or
 *       {@link MiniBranch#DEEP}).</li>
 * </ul>
 *
 * <p>How many thinner twigs a mini branch has is not part of the recipe:
 * it is one of the player's numbers ({@link ShapeSettings#twigForks}),
 * like the size of a card. That is why the counts below ask for it.</p>
 *
 * <p>Two leaf blocks with the same recipe are drawn from the very same
 * faces. The model builds the faces of a recipe once and keeps them.</p>
 *
 * <p>A recipe is never negative. {@link #NONE} stands for "this block is
 * not drawn from a recipe".</p>
 */
public final class Recipe {

    /** The bits of the open sides. */
    public static final int OPEN_MASK = 0x3F;

    /** The outer faces of the open sides are drawn. */
    public static final int FACES_BIT = 1 << 6;

    /** The inner plane across the x axis (it faces west and east). */
    public static final int PLANE_X = 1 << 7;

    /** The inner plane across the y axis (it faces down and up). */
    public static final int PLANE_Y = 1 << 8;

    /** The inner plane across the z axis (it faces north and south). */
    public static final int PLANE_Z = 1 << 9;

    private static final int CARDS_SHIFT = 10;
    private static final int CLUMP_SHIFT = 12;
    private static final int TWIG_SHIFT = 16;
    private static final int ANCHOR_SHIFT = 19;
    private static final int VARIANT_SHIFT = 22;
    private static final int STAGE_SHIFT = 26;
    private static final int BASE_SHIFT = 29;

    /**
     * Not a recipe: the block is drawn by the model it had before Dynamic Leaves
     * (a plain cube in full leaf).
     */
    public static final int NONE = -1;

    /** How many variants there are. */
    public static final int VARIANTS = 16;

    /** How many faces each kind of flat piece is drawn from. Planes and cards are seen from both sides. */
    public static final int FACES_PER_PLANE = 2;
    public static final int FACES_PER_CARD = 2;

    /** How many faces the stick is drawn from that carries a clump at the far end of its block. */
    public static final int FACES_PER_STEM = MiniBranch.BARE_STEM_FACES;

    private static final Direction[] DIRECTIONS = Direction.values();

    private Recipe() {
    }

    /**
     * The start of a recipe: the open sides and the variant, no pieces yet.
     * A block drawn from this alone shows nothing.
     *
     * @param open    the open sides, one bit per direction
     * @param variant the position's variant, 0 to 15
     */
    public static int start(int open, int variant) {
        return (open & OPEN_MASK) | (variant & VARIANTS - 1) << VARIANT_SHIFT;
    }

    /** Sets the stage of the leaf calendar on a recipe that has none yet (which means {@link LeafStage#FULL}). */
    public static int withStage(int recipe, LeafStage stage) {
        return recipe | stage.ordinal() << STAGE_SHIFT;
    }

    /** The stage of the leaf calendar the block is drawn at. */
    public static LeafStage stage(int recipe) {
        return LeafStage.byNumber(recipe >> STAGE_SHIFT & 7);
    }

    /** Adds a number of cards (0 to 2) to a recipe that has none yet. */
    public static int withCards(int recipe, int cards) {
        return recipe | (cards & 3) << CARDS_SHIFT;
    }

    /**
     * Adds a clump to a recipe that has none yet.
     *
     * @param size   the clump's side in pixels, 1 to 15
     * @param anchor the side the clump sits against, or null for a clump
     *               in the middle of the block
     */
    public static int withClump(int recipe, int size, @Nullable Direction anchor) {
        return recipe | (size & 15) << CLUMP_SHIFT | (anchor == null ? 0 : 1 + anchor.ordinal()) << ANCHOR_SHIFT;
    }

    /**
     * Adds a mini branch that grows in the given direction and starts at
     * the block's side ({@link MiniBranch#FLUSH}) to a recipe that has
     * none yet.
     */
    public static int withTwig(int recipe, Direction grow) {
        return withTwig(recipe, grow, MiniBranch.FLUSH);
    }

    /**
     * Adds a mini branch to a recipe that has none yet.
     *
     * @param grow the direction it grows in
     * @param base where it starts: {@link MiniBranch#FLUSH},
     *             {@link MiniBranch#REACH} or {@link MiniBranch#DEEP}
     */
    public static int withTwig(int recipe, Direction grow, int base) {
        return recipe | (1 + grow.ordinal()) << TWIG_SHIFT | (base & 3) << BASE_SHIFT;
    }

    /** The open sides of the block, one bit per direction. */
    public static int open(int recipe) {
        return recipe & OPEN_MASK;
    }

    /** Whether the outer faces of the open sides are drawn. */
    public static boolean faces(int recipe) {
        return (recipe & FACES_BIT) != 0;
    }

    /** Whether one of the inner planes ({@link #PLANE_X}, {@link #PLANE_Y}, {@link #PLANE_Z}) is drawn. */
    public static boolean plane(int recipe, int planeBit) {
        return (recipe & planeBit) != 0;
    }

    /** How many inner planes are drawn. */
    public static int planeCount(int recipe) {
        return Integer.bitCount(recipe & (PLANE_X | PLANE_Y | PLANE_Z));
    }

    /** How many cards are drawn, 0 to 2. */
    public static int cards(int recipe) {
        return recipe >> CARDS_SHIFT & 3;
    }

    /** The side of the clump in pixels, or 0 for none. */
    public static int clump(int recipe) {
        return recipe >> CLUMP_SHIFT & 15;
    }

    /** The side the clump sits against, or null for a clump in the middle of the block (or no clump). */
    @Nullable
    public static Direction anchor(int recipe) {
        int code = recipe >> ANCHOR_SHIFT & 7;
        return code == 0 || code > DIRECTIONS.length ? null : DIRECTIONS[code - 1];
    }

    /**
     * How many faces the clump of a recipe is drawn from: the six of a
     * cube, less the one against a covered anchor side. 0 without a clump.
     */
    public static int clumpFaces(int recipe) {
        if (clump(recipe) == 0) {
            return 0;
        }
        return clumpFaces(open(recipe), anchor(recipe));
    }

    /**
     * How many faces a clump is drawn from, given the open sides of its
     * block and the side it sits against.
     */
    public static int clumpFaces(int open, @Nullable Direction anchor) {
        return anchor != null && (open & 1 << anchor.ordinal()) == 0 ? 5 : 6;
    }

    /** The direction the mini branch grows in, or null for none. */
    @Nullable
    public static Direction twig(int recipe) {
        int code = recipe >> TWIG_SHIFT & 7;
        return code == 0 || code > DIRECTIONS.length ? null : DIRECTIONS[code - 1];
    }

    /**
     * Where the mini branch starts: {@link MiniBranch#FLUSH},
     * {@link MiniBranch#REACH} or {@link MiniBranch#DEEP}. Means nothing
     * without a mini branch.
     */
    public static int twigBase(int recipe) {
        return recipe >> BASE_SHIFT & 3;
    }

    /**
     * Whether only the main twig of the mini branch is drawn, as the
     * stick that carries a clump: the recipe has a clump that sits
     * against the side the twig grows toward.
     */
    public static boolean stemOnly(int recipe) {
        Direction grow = twig(recipe);
        return grow != null && clump(recipe) != 0 && anchor(recipe) == grow;
    }

    /**
     * Whether the clump of the recipe sits on the end of a mini branch
     * instead of against a side of the block: a clump without an anchor
     * on a spot with a mini branch (the last leaves of autumn, the buds
     * and the first leaves of spring on a spot that touches wood).
     */
    public static boolean tuft(int recipe) {
        return twig(recipe) != null && clump(recipe) != 0 && anchor(recipe) == null;
    }

    /**
     * How many thinner twigs the mini branch of a recipe is drawn with at
     * most: the number the player wants, but none while the spot still
     * has most of its leaves gone and not all ({@link LeafStage#THINNER}).
     * There the main twig alone shows through the thinned clump of
     * leaves; the thinner twigs would be buried in it and cost fifteen
     * faces for nothing.
     *
     * @param forks how many thinner twigs the player wants on a mini
     *              branch at most ({@link ShapeSettings#forks})
     */
    public static int forks(int recipe, int forks) {
        return stage(recipe) == LeafStage.THINNER ? 0 : forks;
    }

    /**
     * How many faces the mini branch of a recipe is drawn from.
     *
     * @param forks how many thinner twigs the player wants on a mini
     *              branch at most ({@link ShapeSettings#forks})
     */
    public static int twigFaces(int recipe, int forks) {
        if (twig(recipe) == null) {
            return 0;
        }
        return stemOnly(recipe) ? FACES_PER_STEM : MiniBranch.faceCount(twigBase(recipe), forks(recipe, forks));
    }

    /** The position's variant, 0 to 15. */
    public static int variant(int recipe) {
        return recipe >> VARIANT_SHIFT & VARIANTS - 1;
    }

    /**
     * How many faces the game has to draw for a recipe.
     *
     * @param forks how many thinner twigs the player wants on a mini
     *              branch at most ({@link ShapeSettings#forks})
     */
    public static int quadCount(int recipe, int forks) {
        return leafQuadCount(recipe) + twigFaces(recipe, forks);
    }

    /**
     * How many of the faces of a recipe show leaves: all but those of the
     * mini branch. This is the number the limit on faces
     * ({@link ShapeSettings#maxFaces}) is about; only the stick under a
     * clump in full leaf is counted against the limit as well
     * ({@link #FACES_PER_STEM}).
     */
    public static int leafQuadCount(int recipe) {
        int count = faces(recipe) ? Integer.bitCount(open(recipe)) : 0;
        count += FACES_PER_PLANE * planeCount(recipe);
        count += FACES_PER_CARD * cards(recipe);
        count += clumpFaces(recipe);
        return count;
    }

    /**
     * How many of the faces of a recipe stand at a slant: those of its
     * cards. Slanted faces cost a renderer more than faces along the
     * block's sides. A mini branch has none: all its boxes lie along the
     * block's sides.
     */
    public static int slantedQuadCount(int recipe) {
        return FACES_PER_CARD * cards(recipe);
    }

    /**
     * Whether a recipe is drawn as a whole block where leaves cannot be
     * see-through (the game's "fast" graphics): while the spot is in full
     * leaf or nearly so ({@link LeafStage#covers}). There a leaf picture
     * is drawn as a filled square, so a crown in leaf is a wall of whole
     * blocks, as in the unmodded game.
     */
    public static boolean solidWhole(int recipe) {
        return stage(recipe).covers();
    }

    /**
     * Whether the outer faces of a recipe are drawn where leaves cannot
     * be see-through, at a stage that is not drawn as a whole block
     * ({@link #solidWhole}): only while most of the leaves are there (the
     * stages "thinner" and "half grown"). With the last or the first few
     * leaves a filled square would be a lie.
     */
    private static boolean solidFaces(int recipe) {
        LeafStage stage = stage(recipe);
        return faces(recipe) && (stage == LeafStage.THINNER || stage == LeafStage.HALF);
    }

    /**
     * How many faces are drawn where leaves cannot be see-through (the
     * game's "fast" graphics). A spot in full leaf or nearly so is a
     * whole block: the outer faces of its open sides. After that the
     * solid pieces are drawn as they are (the outer faces while most
     * leaves are there, the clump, the mini branch), and the flat
     * pictures (planes and cards) are left out, because a picture that is
     * not see-through is a filled square.
     *
     * @param forks how many thinner twigs the player wants on a mini
     *              branch at most ({@link ShapeSettings#forks})
     */
    public static int solidQuadCount(int recipe, int forks) {
        if (solidWhole(recipe)) {
            return Integer.bitCount(open(recipe));
        }
        return (solidFaces(recipe) ? Integer.bitCount(open(recipe)) : 0) + clumpFaces(recipe) + twigFaces(recipe, forks);
    }

    /** Whether the outer faces are among the pieces drawn where leaves cannot be see-through; see {@link #solidQuadCount}. */
    public static boolean solidOuter(int recipe) {
        return solidWhole(recipe) || solidFaces(recipe);
    }
}
