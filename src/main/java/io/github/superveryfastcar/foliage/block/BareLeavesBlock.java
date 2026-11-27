package io.github.superveryfastcar.foliage.block;

import com.dtteam.dynamictrees.block.leaves.DynamicLeavesBlock;
import com.dtteam.dynamictrees.block.leaves.LeavesProperties;
import io.github.superveryfastcar.foliage.FoliageConfig;
import io.github.superveryfastcar.foliage.world.LeafWalker;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * What stands where a leaf block was, while its tree has shed the leaves
 * of that spot: the bare block.
 *
 * <h2>Why there is a block at all</h2>
 * Dynamic Trees keeps a tree alive through its leaf blocks. A twig holds
 * only while leaf blocks surround its tip, and a leaf block lives only
 * while water reaches it from a twig through its neighbors. If the leaf
 * blocks of a crown were simply removed in autumn, Dynamic Trees would let
 * the twigs rot and then grow everything anew, in different places. So a
 * spot that goes bare keeps a block that Dynamic Trees takes for a leaf,
 * and that the rest of the game hardly notices.
 *
 * <h2>For Dynamic Trees: a leaf</h2>
 * It is a {@link DynamicLeavesBlock} of the same family as the leaf it
 * stands in for, and keeps that leaf's supply number (the {@code distance}
 * of the block state). It holds up its twig, passes water on to the leaves
 * behind it, and no new leaf grows into it. When its twig is gone, it goes
 * too, as a leaf would.
 *
 * <h2>For everything else: next to nothing</h2>
 * <ul>
 *   <li>Players, animals, arrows and items pass through it. It has no
 *       outline: the crosshair goes through it to whatever lies behind,
 *       and a block can be placed where it is.</li>
 *   <li>Light passes through it undimmed, and it throws no shade on the
 *       corners of its neighbors. So the ground under a bare tree is as
 *       bright as open land, and the last leaves inside a crown that is
 *       going bare are lit by the sky.</li>
 *   <li>Rain and snow fall through it, and nothing rests on it.</li>
 *   <li>It never ticks at random, so a bare forest costs the server
 *       nothing.</li>
 *   <li>It drops nothing.</li>
 *   <li>Fire eats it as slowly as it eats wood, and does not jump along
 *       it as it does along leaves (see {@link #getFlammability}).</li>
 *   <li>Water does not flow into it, as it does not flow into leaves: a
 *       stream that a crown holds back in summer stays where it is in
 *       winter.</li>
 * </ul>
 *
 * <h2>How it is drawn</h2>
 * By the same leaf model as its leaf, from the same rule of the leaf
 * calendar: usually as nothing, a twig, a twig with buds, or the last or
 * first few leaves. The look of a spot never depends on which of the two
 * blocks is there at the moment.
 *
 * <h2>Who puts it there</h2>
 * {@link LeafWalker}, on the server, when the leaf calendar says the spot
 * is bare; and it puts the leaf back in spring.
 */
public final class BareLeavesBlock extends DynamicLeavesBlock {

    /** The translation key all bare blocks share. */
    private static final String NAME_KEY = "block.foliage.bare_leaves";

    /** The leaf block this one stands in for; looked up at first use, when all blocks exist. */
    @Nullable
    private DynamicLeavesBlock leafBlock;

    BareLeavesBlock(BareLeavesProperties kind, BlockBehaviour.Properties properties) {
        super(kind, properties);
    }

    /**
     * The fixed properties of every bare block: no collision, never in the
     * way of sight or light, can be built over like tall grass, does not
     * show on maps.
     */
    static BlockBehaviour.Properties blockProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.NONE)
                .noCollission()
                .noOcclusion()
                .replaceable()
                .instabreak()
                .sound(SoundType.GRASS)
                .pushReaction(PushReaction.DESTROY)
                .ignitedByLava()
                .noLootTable()
                .isValidSpawn((state, level, pos, entity) -> false)
                .isSuffocating((state, level, pos) -> false)
                .isViewBlocking((state, level, pos) -> false)
                .isRedstoneConductor((state, level, pos) -> false);
    }

    /** The kind of this bare block, which knows the leaf it stands in for. */
    public BareLeavesProperties kind() {
        return (BareLeavesProperties) getLeavesProperties();
    }

    /**
     * The leaf block this one stands in for, or null if that kind of
     * leaves has no block (then this bare block is never used).
     */
    @Nullable
    public DynamicLeavesBlock leaf() {
        DynamicLeavesBlock leaf = leafBlock;
        if (leaf == null) {
            leaf = kind().leaf().getDynamicLeavesBlock().orElse(null);
            leafBlock = leaf;
        }
        return leaf;
    }

    @Override
    public String getDescriptionId() {
        return NAME_KEY;
    }

    // ------------------------------------------------------------------
    // Next to nothing: shape, light, movement
    // ------------------------------------------------------------------

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    /** Nothing rests on a bare block or hangs on its side: no snow, no vine. */
    @Override
    public VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    @Override
    protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    /** Light passes undimmed, unless the block holds water. */
    @Override
    protected int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getFluidState().isEmpty() ? 0 : 1;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getFluidState().isEmpty();
    }

    /** A bare block throws no shade on the corners of the blocks around it. A leaf block of Dynamic Trees does (0.2). */
    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    /** A leaf block of Dynamic Trees slows whoever is inside it. A bare block does nothing. */
    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
    }

    /**
     * For animals and monsters looking for a way, a bare block is open
     * air (or water, if it holds water). The game would otherwise take it
     * for leaves, which they do not walk through.
     */
    @Override
    public PathType getBlockPathType(BlockState state, BlockGetter level, BlockPos pos, @Nullable Mob mob) {
        return state.getFluidState().isEmpty() ? PathType.OPEN : PathType.WATER;
    }

    // ------------------------------------------------------------------
    // Fire
    // ------------------------------------------------------------------

    /**
     * How easily fire eats a bare block (for the game: the chance that
     * fire next to it burns it away). A setting; by default the number of
     * a log. Not 0, or the bare blocks around the twigs of a crown would
     * keep fire from them: fire only starts in open air.
     *
     * <p>Dynamic Trees answers this for its leaves with the other of the
     * kind's two fire numbers; here each question gets its own.</p>
     */
    @Override
    public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return FoliageConfig.twigsBurn();
    }

    /**
     * How easily fire starts in the open air next to a bare block. A
     * setting; by default 0, as for the thinnest twigs of Dynamic Trees.
     * Fire can then eat its way into a bare crown but does not run along
     * its outside as it does along leaves.
     */
    @Override
    public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return FoliageConfig.twigsSpreadFire();
    }

    // ------------------------------------------------------------------
    // No life of its own
    // ------------------------------------------------------------------

    /** A leaf block of Dynamic Trees ticks at random to check its light and to grow. A bare block does neither. */
    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return false;
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource rand) {
    }

    /** No dripping water under a bare block in the rain, and no particles of the leaf. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
    }

    /**
     * When Dynamic Trees ages a crown (a growth pulse from bone meal, for
     * one), a leaf block checks its supply and its light and then grows
     * new leaves into the free spots next to it. A bare block only checks
     * its supply, so that it goes when its twig is gone. It grows nothing:
     * with the crown bare, light reaches spots that are too dark for
     * leaves in summer, and what grew there would die again in spring.
     *
     * @return the block's supply number after the check, 0 if it is gone
     */
    @Override
    public int age(LevelAccessor level, BlockPos pos, BlockState state, RandomSource rand, boolean worldgen) {
        return updateHydro(level, pos, state, worldgen);
    }

    @Override
    public int updateLeaves(LevelAccessor level, BlockPos pos, BlockState state, RandomSource rand, boolean worldGen) {
        return updateHydro(level, pos, state, worldGen);
    }

    /** A bare block is never taken away for want of light or for standing in water. */
    @Override
    public boolean removeIfInvalid(BlockState state, LevelAccessor level, BlockPos pos, RandomSource rand) {
        return false;
    }

    // ------------------------------------------------------------------
    // Growing: what the tree places through a bare block
    // ------------------------------------------------------------------

    /**
     * Whether a new leaf may grow at a spot. Asked of the leaf block this
     * one stands in for, which knows its kind's own rules (some leaves
     * grow under water).
     */
    @Override
    public boolean isLocationSuitableForNewLeaves(LevelAccessor level, LeavesProperties leavesProperties, BlockPos pos) {
        DynamicLeavesBlock leaf = leaf();
        return leaf != null
                ? leaf.isLocationSuitableForNewLeaves(level, leavesProperties, pos)
                : super.isLocationSuitableForNewLeaves(level, leavesProperties, pos);
    }

    /**
     * Dynamic Trees asks this just before it puts a leaf block down, or
     * changes the supply number of one: when a twig grows through this
     * block, or when the supply of this block or of a neighbor changes.
     * The answer is the block the leaf calendar wants at that spot right
     * now, the leaf or its bare block. So a tree that grows in winter
     * grows bare.
     */
    @Override
    public BlockState getLeavesBlockStateForPlacement(LevelAccessor level, BlockPos pos, BlockState leavesStateWithHydro,
                                                      int oldHydro, boolean worldGen) {
        return LeafWalker.fit(level, pos, leavesStateWithHydro);
    }

    /**
     * Dynamic Trees tells the players about a new supply number only if
     * this says the look changes with it. For its own leaves it does not.
     * Here the change may also swap the bare block for its leaf (see
     * {@link #getLeavesBlockStateForPlacement}), which the players must
     * learn.
     */
    @Override
    public boolean appearanceChangesWithHydro(int oldHydro, int newHydro) {
        return true;
    }

    /**
     * The game calls this whenever a block next to this one changes. If
     * the new neighbor is a leaf block where the leaf calendar wants a
     * bare one, Dynamic Trees has just put it there: a twig that grew
     * into open air grows its leaves through the leaf block of its
     * species, not through a bare block. The walker is told, and puts the
     * spot right in the next tick.
     */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        if (level instanceof ServerLevel serverLevel && neighborState.getBlock() instanceof DynamicLeavesBlock other
                && !(other instanceof BareLeavesBlock)) {
            LeafWalker.noticed(serverLevel, neighborPos, neighborState);
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    // ------------------------------------------------------------------
    // Nothing to take
    // ------------------------------------------------------------------

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        return List.of();
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return ItemStack.EMPTY;
    }
}
