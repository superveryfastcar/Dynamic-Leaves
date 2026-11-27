package io.github.superveryfastcar.dynamicleaves.season;

import io.github.superveryfastcar.calendar.api.CalendarRules;
import io.github.superveryfastcar.calendar.api.Season;
import io.github.superveryfastcar.dynamicleaves.block.Twins;
import io.github.superveryfastcar.dynamicleaves.util.PosHash;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * The leaf calendar at one moment: answers which {@link LeafStage} a leaf
 * spot shows.
 *
 * <h2>The rule</h2>
 * <ul>
 *   <li><b>The year.</b> Leaves fall in a window at the end of autumn and
 *       come back in a window at the start of spring
 *       ({@link SeasonSettings#fallDays}, {@link SeasonSettings#returnDays}).
 *       Between spring's window and autumn's every spot is in full leaf,
 *       and from the end of autumn to the start of spring every spot is
 *       bare.</li>
 *   <li><b>The tree.</b> Inside a window each tree takes its own stretch
 *       ({@link SeasonSettings#treeDays}). It starts later by the day its
 *       kind starts on ({@link LeafSchedule#startDay}) plus a delay that
 *       depends on where it stands ({@link SeasonSettings#shiftDays}, from
 *       a smooth pattern over the map). A stretch that would run past the
 *       window is cut short, so every tree is done when the window ends.</li>
 *   <li><b>The spot.</b> Inside the tree's stretch each leaf spot has its
 *       turn, by how deep it sits in the crown and a little scatter
 *       ({@link TreeOrder}, {@link SeasonSettings#depthWeight}), and takes
 *       a share of the stretch for its own change
 *       ({@link SeasonSettings#spotShare}). In autumn it goes FULL, THIN,
 *       THINNER, LAST, BARE. In spring the tree first shows buds on all its
 *       spots ({@link SeasonSettings#budDays}); then each spot goes BUDS,
 *       FIRST, HALF, FULL, those at the twigs first.</li>
 * </ul>
 *
 * <h2>Time</h2>
 * The leaves move on in steps, a few per day
 * ({@link SeasonSettings#stepsPerDay}). A clock stands for one step of the
 * year; within a step nothing changes. Inside the rule, time is counted in
 * 256ths of a step, so that delays of a part of a step still put trees in
 * an order.
 *
 * <h2>The same everywhere</h2>
 * The rule is whole-number arithmetic on the settings, the season lengths,
 * the step and the position. A server and every player's game get the same
 * answer, and so does any thread. A clock never changes once made; a new
 * step means a new clock.
 *
 * <p>The rule does not know biomes. Where there is no winter, trees keep
 * their leaves; the caller asks for that first and does not call
 * {@link #stage} there.</p>
 */
public final class LeafClock {

    /** The parts a step is cut into for the arithmetic. */
    public static final int UNIT = 256;

    /** The scale of shares inside the rule: 1024 stands for "all of it". */
    private static final int WHOLE = 1024;

    /** Keeps the delay per place and the scatter per spot apart from other fixed numbers made from a position. */
    private static final long SHIFT_SALT = 0x4C65_6166_5368_6966L;
    private static final long SPOT_SALT = 0x4C65_6166_5370_6F74L;

    /** Which part of the year a step lies in. */
    private enum Phase {
        /** Spring's window: leaves come back. */
        RETURN,
        /** From the end of spring's window to the start of autumn's: full leaf. */
        FULL,
        /** Autumn's window: leaves fall. */
        FALL,
        /** From the end of autumn to the start of spring: bare. */
        BARE
    }

    private final SeasonSettings settings;
    private final CalendarRules rules;
    private final long step;
    private final Phase phase;

    /** Units since the current window opened (only in a window). */
    private final long into;

    /** The length of the current window in units (only in a window). */
    private final long window;

    /** A day, a tree's stretch, the buds' part of it and the longest delay, in units. */
    private final long dayUnits;
    private final long treeSpan;
    private final long budSpan;
    private final long shiftSpan;

    /** {@link SeasonSettings#spotShare} and {@link SeasonSettings#depthWeight} on the scale of {@link #WHOLE}. */
    private final int spotShare;
    private final int depthWeight;

    /** The leaf blocks that shed, their bare blocks, and the schedule of each, side by side. Few, so a plain search is fastest. */
    private final Block[] kindBlocks;
    private final LeafSchedule[] kindSchedules;

    private LeafClock(SeasonSettings settings, CalendarRules rules, long step) {
        this.settings = settings;
        this.rules = rules;
        this.step = step;
        int stepsPerDay = settings.stepsPerDay();
        this.dayUnits = (long) stepsPerDay * UNIT;
        this.treeSpan = units(settings.treeDays());
        this.budSpan = units(settings.budDays());
        this.shiftSpan = units(settings.shiftDays());
        this.spotShare = Math.max(1, (int) Math.round(settings.spotShare() * WHOLE));
        this.depthWeight = (int) Math.round(settings.depthWeight() * WHOLE);

        // Where the step lies, counted from the first day of spring.
        long yearSteps = (long) rules.yearLength() * stepsPerDay;
        long springStart = (long) (rules.firstDayOfYear(Season.SPRING) - 1) * stepsPerDay;
        long sinceSpring = Math.floorMod(step - springStart, yearSteps) * UNIT;
        int spring = rules.seasonLength(Season.SPRING);
        int autumn = rules.seasonLength(Season.AUTUMN);
        long returnEnd = Math.min(units(settings.returnDays()), spring * dayUnits);
        long fallEnd = (long) (spring + rules.seasonLength(Season.SUMMER) + autumn) * dayUnits;
        long fallStart = fallEnd - Math.min(units(settings.fallDays()), autumn * dayUnits);

        if (!settings.enabled()) {
            this.phase = Phase.FULL;
            this.into = 0;
            this.window = 0;
        } else if (sinceSpring < returnEnd) {
            this.phase = Phase.RETURN;
            this.into = sinceSpring;
            this.window = returnEnd;
        } else if (sinceSpring < fallStart) {
            this.phase = Phase.FULL;
            this.into = 0;
            this.window = 0;
        } else if (sinceSpring < fallEnd) {
            this.phase = Phase.FALL;
            this.into = sinceSpring - fallStart;
            this.window = fallEnd - fallStart;
        } else {
            this.phase = Phase.BARE;
            this.into = 0;
            this.window = 0;
        }

        // The leaf blocks named in the settings that the game knows, each
        // followed by its bare block if it has one: a spot keeps its
        // schedule whichever of the two blocks stands there.
        int count = 0;
        Block[] blocks = new Block[settings.kinds().size() * 2];
        LeafSchedule[] schedules = new LeafSchedule[blocks.length];
        for (Map.Entry<ResourceLocation, LeafSchedule> kind : settings.kinds().entrySet()) {
            Block block = BuiltInRegistries.BLOCK.get(kind.getKey());
            if (block != Blocks.AIR) {
                blocks[count] = block;
                schedules[count] = kind.getValue();
                count++;
                Block bare = Twins.bareOf(block);
                if (bare != null) {
                    blocks[count] = bare;
                    schedules[count] = kind.getValue();
                    count++;
                }
            }
        }
        this.kindBlocks = java.util.Arrays.copyOf(blocks, count);
        this.kindSchedules = java.util.Arrays.copyOf(schedules, count);
    }

    /**
     * The clock for one step of the year.
     *
     * @param settings the rules of the leaf calendar
     * @param rules    the Calendar mod's season lengths
     * @param step     the step of the year, from {@link #stepOf}; any whole
     *                 number, the year wraps around
     */
    public static LeafClock of(SeasonSettings settings, CalendarRules rules, long step) {
        return new LeafClock(settings, rules, step);
    }

    /**
     * Which step of the year a moment lies in.
     *
     * @param settings  the rules of the leaf calendar
     * @param dayOfYear the day of the year, 1 for the first
     * @param tickOfDay the tick of the day counted from midnight, 0 to 23999
     * @return the step, 0 for the first of the year
     */
    public static long stepOf(SeasonSettings settings, int dayOfYear, int tickOfDay) {
        int stepsPerDay = settings.stepsPerDay();
        int stepOfDay = Math.min(stepsPerDay - 1, Math.max(0, tickOfDay) * stepsPerDay / CalendarRules.TICKS_PER_DAY);
        return (long) (dayOfYear - 1) * stepsPerDay + stepOfDay;
    }

    /** The rules this clock was made from. */
    public SeasonSettings settings() {
        return settings;
    }

    /** The season lengths this clock was made from. */
    public CalendarRules rules() {
        return rules;
    }

    /** The step of the year this clock stands for. */
    public long step() {
        return step;
    }

    /** Whether every leaf spot is in full leaf at this step: summer, or shedding is switched off. */
    public boolean allFull() {
        return phase == Phase.FULL;
    }

    /**
     * Whether leaves are falling at this step: the step lies in autumn's
     * window. Spots of a tree whose turn has not come are still in full
     * leaf, and those that are done are bare; this says only that
     * somewhere leaves are coming down.
     */
    public boolean falling() {
        return phase == Phase.FALL;
    }

    /** Whether every leaf spot of a tree that sheds is bare at this step: winter. */
    public boolean allBare() {
        return phase == Phase.BARE;
    }

    /**
     * Whether a step from this clock to another can change what any leaf
     * spot shows. False only when both lie in the same quiet part of the
     * year (both in summer, or both in winter) under the same rules.
     */
    public boolean looksDifferentFrom(@Nullable LeafClock other) {
        if (other == null) {
            return phase != Phase.FULL;
        }
        if (!settings.equals(other.settings) || !rules.equals(other.rules)) {
            return true;
        }
        if (phase != other.phase) {
            return true;
        }
        return (phase == Phase.FALL || phase == Phase.RETURN) && step != other.step;
    }

    /**
     * The schedule of a leaf block or of its bare block, or null if the
     * kind does not shed.
     *
     * @param block any block
     */
    @Nullable
    public LeafSchedule schedule(Block block) {
        Block[] blocks = kindBlocks;
        for (int i = 0; i < blocks.length; i++) {
            if (blocks[i] == block) {
                return kindSchedules[i];
            }
        }
        return null;
    }

    /** Whether any leaf block sheds under these rules. */
    public boolean hasKinds() {
        return kindBlocks.length > 0;
    }

    /**
     * What a leaf spot shows at this step, in a place that has a winter.
     *
     * @param schedule the schedule of the spot's kind of leaves
     * @param x        the leaf block's position
     * @param y        the leaf block's position
     * @param z        the leaf block's position
     * @param depth    the supply number Dynamic Trees keeps for the leaf
     *                 block (its {@code distance}): 1 at the far edge of
     *                 the crown, higher toward the twig
     * @return the stage
     */
    public LeafStage stage(LeafSchedule schedule, int x, int y, int z, int depth) {
        switch (phase) {
            case FULL:
                return LeafStage.FULL;
            case BARE:
                return LeafStage.BARE;
            case FALL: {
                long begin = begin(schedule, x, z);
                long span = span(begin);
                long since = into - Math.min(begin, window - span);
                if (since <= 0) {
                    return LeafStage.FULL;
                }
                int tree = (int) Math.min(WHOLE, since * WHOLE / span);
                int spot = spot(tree, order(schedule.order() == TreeOrder.INSIDE_OUT, depth, x, y, z));
                if (spot <= 0) {
                    return LeafStage.FULL;
                }
                if (spot < WHOLE / 3) {
                    return LeafStage.THIN;
                }
                if (spot < WHOLE * 2 / 3) {
                    return LeafStage.THINNER;
                }
                return spot < WHOLE ? LeafStage.LAST : LeafStage.BARE;
            }
            case RETURN:
            default: {
                long begin = begin(schedule, x, z);
                long span = span(begin);
                long since = into - Math.min(begin, window - span);
                if (since < 0) {
                    return LeafStage.BARE;
                }
                // The buds take the first part of the tree's stretch, but leave the leaves at least a step.
                long buds = Math.max(0, Math.min(budSpan, span - UNIT));
                if (since < buds) {
                    return LeafStage.BUDS;
                }
                int tree = (int) Math.min(WHOLE, (since - buds) * WHOLE / (span - buds));
                int spot = spot(tree, order(true, depth, x, y, z));
                if (spot <= 0) {
                    return LeafStage.BUDS;
                }
                if (spot < WHOLE / 2) {
                    return LeafStage.FIRST;
                }
                return spot < WHOLE ? LeafStage.HALF : LeafStage.FULL;
            }
        }
    }

    /**
     * How many units after its window opened the tree at a place wants to
     * start: the start of its kind plus the delay of the place.
     */
    private long begin(LeafSchedule schedule, int x, int z) {
        long delay = shiftSpan == 0 ? 0 : PosHash.smooth(SHIFT_SALT, x, z, settings.shiftPatch()) * shiftSpan / 255;
        return units(schedule.startDay()) + delay;
    }

    /**
     * How long a tree's stretch is: the set length, or as much of it as is
     * left of the window after its start, but never less than a step (or
     * the whole window, if that is shorter).
     */
    private long span(long begin) {
        long left = window - begin;
        long span = Math.min(treeSpan, Math.max(left, UNIT));
        return Math.max(1, Math.min(span, window));
    }

    /**
     * A spot's turn inside its tree's stretch: 0 for the first spots, just
     * under {@link #WHOLE} for the last.
     *
     * @param twigFirst true if the spots right at the twigs go first,
     *                  false if the far edge of the crown goes first
     */
    private int order(boolean twigFirst, int depth, int x, int y, int z) {
        int deepest = settings.deepest();
        int deep = Math.max(1, Math.min(deepest, depth));
        // 0 at the far edge of the crown, WHOLE - 1 right at the twig.
        int place = (deep - 1) * (WHOLE - 1) / (deepest - 1);
        if (twigFirst) {
            place = WHOLE - 1 - place;
        }
        int scatter = PosHash.bits(PosHash.hash(SPOT_SALT, x, y, z), 10);
        return (place * depthWeight + scatter * (WHOLE - depthWeight)) >> 10;
    }

    /**
     * How far a spot is through its own change, given how far its tree is.
     * A spot whose turn is {@code order} starts when the tree has done the
     * matching part of the time that is not one spot's share, and then
     * takes one share. At 0 no spot has begun, at {@link #WHOLE} all are
     * done.
     *
     * @param tree  how far the tree is through its stretch, 0 to {@link #WHOLE}
     * @param order the spot's turn, 0 to just under {@link #WHOLE}
     * @return 0 or less: not begun; {@link #WHOLE} or more: done
     */
    private int spot(int tree, int order) {
        return (tree * WHOLE - order * (WHOLE - spotShare)) / spotShare;
    }

    /** A span of days in units. */
    private long units(double days) {
        return Math.round(days * dayUnits);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof LeafClock clock && step == clock.step && settings.equals(clock.settings) && rules.equals(clock.rules);
    }

    @Override
    public int hashCode() {
        return Long.hashCode(step) * 31 + settings.hashCode();
    }

    @Override
    public String toString() {
        return "LeafClock[step " + step + ", " + phase + (window > 0 ? ", " + into + " of " + window : "") + "]";
    }
}
