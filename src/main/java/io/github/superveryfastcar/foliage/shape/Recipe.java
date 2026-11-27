package io.github.superveryfastcar.foliage.shape;

import io.github.superveryfastcar.foliage.season.LeafStage;
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
 *   <li><b>A twig</b>: two crossed planes with a bare twig on them, growing
 *       in one of the six directions, from one side of the block to the
 *       other.</li>
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
 *   <li>bits 16 to 18: the twig, as 1 plus the number of the direction it
 *       grows in, or 0 for none;</li>
 *   <li>bits 19 to 21: the clump's anchor, as 1 plus the number of the
 *       side it sits against, or 0 for a clump in the middle of the block;</li>
 *   <li>bits 22 to 25: the variant, a number from 0 to 15 that is fixed for
 *       the position and turns and shifts the pieces, so that neighbors do
 *       not look alike;</li>
 *   <li>bits 26 to 28: the stage of the leaf calendar the block is drawn
 *       at ({@link LeafStage}). It picks the pictures: thinned leaves in
 *       autumn, young ones in spring, a twig with or without buds;</li>
 *   <li>bits 29 and 30: free.</li>
 * </ul>
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

    /**
     * Not a recipe: the block is drawn by the model it had before Foliage
     * (a plain cube in full leaf).
     */
    public static final int NONE = -1;

    /** How many variants there are. */
    public static final int VARIANTS = 16;

    /** How many faces each kind of piece is drawn from. Planes, cards and twigs are seen from both sides. */
    public static final int FACES_PER_PLANE = 2;
    public static final int FACES_PER_CARD = 2;
    public static final int FACES_PER_TWIG = 4;

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

    /** Adds a twig that grows in the given direction to a recipe that has none yet. */
    public static int withTwig(int recipe, Direction grow) {
        return recipe | (1 + grow.ordinal()) << TWIG_SHIFT;
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

    /** The direction the twig grows in, or null for no twig. */
    @Nullable
    public static Direction twig(int recipe) {
        int code = recipe >> TWIG_SHIFT & 7;
        return code == 0 || code > DIRECTIONS.length ? null : DIRECTIONS[code - 1];
    }

    /** The position's variant, 0 to 15. */
    public static int variant(int recipe) {
        return recipe >> VARIANT_SHIFT & VARIANTS - 1;
    }

    /** How many faces the game has to draw for a recipe. */
    public static int quadCount(int recipe) {
        int count = faces(recipe) ? Integer.bitCount(open(recipe)) : 0;
        count += FACES_PER_PLANE * planeCount(recipe);
        count += FACES_PER_CARD * cards(recipe);
        count += clumpFaces(recipe);
        count += twig(recipe) != null ? FACES_PER_TWIG : 0;
        return count;
    }

    /**
     * How many of those faces stand at a slant (cards and twigs). Slanted
     * faces cost a renderer more than faces along the block's sides.
     */
    public static int slantedQuadCount(int recipe) {
        return FACES_PER_CARD * cards(recipe) + (twig(recipe) != null ? FACES_PER_TWIG : 0);
    }

    /**
     * Whether a recipe shows anything where leaves cannot be see-through
     * (the game's "fast" graphics). There a leaf picture is drawn as a
     * filled square, so only stages with most of their leaves are drawn,
     * as whole blocks; a spot with few leaves or none shows nothing.
     */
    public static boolean solid(int recipe) {
        switch (stage(recipe)) {
            case FULL:
            case THIN:
            case THINNER:
            case HALF:
                return true;
            default:
                return false;
        }
    }

    /**
     * How many faces are drawn where leaves cannot be see-through (the
     * game's "fast" graphics): the outer faces of the open sides, or none
     * at a stage with few leaves ({@link #solid}).
     */
    public static int solidQuadCount(int recipe) {
        return solid(recipe) ? Integer.bitCount(open(recipe)) : 0;
    }
}
