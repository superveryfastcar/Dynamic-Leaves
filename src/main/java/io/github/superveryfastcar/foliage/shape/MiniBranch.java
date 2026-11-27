package io.github.superveryfastcar.foliage.shape;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;

/**
 * The small branch that stands in a leaf spot of a bare tree: one main
 * twig that comes out of the wood, and up to three thinner twigs that
 * leave it in different directions. Plain arithmetic, no drawing: this
 * class says where the boxes are, and
 * {@code client.model.QuadMaker} turns them into faces.
 *
 * <h2>Made like the branches of Dynamic Trees</h2>
 * A branch of Dynamic Trees is a square stick along one of the three axes,
 * in the bark of its tree. A mini branch is made the same way, only
 * smaller: every part of it is a box along an axis, and it is drawn in the
 * bark of the tree it stands on. The thinnest branch of Dynamic Trees is
 * two pixels thick, and so is the main twig, so that it reads as the same
 * branch going on into the leaf spot. The twigs that leave it are one
 * pixel thick.
 *
 * <h2>The measures</h2>
 * All measures are in pixels of a block (16 from one side to the other),
 * in the mini branch's own frame: it grows upward (toward higher y) from
 * the middle of the block's bottom face. Whoever draws it turns the whole
 * thing so that "up" becomes the direction the twig really grows in.
 *
 * <h2>Where it starts</h2>
 * <ul>
 *   <li>{@link #FLUSH}: at the block's own side. A thin branch of Dynamic
 *       Trees reaches the side of a leaf block with a stub of its own, and
 *       the main twig goes on from the end of that stub.</li>
 *   <li>{@link #REACH}: seven pixels outside the block, inside the wood. A
 *       thick branch has no stub toward its leaves, and its bark lies up
 *       to six pixels behind the block's side. The main twig crosses that
 *       gap and ends hidden in the wood.</li>
 *   <li>{@link #DEEP}: for a leaf spot that touches no wood but hangs on
 *       to another leaf spot (the "more twigs" setting). Its main twig is
 *       one pixel thick and starts in the middle of the spot it hangs on
 *       to, where that spot's own main twig passes, so the two join. It
 *       ends half a pixel past the middle of its own block, exactly where
 *       a twig of the next spot would begin.</li>
 * </ul>
 *
 * <h2>Shorter when it stands upright</h2>
 * A mini branch that grows straight up or straight down is the short
 * kind ({@code upright}). The owner's rule (2026-11-27): nothing on the
 * tree goes straight up for more than about half to two thirds of a
 * block. What is seen standing on a limb is the limb's own stub (from its
 * bark to the block's side, seven pixels on the thinnest limb) and then
 * the main twig, so an upright main twig goes only one to four pixels
 * into its block: eight to ten pixels of upright wood in all. Its
 * thinner twigs are spread over that whole height, so some of them leave
 * the stub below the block's side (a height below zero). A level mini
 * branch keeps its full length. The {@link #DEEP} kind is the same both
 * ways: it is the link of a chain that the "more twigs" setting asks for.
 *
 * <h2>Never in the same plane as a leaf</h2>
 * A leaf spot with a mini branch can carry a small cube of leaves at the
 * same time (the last leaves of autumn, the buds and first leaves of
 * spring). Two faces that lie in the same plane flicker. The cubes have
 * their faces on whole and half pixels, so everything here that could meet
 * one (how long the twigs are and how high up they leave the main twig)
 * sits a quarter of a pixel off. Nobody can see a quarter of a pixel, and
 * no two faces ever share a plane.
 */
public final class MiniBranch {

    /** The main twig starts at the block's side, on the end of a stub of the branch. */
    public static final int FLUSH = 0;

    /** The main twig starts inside a thick branch, {@link #REACH_BACK} pixels outside the block. */
    public static final int REACH = 1;

    /** A thinner main twig that starts in the middle of the leaf spot this one hangs on to. */
    public static final int DEEP = 2;

    /** The most thinner twigs that can leave a main twig. */
    public static final int MAX_FORKS = 3;

    /** The most thinner twigs on a {@link #DEEP} mini branch, which is the thinner and shorter kind. */
    public static final int DEEP_FORKS = 2;

    /** How far outside the block a {@link #REACH} twig starts: one pixel short of the middle of the wood. */
    public static final float REACH_BACK = 7.0F;

    /** How far outside the block a {@link #DEEP} twig starts, and how far into its own block it goes. */
    public static final float DEEP_BACK = 7.5F;
    public static final float DEEP_END = 8.5F;

    /** The quarter of a pixel that keeps twigs out of the planes of leaf cubes. */
    private static final float OFF = 0.25F;

    /** How many faces a main twig is drawn from: its four sides and its far end. The near end is in the wood. */
    public static final int STEM_FACES = 5;

    /**
     * How many faces a thinner twig is drawn from: its four sides. Its far
     * end is a single pixel, which nobody sees from where a player can
     * stand, and leaving it out saves three faces on every mini branch.
     */
    public static final int FORK_FACES = 4;

    /** How many faces a main twig is drawn from when its far end is hidden in a clump of leaves. */
    public static final int BARE_STEM_FACES = 4;

    /**
     * One box of a mini branch.
     *
     * @param x0    the box reaches from here ...
     * @param y0    ...
     * @param z0    ...
     * @param x1    ... to here, in pixels of the block
     * @param y1    ...
     * @param z1    ...
     * @param faces which of its six faces are drawn, one bit per direction
     *              in the order of {@link Direction#ordinal()}; the face
     *              where the box joins what it grows from is never drawn,
     *              and neither is the far end of a thinner twig
     * @param along the axis the box is long in (0 = x, 1 = y, 2 = z); the
     *              grain of the bark runs this way
     * @param strip which strip of the bark picture the box shows, so that
     *              the twigs of one mini branch do not all show the same
     *              pixels
     */
    public record Box(float x0, float y0, float z0, float x1, float y1, float z1, int faces, int along, int strip) {

        /** Whether the face toward a direction (in the mini branch's own frame) is drawn. */
        public boolean has(Direction side) {
            return (faces & 1 << side.ordinal()) != 0;
        }
    }

    /**
     * The four ways the thinner twigs are set on a main twig. Each row is
     * one way: the length of the main twig, then for each of the three
     * thinner twigs how high up it leaves (in whole pixels above the
     * block's side), which way it points (0 to 3, a quarter turn apart),
     * how long it is, and on which half of the main twig's side it sits.
     * The position's variant picks the row and turns the whole thing, so
     * there are sixteen different mini branches.
     */
    private static final int[][] WAYS = {
        {11, /* first */ 3, 0, 4, 0, /* second */ 6, 2, 5, 1, /* third */ 9, 1, 3, 0},
        {10, /* first */ 2, 1, 3, 1, /* second */ 5, 3, 5, 0, /* third */ 8, 0, 4, 1},
        {12, /* first */ 4, 2, 5, 0, /* second */ 7, 0, 3, 1, /* third */ 10, 3, 4, 1},
        {9, /* first */ 2, 3, 4, 1, /* second */ 5, 1, 4, 0, /* third */ 7, 2, 5, 0},
    };

    /**
     * The same for a mini branch that stands upright on a thin limb
     * ({@link #FLUSH}, growing up or down): a main twig of one to three
     * pixels on top of the limb's stub of seven, and thinner twigs of
     * three or four pixels. A height below zero is on the stub, outside
     * the block; the stub is as thick as the main twig, so the thinner
     * twig sits on it just the same. The lowest is two pixels clear of
     * the limb's bark.
     */
    private static final int[][] UPRIGHT_FLUSH_WAYS = {
        {2, /* first */ -5, 0, 3, 0, /* second */ -2, 2, 4, 1, /* third */ 0, 1, 3, 0},
        {1, /* first */ -5, 1, 3, 1, /* second */ -3, 3, 4, 0, /* third */ -1, 0, 3, 1},
        {3, /* first */ -4, 2, 4, 0, /* second */ -1, 0, 3, 1, /* third */ 1, 3, 3, 1},
        {2, /* first */ -4, 3, 3, 1, /* second */ -2, 1, 4, 0, /* third */ 0, 2, 3, 0},
    };

    /**
     * And for one that stands upright on a thicker limb ({@link #REACH}).
     * The limb's bark lies one to six pixels behind the block's side, so
     * the main twig goes a pixel further in, and the thinner twigs leave
     * it no lower than three pixels behind the side, where a limb up to
     * ten pixels thick leaves them in view.
     */
    private static final int[][] UPRIGHT_REACH_WAYS = {
        {3, /* first */ -3, 0, 3, 0, /* second */ -1, 2, 4, 1, /* third */ 1, 1, 3, 0},
        {2, /* first */ -3, 1, 3, 1, /* second */ -1, 3, 4, 0, /* third */ 0, 0, 3, 1},
        {4, /* first */ -3, 2, 4, 0, /* second */ 0, 0, 3, 1, /* third */ 2, 3, 3, 1},
        {3, /* first */ -2, 3, 3, 1, /* second */ 0, 1, 4, 0, /* third */ 1, 2, 3, 0},
    };

    /** The same for the thinner, shorter {@link #DEEP} kind: two twigs, and no "half" because the main twig is one pixel thick. */
    private static final int[][] DEEP_WAYS = {
        {/* first */ 2, 0, 3, /* second */ 5, 2, 4},
        {/* first */ 1, 1, 4, /* second */ 6, 3, 3},
        {/* first */ 3, 2, 3, /* second */ 6, 1, 4},
        {/* first */ 2, 3, 4, /* second */ 5, 0, 3},
    };

    private static final int ALL_BUT_DOWN = 0x3F & ~(1 << Direction.DOWN.ordinal());
    private static final int SIDES_ONLY = ALL_BUT_DOWN & ~(1 << Direction.UP.ordinal());

    /** The four long sides of a thinner twig that points along x, and of one that points along z. */
    private static final int ALONG_X = 0x3F & ~(1 << Direction.WEST.ordinal() | 1 << Direction.EAST.ordinal());
    private static final int ALONG_Z = 0x3F & ~(1 << Direction.NORTH.ordinal() | 1 << Direction.SOUTH.ordinal());

    private MiniBranch() {
    }

    /**
     * How many thinner twigs a mini branch gets.
     *
     * @param base   where it starts ({@link #FLUSH}, {@link #REACH} or {@link #DEEP})
     * @param wanted how many the player wants at most, 0 to {@link #MAX_FORKS}
     */
    public static int forks(int base, int wanted) {
        return Math.max(0, Math.min(wanted, base == DEEP ? DEEP_FORKS : MAX_FORKS));
    }

    /**
     * How many faces a mini branch is drawn from.
     *
     * @param base   where it starts
     * @param wanted how many thinner twigs the player wants at most
     */
    public static int faceCount(int base, int wanted) {
        return STEM_FACES + FORK_FACES * forks(base, wanted);
    }

    /**
     * Whether a mini branch that grows in a direction is the short,
     * upright kind: one that grows straight up or straight down.
     *
     * @param grow the direction the main twig grows in
     */
    public static boolean upright(Direction grow) {
        return grow.getAxis().isVertical();
    }

    /**
     * The table a mini branch that starts at the wood is built from.
     *
     * @param base    {@link #FLUSH} or {@link #REACH}
     * @param upright whether it grows straight up or down
     */
    private static int[][] ways(int base, boolean upright) {
        if (!upright) {
            return WAYS;
        }
        return base == REACH ? UPRIGHT_REACH_WAYS : UPRIGHT_FLUSH_WAYS;
    }

    /**
     * How far from the block's side the main twig ends, in pixels.
     *
     * @param base    where it starts
     * @param variant the position's variant, 0 to 15
     * @param upright whether it grows straight up or down ({@link #upright})
     */
    public static float length(int base, int variant, boolean upright) {
        return base == DEEP ? DEEP_END : ways(base, upright)[variant >> 2 & 3][0] + OFF;
    }

    /**
     * Where the far face of a small cube of leaves lies that sits on a
     * mini branch (the last leaves, the buds, the first leaves): one pixel
     * past the end of the main twig, on a whole pixel, and never outside
     * the block. The cube then holds the end of the main twig like a spray
     * of leaves at the end of a twig.
     *
     * @param base    where the mini branch starts
     * @param variant the position's variant, 0 to 15
     * @param size    the side of the cube in pixels
     * @param upright whether the mini branch grows straight up or down
     * @return how far the cube's far face is from the block's side the
     *         twig grows from, at least {@code size} and at most 16
     */
    public static int tuftEnd(int base, int variant, int size, boolean upright) {
        int end = Math.round(length(base, variant, upright)) + 1;
        return Math.max(size, Math.min(16, end));
    }

    /**
     * The boxes of a whole mini branch: the main twig first, then the
     * thinner ones.
     *
     * @param base    where it starts
     * @param variant the position's variant, 0 to 15: it picks one of the
     *                four ways the twigs are set and one of four quarter
     *                turns
     * @param wanted  how many thinner twigs the player wants at most
     * @param upright whether it grows straight up or down: the short kind
     */
    public static List<Box> boxes(int base, int variant, int wanted, boolean upright) {
        int turn = variant & 3;
        int way = variant >> 2 & 3;
        int count = forks(base, wanted);
        List<Box> boxes = new ArrayList<>(1 + count);
        if (base == DEEP) {
            boxes.add(new Box(7.5F, -DEEP_BACK, 7.5F, 8.5F, DEEP_END, 8.5F, ALL_BUT_DOWN, 1, 0));
            int[] set = DEEP_WAYS[way];
            for (int i = 0; i < count; i++) {
                boxes.add(fork(7.5F, 8.5F, 7.5F, set[i * 3] + OFF, set[i * 3 + 1] + turn & 3, set[i * 3 + 2] + OFF, 1 + i));
            }
            return boxes;
        }
        float from = base == REACH ? -REACH_BACK : 0.0F;
        int[] set = ways(base, upright)[way];
        boxes.add(new Box(7.0F, from, 7.0F, 9.0F, set[0] + OFF, 9.0F, ALL_BUT_DOWN, 1, 0));
        for (int i = 0; i < count; i++) {
            int at = 1 + i * 4;
            // The thinner twig is one pixel wide and the side it leaves is two: it sits on one half of it.
            float low = 7.0F + set[at + 3];
            boxes.add(fork(7.0F, 9.0F, low, set[at] + OFF, set[at + 1] + turn & 3, set[at + 2] + OFF, 1 + i));
        }
        return boxes;
    }

    /**
     * The main twig alone, for a leaf spot that is drawn as a clump of
     * leaves at the far end of the block: a plain stick from the wood
     * into the clump. Its far end is inside the clump and is not drawn.
     *
     * @param base   where it starts ({@link #FLUSH} or {@link #REACH})
     * @param length how far from the block's side it ends, in pixels
     */
    public static Box stem(int base, float length) {
        float from = base == REACH ? -REACH_BACK : 0.0F;
        return new Box(7.0F, from, 7.0F, 9.0F, length, 9.0F, SIDES_ONLY, 1, 0);
    }

    /**
     * One thinner twig: a box one pixel thick that leaves a side of the
     * main twig at a right angle. Only its four long sides are drawn
     * ({@link #FORK_FACES}).
     *
     * @param near   where the main twig's sides are: its low side ...
     * @param far    ... and its high side, across both x and z
     * @param low    where the twig's own pixel starts, across the side it leaves
     * @param height how high up the main twig it leaves (below zero: on
     *               the limb's stub, outside the block)
     * @param way    which way it points: 0 toward higher x, 1 toward
     *               higher z, 2 toward lower x, 3 toward lower z
     * @param length how long it is
     * @param strip  which strip of the bark picture it shows
     */
    private static Box fork(float near, float far, float low, float height, int way, float length, int strip) {
        float top = height + 1.0F;
        float high = low + 1.0F;
        switch (way) {
            case 0:
                return new Box(far, height, low, far + length, top, high, ALONG_X, 0, strip);
            case 1:
                return new Box(low, height, far, high, top, far + length, ALONG_Z, 2, strip);
            case 2:
                return new Box(near - length, height, low, near, top, high, ALONG_X, 0, strip);
            case 3:
            default:
                return new Box(low, height, near - length, high, top, near, ALONG_Z, 2, strip);
        }
    }
}
