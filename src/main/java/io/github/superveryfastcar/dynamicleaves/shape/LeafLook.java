package io.github.superveryfastcar.dynamicleaves.shape;

import io.github.superveryfastcar.dynamicleaves.season.LeafClock;
import io.github.superveryfastcar.dynamicleaves.season.LeafSchedule;
import io.github.superveryfastcar.dynamicleaves.season.LeafStage;
import io.github.superveryfastcar.seasons.api.SeasonsApi;
import java.lang.ref.WeakReference;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Works out how the leaf block at a position looks at one moment of the
 * leaf calendar: the stage it is at, and the pieces it is drawn from.
 *
 * <p>This is the one place where the calendar ({@link LeafClock}), the
 * biome's winter and the shape rules ({@link ShapeRules}) come together.
 * The leaf model asks it for every leaf block of every chunk it builds;
 * the tests ask it for single blocks.</p>
 *
 * <h2>One per thread</h2>
 * A worker keeps a position holder and a small memory of which places
 * have a winter, so that a question makes no garbage and looks a biome up
 * only once per small cube of blocks. That is why each thread has its own
 * ({@link #forThread}); a worker must not be shared between threads.
 *
 * <h2>Where there is a winter</h2>
 * Trees only shed where Seasons says the biome has a winter
 * ({@link SeasonsApi#hasWinter}). The biome is read the way the game
 * stores it, in cubes of 4 by 4 by 4 blocks, not the way it blends biomes
 * for colors. A server and a player's game hold the same stored biomes, so
 * they agree on every block.
 */
public final class LeafLook implements Surround.LeafCover {

    private static final ThreadLocal<LeafLook> PER_THREAD = ThreadLocal.withInitial(LeafLook::new);

    /** How many places the winter memory holds: one per 4-block cube of a chunk section. */
    private static final int MEMORY = 64;

    private static final byte UNKNOWN = 0;
    private static final byte NO_WINTER = 1;
    private static final byte WINTER = 2;

    /** For looking at the neighbors. */
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    /** The winter memory: which 4-block cube each slot is about, and the answer. */
    private final long[] cubes = new long[MEMORY];
    private final byte[] winters = new byte[MEMORY];

    /** What the winter memory was filled from. It is emptied when either changes. */
    private WeakReference<LevelReader> rememberedBiomes = new WeakReference<>(null);
    @Nullable
    private LeafClock rememberedClock;

    /** The question being answered, for {@link #leaves}. Only set while {@link #recipe} runs. */
    @Nullable
    private LeafClock clock;
    @Nullable
    private LevelReader biomes;

    private LeafLook() {
    }

    /** The worker of the thread that asks. */
    public static LeafLook forThread() {
        return PER_THREAD.get();
    }

    /**
     * The stage of the leaf calendar a leaf block is at.
     *
     * @param clock  the leaf calendar at this moment, or null if there is
     *               none (then every leaf block is in full leaf)
     * @param biomes where to read biomes from, or null if nowhere (then
     *               no place has a winter)
     * @param state  the leaf block
     * @param x      its position
     * @param y      its position
     * @param z      its position
     */
    public LeafStage stage(@Nullable LeafClock clock, @Nullable LevelReader biomes, BlockState state, int x, int y, int z) {
        if (clock == null || biomes == null || clock.allFull()) {
            return LeafStage.FULL;
        }
        begin(clock, biomes);
        LeafStage stage = stageOf(state, x, y, z);
        end();
        return stage;
    }

    /**
     * The recipe the leaf block at a position is drawn by.
     *
     * @param settings the player's shape settings
     * @param clock    the leaf calendar at this moment, or null if there
     *                 is none (then every leaf block is in full leaf)
     * @param biomes   where to read biomes from, or null if nowhere (then
     *                 no place has a winter)
     * @param blocks   the blocks around (a chunk being built, or a world)
     * @param pos      the leaf block's position
     * @param state    the leaf block
     * @return the recipe ({@link Recipe}), or {@link Recipe#NONE} for a
     *         block that is drawn by the model it had before: one in full
     *         leaf while the shape is {@link LeafShape#PLAIN}
     */
    public int recipe(ShapeSettings settings, @Nullable LeafClock clock, @Nullable LevelReader biomes,
                      BlockGetter blocks, BlockPos pos, BlockState state) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        // Outside the months in which leaves fall, lie on the ground or come back, nothing below costs anything.
        boolean seasonal = clock != null && biomes != null && !clock.allFull() && clock.hasKinds();
        LeafStage stage = LeafStage.FULL;
        if (seasonal) {
            begin(clock, biomes);
            stage = stageOf(state, x, y, z);
        }
        boolean plain = settings.shape() == LeafShape.PLAIN;
        if (plain && stage == LeafStage.FULL) {
            end();
            return Recipe.NONE;
        }
        // In the plain look leaf blocks never hide each other's faces. In the
        // others a neighbor hides a face only while it has most of its leaves.
        Surround.LeafCover cover = plain ? Surround.NO_LEAF_COVERS : seasonal ? this : null;
        int surround = Surround.read(blocks, pos, state, cursor, cover);
        end();
        boolean twigSpot = ShapeRules.twigSpot(settings, surround, Surround.depth(state));
        return ShapeRules.recipe(settings, surround, ShapeRules.variant(x, y, z), stage, twigSpot);
    }

    /**
     * How much of a neighboring leaf block is there at the moment in
     * question. It hides what lies behind it unless it is of a kind that
     * sheds, stands where there is a winter and has lost most of its
     * leaves; and it has nothing to speak of from its last leaves of
     * autumn to its first of spring.
     */
    @Override
    public int leaves(BlockState state, int x, int y, int z) {
        LeafStage stage = stageOf(state, x, y, z);
        return stage.covers() ? Surround.COVERS : stage.bareBlock() ? Surround.NOTHING : Surround.LEAVES;
    }

    /** The stage of a leaf block, between {@link #begin} and {@link #end}. */
    private LeafStage stageOf(BlockState state, int x, int y, int z) {
        LeafSchedule schedule = clock.schedule(state.getBlock());
        if (schedule == null || !hasWinter(x, y, z)) {
            return LeafStage.FULL;
        }
        return clock.stage(schedule, x, y, z, Surround.depth(state));
    }

    /** Takes up a question, and empties the winter memory if it is about another world or another moment. */
    private void begin(LeafClock clock, LevelReader biomes) {
        this.clock = clock;
        this.biomes = biomes;
        // Compared by identity: a new moment is a new clock object. Emptying
        // the memory at every step of the calendar also keeps it from going
        // stale when a server changes which biomes have a winter.
        if (rememberedClock != clock || rememberedBiomes.get() != biomes) {
            java.util.Arrays.fill(winters, UNKNOWN);
            rememberedClock = clock;
            rememberedBiomes = new WeakReference<>(biomes);
        }
    }

    /** Lets go of the world, so that a thread that rests does not keep a world alive that the player has left. */
    private void end() {
        this.biomes = null;
    }

    /** Whether the place of a block has a winter, between {@link #begin} and {@link #end}. */
    private boolean hasWinter(int x, int y, int z) {
        int cubeX = x >> 2;
        int cubeY = y >> 2;
        int cubeZ = z >> 2;
        int slot = (cubeX & 3) | (cubeY & 3) << 2 | (cubeZ & 3) << 4;
        long cube = BlockPos.asLong(cubeX, cubeY, cubeZ);
        byte known = winters[slot];
        if (known != UNKNOWN && cubes[slot] == cube) {
            return known == WINTER;
        }
        boolean winter = SeasonsApi.hasWinter(biomes.getNoiseBiome(cubeX, cubeY, cubeZ).value());
        cubes[slot] = cube;
        winters[slot] = winter ? WINTER : NO_WINTER;
        return winter;
    }
}
