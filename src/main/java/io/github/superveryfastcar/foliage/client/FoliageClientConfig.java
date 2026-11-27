package io.github.superveryfastcar.foliage.client;

import io.github.superveryfastcar.foliage.shape.LeafShape;
import io.github.superveryfastcar.foliage.shape.ShapeSettings;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The player's own settings, kept in {@code config/foliage-client.toml}
 * and shown in the game under Mods, Foliage, Config. They change how
 * leaves are drawn for this player only.
 *
 * <p>Every number the leaf shapes are drawn by is a setting here. The
 * values the mod ships with are those of {@link ShapeSettings#DEFAULT}.</p>
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
        MAX_FACES = b.comment("The most faces a single leaf block may be drawn from. The unmodded game draws 6 for every leaf block.",
                              "6: no leaf block ever costs more than in the unmodded game, and most cost less.",
                              "Higher: richer tips of the crown (a second leaf card, clumps that sit on a twig), for strong computers.")
                .defineInRange("maxFaces", standard.maxFaces(), ShapeSettings.MIN_FACES, ShapeSettings.MAX_FACES);
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
                CARD_WIDTH.get(), CARD_HEIGHT.get(), TUFT_SIZE.get(), TIP_SIZE.get(), CLUMP_SIZE.get());
    }
}
