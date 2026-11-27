package io.github.superveryfastcar.foliage.gametest;

import static io.github.superveryfastcar.foliage.gametest.FoliageGameTests.check;

import com.dtteam.dynamictrees.block.branch.BranchBlock;
import com.dtteam.dynamictrees.block.leaves.DynamicLeavesBlock;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.superveryfastcar.calendar.api.CalendarRules;
import io.github.superveryfastcar.calendar.api.Season;
import io.github.superveryfastcar.foliage.Foliage;
import io.github.superveryfastcar.foliage.network.LeafCalendarPayload;
import io.github.superveryfastcar.foliage.season.LeafCalendar;
import io.github.superveryfastcar.foliage.season.LeafClock;
import io.github.superveryfastcar.foliage.season.LeafSchedule;
import io.github.superveryfastcar.foliage.season.LeafStage;
import io.github.superveryfastcar.foliage.season.SeasonSettings;
import io.github.superveryfastcar.foliage.season.TreeOrder;
import io.github.superveryfastcar.foliage.shape.CardPicture;
import io.github.superveryfastcar.foliage.shape.LeafLook;
import io.github.superveryfastcar.foliage.shape.LeafPicture;
import io.github.superveryfastcar.foliage.shape.LeafPictures;
import io.github.superveryfastcar.foliage.shape.LeafShape;
import io.github.superveryfastcar.foliage.shape.MiniBranch;
import io.github.superveryfastcar.foliage.shape.PictureShares;
import io.github.superveryfastcar.foliage.shape.Recipe;
import io.github.superveryfastcar.foliage.shape.ShapeRules;
import io.github.superveryfastcar.foliage.shape.ShapeSettings;
import io.github.superveryfastcar.foliage.shape.Surround;
import io.github.superveryfastcar.foliage.shape.WinterTwigs;
import io.github.superveryfastcar.foliage.util.PosHash;
import io.github.superveryfastcar.seasons.api.SeasonsApi;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Automatic tests for the leaf calendar: when a leaf spot shows what, and
 * what it is drawn from at each stage. Like {@link FoliageGameTests} they
 * run with {@code ./gradlew runGameTestServer}.
 *
 * <h2>Why no test changes the date</h2>
 * All tests share one world and run side by side, and the date belongs to
 * the world. A test that moved the date would pull the ground from under
 * the others. The leaf calendar does not need it: a {@link LeafClock} is
 * made for any step of the year by hand, and the tests walk through the
 * year by making one clock after another.
 *
 * <p>How a real tree goes through the year is tested with the trees of
 * {@link FoliageGameTests}; what the game client draws, by the self test in
 * the {@code dev} package.</p>
 */
@GameTestHolder(Foliage.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LeafCalendarGameTests {

    private static final Direction[] DIRECTIONS = Direction.values();

    /** The seasons of the Calendar mod as it ships: four of 14 days, the year starts with spring. */
    private static final CalendarRules YEAR = CalendarRules.DEFAULT;

    /**
     * The schedules of the three kinds that shed by default. The birch of
     * these tests sheds from the twigs outward, the other order, so that
     * both orders are tested; the mod's own birch goes the usual way.
     */
    private static final LeafSchedule BIRCH = new LeafSchedule(0, TreeOrder.INSIDE_OUT);
    private static final LeafSchedule OAK = new LeafSchedule(1, TreeOrder.OUTSIDE_IN);
    private static final LeafSchedule DARK_OAK = new LeafSchedule(2, TreeOrder.OUTSIDE_IN);

    /** The order of the stages in autumn and in spring; a spot only ever moves forward in it. */
    private static final List<LeafStage> FALL = List.of(LeafStage.FULL, LeafStage.THIN, LeafStage.THINNER, LeafStage.LAST, LeafStage.BARE);
    private static final List<LeafStage> RETURN = List.of(LeafStage.BARE, LeafStage.BUDS, LeafStage.FIRST, LeafStage.HALF, LeafStage.FULL);

    private LeafCalendarGameTests() {
    }

    // ------------------------------------------------------------------
    // Small helpers
    // ------------------------------------------------------------------

    /** The first step of a day of a season (day 0 is the season's first), for the rules given. */
    static long stepOn(SeasonSettings settings, CalendarRules rules, Season season, int day) {
        return (long) (rules.firstDayOfYear(season) - 1 + day) * settings.stepsPerDay();
    }

    /** How many steps a year has. */
    static long stepsPerYear(SeasonSettings settings, CalendarRules rules) {
        return (long) rules.yearLength() * settings.stepsPerDay();
    }

    /** A made-up leaf spot: a position and a depth in the crown, fixed for a number. */
    private record Spot(int x, int y, int z, int depth) {

        static Spot number(int number) {
            long hash = PosHash.hash(0x53706F74L, number, 17);
            return new Spot((int) (hash & 1023) - 512, 60 + (int) (hash >> 10 & 63), (int) (hash >> 16 & 1023) - 512, 1 + (int) (hash >> 26 & 3));
        }

        LeafStage at(LeafClock clock, LeafSchedule schedule) {
            return clock.stage(schedule, x, y, z, depth);
        }
    }

    // ------------------------------------------------------------------
    // The calendar as arithmetic
    // ------------------------------------------------------------------

    /** The step of the year follows the day and the time of day. */
    @GameTest(template = "empty", batch = "foliage_calendar")
    public static void stepsFollowTheClock(GameTestHelper helper) {
        SeasonSettings settings = SeasonSettings.DEFAULT;
        check(settings.stepsPerDay() == 4, "the mod's own rules move the leaves on 4 times a day");
        check(LeafClock.stepOf(settings, 1, 0) == 0, "the year starts with step 0");
        check(LeafClock.stepOf(settings, 1, 5999) == 0 && LeafClock.stepOf(settings, 1, 6000) == 1, "a step lasts a quarter of a day");
        check(LeafClock.stepOf(settings, 1, 23999) == 3 && LeafClock.stepOf(settings, 2, 0) == 4, "the last step of a day, the first of the next");
        check(LeafClock.stepOf(settings, 3, 99999) == 11 && LeafClock.stepOf(settings, 3, -5) == 8, "a time of day out of range stays within its day");
        SeasonSettings once = settings.withStepsPerDay(1);
        check(LeafClock.stepOf(once, 5, 0) == 4 && LeafClock.stepOf(once, 5, 23999) == 4, "with one step a day, the step is the day");
        // The year wraps around: a clock a whole year on shows the same.
        long year = stepsPerYear(settings, YEAR);
        for (long step = 0; step < year; step += 7) {
            LeafClock clock = LeafClock.of(settings, YEAR, step);
            LeafClock next = LeafClock.of(settings, YEAR, step + year);
            LeafClock before = LeafClock.of(settings, YEAR, step - year);
            for (int number = 0; number < 20; number++) {
                Spot spot = Spot.number(number);
                check(spot.at(clock, OAK) == spot.at(next, OAK) && spot.at(clock, OAK) == spot.at(before, OAK),
                        "a year on and a year back, a spot shows the same, at step " + step);
            }
        }
        helper.succeed();
    }

    /**
     * The whole year, step by step, for many made-up leaf spots of each
     * kind: full in summer, bare in winter, and in between every spot
     * moves forward through the stages and never back.
     */
    @GameTest(template = "empty", batch = "foliage_calendar")
    public static void theYearStepByStep(GameTestHelper helper) {
        SeasonSettings settings = SeasonSettings.DEFAULT;
        long year = stepsPerYear(settings, YEAR);
        int perDay = settings.stepsPerDay();
        long returnEnd = stepOn(settings, YEAR, Season.SPRING, 7);
        long fallStart = stepOn(settings, YEAR, Season.WINTER, 0) - 7L * perDay;
        long winterStart = stepOn(settings, YEAR, Season.WINTER, 0);
        check(stepOn(settings, YEAR, Season.SPRING, 0) == 0, "the Calendar mod's year starts with spring");
        int spots = 400;
        Map<String, LeafSchedule> kinds = new LinkedHashMap<>();
        kinds.put("birch", BIRCH);
        kinds.put("oak", OAK);
        kinds.put("dark oak", DARK_OAK);
        Map<String, Double> meanFallStart = new LinkedHashMap<>();
        for (Map.Entry<String, LeafSchedule> kind : kinds.entrySet()) {
            LeafSchedule schedule = kind.getValue();
            int[] fallPlace = new int[spots];
            int[] returnPlace = new int[spots];
            long firstChange = 0;
            int[][] seen = new int[2][5];
            LeafClock previous = null;
            for (long step = 0; step < year; step++) {
                LeafClock clock = LeafClock.of(settings, YEAR, step);
                String when = kind.getKey() + " at step " + step;
                boolean inReturn = step < returnEnd;
                boolean inFall = step >= fallStart && step < winterStart;
                check(clock.allFull() == (step >= returnEnd && step < fallStart), "all in full leaf exactly between the two windows: " + when);
                check(clock.allBare() == (step >= winterStart), "all bare exactly in winter: " + when);
                if (previous != null) {
                    boolean quiet = !inReturn && !inFall && step != returnEnd && step != fallStart && step != winterStart;
                    check(clock.looksDifferentFrom(previous) == !quiet, "a step changes the look exactly in and at the ends of the windows: " + when);
                }
                previous = clock;
                for (int number = 0; number < spots; number++) {
                    Spot spot = Spot.number(number);
                    LeafStage stage = spot.at(clock, schedule);
                    check(stage == spot.at(clock, schedule), "the same question gives the same answer: " + when);
                    if (inReturn) {
                        int place = RETURN.indexOf(stage);
                        check(place >= returnPlace[number], "in spring a spot only moves forward, but went from "
                                + RETURN.get(returnPlace[number]) + " to " + stage + ": " + when);
                        returnPlace[number] = place;
                        seen[0][place]++;
                    } else if (inFall) {
                        int place = FALL.indexOf(stage);
                        check(place >= fallPlace[number], "in autumn a spot only moves forward, but went from "
                                + FALL.get(fallPlace[number]) + " to " + stage + ": " + when);
                        if (place > 0 && fallPlace[number] == 0) {
                            firstChange += step - fallStart;
                        }
                        fallPlace[number] = place;
                        seen[1][place]++;
                    } else {
                        check(stage == (step >= winterStart ? LeafStage.BARE : LeafStage.FULL), "outside the windows: full in summer, bare in winter: " + when);
                    }
                }
                if (step == 0) {
                    for (int number = 0; number < spots; number++) {
                        check(returnPlace[number] <= 1, "on the first step of spring nothing is out but buds: " + when);
                    }
                }
            }
            for (int number = 0; number < spots; number++) {
                // A spot may hold its last few leaves until the very last step of autumn; winter takes them.
                check(fallPlace[number] >= 3, "by the end of autumn every spot is down to its last leaves or bare (" + kind.getKey() + ")");
                check(returnPlace[number] >= 3, "by the end of the first week of spring every spot is half grown or full (" + kind.getKey() + ")");
            }
            for (int place = 0; place < 5; place++) {
                check(seen[0][place] > 0, kind.getKey() + " shows " + RETURN.get(place) + " in spring");
                check(seen[1][place] > 0, kind.getKey() + " shows " + FALL.get(place) + " in autumn");
            }
            meanFallStart.put(kind.getKey(), firstChange / (double) spots / perDay);
        }
        Foliage.LOGGER.info("Foliage test: days into the fall at which a leaf spot starts to thin, on average: {}", meanFallStart);
        double birch = meanFallStart.get("birch");
        double oak = meanFallStart.get("oak");
        double darkOak = meanFallStart.get("dark oak");
        check(birch + 0.5 < oak && oak + 0.5 < darkOak, "birch goes first, then oak, then dark oak: " + meanFallStart);
        helper.succeed();
    }

    /**
     * Within one tree the spots go in order of their depth in the crown:
     * from the far edge inward or from the twigs outward in autumn, as the
     * kind says, and always from the twigs outward in spring.
     */
    @GameTest(template = "empty", batch = "foliage_calendar")
    public static void spotsGoInOrder(GameTestHelper helper) {
        // No delay by place, so that all made-up spots belong to "one tree".
        SeasonSettings settings = new SeasonSettings(true, 7, 7, 4, 1, 0, 16, 4, 0.4, 0.6, 4, Map.of());
        long fallStart = stepOn(settings, YEAR, Season.WINTER, 0) - 28;
        for (TreeOrder order : TreeOrder.values()) {
            LeafSchedule schedule = new LeafSchedule(0, order);
            // When, on average, the spots of each depth have lost all leaves, and when they are in full leaf again.
            double[] bareAt = new double[5];
            double[] fullAt = new double[5];
            int[] count = new int[5];
            for (int number = 0; number < 2000; number++) {
                Spot spot = Spot.number(number);
                count[spot.depth()]++;
                for (long step = 0; step <= 28; step++) {
                    if (spot.at(LeafClock.of(settings, YEAR, fallStart + step), schedule) != LeafStage.BARE) {
                        bareAt[spot.depth()] += 1;
                    }
                    if (spot.at(LeafClock.of(settings, YEAR, step), schedule) != LeafStage.FULL) {
                        fullAt[spot.depth()] += 1;
                    }
                }
            }
            for (int depth = 1; depth <= 4; depth++) {
                check(count[depth] > 300, "enough made-up spots of depth " + depth);
                bareAt[depth] /= count[depth];
                fullAt[depth] /= count[depth];
            }
            String what = order + ": steps until bare by depth 1 to 4: " + String.format(Locale.ROOT, "%.1f %.1f %.1f %.1f; until full: %.1f %.1f %.1f %.1f",
                    bareAt[1], bareAt[2], bareAt[3], bareAt[4], fullAt[1], fullAt[2], fullAt[3], fullAt[4]);
            Foliage.LOGGER.info("Foliage test: {}", what);
            for (int depth = 1; depth < 4; depth++) {
                if (order == TreeOrder.OUTSIDE_IN) {
                    check(bareAt[depth] + 0.5 < bareAt[depth + 1], "outside in: the far edge is bare first. " + what);
                } else {
                    check(bareAt[depth] > bareAt[depth + 1] + 0.5, "inside out: the spots at the twigs are bare first. " + what);
                }
                check(fullAt[depth] > fullAt[depth + 1] + 0.5, "in spring the spots at the twigs are in leaf first. " + what);
            }
        }
        // With the order strictly by depth and no delay by place, spots of one depth change together.
        SeasonSettings strict = new SeasonSettings(true, 7, 7, 4, 1, 0, 16, 4, 0.5, 1.0, 4, Map.of());
        for (long step = 0; step < stepsPerYear(strict, YEAR); step++) {
            LeafClock clock = LeafClock.of(strict, YEAR, step);
            LeafStage[] byDepth = new LeafStage[5];
            for (int number = 0; number < 200; number++) {
                Spot spot = Spot.number(number);
                LeafStage stage = spot.at(clock, OAK);
                check(byDepth[spot.depth()] == null || byDepth[spot.depth()] == stage, "without scatter and delay, one depth is one stage, at step " + step);
                byDepth[spot.depth()] = stage;
            }
        }
        helper.succeed();
    }

    /** Trees start apart by where they stand, but by no more than the rules allow, and neighbors start together. */
    @GameTest(template = "empty", batch = "foliage_calendar")
    public static void treesStartApart(GameTestHelper helper) {
        // Strictly by depth and all of a tree at once, so that the only thing that differs between places is the delay.
        SeasonSettings settings = new SeasonSettings(true, 7, 7, 4, 1, 1, 16, 4, 1.0, 1.0, 4, Map.of());
        long fallStart = stepOn(settings, YEAR, Season.WINTER, 0) - 28;
        int earliest = Integer.MAX_VALUE;
        int latest = 0;
        int nextDoorApart = 0;
        for (int x = 0; x < 64; x++) {
            for (int z = 0; z < 64; z++) {
                int here = firstFallStep(settings, fallStart, x * 5, z * 5);
                check(here <= 28, "every tree starts within the window");
                earliest = Math.min(earliest, here);
                latest = Math.max(latest, here);
                // A tree is asked about all its columns: one block on must be nearly the same moment.
                int beside = firstFallStep(settings, fallStart, x * 5 + 1, z * 5);
                nextDoorApart = Math.max(nextDoorApart, Math.abs(here - beside));
            }
        }
        Foliage.LOGGER.info("Foliage test: birches start to shed between step {} and step {} of the fall; next-door columns at most {} step(s) apart",
                earliest, latest, nextDoorApart);
        check(latest - earliest >= 2, "trees do not all start in the same step: " + earliest + " to " + latest);
        check(latest - earliest <= settings.stepsPerDay() + 1, "but no more than the allowed day apart: " + earliest + " to " + latest);
        check(nextDoorApart <= 1, "columns next to each other start together: " + nextDoorApart);
        // Without a delay all start in the same step.
        SeasonSettings together = new SeasonSettings(true, 7, 7, 4, 1, 0, 16, 4, 1.0, 1.0, 4, Map.of());
        int first = firstFallStep(together, fallStart, 0, 0);
        for (int number = 0; number < 200; number++) {
            Spot spot = Spot.number(number);
            check(firstFallStep(together, fallStart, spot.x(), spot.z()) == first, "without a delay every tree starts in the same step");
        }
        helper.succeed();
    }

    /** The first step of the fall at which a birch spot at the far edge of a crown is no longer in full leaf. */
    private static int firstFallStep(SeasonSettings settings, long fallStart, int x, int z) {
        for (int step = 0; step <= 28; step++) {
            if (LeafClock.of(settings, YEAR, fallStart + step).stage(BIRCH, x, 70, z, 1) != LeafStage.FULL) {
                return step;
            }
        }
        return 99;
    }

    /** Seasons shorter than the windows, shedding switched off, and long or late stretches: every tree is still done in time. */
    @GameTest(template = "empty", batch = "foliage_calendar")
    public static void oddRulesStillWork(GameTestHelper helper) {
        SeasonSettings standard = SeasonSettings.DEFAULT;
        SeasonSettings[] rulesOfLeaves = {
                standard,
                standard.withDays(30, 30, 30),
                standard.withDays(0.25, 0.25, 0.25),
                standard.withDays(3, 3, 20),
                standard.withStepsPerDay(1),
                standard.withStepsPerDay(48),
                new SeasonSettings(true, 2, 2, 1, 30, 30, 1, 3, 0.05, 0.0, 7, Map.of())};
        CalendarRules[] years = {YEAR, new CalendarRules(1, 1, 1, 1, true), new CalendarRules(2, 5, 3, 9, false), new CalendarRules(30, 2, 30, 1, true)};
        LeafSchedule late = new LeafSchedule(LeafSchedule.MAX_START_DAY, TreeOrder.INSIDE_OUT);
        for (SeasonSettings settings : rulesOfLeaves) {
            for (CalendarRules rules : years) {
                long year = stepsPerYear(settings, rules);
                long winterStart = stepOn(settings, rules, Season.WINTER, 0);
                long summerStart = stepOn(settings, rules, Season.SUMMER, 0);
                long autumnStart = stepOn(settings, rules, Season.AUTUMN, 0);
                for (LeafSchedule schedule : new LeafSchedule[] {BIRCH, OAK, DARK_OAK, late}) {
                    int[] fallPlace = new int[60];
                    int[] returnPlace = new int[60];
                    for (long step = 0; step < year; step++) {
                        LeafClock clock = LeafClock.of(settings, rules, step);
                        String when = settings + " in " + rules + " at step " + step;
                        for (int number = 0; number < 60; number++) {
                            Spot spot = Spot.number(number);
                            LeafStage stage = spot.at(clock, schedule);
                            if (step >= winterStart) {
                                check(stage == LeafStage.BARE, "all winter every spot is bare: " + when);
                            } else if (step >= summerStart && step < autumnStart) {
                                check(stage == LeafStage.FULL, "all summer every spot is in full leaf: " + when);
                            } else if (step < summerStart) {
                                check(RETURN.indexOf(stage) >= returnPlace[number], "spring only moves forward: " + when);
                                returnPlace[number] = RETURN.indexOf(stage);
                            } else {
                                check(FALL.indexOf(stage) >= fallPlace[number], "autumn only moves forward: " + when);
                                fallPlace[number] = FALL.indexOf(stage);
                            }
                        }
                    }
                }
                // Switched off: nothing ever changes.
                SeasonSettings off = settings.withEnabled(false);
                for (long step = 0; step < year; step += Math.max(1, year / 97)) {
                    LeafClock clock = LeafClock.of(off, rules, step);
                    check(clock.allFull() && !clock.allBare() && Spot.number(3).at(clock, OAK) == LeafStage.FULL
                            && !clock.looksDifferentFrom(null), "switched off, every spot is in full leaf all year");
                }
            }
        }
        helper.succeed();
    }

    /** Numbers outside their range are pulled back into it, and the list of kinds is read from and written to lines of text. */
    @GameTest(template = "empty", batch = "foliage_calendar")
    public static void rulesStayInRange(GameTestHelper helper) {
        SeasonSettings wild = new SeasonSettings(true, -4, 9999, Double.NaN, -1, 99, 0, 500, 7.0, -2.0, 99, null);
        check(wild.fallDays() == SeasonSettings.MIN_DAYS && wild.returnDays() == SeasonSettings.MAX_DAYS, "the windows in range");
        check(wild.treeDays() == 4.0, "a number that is none becomes the mod's own: " + wild.treeDays());
        check(wild.budDays() == 0.0 && wild.shiftDays() == SeasonSettings.MAX_EXTRA_DAYS, "buds and delay in range");
        check(wild.shiftPatch() == SeasonSettings.MIN_PATCH && wild.stepsPerDay() == SeasonSettings.MAX_STEPS, "patch and steps in range");
        check(wild.spotShare() == 1.0 && wild.depthWeight() == 0.0 && wild.deepest() == SeasonSettings.MAX_DEEPEST, "shares and depth in range");
        check(wild.kinds().isEmpty(), "no kinds is an empty list");
        LeafSchedule schedule = new LeafSchedule(-3, null);
        check(schedule.startDay() == 0.0 && schedule.order() == TreeOrder.OUTSIDE_IN, "a schedule out of range is pulled back");
        check(new LeafSchedule(1e9, TreeOrder.INSIDE_OUT).startDay() == LeafSchedule.MAX_START_DAY, "a kind starts at most " + LeafSchedule.MAX_START_DAY + " days in");

        SeasonSettings standard = SeasonSettings.DEFAULT;
        check(standard.enabled() && standard.fallDays() == 7.0 && standard.returnDays() == 7.0,
                "the mod's own rules: leaves fall over 7 days and come back over 7");
        check(standard.kinds().size() == 3
                        && new LeafSchedule(0, TreeOrder.OUTSIDE_IN).equals(standard.kinds().get(ResourceLocation.parse("dynamictrees:birch_leaves")))
                        && OAK.equals(standard.kinds().get(ResourceLocation.parse("dynamictrees:oak_leaves")))
                        && DARK_OAK.equals(standard.kinds().get(ResourceLocation.parse("dynamictrees:dark_oak_leaves"))),
                "oak, birch and dark oak shed: " + standard.kinds());
        for (ResourceLocation id : standard.kinds().keySet()) {
            check(BuiltInRegistries.BLOCK.get(id) instanceof DynamicLeavesBlock, id + " is a leaf block of Dynamic Trees");
        }
        // The lines of the settings file.
        List<String> lines = SeasonSettings.defaultKindLines();
        check(lines.size() == 3 && lines.contains("dynamictrees:oak_leaves, 1, outside_in") && lines.contains("dynamictrees:birch_leaves, 0, outside_in"),
                "the kinds as lines: " + lines);
        check(SeasonSettings.parseKinds(lines).equals(standard.kinds()), "lines read back give the same kinds");
        check(SeasonSettings.isKindLine("some_mod:maple_leaves") && SeasonSettings.isKindLine(" a:b , 2.5 , INSIDE_OUT "), "day and order may be left out, spaces and capitals do not matter");
        Map.Entry<ResourceLocation, LeafSchedule> maple = SeasonSettings.parseKind("some_mod:maple_leaves,2.5");
        check(maple != null && maple.getKey().toString().equals("some_mod:maple_leaves") && maple.getValue().startDay() == 2.5
                && maple.getValue().order() == TreeOrder.OUTSIDE_IN, "a line with a part of a day");
        check(SeasonSettings.formatKind(maple.getKey(), maple.getValue()).equals("some_mod:maple_leaves, 2.5, outside_in"), "and written back");
        for (Object bad : new Object[] {"", " ", "Not An Id", "a:b, x", "a:b, -1", "a:b, 61", "a:b, 1, sideways", "a:b, 1, outside_in, more", 5, null}) {
            check(!SeasonSettings.isKindLine(bad), "not a line of the list of kinds: " + bad);
        }
        Map<ResourceLocation, LeafSchedule> twice = SeasonSettings.parseKinds(List.of("a:b, 1", "nonsense here", "a:b, 3, inside_out"));
        check(twice.size() == 1 && twice.get(ResourceLocation.parse("a:b")).equals(new LeafSchedule(3, TreeOrder.INSIDE_OUT)),
                "a block named twice keeps its last line, a bad line is left out: " + twice);
        check(standard.withEnabled(false).withEnabled(true).equals(standard) && standard.withKinds(Map.of()).kinds().isEmpty()
                && standard.withStepsPerDay(8).stepsPerDay() == 8 && standard.withDays(3, 5, 2).returnDays() == 5.0, "changed copies");
        // A clock only knows the kinds whose blocks exist.
        LeafClock clock = LeafClock.of(standard.withKinds(Map.of(ResourceLocation.parse("nobody:nothing"), OAK)), YEAR, 0);
        check(!clock.hasKinds() && clock.schedule(Blocks.OAK_LEAVES) == null, "a kind whose block does not exist is left out");
        LeafClock usual = LeafClock.of(standard, YEAR, 0);
        check(usual.hasKinds() && OAK.equals(usual.schedule(BuiltInRegistries.BLOCK.get(ResourceLocation.parse("dynamictrees:oak_leaves"))))
                && usual.schedule(Blocks.OAK_LEAVES) == null && usual.schedule(Blocks.STONE) == null,
                "the clock finds the schedule of Dynamic Trees' oak leaves and of nothing else");
        helper.succeed();
    }

    /** The rules arrive at a player's game exactly as the server has them. */
    @GameTest(template = "empty", batch = "foliage_calendar")
    public static void rulesTravelUnchanged(GameTestHelper helper) {
        SeasonSettings odd = new SeasonSettings(false, 3.5, 11.25, 2.75, 0.5, 0.125, 77, 9, 0.33, 0.91, 6,
                Map.of(ResourceLocation.parse("some_mod:maple_leaves"), new LeafSchedule(2.5, TreeOrder.INSIDE_OUT),
                        ResourceLocation.parse("dynamictrees:oak_leaves"), new LeafSchedule(0, TreeOrder.OUTSIDE_IN)));
        for (SeasonSettings settings : new SeasonSettings[] {SeasonSettings.DEFAULT, odd, SeasonSettings.DEFAULT.withKinds(Map.of())}) {
            ByteBuf buffer = Unpooled.buffer();
            LeafCalendarPayload.STREAM_CODEC.encode(buffer, new LeafCalendarPayload(settings));
            LeafCalendarPayload back = LeafCalendarPayload.STREAM_CODEC.decode(buffer);
            check(back.settings().equals(settings), "sent " + settings + ", arrived " + back.settings());
            check(buffer.readableBytes() == 0, "nothing of the message is left over");
            buffer.release();
        }
        check(LeafCalendarPayload.TYPE.id().toString().equals("foliage:leaf_calendar"), "the message's name");
        // What a game draws by: its own file until a server sends rules, the server's from then on.
        check(!LeafCalendar.hasSentSettings() && LeafCalendar.clientSettings().equals(LeafCalendar.serverSettings()),
                "without a message from a server, a game uses its own settings file");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // The pieces at each stage
    // ------------------------------------------------------------------

    /**
     * What a leaf block is drawn from at every stage, for every possible
     * set of open sides, with and without wood (that comes to the block's
     * side or does not), a heart side, snow on top and a mini branch, in
     * every shape, at the lowest and the highest limit.
     */
    @GameTest(template = "empty", batch = "foliage_calendar")
    public static void piecesAtEveryStage(GameTestHelper helper) {
        ShapeSettings standard = ShapeSettings.DEFAULT;
        for (LeafShape shape : LeafShape.values()) {
            for (int limit : new int[] {ShapeSettings.MIN_FACES, ShapeSettings.MAX_FACES}) {
                for (int fast = 0; fast < 2; fast++) {
                    ShapeSettings settings = standard.withShape(shape).withMaxFaces(limit).withFastLeaves(fast == 1);
                    for (int open = 0; open < 64; open++) {
                        for (int wood = -1; wood < 6; wood++) {
                            for (int heart = -1; heart < 6; heart += 3) {
                                for (int loaded = 0; loaded < 2; loaded++) {
                                    int around = Surround.of(open, wood < 0 ? null : DIRECTIONS[wood], heart < 0 ? null : DIRECTIONS[heart], loaded == 1);
                                    for (int variant = 0; variant < Recipe.VARIANTS; variant += 5) {
                                        checkPieces(settings, around, variant);
                                        // Next to a branch whose wood comes right to the block's side.
                                        if (wood >= 0) {
                                            checkPieces(settings, Surround.woodReaches(around, true), variant);
                                        }
                                        // The leaf block it hangs on to has lost its leaves or does not have them yet.
                                        if (heart >= 0) {
                                            checkPieces(settings, Surround.heartBare(around, true), variant);
                                        }
                                        // Inside a crown: no branch touches, nothing but leaves and full blocks around.
                                        if (wood < 0 && loaded == 0) {
                                            checkPieces(settings, Surround.sheltered(around, true), variant);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        // Without buds, and with fewer thinner twigs.
        for (int open = 1; open < 64; open += 7) {
            int around = Surround.of(open, Direction.DOWN, null, false);
            checkPieces(standard.withBudSize(0), around, 3);
            for (int forks = 0; forks <= MiniBranch.MAX_FORKS; forks++) {
                checkPieces(standard.withTwigForks(forks), around, 3);
            }
        }
        // Which spots have a mini branch.
        for (int depth = 1; depth <= 7; depth++) {
            for (int wood = -1; wood < 6; wood++) {
                int around = Surround.of(12, wood < 0 ? null : DIRECTIONS[wood], Direction.DOWN, false);
                check(!ShapeRules.twigSpot(standard.withWinterTwigs(WinterTwigs.NONE), around, depth), "no twigs: no spot has one");
                check(ShapeRules.twigSpot(standard.withWinterTwigs(WinterTwigs.NEAR), around, depth) == (wood >= 0), "twigs near branches: where a branch touches");
                check(ShapeRules.twigSpot(standard.withWinterTwigs(WinterTwigs.MORE), around, depth) == (wood >= 0 || depth >= standard.twigDepth()),
                        "more twigs: where a branch touches, or deep in the crown");
                // Deep in the crown, but with no leaf spot to grow out of: none.
                int alone = Surround.of(12, wood < 0 ? null : DIRECTIONS[wood], null, false);
                check(ShapeRules.twigSpot(standard.withWinterTwigs(WinterTwigs.MORE), alone, depth) == (wood >= 0),
                        "more twigs: a spot that touches no wood needs a deeper leaf spot to grow out of");
                // Where a mini branch starts.
                int expected = wood < 0 ? MiniBranch.DEEP : MiniBranch.REACH;
                check(ShapeRules.twigBase(around) == expected, "a mini branch starts inside a branch whose bark lies behind the side, "
                        + "and in the middle of a leaf spot where there is no wood");
                check(ShapeRules.twigBase(Surround.woodReaches(around, true)) == (wood < 0 ? MiniBranch.DEEP : MiniBranch.FLUSH),
                        "and at the block's side where the wood comes that far");
            }
        }
        check(standard.winterTwigs() == WinterTwigs.NEAR && standard.twigDepth() == 3, "the mod's own choice: twigs near branches");
        check(standard.twigForks() == MiniBranch.MAX_FORKS && standard.budSize() == 4 && standard.fallSize() == 12 && standard.lastSize() == 8,
                "the mod's own choice: three thinner twigs, buds of 4, clumps of 12 and 8 in autumn");
        helper.succeed();
    }

    /** The checks of {@link #piecesAtEveryStage} for one leaf block. */
    private static void checkPieces(ShapeSettings settings, int around, int variant) {
        int open = Surround.open(around);
        int openCount = Integer.bitCount(open);
        int forks = settings.forks();
        boolean plain = settings.shape() == LeafShape.PLAIN;
        Direction support = Surround.loaded(around) ? null : Surround.support(around);
        Direction grow = Surround.grow(around);
        boolean sheltered = Surround.sheltered(around);
        int base = ShapeRules.twigBase(around);
        int branchFaces = MiniBranch.faceCount(base, forks);
        check(!Surround.sheltered(Surround.sheltered(around, false)) && Surround.sheltered(Surround.sheltered(around, true))
                && Surround.open(Surround.sheltered(around, true)) == open, "the shelter mark is kept and changes nothing else");
        check(Surround.woodReaches(Surround.woodReaches(around, true)) && !Surround.woodReaches(Surround.woodReaches(around, false))
                && Surround.open(Surround.woodReaches(around, true)) == open && Surround.wood(Surround.woodReaches(around, true)) == Surround.wood(around),
                "the mark for wood at the block's side is kept and changes nothing else");
        int full = ShapeRules.recipe(settings, around, variant, LeafStage.FULL, false);
        check(full == ShapeRules.recipe(settings, around, variant), "in full leaf: the recipe of the shapes");
        if (sheltered && open != 0) {
            // Seen between neighbors that have lost their leaves: the outside of the block and nothing more.
            check(Recipe.faces(full) && Recipe.quadCount(full, forks) == openCount, "in leaf inside the crown: outer faces only, open " + open);
        }
        for (LeafStage stage : LeafStage.values()) {
            for (int twig = 0; twig < 2; twig++) {
                boolean twigSpot = twig == 1;
                int recipe = ShapeRules.recipe(settings, around, variant, stage, twigSpot);
                String what = settings.shape() + " limit " + settings.maxFaces() + (settings.fastLeaves() ? " fast" : "") + " " + stage
                        + (twigSpot ? " twig spot" : "") + " open " + open + " wood " + Surround.wood(around)
                        + (Surround.woodReaches(around) ? " at the side" : "") + " heart " + Surround.heart(around)
                        + (Surround.heartBare(around) ? " (bare)" : "") + (Surround.loaded(around) ? " loaded" : "") + (sheltered ? " sheltered" : "") + " variant " + variant;
                check(recipe >= 0 && recipe != Recipe.NONE, "a recipe is never negative: " + what);
                check(Recipe.stage(recipe) == stage && Recipe.open(recipe) == open && Recipe.variant(recipe) == variant, "stage, open sides and variant kept: " + what);
                // The limit is about the leaves, and the stick under a clump. A mini branch is counted apart.
                int leaves = Recipe.leafQuadCount(recipe);
                int count = Recipe.quadCount(recipe, forks);
                check(leaves + (Recipe.stemOnly(recipe) ? Recipe.FACES_PER_STEM : 0) <= settings.maxFaces(),
                        "never more faces of leaves than the limit, got " + leaves + ": " + what);
                check(count == leaves + Recipe.twigFaces(recipe, forks), "all faces are those of the leaves and those of the mini branch: " + what);
                if (Recipe.twig(recipe) != null) {
                    check(Recipe.twig(recipe) == grow, "a mini branch grows away from the side its spot hangs on to: " + what);
                    // Under leaves that are thinning the main twig alone is drawn: the thinner ones would be buried.
                    int whole = stage == LeafStage.THINNER ? MiniBranch.faceCount(base, 0) : branchFaces;
                    check(Recipe.twigFaces(recipe, forks) == (Recipe.stemOnly(recipe) ? Recipe.FACES_PER_STEM : whole),
                            "a mini branch has five faces and five for each thinner twig, a stick four: " + what);
                    check(Recipe.forks(recipe, forks) == (stage == LeafStage.THINNER ? 0 : forks), "thinner twigs at every stage but under thinning leaves: " + what);
                    check(Recipe.stemOnly(recipe) || Recipe.twigBase(recipe) == base, "it starts where the wood is: " + what);
                }
                // Where leaves cannot be see-through.
                boolean wholeBlock = stage == LeafStage.FULL || stage == LeafStage.THIN;
                boolean most = stage == LeafStage.THINNER || stage == LeafStage.HALF;
                int solid = wholeBlock ? openCount : (most && Recipe.faces(recipe) ? openCount : 0) + Recipe.clumpFaces(recipe) + Recipe.twigFaces(recipe, forks);
                check(Recipe.solidWhole(recipe) == wholeBlock && Recipe.solidOuter(recipe) == (wholeBlock || most && Recipe.faces(recipe))
                                && Recipe.solidQuadCount(recipe, forks) == solid,
                        "where leaves are not see-through: a whole block in leaf, then the clump and the mini branch, never a flat picture: " + what);
                if (open == 0) {
                    check(count == 0, "a buried block shows nothing at any stage: " + what);
                    continue;
                }
                if (stage.leafless()) {
                    if (!twigSpot) {
                        check(count == 0, "without leaves and without a mini branch: nothing: " + what);
                        continue;
                    }
                    boolean bud = stage == LeafStage.BUDS && settings.budSize() != 0;
                    check(Recipe.twig(recipe) == grow && !Recipe.stemOnly(recipe) && !Recipe.faces(recipe)
                                    && Recipe.planeCount(recipe) == 0 && Recipe.cards(recipe) == 0,
                            "without leaves: the mini branch on a twig spot, growing away from the branch: " + what);
                    check(Recipe.clump(recipe) == (bud ? settings.budSize() : 0) && Recipe.tuft(recipe) == bud && count == branchFaces + (bud ? 6 : 0),
                            "and nothing else, but a bud on the end of the main twig before the first leaves: " + what);
                    continue;
                }
                if (plain) {
                    check(Recipe.faces(recipe) && count == openCount, "plain: the outer faces and nothing else, at every stage with leaves: " + what);
                    continue;
                }
                // Few leaves next to a leaf block that has none: nothing at all is drawn.
                boolean inTheAir = (stage == LeafStage.LAST || stage == LeafStage.FIRST) && !twigSpot && support != null && Surround.supportBare(around);
                check(Recipe.faces(recipe) != (Recipe.clump(recipe) != 0) || Recipe.planeCount(recipe) != 0 || inTheAir,
                        "outer faces or a clump, never both; neither only where planes stand in: " + what);
                switch (stage) {
                    case FULL:
                        check(twigSpot == (twig == 1) && recipe == full, "in full leaf a mini branch is hidden and not drawn: " + what);
                        break;
                    case THIN:
                        check(recipe == Recipe.withStage(full, stage), "thinning: the pieces of full leaf, with another picture: " + what);
                        break;
                    case THINNER:
                    case HALF: {
                        boolean fall = stage == LeafStage.THINNER;
                        int wanted = fall ? settings.fallSize() : settings.halfSize();
                        if (sheltered) {
                            check(Recipe.faces(recipe) && count == openCount, "inside the crown: the outer faces and nothing else: " + what);
                        } else if (support == null) {
                            check(Recipe.faces(recipe) && Recipe.clump(recipe) == 0 && Recipe.twig(recipe) == null && (fall || Recipe.cards(recipe) == 0),
                                    "with nothing to hang on to: a whole block: " + what);
                        } else {
                            // A clump against the side the block hangs on to; a spot that is a clump in full leaf keeps that one.
                            int expected = Recipe.clump(full) != 0 ? Recipe.withStage(full, stage)
                                    : Recipe.withClump(Recipe.withStage(Recipe.start(open, variant), stage), wanted, support);
                            if (fall && twigSpot && Recipe.twig(expected) == null) {
                                // In autumn the mini branch shows through the thinned leaves.
                                expected = Recipe.withTwig(expected, grow, base);
                            }
                            check(recipe == expected, (fall ? "most leaves gone" : "half grown") + ": a clump on the side the block hangs on to: " + what);
                            check(Recipe.clump(recipe) != 0 && !Recipe.faces(recipe) && Recipe.planeCount(recipe) == 0 && Recipe.cards(recipe) == 0,
                                    "a clump and no flat pieces: " + what);
                        }
                        break;
                    }
                    case LAST:
                    case FIRST: {
                        int wanted = stage == LeafStage.LAST ? settings.lastSize() : settings.firstSize();
                        check(!Recipe.faces(recipe) && Recipe.cards(recipe) == 0, "few leaves: no outer faces, no cards: " + what);
                        if (twigSpot) {
                            check(Recipe.tuft(recipe) && Recipe.twig(recipe) == grow && Recipe.clump(recipe) > 0 && Recipe.clump(recipe) <= wanted
                                            && Recipe.planeCount(recipe) == 0 && count == branchFaces + 6,
                                    "a mini branch with a small clump on the end of its main twig: " + what);
                        } else if (support != null && Surround.supportBare(around)) {
                            check(count == 0 && Recipe.clump(recipe) == 0 && Recipe.twig(recipe) == null,
                                    "nothing to sit against but a leaf block without leaves: nothing is drawn: " + what);
                        } else if (support != null) {
                            check(Recipe.clump(recipe) > 0 && Recipe.clump(recipe) <= wanted && Recipe.anchor(recipe) == support
                                            && Recipe.twig(recipe) == null && Recipe.planeCount(recipe) == 0 && count == Recipe.clumpFaces(recipe),
                                    "a small clump on the side the block hangs on to: " + what);
                        } else if (sheltered) {
                            check(count == 2 && (Recipe.plane(recipe, Recipe.PLANE_X) || Recipe.plane(recipe, Recipe.PLANE_Z)),
                                    "inside the crown with nothing to hang on to: one upright plane and nothing else: " + what);
                        } else {
                            check(Recipe.plane(recipe, Recipe.PLANE_X) && Recipe.plane(recipe, Recipe.PLANE_Z) && count == 4, "two upright planes: " + what);
                        }
                        break;
                    }
                    default:
                        break;
                }
                // A clump of autumn or spring is never larger than the clump the spot is in full leaf.
                if (Recipe.clump(recipe) != 0 && Recipe.clump(full) != 0) {
                    check(Recipe.clump(recipe) <= Recipe.clump(full), "a spot does not shrink when its leaves are grown, nor grow while it loses them: " + what);
                }
                // And a spot never grows while it loses leaves, nor shrinks while it gets them.
                if (stage == LeafStage.LAST || stage == LeafStage.FIRST) {
                    int larger = Recipe.clump(ShapeRules.recipe(settings, around, variant, stage == LeafStage.LAST ? LeafStage.THINNER : LeafStage.HALF, twigSpot));
                    check(larger == 0 || Recipe.clump(recipe) <= larger, "the clump of few leaves is not larger than the one of most leaves: " + what);
                }
            }
        }
    }

    /**
     * Read from real blocks: a leaf block that has lost its leaves no
     * longer hides the face of its neighbor, a kind that does not shed
     * always does, and in the plain look no leaf block ever does.
     */
    @GameTest(template = "empty", batch = "foliage_calendar")
    public static void bareNeighborsOpenSides(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Block oak = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("dynamictrees:oak_leaves"));
        Block spruce = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("dynamictrees:spruce_leaves"));
        check(oak instanceof DynamicLeavesBlock && spruce instanceof DynamicLeavesBlock, "Dynamic Trees' oak and spruce leaves exist");
        BlockPos leaf = helper.absolutePos(new BlockPos(3, 3, 3));
        check(SeasonsApi.hasWinter(level.getNoiseBiome(leaf.getX() >> 2, leaf.getY() >> 2, leaf.getZ() >> 2).value()),
                "the test world has a winter where the tests stand (" + level.getBiome(leaf).unwrapKey().orElse(null) + ")");
        // Flags 2 and 16: tell the clients, but let no neighbor react.
        level.setBlock(leaf, oak.defaultBlockState().setValue(LeavesBlock.DISTANCE, 2), 2 | 16);
        level.setBlock(leaf.east(), oak.defaultBlockState().setValue(LeavesBlock.DISTANCE, 3), 2 | 16);
        level.setBlock(leaf.west(), spruce.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1), 2 | 16);
        level.setBlock(leaf.below(), Blocks.STONE.defaultBlockState(), 2 | 16);
        BlockState state = level.getBlockState(leaf);
        check(state.is(oak) && level.getBlockState(leaf.east()).is(oak) && level.getBlockState(leaf.west()).is(spruce), "the leaf blocks are placed");

        SeasonSettings rules = SeasonSettings.DEFAULT;
        LeafClock summer = LeafClock.of(rules, YEAR, stepOn(rules, YEAR, Season.SUMMER, 3));
        LeafClock winter = LeafClock.of(rules, YEAR, stepOn(rules, YEAR, Season.WINTER, 3));
        LeafLook look = LeafLook.forThread();
        ShapeSettings mixed = ShapeSettings.DEFAULT;
        ShapeSettings plain = mixed.withShape(LeafShape.PLAIN);
        int x = leaf.getX();
        int y = leaf.getY();
        int z = leaf.getZ();

        // Summer, and no clock at all: as in stage 1.
        int inSummer = look.recipe(mixed, summer, level, level, leaf, state);
        check(inSummer == look.recipe(mixed, null, level, level, leaf, state) && inSummer == look.recipe(mixed, winter, null, level, leaf, state),
                "in summer, without a clock and without biomes a leaf block is drawn the same");
        check(Recipe.stage(inSummer) == LeafStage.FULL && look.stage(summer, level, state, x, y, z) == LeafStage.FULL, "in summer it is in full leaf");
        check(Recipe.open(inSummer) == (1 << Direction.UP.ordinal() | 1 << Direction.NORTH.ordinal() | 1 << Direction.SOUTH.ordinal()),
                "in summer leaf blocks and stone cover their sides; up, north and south are open");
        check(look.recipe(plain, summer, level, level, leaf, state) == Recipe.NONE, "plain and in full leaf: drawn by the model the block had before");

        // The oak block hangs on to the deeper oak block to its east. Whether that one has leaves is passed on.
        BlockPos.MutableBlockPos holder = new BlockPos.MutableBlockPos();
        int nextToLeaves = Surround.read(level, leaf, state, holder, (other, a, b, c) -> Surround.LEAVES);
        int nextToNothing = Surround.read(level, leaf, state, holder, (other, a, b, c) -> Surround.NOTHING);
        check(Surround.heart(nextToLeaves) == Direction.EAST && Surround.isOpen(nextToLeaves, Direction.EAST)
                        && !Surround.heartBare(nextToLeaves) && !Surround.supportBare(nextToLeaves),
                "a thinned neighbor toward the heart no longer covers, but still has leaves to sit against");
        check(Surround.heart(nextToNothing) == Direction.EAST && Surround.heartBare(nextToNothing) && Surround.supportBare(nextToNothing),
                "a neighbor toward the heart without leaves is told apart");
        check(!Surround.heartBare(Surround.read(level, leaf, state)) && !Surround.isOpen(Surround.read(level, leaf, state), Direction.EAST),
                "in full leaf the neighbor toward the heart covers and has its leaves");
        int lastNextToLeaves = ShapeRules.recipe(mixed, nextToLeaves, 0, LeafStage.LAST, false);
        int lastNextToNothing = ShapeRules.recipe(mixed, nextToNothing, 0, LeafStage.LAST, false);
        check(Recipe.clump(lastNextToLeaves) > 0 && Recipe.anchor(lastNextToLeaves) == Direction.EAST && Recipe.quadCount(lastNextToNothing, mixed.forks()) == 0,
                "the last leaves sit against a neighbor that still has leaves, and are gone where it has none");

        // Winter: the oak neighbor is bare and covers nothing; the spruce still does.
        int inWinter = look.recipe(mixed, winter, level, level, leaf, state);
        check(Recipe.stage(inWinter) == LeafStage.BARE && look.stage(winter, level, state, x, y, z) == LeafStage.BARE, "in winter the oak leaf block is bare");
        check(Recipe.open(inWinter) == (Recipe.open(inSummer) | 1 << Direction.EAST.ordinal()), "the bare oak neighbor no longer covers the east side");
        check(Recipe.quadCount(inWinter, mixed.forks()) == 0, "a bare block away from any branch shows nothing");
        BlockState spruceState = level.getBlockState(leaf.west());
        check(look.stage(winter, level, spruceState, x - 1, y, z) == LeafStage.FULL, "spruce keeps its leaves");
        int spruceInWinter = look.recipe(mixed, winter, level, level, leaf.west(), spruceState);
        check(Recipe.stage(spruceInWinter) == LeafStage.FULL && (Recipe.open(spruceInWinter) & 1 << Direction.EAST.ordinal()) != 0,
                "the spruce block's side toward the bare oak block is open");
        int spruceInSummer = look.recipe(mixed, summer, level, level, leaf.west(), spruceState);
        check((Recipe.open(spruceInSummer) & 1 << Direction.EAST.ordinal()) == 0, "and not toward the oak block in leaf");

        // Plain: leaf blocks never cover each other; only the stone below does.
        int plainInWinter = look.recipe(plain, winter, level, level, leaf.west(), spruceState);
        check(plainInWinter == Recipe.NONE, "plain: the spruce block is still drawn by the model it had before");
        int plainOak = look.recipe(plain, winter, level, level, leaf, state);
        check(plainOak != Recipe.NONE && Recipe.stage(plainOak) == LeafStage.BARE && Recipe.open(plainOak) == (63 & ~(1 << Direction.DOWN.ordinal())),
                "plain: the bare oak block is read with no leaf block covering it");

        // A branch next to it makes it a twig spot.
        Block branch = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("dynamictrees:oak_branch"));
        if (branch instanceof BranchBlock wood) {
            // The thinnest branch: Dynamic Trees joins it to its leaves with a stub, so the wood comes to the block's side.
            level.setBlock(leaf.north(), wood.getStateForRadius(1), 2 | 16);
            int thin = Surround.read(level, leaf, state);
            check(Surround.wood(thin) == Direction.NORTH && Surround.woodReaches(thin), "the thinnest branch reaches the side of its leaf block with a stub");
            int withTwig = look.recipe(mixed, winter, level, level, leaf, state);
            check(Recipe.twig(withTwig) == Direction.SOUTH && Recipe.twigBase(withTwig) == MiniBranch.FLUSH
                            && Recipe.quadCount(withTwig, mixed.forks()) == MiniBranch.faceCount(MiniBranch.FLUSH, mixed.forks()),
                    "next to a thin branch the bare block shows a mini branch that starts at the block's side and grows away from the branch");
            check(Recipe.quadCount(withTwig, mixed.forks()) == 17, "a main twig of five faces and three thinner ones of four each");
            check(Recipe.quadCount(look.recipe(mixed.withWinterTwigs(WinterTwigs.NONE), winter, level, level, leaf, state), mixed.forks()) == 0,
                    "unless twigs are switched off");
            check(Recipe.twig(look.recipe(plain, winter, level, level, leaf, state)) == Direction.SOUTH, "also in the plain look");
            // A thicker branch has no stub: its bark lies behind the block's side, and the mini branch starts inside the wood.
            level.setBlock(leaf.north(), wood.getStateForRadius(3), 2 | 16);
            int thick = Surround.read(level, leaf, state);
            check(Surround.wood(thick) == Direction.NORTH && !Surround.woodReaches(thick), "a thicker branch does not reach the side of its leaf block");
            int reaching = look.recipe(mixed, winter, level, level, leaf, state);
            check(Recipe.twig(reaching) == Direction.SOUTH && Recipe.twigBase(reaching) == MiniBranch.REACH,
                    "next to a thicker branch the mini branch starts inside the wood");
            // One that fills its block is right at the side again.
            level.setBlock(leaf.north(), wood.getStateForRadius(8), 2 | 16);
            check(Surround.woodReaches(Surround.read(level, leaf, state)), "a trunk that fills its block is right at the side");
        } else {
            Foliage.LOGGER.warn("Foliage test: no block dynamictrees:oak_branch, the twig spot check is left out");
        }

        for (BlockPos pos : List.of(leaf, leaf.east(), leaf.west(), leaf.below(), leaf.north())) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // The pictures
    // ------------------------------------------------------------------

    /** The leaf pictures of the year lose and gain leaves step by step, and never the other way around. */
    @GameTest(template = "empty", batch = "foliage_calendar")
    public static void picturesThinStepByStep(GameTestHelper helper) {
        PictureShares shares = PictureShares.DEFAULT;
        for (int size : new int[] {16, 32}) {
            // A made-up leaf texture: about two thirds of its pixels are leaf, in a fixed scatter, in mid greys.
            int[] texture = new int[size * size];
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    boolean leaf = PosHash.unit(PosHash.hash(7L, x, y)) < 0.66F;
                    int grey = 90 + (x * 7 + y * 3) % 80;
                    texture[y * size + x] = leaf ? 0xFF000000 | grey << 16 | grey + 20 << 8 | grey : 0x00FFFFFF;
                }
            }
            float full = LeafPictures.coverage(texture);
            int[][] pictures = new int[LeafPicture.COUNT][];
            float[] coverage = new float[LeafPicture.COUNT];
            for (int number = 0; number < LeafPicture.COUNT; number++) {
                LeafPicture which = LeafPicture.byNumber(number);
                pictures[number] = LeafPictures.leaf(which, texture, size, 11L, shares);
                check(pictures[number].length == texture.length, which + " is as large as the texture");
                check(java.util.Arrays.equals(pictures[number], LeafPictures.leaf(which, texture, size, 11L, shares)), which + " comes out the same every time");
                coverage[number] = LeafPictures.coverage(pictures[number]);
            }
            check(java.util.Arrays.equals(pictures[LeafPicture.FULL.ordinal()], texture), "the full picture is the texture");
            float[] gone = {0.0F, shares.thin(), shares.thinner(), shares.last(), shares.youngGone(), shares.halfGone()};
            for (int number = 1; number < LeafPicture.COUNT; number++) {
                check(Math.abs(coverage[number] - full * (1.0F - gone[number])) < 0.02F, String.format(Locale.ROOT,
                        "%s keeps %.0f %% of the leaves: covers %.3f of the picture, the texture %.3f",
                        LeafPicture.byNumber(number), 100 * (1 - gone[number]), coverage[number], full));
            }
            // Leaves that are gone in one picture of autumn are gone in the next; spring grows them back in the same places.
            int[][] chains = {{0, 1, 2, 3}, {0, 5, 4}};
            for (int[] chain : chains) {
                for (int link = 1; link < chain.length; link++) {
                    int[] more = pictures[chain[link - 1]];
                    int[] fewer = pictures[chain[link]];
                    for (int i = 0; i < texture.length; i++) {
                        check(fewer[i] >>> 24 == 0 || more[i] >>> 24 != 0, LeafPicture.byNumber(chain[link]) + " has no leaf where "
                                + LeafPicture.byNumber(chain[link - 1]) + " has none");
                    }
                }
            }
            // Autumn keeps the colors; spring makes them lighter.
            for (int i = 0; i < texture.length; i++) {
                if (pictures[1][i] >>> 24 != 0) {
                    check(pictures[1][i] == texture[i], "a thinned picture keeps the colors of its leaves");
                }
                if (pictures[4][i] >>> 24 != 0) {
                    int red = texture[i] & 0xFF;
                    check((pictures[4][i] & 0xFF) == Math.min(255, Math.round(red * shares.youngLight())), "young leaves are lighter by the set factor");
                }
            }
            // The cards thin with the leaves.
            int[] card = LeafPictures.card(LeafPicture.FULL, texture, size, 11L, shares);
            check(java.util.Arrays.equals(card, CardPicture.make(texture, size, 11L)), "the full card is the card of the texture");
            int[] thinCard = LeafPictures.card(LeafPicture.THIN, texture, size, 11L, shares);
            int[] thinnerCard = LeafPictures.card(LeafPicture.THINNER, texture, size, 11L, shares);
            for (int i = 0; i < card.length; i++) {
                check((thinCard[i] == 0 || card[i] != 0) && (thinnerCard[i] == 0 || thinCard[i] != 0), "a thinned card has no leaf where the fuller one has none");
            }
            check(CardPicture.coverage(thinnerCard) < CardPicture.coverage(thinCard) && CardPicture.coverage(thinCard) < CardPicture.coverage(card),
                    "each card has fewer leaves than the one before");
        }
        // Which picture a stage is drawn with.
        check(LeafPicture.forStage(LeafStage.FULL) == LeafPicture.FULL && LeafPicture.forStage(LeafStage.THIN) == LeafPicture.THIN
                && LeafPicture.forStage(LeafStage.THINNER) == LeafPicture.THINNER && LeafPicture.forStage(LeafStage.LAST) == LeafPicture.LAST
                && LeafPicture.forStage(LeafStage.FIRST) == LeafPicture.YOUNG && LeafPicture.forStage(LeafStage.HALF) == LeafPicture.HALF,
                "every stage with leaves has its picture");
        for (int number = 0; number < LeafPicture.COUNT; number++) {
            check((LeafPicture.byNumber(number).cardSuffix() != null) == (number < LeafPicture.CARD_COUNT), "the first pictures come with a card: " + number);
        }
        // The numbers of the pictures: in range and in order, and read from an atlas file with gaps.
        PictureShares wild = new PictureShares(0.9F, 0.2F, -1.0F, 0.1F, 9.0F, 0.6F, Float.NaN);
        check(wild.thin() == 0.9F && wild.thinner() == 0.9F && wild.last() == 0.9F, "autumn's pictures never gain leaves: " + wild);
        check(wild.halfGone() == 0.6F && wild.youngGone() == 0.6F, "young leaves are never more than half-grown ones: " + wild);
        check(wild.youngLight() == PictureShares.MAX_LIGHT && wild.halfLight() >= PictureShares.MIN_LIGHT, "lightness in range: " + wild);
        PictureShares read = PictureShares.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"thin\": 0.25, \"young_light\": 1.3}")).getOrThrow();
        check(read.thin() == 0.25F && read.youngLight() == 1.3F && read.thinner() == shares.thinner() && read.halfGone() == shares.halfGone(),
                "numbers left out of an atlas file keep the mod's own values: " + read);
        check(PictureShares.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{}")).getOrThrow().equals(shares), "an empty entry is the mod's own values");
        helper.succeed();
    }
}
