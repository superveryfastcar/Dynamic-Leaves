package io.github.superveryfastcar.foliage.shape;

import com.dtteam.dynamictrees.tree.TreeHelper;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What stands around a leaf block: which of its six sides are open, where
 * the nearest wood is, where the heart of its leaf cluster lies, and
 * whether something rests on top of it.
 *
 * <p>The answer is packed into one whole number, so that reading it for
 * every leaf block of a chunk makes no garbage:</p>
 * <ul>
 *   <li>bits 0 to 5: the open sides, one bit per direction in the order of
 *       {@link Direction#ordinal()} (down, up, north, south, west, east);</li>
 *   <li>bits 6 to 8: the side on which a branch touches the block, as 1 plus
 *       the direction's number, or 0 for none;</li>
 *   <li>bits 9 to 11: the side toward the heart of the leaf cluster (a leaf
 *       block that sits deeper in the crown), packed the same way;</li>
 *   <li>bit 12: something that is not a leaf and not a full block rests on
 *       top, for example a layer of snow;</li>
 *   <li>bit 13: the block is sheltered: each of its six neighbors is a
 *       leaf block or a full, solid block, so it sits inside its crown
 *       whatever the season.</li>
 * </ul>
 *
 * <h2>Open and covered</h2>
 * A side is covered when the neighbor there is a leaf block or a full,
 * solid block. Then nothing of this block can be seen from that side, and
 * no face is drawn toward it. Every other side is open.
 *
 * <p>While leaves fall or come back, a neighboring leaf block may show few
 * leaves or none. The caller then passes a {@link LeafCover} that knows
 * the neighbor's look, and a side toward a leaf block that does not cover
 * counts as open.</p>
 *
 * <h2>Deeper in the crown</h2>
 * Dynamic Trees keeps for every leaf block how well it is supplied from
 * the twig (it calls this hydration and stores it in the block's
 * {@code distance} number): high next to the twig, 1 at the edge of the
 * crown. A neighbor with a higher number therefore lies toward the inside.
 */
public final class Surround {

    /** The bits of the open sides. */
    public static final int OPEN_MASK = 0x3F;

    private static final int WOOD_SHIFT = 6;
    private static final int HEART_SHIFT = 9;
    private static final int LOADED_BIT = 1 << 12;
    private static final int SHELTERED_BIT = 1 << 13;

    private static final Direction[] DIRECTIONS = Direction.values();

    /**
     * Says whether a neighboring leaf block hides what lies behind it.
     * Asked once per leaf block next to the one that is being read.
     */
    @FunctionalInterface
    public interface LeafCover {

        /**
         * @param state the neighboring leaf block
         * @param x     its position
         * @param y     its position
         * @param z     its position
         * @return true if nothing can be seen through it
         */
        boolean covers(BlockState state, int x, int y, int z);
    }

    /** For the plain look of the unmodded game, where leaf blocks draw their faces toward each other: no leaf block covers. */
    public static final LeafCover NO_LEAF_COVERS = (state, x, y, z) -> false;

    private Surround() {
    }

    /**
     * Looks at the six neighbors of a leaf block.
     *
     * @param level the blocks around (a chunk being built, or a world)
     * @param pos   the leaf block
     * @param state the leaf block's state
     * @return the packed surroundings, see the class notes
     */
    public static int read(BlockGetter level, BlockPos pos, BlockState state) {
        return read(level, pos, state, new BlockPos.MutableBlockPos());
    }

    /**
     * Looks at the six neighbors of a leaf block, using a position holder
     * the caller keeps. A caller that reads many leaf blocks in a row (the
     * leaf model, once per leaf block of a chunk) passes the same holder
     * every time, so reading makes no garbage at all.
     *
     * @param level the blocks around (a chunk being built, or a world)
     * @param pos   the leaf block
     * @param state the leaf block's state
     * @param near  a position holder to work with; what it held is lost
     * @return the packed surroundings, see the class notes
     */
    public static int read(BlockGetter level, BlockPos pos, BlockState state, BlockPos.MutableBlockPos near) {
        return read(level, pos, state, near, null);
    }

    /**
     * Looks at the six neighbors of a leaf block at a time of the year
     * when leaf blocks may show few leaves or none.
     *
     * @param level the blocks around (a chunk being built, or a world)
     * @param pos   the leaf block
     * @param state the leaf block's state
     * @param near  a position holder to work with; what it held is lost
     * @param cover says for each neighboring leaf block whether it covers
     *              the side toward it; null if all leaf blocks do
     * @return the packed surroundings, see the class notes
     */
    public static int read(BlockGetter level, BlockPos pos, BlockState state, BlockPos.MutableBlockPos near,
                           @Nullable LeafCover cover) {
        int ownDepth = depth(state);
        int deepest = ownDepth;
        int open = 0;
        int wood = 0;
        int heart = 0;
        boolean loaded = false;
        boolean sheltered = true;
        for (Direction direction : DIRECTIONS) {
            near.setWithOffset(pos, direction);
            BlockState other = level.getBlockState(near);
            boolean covered;
            if (other.getBlock() instanceof LeavesBlock) {
                covered = cover == null || cover.covers(other, near.getX(), near.getY(), near.getZ());
                int depth = depth(other);
                if (depth > deepest) {
                    deepest = depth;
                    heart = 1 + direction.ordinal();
                }
            } else {
                covered = other.isSolidRender(level, near);
                sheltered &= covered;
                if (wood == 0 && TreeHelper.isBranch(other)) {
                    wood = 1 + direction.ordinal();
                }
                if (direction == Direction.UP && !covered && !other.isAir()) {
                    loaded = true;
                }
            }
            if (!covered) {
                open |= 1 << direction.ordinal();
            }
        }
        return open | wood << WOOD_SHIFT | heart << HEART_SHIFT | (loaded ? LOADED_BIT : 0) | (sheltered ? SHELTERED_BIT : 0);
    }

    /**
     * Packs surroundings by hand. For tests.
     *
     * @param open   the open sides, one bit per direction
     * @param wood   the side a branch touches, or null
     * @param heart  the side toward the inside of the crown, or null
     * @param loaded whether something rests on top
     */
    public static int of(int open, @Nullable Direction wood, @Nullable Direction heart, boolean loaded) {
        return (open & OPEN_MASK)
                | (wood == null ? 0 : 1 + wood.ordinal()) << WOOD_SHIFT
                | (heart == null ? 0 : 1 + heart.ordinal()) << HEART_SHIFT
                | (loaded ? LOADED_BIT : 0);
    }

    /**
     * The same surroundings, marked as sheltered: every neighbor is a leaf
     * block or a full block. For tests.
     */
    public static int sheltered(int surround, boolean sheltered) {
        return sheltered ? surround | SHELTERED_BIT : surround & ~SHELTERED_BIT;
    }

    /**
     * Whether the block sits inside its crown: each of its six neighbors
     * is a leaf block or a full, solid block. In full leaf such a block is
     * buried and shows nothing. While leaves fall or come back it can be
     * seen through its neighbors, but only from afar and in part, so it is
     * drawn from fewer pieces.
     */
    public static boolean sheltered(int surround) {
        return (surround & SHELTERED_BIT) != 0;
    }

    /** The open sides, one bit per direction. */
    public static int open(int surround) {
        return surround & OPEN_MASK;
    }

    /** How many of the six sides are open. */
    public static int openCount(int surround) {
        return Integer.bitCount(surround & OPEN_MASK);
    }

    /** Whether the side in a direction is open. */
    public static boolean isOpen(int surround, Direction direction) {
        return (surround & 1 << direction.ordinal()) != 0;
    }

    /** The side on which a branch touches the block, or null. */
    @Nullable
    public static Direction wood(int surround) {
        int code = surround >> WOOD_SHIFT & 7;
        return code == 0 ? null : DIRECTIONS[code - 1];
    }

    /** The side toward the inside of the crown, or null if no neighbor lies deeper. */
    @Nullable
    public static Direction heart(int surround) {
        int code = surround >> HEART_SHIFT & 7;
        return code == 0 ? null : DIRECTIONS[code - 1];
    }

    /** Whether something that is not a leaf rests on top (a layer of snow, say). */
    public static boolean loaded(int surround) {
        return (surround & LOADED_BIT) != 0;
    }

    /** The order in which covered sides are tried as a support: below first, above last. */
    private static final Direction[] SUPPORT_ORDER = {
            Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.UP};

    /**
     * The side on which this leaf block hangs on to its tree: the side a
     * branch touches, or else the side toward the heart of the leaf
     * cluster, or else any covered side (the one below first). Null for a
     * leaf block that touches nothing at all.
     *
     * <p>A leaf block that is drawn smaller than a full block is moved
     * against this side, so that it sits on its branch and does not hang
     * in the air.</p>
     */
    @Nullable
    public static Direction support(int surround) {
        Direction side = wood(surround);
        if (side == null) {
            side = heart(surround);
        }
        if (side == null) {
            for (Direction candidate : SUPPORT_ORDER) {
                if ((surround & 1 << candidate.ordinal()) == 0) {
                    return candidate;
                }
            }
        }
        return side;
    }

    /**
     * The direction a twig in this block grows in: away from the side the
     * block hangs on to ({@link #support}), or upward if there is none.
     */
    public static Direction grow(int surround) {
        Direction from = support(surround);
        return from == null ? Direction.UP : from.getOpposite();
    }

    /**
     * How deep in the crown a leaf block sits: the supply number Dynamic
     * Trees keeps in the block's {@code distance}, 1 at the far edge of
     * the crown and higher toward the twig; 1 for blocks without one.
     */
    public static int depth(BlockState state) {
        return state.hasProperty(LeavesBlock.DISTANCE) ? state.getValue(LeavesBlock.DISTANCE) : 1;
    }
}
