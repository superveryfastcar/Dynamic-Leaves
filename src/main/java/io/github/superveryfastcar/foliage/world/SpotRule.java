package io.github.superveryfastcar.foliage.world;

import io.github.superveryfastcar.foliage.season.LeafClock;
import io.github.superveryfastcar.foliage.season.LeafSchedule;
import io.github.superveryfastcar.foliage.season.LeafStage;
import javax.annotation.Nullable;
import net.minecraft.world.level.block.Block;

/**
 * The rule that says which of its two blocks a leaf spot holds at a
 * moment: the leaf block or the bare block.
 *
 * <p>A spot holds the bare block exactly when all of this is true:</p>
 * <ul>
 *   <li>bare branches are switched on ({@link WalkSettings#enabled});</li>
 *   <li>its kind of leaves sheds (it is listed in the leaf calendar);</li>
 *   <li>it stands where there is a winter;</li>
 *   <li>the leaf calendar puts it at a stage with few leaves or none
 *       ({@link LeafStage#bareBlock()}).</li>
 * </ul>
 * In every other case it holds the leaf block.
 *
 * <p>The answer depends on nothing but the date, the kind, the place and
 * the spot's supply number. Nothing is remembered: whoever asks at the
 * same moment gets the same answer, and a spot that was missed is put
 * right the next time someone looks.</p>
 */
public final class SpotRule {

    private SpotRule() {
    }

    /**
     * Whether a leaf spot holds the bare block.
     *
     * @param clock      the leaf calendar at this moment, or null if there
     *                   is none
     * @param enabled    whether bare branches are switched on
     * @param block      the block at the spot: a leaf block or a bare block
     * @param winterHere whether the spot stands where there is a winter
     * @param x          the spot's position
     * @param y          the spot's position
     * @param z          the spot's position
     * @param depth      the spot's supply number (the {@code distance} of
     *                   its block state)
     */
    public static boolean bare(@Nullable LeafClock clock, boolean enabled, Block block, boolean winterHere,
                               int x, int y, int z, int depth) {
        if (!enabled || clock == null || clock.allFull() || !winterHere) {
            return false;
        }
        LeafSchedule schedule = clock.schedule(block);
        if (schedule == null) {
            return false;
        }
        return clock.allBare() || clock.stage(schedule, x, y, z, depth).bareBlock();
    }
}
