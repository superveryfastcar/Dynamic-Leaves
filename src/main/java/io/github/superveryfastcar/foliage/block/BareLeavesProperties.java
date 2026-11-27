package io.github.superveryfastcar.foliage.block;

import com.dtteam.dynamictrees.api.cell.CellKit;
import com.dtteam.dynamictrees.block.branch.BranchBlock;
import com.dtteam.dynamictrees.block.leaves.DynamicLeavesBlock;
import com.dtteam.dynamictrees.block.leaves.LeavesProperties;
import com.dtteam.dynamictrees.tree.family.Family;
import com.dtteam.dynamictrees.tree.species.Species;
import io.github.superveryfastcar.foliage.FoliageConfig;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What Dynamic Trees needs to know about a bare block: the "leaf kind"
 * ({@link LeavesProperties}) of a {@link BareLeavesBlock}.
 *
 * <p>Dynamic Trees asks a leaf block's kind which tree family it belongs
 * to, how water spreads through the crown from the twigs (the cell kit),
 * how much light the leaves need and how well they burn. A bare block
 * stands in for a leaf block for a few months and must give the same
 * answers, or the tree would treat the spot as foreign: twigs would lose
 * their hold, and leaves behind the spot would dry out.</p>
 *
 * <h2>Nothing of its own</h2>
 * So this kind keeps no numbers. Every question is passed on to the kind
 * of the leaf it stands in for ({@link #leaf()}), at the moment it is
 * asked. A tree pack or a data pack that changes the leaf's numbers, also
 * while the game runs, changes the bare block's with them.
 *
 * <h2>Not in the list of leaf kinds</h2>
 * Bare kinds are made in code ({@link Twins}), one for each plain leaf
 * kind, and are not entered in Dynamic Trees' list of leaf kinds. That
 * list is for kinds that tree packs name; nothing names a bare kind.
 * Dynamic Trees finds it through the block, which is all it needs.
 */
public final class BareLeavesProperties extends LeavesProperties {

    /** The kind of the leaf this bare kind stands in for. */
    private final LeavesProperties leaf;

    /**
     * @param leaf      the kind of the leaf to stand in for
     * @param blockName the registry name of the bare block
     */
    BareLeavesProperties(LeavesProperties leaf, ResourceLocation blockName) {
        super(blockName);
        this.leaf = leaf;
    }

    /** The kind carries the name of its block as it is; Dynamic Trees would add "_leaves" to it. */
    @Override
    protected String getBlockRegistryNameSuffix() {
        return "";
    }

    /** The kind of the leaf this bare kind stands in for. */
    public LeavesProperties leaf() {
        return leaf;
    }

    /** Makes the bare block. Called once, when the game registers blocks. */
    BareLeavesBlock makeBlock() {
        return new BareLeavesBlock(this, BareLeavesBlock.blockProperties());
    }

    // ------------------------------------------------------------------
    // The block
    // ------------------------------------------------------------------

    @Override
    protected DynamicLeavesBlock createDynamicLeaves(BlockBehaviour.Properties properties) {
        return new BareLeavesBlock(this, properties);
    }

    @Override
    public BlockBehaviour.Properties getDefaultBlockProperties() {
        return BareLeavesBlock.blockProperties();
    }

    // ------------------------------------------------------------------
    // Passed on to the leaf
    // ------------------------------------------------------------------

    @Override
    public Family getFamily() {
        return leaf.getFamily();
    }

    /** The family is the leaf's; there is nothing to set. */
    @Override
    public LeavesProperties setFamily(Family family) {
        return this;
    }

    @Override
    public CellKit getCellKit() {
        return leaf.getCellKit();
    }

    @Override
    public BlockState getPrimitiveLeaves() {
        return leaf.getPrimitiveLeaves();
    }

    @Override
    public Optional<Block> getPrimitiveLeavesBlock() {
        return leaf.getPrimitiveLeavesBlock();
    }

    /** A bare block is nothing a player can pick up or pick. */
    @Override
    public ItemStack getPrimitiveLeavesItemStack() {
        return ItemStack.EMPTY;
    }

    /**
     * How easily fire eats a bare block: a setting, by default as slowly
     * as it eats wood. A bare tree does not go up in flames as a tree in
     * leaf does. The number is not 0 by default, because fire only starts
     * in open air and a bare block is not air: a crown of bare blocks
     * that cannot burn would keep fire away from its twigs and branches
     * for good.
     *
     * <p>The bare block itself answers the game (see
     * {@link BareLeavesBlock#getFlammability}); this is for whoever asks
     * the kind.</p>
     */
    @Override
    public int getFlammability() {
        return FoliageConfig.twigsBurn();
    }

    /** How easily fire starts in the open air next to a bare block: a setting, by default not at all. */
    @Override
    public int getFireSpreadSpeed() {
        return FoliageConfig.twigsSpreadFire();
    }

    @Override
    public int getSmotherLeavesMax() {
        return leaf.getSmotherLeavesMax();
    }

    @Override
    public int getLightRequirement() {
        return leaf.getLightRequirement();
    }

    @Override
    public boolean canGrowOnGround() {
        return leaf.canGrowOnGround();
    }

    @Override
    public boolean isConnectAnyRadius() {
        return leaf.isConnectAnyRadius();
    }

    @Override
    public boolean shouldAge(boolean worldGen, BlockState state) {
        return leaf.shouldAge(worldGen, state);
    }

    @Override
    public boolean doRequireShears() {
        return leaf.doRequireShears();
    }

    @Override
    public int getRadiusForConnection(BlockState state, BlockGetter blockAccess, BlockPos pos, BranchBlock from,
                                      Direction side, int fromRadius) {
        return leaf.getRadiusForConnection(state, blockAccess, pos, from, side, fromRadius);
    }

    /** The color of the leaf, for the leaves that are still drawn at this spot. */
    @Override
    public int foliageColorMultiplier(BlockState state, BlockAndTintGetter level, BlockPos pos) {
        return leaf.foliageColorMultiplier(leaf.getDynamicLeavesState(), level, pos);
    }

    // ------------------------------------------------------------------
    // What a bare block does not do
    // ------------------------------------------------------------------

    /** Nothing falls out of a bare block, also not when its tree is cut down. */
    @Override
    public List<ItemStack> getDrops(Level level, BlockPos pos, ItemStack tool, Species species) {
        return List.of();
    }

    @Override
    public boolean shouldGenerateDrops() {
        return false;
    }

    @Override
    public boolean shouldGenerateBlockDrops() {
        return false;
    }

    /** A bare block is never taken away for want of light. */
    @Override
    public boolean updateTick(LevelAccessor level, BlockPos pos, BlockState state, RandomSource rand) {
        return false;
    }
}
