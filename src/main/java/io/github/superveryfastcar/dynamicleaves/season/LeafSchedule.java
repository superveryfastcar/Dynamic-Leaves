package io.github.superveryfastcar.dynamicleaves.season;

/**
 * When and how one kind of tree sheds its leaves.
 *
 * @param startDay how many days after the first trees this kind starts, in
 *                 autumn with the fall of its leaves and in spring with its
 *                 buds; 0 for the earliest kind. May have a fraction.
 * @param order    in which order the leaf spots of one tree go bare
 */
public record LeafSchedule(double startDay, TreeOrder order) {

    /** The latest start a kind may have, in days. */
    public static final double MAX_START_DAY = 60.0;

    /** Forces the start into its allowed range; a start that is not a number becomes 0. */
    public LeafSchedule {
        startDay = Double.isNaN(startDay) ? 0.0 : Math.max(0.0, Math.min(MAX_START_DAY, startDay));
        if (order == null) {
            order = TreeOrder.OUTSIDE_IN;
        }
    }
}
