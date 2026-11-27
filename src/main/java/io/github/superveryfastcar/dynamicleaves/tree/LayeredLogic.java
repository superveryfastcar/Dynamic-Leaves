package io.github.superveryfastcar.dynamicleaves.tree;

import com.dtteam.dynamictrees.systems.growthlogic.GrowthLogicKit;
import com.dtteam.dynamictrees.systems.growthlogic.GrowthLogicKitConfiguration;
import com.dtteam.dynamictrees.systems.growthlogic.context.DirectionManipulationContext;
import com.dtteam.dynamictrees.tree.TreeHelper;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

/**
 * How a tree with limbs in layers grows: Dynamic Trees' usual way, except
 * that a branch that has left the trunk stays level.
 *
 * <p>Dynamic Trees grows a tree by sending a "signal" up from the root.
 * At every branch block the signal picks the direction it goes on in,
 * by chance, from six weights (one per direction). Where it leaves the
 * wood it adds a new block. The species' "growth logic kit" sets those
 * weights. This kit takes the weights Dynamic Trees would use anyway and,
 * off the trunk, sets up and down to zero unless wood is there already:
 * limbs then run level from the trunk and fork level, and the tree gains
 * height through its trunk only. Dynamic Trees' own dark oak grows by
 * much the same rule.</p>
 *
 * <p>Registered as {@code dynamicleaves:layered}, so a tree pack can name it in
 * a species file ({@code "growth_logic_kit": "dynamicleaves:layered"}).
 * {@link LayeredLimbs} gives it to the species the mod reshapes.</p>
 */
public final class LayeredLogic extends GrowthLogicKit {

    public LayeredLogic(ResourceLocation registryName) {
        super(registryName);
    }

    /**
     * The six weights (by {@link Direction#ordinal()}) for the signal's
     * next step.
     */
    @Override
    public int[] populateDirectionProbabilityMap(GrowthLogicKitConfiguration configuration,
                                                 DirectionManipulationContext context) {
        int[] weights = super.populateDirectionProbabilityMap(configuration, context);
        if (context.signal().isInTrunk()) {
            return weights;
        }
        // A tree that grew before it got this rule may have limbs that climb
        // (the mod's own stored shapes have none): the signal may still
        // follow wood that is there. It only makes no new wood up or down.
        boolean woodAbove = TreeHelper.isBranch(context.level().getBlockState(context.pos().above()));
        boolean woodBelow = TreeHelper.isBranch(context.level().getBlockState(context.pos().below()));
        levelOnly(weights, context.signal().dir, woodAbove, woodBelow);
        return weights;
    }

    /**
     * Takes up and down out of the weights of a branch off the trunk,
     * unless the tree already has wood there. Kept apart from the game so
     * that it can be tested by itself.
     *
     * @param weights   the six weights, changed in place
     * @param heading   the direction the signal came in
     * @param woodAbove whether the block above is a branch already
     * @param woodBelow whether the block below is a branch already
     */
    public static void levelOnly(int[] weights, Direction heading, boolean woodAbove, boolean woodBelow) {
        if (!woodAbove) {
            weights[Direction.UP.ordinal()] = 0;
        }
        if (!woodBelow) {
            weights[Direction.DOWN.ordinal()] = 0;
        }
        // With no weight left at all Dynamic Trees falls back on "up". Going
        // straight on is what a level limb does when nothing pulls it aside.
        for (int weight : weights) {
            if (weight > 0) {
                return;
            }
        }
        if (heading.getAxis().isHorizontal()) {
            weights[heading.ordinal()] = 1;
        }
    }
}
