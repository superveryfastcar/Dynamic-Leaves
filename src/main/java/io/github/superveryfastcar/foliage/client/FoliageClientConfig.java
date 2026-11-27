package io.github.superveryfastcar.foliage.client;

import io.github.superveryfastcar.foliage.client.fall.FallSettings;
import io.github.superveryfastcar.foliage.shape.LeafShape;
import io.github.superveryfastcar.foliage.shape.MiniBranch;
import io.github.superveryfastcar.foliage.shape.ShapeSettings;
import io.github.superveryfastcar.foliage.shape.WinterTwigs;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The player's own settings, kept in {@code config/foliage-client.toml}
 * and shown in the game under Mods, Foliage, Config. They change how
 * leaves are drawn for this player only.
 *
 * <p>Every number the leaf shapes are drawn by is a setting here. The
 * values the mod ships with are those of {@link ShapeSettings#DEFAULT}.
 * When the leaves fall and come back is not a player's setting but a rule
 * of the world ({@link io.github.superveryfastcar.foliage.FoliageConfig}).</p>
 */
public final class FoliageClientConfig {

    /** The settings file's layout. */
    public static final ModConfigSpec SPEC;

    /** The shape crowns are drawn in. */
    public static final ModConfigSpec.EnumValue<LeafShape> SHAPE;

    /** Fewer faces per leaf block, for slow computers. */
    public static final ModConfigSpec.BooleanValue FAST_LEAVES;

    /** The most faces one leaf block may be drawn from. */
    public static final ModConfigSpec.IntValue MAX_FACES;

    /** From how many open sides a leaf block counts as a tip of the crown. */
    public static final ModConfigSpec.IntValue TIP_SIDES;

    /** From how many open sides a leaf block becomes a small clump on a twig. */
    public static final ModConfigSpec.IntValue CLUMP_SIDES;

    /** How wide a leaf card is, in pixels of a block. */
    public static final ModConfigSpec.IntValue CARD_WIDTH;

    /** How tall a leaf card is, in pixels of a block. */
    public static final ModConfigSpec.IntValue CARD_HEIGHT;

    /** The size of a tip's clump in the "tufts" shape. */
    public static final ModConfigSpec.IntValue TUFT_SIZE;

    /** The size of an outermost tip's clump in the "tufts" shape. */
    public static final ModConfigSpec.IntValue TIP_SIZE;

    /** The size of an outermost tip's clump in the "mixed" shape. */
    public static final ModConfigSpec.IntValue CLUMP_SIZE;

    /** On which leaf spots of a bare tree a mini branch is drawn. */
    public static final ModConfigSpec.EnumValue<WinterTwigs> WINTER_TWIGS;

    /** How many thinner twigs leave the main twig of a mini branch. */
    public static final ModConfigSpec.IntValue TWIG_FORKS;

    /** With "more twigs": from which depth in the crown a leaf spot gets a twig. */
    public static final ModConfigSpec.IntValue TWIG_DEPTH;

    /** The size of the clump of first leaves in spring. */
    public static final ModConfigSpec.IntValue FIRST_SIZE;

    /** The size of the clump of half-grown leaves in spring. */
    public static final ModConfigSpec.IntValue HALF_SIZE;

    /** The size of the bud at the end of a mini branch, 0 for none. */
    public static final ModConfigSpec.IntValue BUD_SIZE;

    /** The size of the clump a leaf block shrinks to in autumn. */
    public static final ModConfigSpec.IntValue FALL_SIZE;

    /** The size of the clump of last leaves in autumn. */
    public static final ModConfigSpec.IntValue LAST_SIZE;

    /** Whether leaves drift down from the crowns while the leaves fall. */
    public static final ModConfigSpec.BooleanValue FALLING_LEAVES;

    /** How many leaves fall. */
    public static final ModConfigSpec.IntValue FALL_AMOUNT;

    /** How far from the player leaves start, in blocks. */
    public static final ModConfigSpec.IntValue FALL_DISTANCE;

    /** How many leaves may be on their way at once. */
    public static final ModConfigSpec.IntValue FALL_MOST;

    /** How many leaves a leaf block lets go when it changes in view. */
    public static final ModConfigSpec.IntValue FALL_BURST;

    static {
        ShapeSettings standard = ShapeSettings.DEFAULT;
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("How the leaves of Dynamic Trees are drawn, as seen by this player.",
                  "Changes to this file are used as soon as it is saved.")
         .push("leaves");
        SHAPE = b.comment("The shape of the crowns.",
                          "PLAIN: the unmodded look, one plain cube per leaf block.",
                          "SHELL: only the outside of the crown is drawn, with leaf planes inside the blocks for depth.",
                          "BUSHY: as SHELL, with round leaf cards that reach out of the blocks. Softer, rounder crowns.",
                          "TUFTS: leaf blocks at the edge of the crown shrink to small clumps on their branches. Airy crowns.",
                          "MIXED: bushy crowns, with small clumps at the very tips and on thin side branches.")
                .defineEnum("shape", standard.shape());
        FAST_LEAVES = b.comment("true: the fewest faces per leaf block, whatever the shape: only the outside of the crown",
                                "and one leaf plane inside each block. For slow computers.")
                .define("fastLeaves", standard.fastLeaves());
        MAX_FACES = b.comment("The most faces the leaves of a single leaf block may be drawn from. The unmodded game draws 6 for",
                              "every leaf block.",
                              "6: the leaves of no block ever cost more than in the unmodded game, and most cost less.",
                              "Higher: richer tips of the crown (a second leaf card, clumps that sit on a stick), for strong computers.",
                              "The small branches of bare trees are not leaves and are counted apart: see twigForks.")
                .defineInRange("maxFaces", standard.maxFaces(), ShapeSettings.MIN_FACES, ShapeSettings.MAX_FACES);
        WINTER_TWIGS = b.comment("Small branches on trees that have lost their leaves, in the bark of the tree: one main twig",
                                 "that grows out of the wood, with thinner twigs that leave it (see twigForks).",
                                 "NONE: a bare tree shows the branches of Dynamic Trees only.",
                                 "NEAR: a small branch wherever a leaf block touches a branch.",
                                 "MORE: also on leaf blocks deeper in the crown (see twigDepth). A denser winter crown.",
                                 "In spring the same small branches carry a bud, and then the first leaves.")
                .defineEnum("winterTwigs", standard.winterTwigs());
        TWIG_FORKS = b.comment("How many thinner twigs leave the main twig of a small branch, each in another direction.",
                               "The main twig is drawn from 5 faces and every thinner twig from 4 more. With 3 that is 17 faces",
                               "on every leaf block that touches a branch, all winter. Lower this if bare forests cost too many frames;",
                               "0 leaves the main twig alone.")
                .defineInRange("twigForks", standard.twigForks(), 0, MiniBranch.MAX_FORKS);
        b.pop();

        b.comment("The numbers the shapes are drawn by. A block has 16 pixels from one side to the other.")
         .push("numbers");
        TIP_SIDES = b.comment("A leaf block with at least this many of its six sides open counts as a tip of the crown.",
                              "Tips get a second leaf card where maxFaces allows (BUSHY, MIXED) or become a clump (TUFTS).")
                .defineInRange("tipSides", standard.tipSides(), 1, 6);
        CLUMP_SIDES = b.comment("A leaf block with at least this many sides open is an outermost tip and becomes a small",
                                "clump of leaves on its branch (TUFTS, MIXED). Not below tipSides.")
                .defineInRange("clumpSides", standard.clumpSides(), 1, 6);
        CARD_WIDTH = b.comment("How wide a leaf card is, in pixels. Above 16 it reaches out of its block.")
                .defineInRange("cardWidth", standard.cardWidth(), ShapeSettings.MIN_CARD, ShapeSettings.MAX_CARD);
        CARD_HEIGHT = b.comment("How tall a leaf card is, in pixels.")
                .defineInRange("cardHeight", standard.cardHeight(), ShapeSettings.MIN_CARD, ShapeSettings.MAX_CARD);
        TUFT_SIZE = b.comment("TUFTS: the size of a tip's clump of leaves, in pixels.")
                .defineInRange("tuftSize", standard.tuftSize(), ShapeSettings.MIN_CLUMP, ShapeSettings.MAX_CLUMP);
        TIP_SIZE = b.comment("TUFTS: the size of an outermost tip's clump, in pixels.")
                .defineInRange("tipSize", standard.tipSize(), ShapeSettings.MIN_CLUMP, ShapeSettings.MAX_CLUMP);
        CLUMP_SIZE = b.comment("MIXED: the size of an outermost tip's clump, in pixels.")
                .defineInRange("clumpSize", standard.clumpSize(), ShapeSettings.MIN_CLUMP, ShapeSettings.MAX_CLUMP);
        TWIG_DEPTH = b.comment("winterTwigs = MORE: Dynamic Trees gives every leaf block a number for how well it is supplied,",
                               "1 at the far edge of the crown and higher toward the twig. A leaf block with this number",
                               "or more gets a small branch even if it touches no wood. It grows out of the leaf block it",
                               "hangs on to, and is thinner than one that grows out of wood.")
                .defineInRange("twigDepth", standard.twigDepth(), ShapeSettings.MIN_DEPTH, ShapeSettings.MAX_DEPTH);
        FIRST_SIZE = b.comment("Spring: the size of the small clump of first leaves on a leaf block, in pixels.")
                .defineInRange("firstSize", standard.firstSize(), ShapeSettings.MIN_CLUMP, ShapeSettings.MAX_CLUMP);
        HALF_SIZE = b.comment("Spring: the size of the clump of half-grown leaves, in pixels. Not below firstSize.")
                .defineInRange("halfSize", standard.halfSize(), ShapeSettings.MIN_CLUMP, ShapeSettings.MAX_CLUMP);
        BUD_SIZE = b.comment("Spring: the size of the bud at the end of a small branch in the days before the first leaves,",
                             "in pixels. 0: no buds. (1 and 2 are read as 3: a bud as thin as its twig could not be seen.)")
                .defineInRange("budSize", standard.budSize(), 0, ShapeSettings.MAX_BUD);
        FALL_SIZE = b.comment("Autumn: the size of the clump a leaf block shrinks to when most of its leaves are gone, in pixels.",
                              "Not below lastSize.")
                .defineInRange("fallSize", standard.fallSize(), ShapeSettings.MIN_CLUMP, ShapeSettings.MAX_CLUMP);
        LAST_SIZE = b.comment("Autumn: the size of the small clump of last leaves before a leaf block is bare, in pixels.")
                .defineInRange("lastSize", standard.lastSize(), ShapeSettings.MIN_CLUMP, ShapeSettings.MAX_CLUMP);
        b.pop();

        FallSettings fall = FallSettings.DEFAULT;
        b.comment("Leaves that drift down from the crowns while the leaves fall, in the last days of autumn.",
                  "They are a look of this player's game only: nothing falls in the world, and nothing piles up.")
         .push("fallingLeaves");
        FALLING_LEAVES = b.comment("false: no leaves drift down. Nothing is looked at either, so this costs nothing.")
                .define("fallingLeaves", fall.enabled());
        FALL_AMOUNT = b.comment("How many leaves fall: 1 is a few, 3 a steady fall, 10 a shower.",
                                "This is how many places near the player are looked at per tick, so it costs",
                                "a little more at a higher number even where there are no trees.")
                .defineInRange("amount", fall.amount(), FallSettings.MIN_AMOUNT, FallSettings.MAX_AMOUNT);
        FALL_DISTANCE = b.comment("How far from the player leaves may start, in blocks. Further: leaves in more of the view,",
                                  "but fewer close by, because the places are drawn from a larger space.")
                .defineInRange("distance", fall.distance(), FallSettings.MIN_DISTANCE, FallSettings.MAX_DISTANCE);
        FALL_MOST = b.comment("How many leaves may be on their way at once. 200 cost about as much as light rain.",
                              "0: none, and nothing is looked at either, the same as fallingLeaves = false.")
                .defineInRange("most", fall.most(), FallSettings.MIN_MOST, FallSettings.MAX_MOST);
        FALL_BURST = b.comment("How many leaves a leaf block lets go in the moment it thins, shrinks or goes bare, so that the",
                               "change is not a sudden swap. Only for leaf blocks within 'distance' that the player is looking",
                               "toward, and never more leaves than 'most' allows. 0: none, and nothing is looked at for it.")
                .defineInRange("burst", fall.burst(), FallSettings.MIN_BURST, FallSettings.MAX_BURST);
        b.pop();

        SPEC = b.build();
    }

    private FoliageClientConfig() {
    }

    /**
     * The shape settings as the file has them now, or the mod's own
     * values while the file is not read yet.
     */
    public static ShapeSettings shapeSettings() {
        if (!SPEC.isLoaded()) {
            return ShapeSettings.DEFAULT;
        }
        return new ShapeSettings(SHAPE.get(), FAST_LEAVES.get(), MAX_FACES.get(), TIP_SIDES.get(), CLUMP_SIDES.get(),
                CARD_WIDTH.get(), CARD_HEIGHT.get(), TUFT_SIZE.get(), TIP_SIZE.get(), CLUMP_SIZE.get(),
                WINTER_TWIGS.get(), TWIG_DEPTH.get(), FIRST_SIZE.get(), HALF_SIZE.get(),
                TWIG_FORKS.get(), BUD_SIZE.get(), FALL_SIZE.get(), LAST_SIZE.get());
    }

    /**
     * The settings of the falling leaves as the file has them now, or the
     * mod's own values while the file is not read yet.
     */
    public static FallSettings fallSettings() {
        if (!SPEC.isLoaded()) {
            return FallSettings.DEFAULT;
        }
        return new FallSettings(FALLING_LEAVES.get(), FALL_AMOUNT.get(), FALL_DISTANCE.get(), FALL_MOST.get(), FALL_BURST.get());
    }
}
