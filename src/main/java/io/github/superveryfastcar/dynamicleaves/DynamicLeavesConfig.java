package io.github.superveryfastcar.dynamicleaves;

import io.github.superveryfastcar.dynamicleaves.season.LeafSchedule;
import io.github.superveryfastcar.dynamicleaves.season.SeasonSettings;
import io.github.superveryfastcar.dynamicleaves.world.WalkSettings;
import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The rules of the world, kept in {@code config/dynamicleaves-common.toml}:
 * when the leaves fall and come back, and for which trees (section
 * {@code leafCalendar}), and how the server swaps leaf blocks for bare
 * blocks (section {@code bareBranches}). On a server the leaf calendar is
 * the same for every player; the server sends it to each player who
 * joins. The second section is the server's own business.
 *
 * <p>Every number of the leaf calendar is a setting here. The values the
 * mod ships with are those of {@link SeasonSettings#DEFAULT}.</p>
 */
public final class DynamicLeavesConfig {

    /** The settings file's layout. */
    public static final ModConfigSpec SPEC;

    /** Whether trees shed their leaves at all. */
    public static final ModConfigSpec.BooleanValue ENABLED;

    /** Over how many days at the end of autumn the leaves fall. */
    public static final ModConfigSpec.DoubleValue FALL_DAYS;

    /** Over how many days at the start of spring the leaves come back. */
    public static final ModConfigSpec.DoubleValue RETURN_DAYS;

    /** How many days one tree takes to lose or grow its leaves. */
    public static final ModConfigSpec.DoubleValue TREE_DAYS;

    /** How many of a tree's days in spring it shows buds only. */
    public static final ModConfigSpec.DoubleValue BUD_DAYS;

    /** By up to how many days trees start later, depending on where they stand. */
    public static final ModConfigSpec.DoubleValue SHIFT_DAYS;

    /** How many blocks across the patches of trees are that start together. */
    public static final ModConfigSpec.IntValue SHIFT_PATCH;

    /** How many times a day the leaves move on. */
    public static final ModConfigSpec.IntValue STEPS_PER_DAY;

    /** The share of a tree's days that one leaf spot takes for its own change. */
    public static final ModConfigSpec.DoubleValue SPOT_SHARE;

    /** How much the order of the leaf spots follows their depth in the crown. */
    public static final ModConfigSpec.DoubleValue DEPTH_WEIGHT;

    /** The supply number of Dynamic Trees that counts as "right at the twig". */
    public static final ModConfigSpec.IntValue DEEPEST;

    /** The kinds of leaves that shed, one line each. */
    public static final ModConfigSpec.ConfigValue<List<? extends String>> KINDS;

    /** Whether a leaf spot that goes bare really holds a bare block. */
    public static final ModConfigSpec.BooleanValue BARE_BLOCKS;

    /** The most chunks the server looks at per tick to keep the blocks in step with the leaf calendar. */
    public static final ModConfigSpec.IntValue CHUNKS_PER_TICK;

    /** The most blocks the server swaps per tick. */
    public static final ModConfigSpec.IntValue CHANGES_PER_TICK;

    /** The most time the server spends on it per tick, in thousandths of a second. */
    public static final ModConfigSpec.DoubleValue MILLIS_PER_TICK;

    /** How easily fire eats a bare block. */
    public static final ModConfigSpec.IntValue TWIGS_BURN;

    /** How easily fire starts in the open air next to a bare block. */
    public static final ModConfigSpec.IntValue TWIGS_SPREAD_FIRE;

    /**
     * The mod's own fire numbers of a bare block: those of the thinnest
     * twig of Dynamic Trees. Fire eats it as slowly as it eats a log, and
     * does not jump along it.
     */
    public static final int DEFAULT_TWIGS_BURN = 5;
    public static final int DEFAULT_TWIGS_SPREAD_FIRE = 0;

    /** The highest fire number that means anything to the game. */
    public static final int MAX_FIRE_NUMBER = 300;

    /** Whether Dynamic Leaves tells Dynamic Trees what the season is. */
    public static final ModConfigSpec.BooleanValue SEASON_SOURCE;

    /** Whether trees grow, drop seeds and bear fruit in winter too. */
    public static final ModConfigSpec.BooleanValue GROWTH_IN_WINTER;

    /** Whether trees get limbs in layers. */
    public static final ModConfigSpec.BooleanValue LAYERED_LIMBS;

    static {
        SeasonSettings standard = SeasonSettings.DEFAULT;
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("The leaf calendar: when the trees of Dynamic Trees lose their leaves and get them back.",
                  "These are rules of the world. On a server they are the same for every player.",
                  "Changes to this file are used as soon as it is saved.",
                  "Days are days of the Calendar mod. Parts of a day are allowed (0.5 is half a day).")
         .push("leafCalendar");
        ENABLED = b.comment("false: trees keep their leaves all year.")
                .define("enabled", standard.enabled());
        FALL_DAYS = b.comment("The leaves fall during this many days at the end of autumn.",
                              "On the last of them every tree that sheds is bare. Not more than autumn is long.")
                .defineInRange("fallDays", standard.fallDays(), SeasonSettings.MIN_DAYS, SeasonSettings.MAX_DAYS);
        RETURN_DAYS = b.comment("The leaves come back during this many days at the start of spring.",
                                "On the last of them every tree is in full leaf. Not more than spring is long.")
                .defineInRange("returnDays", standard.returnDays(), SeasonSettings.MIN_DAYS, SeasonSettings.MAX_DAYS);
        TREE_DAYS = b.comment("How many of those days a single tree takes to lose its leaves, or to grow them.",
                              "Lower than fallDays and returnDays, so that not all trees change at once:",
                              "kinds start on different days (see kinds) and trees start a little apart (see shiftDays).",
                              "A tree that starts late takes fewer days, so that it is done in time.")
                .defineInRange("treeDays", standard.treeDays(), SeasonSettings.MIN_DAYS, SeasonSettings.MAX_DAYS);
        BUD_DAYS = b.comment("In spring a tree first shows buds on its twigs and no leaves.",
                             "This many of its days (treeDays) go to the buds. 0: leaves at once.")
                .defineInRange("budDays", standard.budDays(), 0.0, SeasonSettings.MAX_EXTRA_DAYS);
        SHIFT_DAYS = b.comment("Trees of the same kind do not start on the same hour. Depending on where it stands,",
                               "a tree starts up to this many days after its kind's day. 0: all at once.")
                .defineInRange("shiftDays", standard.shiftDays(), 0.0, SeasonSettings.MAX_EXTRA_DAYS);
        SHIFT_PATCH = b.comment("Trees close to each other start close in time. This is how many blocks across",
                                "the patches of the landscape are within which the start changes smoothly.")
                .defineInRange("shiftPatch", standard.shiftPatch(), SeasonSettings.MIN_PATCH, SeasonSettings.MAX_PATCH);
        STEPS_PER_DAY = b.comment("The leaves do not change all the time, but this many times a day, at even spaces.",
                                  "More steps: smoother, but the landscape is drawn again more often while leaves fall or grow.")
                .defineInRange("stepsPerDay", standard.stepsPerDay(), SeasonSettings.MIN_STEPS, SeasonSettings.MAX_STEPS);
        SPOT_SHARE = b.comment("The share of a tree's days that a single leaf block takes for its own change.",
                               "1: all leaf blocks of a tree change together. Low: one after the other, each quickly.")
                .defineInRange("spotShare", standard.spotShare(), SeasonSettings.MIN_SPOT_SHARE, 1.0);
        DEPTH_WEIGHT = b.comment("In which order the leaf blocks of a tree change.",
                                 "1: strictly by how deep they sit in the crown (see kinds for the direction).",
                                 "0: in no order at all. In between: by depth, with some leaf blocks out of turn.")
                .defineInRange("depthWeight", standard.depthWeight(), 0.0, 1.0);
        DEEPEST = b.comment("Dynamic Trees gives every leaf block a number for how well it is supplied: 1 at the far",
                            "edge of the crown, higher toward the twig. Blocks with this number or more count as",
                            "sitting right at the twig. Most trees go up to 4.")
                .defineInRange("deepest", standard.deepest(), SeasonSettings.MIN_DEEPEST, SeasonSettings.MAX_DEEPEST);
        KINDS = b.comment("The kinds of leaves that fall, one line each. All other leaves stay green.",
                          "A line is: the id of the leaf block, the day its kind starts on, the order.",
                          "  day: 0 for the kinds that start first, 1 for those that start a day later, and so on (up to "
                                  + (int) LeafSchedule.MAX_START_DAY + ").",
                          "  order: outside_in (the leaves at the far edge of the crown fall first) or",
                          "         inside_out (those at the twigs fall first). In spring leaves always grow from the twigs outward.",
                          "Leaves of other mods' Dynamic Trees addons can be added here. For their own pictures of",
                          "thinned and young leaves, their texture must be listed in a resource pack (see the README).")
                .defineListAllowEmpty("kinds", SeasonSettings.defaultKindLines(),
                        () -> "dynamictrees:oak_leaves, 0, outside_in", SeasonSettings::isKindLine);
        b.pop();

        WalkSettings walk = WalkSettings.DEFAULT;
        b.comment("Bare branches: while a leaf block has shed its leaves, the server puts a bare block in its place.",
                  "A bare block lets light, rain, snow, players and animals through, and drops nothing.",
                  "For the tree it is still a leaf, so the tree keeps its shape. In spring the leaf block comes back.",
                  "The server does this a little at a time, the trees nearest to the players first.",
                  "How a tree looks does not depend on it: a leaf block that has not been swapped yet is drawn bare all the same.")
         .push("bareBranches");
        BARE_BLOCKS = b.comment("false: leaf blocks are never swapped. Bare trees then still give shade, hold snow and stop arrows.",
                                "Bare blocks already in the world are turned back into leaf blocks as players come near.",
                                "Set this to false and visit your land before you take the mod out of a world.")
                .define("bareBlocks", walk.enabled());
        CHUNKS_PER_TICK = b.comment("The most chunks the server looks at in one tick. Most are done at a glance.")
                .defineInRange("chunksPerTick", walk.chunksPerTick(), WalkSettings.MIN_CHUNKS, WalkSettings.MAX_CHUNKS);
        CHANGES_PER_TICK = b.comment("The most blocks the server swaps in one tick. Every swap is sent to the players in view,",
                                     "whose games then work out the light anew: lower this if players stutter when leaves fall.")
                .defineInRange("changesPerTick", walk.changesPerTick(), WalkSettings.MIN_CHANGES, WalkSettings.MAX_CHANGES);
        MILLIS_PER_TICK = b.comment("The most time the server spends on this in one tick, in thousandths of a second.",
                                    "A tick lasts 50.")
                .defineInRange("millisPerTick", walk.millisPerTick(), WalkSettings.MIN_MILLIS, WalkSettings.MAX_MILLIS);
        TWIGS_BURN = b.comment("How easily fire eats a bare block. A log has 5, leaves have 30 to 60.",
                               "0: bare blocks never burn. Fire then cannot reach the twigs and branches inside a bare crown,",
                               "because fire only starts in open air and a bare block is not air.")
                .defineInRange("twigsBurn", DEFAULT_TWIGS_BURN, 0, MAX_FIRE_NUMBER);
        TWIGS_SPREAD_FIRE = b.comment("How easily fire starts in the open air next to a bare block. A log has 5, leaves have 30 to 60.",
                                      "0: fire does not jump along the outside of a bare crown. It can still eat its way in.")
                .defineInRange("twigsSpreadFire", DEFAULT_TWIGS_SPREAD_FIRE, 0, MAX_FIRE_NUMBER);
        b.pop();

        b.comment("The year of the trees themselves: how the season changes what Dynamic Trees does with them.",
                  "This is about growth and fruit, not about the leaves.")
         .push("trees");
        SEASON_SOURCE = b.comment("true: Dynamic Leaves tells Dynamic Trees what the season is, so that its trees rest in winter",
                                  "and bear fruit in its season. Where Seasons says there is no winter (deserts, jungles,",
                                  "savannas), trees grow and bear all year, as in a game without seasons.",
                                  "Dynamic Trees decides which mod it listens to, in its own settings file",
                                  "(config/dynamictrees-common.toml, preferredSeasonMod): \"*\" or \"dynamicleaves\" means Dynamic Leaves.",
                                  "false: Dynamic Trees is left as it is.")
                .define("seasonSource", true);
        GROWTH_IN_WINTER = b.comment("true: trees grow, drop seeds and bear fruit in winter as in the rest of the year.",
                                     "false: in winter a tree rests. Nothing grows, no seed falls from a crown by itself,",
                                     "and fruit that still hangs withers. Only where there is a winter.",
                                     "Needs seasonSource.")
                .define("growthInWinter", false);
        LAYERED_LIMBS = b.comment("false (the default): the trees are shaped as Dynamic Trees shapes them.",
                                  "true: oaks and birches get limbs in layers, as on a real tree: level limbs that leave the",
                                  "trunk at many heights, each with side limbs, instead of branches that climb.",
                                  "This goes for trees planted with new land and for trees that grow from a seed.",
                                  "Trees that already stand keep their wood; those that still grow, grow on level.",
                                  "Which trees: those with a file in a data pack under tree_shapes (see the README).")
                .define("layeredLimbs", false);
        b.pop();

        SPEC = b.build();
    }

    private DynamicLeavesConfig() {
    }

    /**
     * The rules as the file has them now, or the mod's own values while
     * the file is not read yet.
     */
    public static SeasonSettings seasonSettings() {
        if (!SPEC.isLoaded()) {
            return SeasonSettings.DEFAULT;
        }
        return new SeasonSettings(ENABLED.get(), FALL_DAYS.get(), RETURN_DAYS.get(), TREE_DAYS.get(), BUD_DAYS.get(),
                SHIFT_DAYS.get(), SHIFT_PATCH.get(), STEPS_PER_DAY.get(), SPOT_SHARE.get(), DEPTH_WEIGHT.get(),
                DEEPEST.get(), SeasonSettings.parseKinds(KINDS.get()));
    }

    /**
     * The settings of the bare branches as the file has them now, or the
     * mod's own values while the file is not read yet.
     */
    public static WalkSettings walkSettings() {
        if (!SPEC.isLoaded()) {
            return WalkSettings.DEFAULT;
        }
        return new WalkSettings(BARE_BLOCKS.get(), CHUNKS_PER_TICK.get(), CHANGES_PER_TICK.get(), MILLIS_PER_TICK.get());
    }

    /** Whether Dynamic Leaves tells Dynamic Trees what the season is, as the file has it now. */
    public static boolean seasonSource() {
        return SPEC.isLoaded() ? SEASON_SOURCE.get() : true;
    }

    /** Whether trees grow, drop seeds and bear fruit in winter too, as the file has it now. */
    public static boolean growthInWinter() {
        return SPEC.isLoaded() && GROWTH_IN_WINTER.get();
    }

    /** Whether trees get limbs in layers, as the file has it now. */
    public static boolean layeredLimbs() {
        return SPEC.isLoaded() && LAYERED_LIMBS.get();
    }

    /** How easily fire eats a bare block, as the file has it now (the game's "flammability"). */
    public static int twigsBurn() {
        return SPEC.isLoaded() ? TWIGS_BURN.get() : DEFAULT_TWIGS_BURN;
    }

    /** How easily fire starts in the open air next to a bare block, as the file has it now (the game's "fire spread speed"). */
    public static int twigsSpreadFire() {
        return SPEC.isLoaded() ? TWIGS_SPREAD_FIRE.get() : DEFAULT_TWIGS_SPREAD_FIRE;
    }
}
