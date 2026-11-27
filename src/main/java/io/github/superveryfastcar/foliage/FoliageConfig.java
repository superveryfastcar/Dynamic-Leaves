package io.github.superveryfastcar.foliage;

import io.github.superveryfastcar.foliage.season.LeafSchedule;
import io.github.superveryfastcar.foliage.season.SeasonSettings;
import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The rules of the world, kept in {@code config/foliage-common.toml}:
 * when the leaves fall and come back, and for which trees. On a server
 * they are the same for every player; the server sends them to each player
 * who joins.
 *
 * <p>Every number of the leaf calendar is a setting here. The values the
 * mod ships with are those of {@link SeasonSettings#DEFAULT}.</p>
 */
public final class FoliageConfig {

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

        SPEC = b.build();
    }

    private FoliageConfig() {
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
}
