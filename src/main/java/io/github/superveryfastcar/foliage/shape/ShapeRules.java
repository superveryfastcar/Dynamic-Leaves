package io.github.superveryfastcar.foliage.shape;

import io.github.superveryfastcar.foliage.season.LeafStage;
import io.github.superveryfastcar.foliage.util.PosHash;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;

/**
 * Decides which pieces a leaf block is drawn from, given its surroundings,
 * the shape the player chose and the stage of the leaf calendar the block
 * is at. Plain arithmetic on whole numbers: the same inputs always give the
 * same {@link Recipe}.
 *
 * <h2>The rules in leaf</h2>
 * For a block in full leaf, and in the first stage of autumn (the same
 * pieces, with a thinned leaf picture):
 * <ul>
 *   <li>A leaf block with no open side is buried in the crown and shows
 *       nothing, in every shape and at every stage.</li>
 *   <li><b>Plain</b>: the outer faces of the cube and nothing else, at
 *       every stage that has leaves; only the leaf picture changes through
 *       the year. Leaf blocks do not hide each other's faces in this shape,
 *       as in the unmodded game. (In full leaf a plain block is not drawn
 *       from a recipe at all, but by the model it had before.)</li>
 *   <li><b>Shell</b>: outer faces on the open sides, and an inner plane
 *       behind each pair of sides of which at least one is open.</li>
 *   <li><b>Bushy</b>: outer faces, one card, one inner plane, and a second
 *       card on a tip of the crown.</li>
 *   <li><b>Tufts</b>: a tip becomes a clump of leaves, an outermost tip a
 *       smaller clump (on a stick, where the limit on faces allows);
 *       every other block is drawn as shell.</li>
 *   <li><b>Mixed</b>: an outermost tip becomes a clump (on a stick, where
 *       the limit on faces allows); every other block is drawn as bushy.</li>
 *   <li><b>Fast leaves</b>: outer faces and one inner plane, whatever the
 *       shape.</li>
 * </ul>
 *
 * <h2>The limit on faces</h2>
 * The leaves of no leaf block are drawn from more faces than the settings
 * allow ({@link ShapeSettings#maxFaces}). The outer faces of the open sides
 * are always drawn. The other pieces are added one at a time, in the order
 * the rules above name them, and a piece that would go over the limit is
 * left out. With the limit at 6, the number of faces of a plain cube, a
 * block with one or two open sides still gets everything, a block with
 * three or four open sides gets one card and no plane, and a clump comes
 * without the stick that would carry it.
 *
 * <p>The mini branch of a leaf spot that has lost its leaves
 * ({@link MiniBranch}) is not leaves and is not counted against this
 * limit. What it costs is set by the number of its thinner twigs
 * ({@link ShapeSettings#twigForks}).</p>
 *
 * <h2>Clumps</h2>
 * A clump sits against the side on which the block hangs on to its tree
 * ({@link Surround#support}), so it rests on its branch like a smaller
 * leaf block. Where the limit on faces allows a stick under it, it sits
 * at the far end of the block instead: the stick grows from the branch
 * across the gap and ends inside the clump, like a spray of leaves at the
 * end of a twig. A leaf block that touches nothing is never made a clump,
 * and neither is one with something resting on top (a layer of snow),
 * because the snow would hang in the air.
 *
 * <h2>The rules through the year</h2>
 * Autumn runs like spring backwards: a leaf block thins, shrinks toward
 * the side it hangs on to, and is gone. For every shape but plain:
 * <ul>
 *   <li><b>Thinner</b> (most leaves gone): the block shrinks to a clump
 *       against the side it hangs on to ({@link ShapeSettings#fallSize}).
 *       On a spot with a mini branch ({@link #twigSpot}) its main twig
 *       shows through the thinned leaves ({@link Recipe#forks}).</li>
 *   <li><b>Last leaves</b> (the end of a spot's autumn): a spot with a
 *       mini branch shows it with a small clump of leaves on the end of
 *       its main twig ({@link ShapeSettings#lastSize},
 *       {@link MiniBranch#tuftEnd}); any other spot a clump of that size
 *       against the side it hangs on to.</li>
 *   <li><b>Bare</b>: the mini branch on a spot that has one, nothing
 *       elsewhere.</li>
 *   <li><b>Buds</b>: as bare, with a bud on the end of the main twig
 *       ({@link ShapeSettings#budSize}).</li>
 *   <li><b>First leaves</b>: as the last leaves, with the young leaves of
 *       spring and its own size ({@link ShapeSettings#firstSize}).</li>
 *   <li><b>Half grown</b>: a larger clump against the side the block hangs
 *       on to ({@link ShapeSettings#halfSize}).</li>
 * </ul>
 * A clump of autumn or spring is never larger than the clump the spot is
 * in full leaf, and a spot that is a clump in full leaf keeps that clump
 * while it is thinner or half grown. A spot that hangs on to nothing (or
 * carries snow) cannot shrink toward a side: it stays a whole block while
 * it is thinner or half grown, and shows two upright planes of leaves for
 * its last and first ones. None of these needs more than six faces of
 * leaves, so the limit on faces always holds.
 *
 * <h2>Inside the crown</h2>
 * A block whose six neighbors are all leaf blocks or full blocks
 * ({@link Surround#sheltered}) is buried in summer and costs nothing. While
 * leaves fall or come back, its neighbors show few leaves and it can be
 * seen between them, but never close up and never whole. While it is
 * thinner or half grown it is drawn as the outer faces toward the
 * neighbors that have stopped hiding it and nothing else: the inside of a
 * crown stays a mass of leaves while its outside shrinks.
 *
 * <h2>Where a mini branch starts</h2>
 * A mini branch grows away from the side its spot hangs on to. Next to
 * wood it starts at the block's side if the wood comes that far, and
 * inside the wood if it does not ({@link Surround#woodReaches}). A spot
 * that only hangs on to another leaf spot (the "more twigs" setting) gets
 * the thinner kind that starts in the middle of that spot, where that
 * spot's own main twig passes ({@link #twigBase}).
 */
public final class ShapeRules {

    /** The sides whose openness calls for the plane across the x axis: west and east. */
    private static final int X_SIDES = 1 << Direction.WEST.ordinal() | 1 << Direction.EAST.ordinal();

    /** The sides that call for the plane across the y axis: down and up. */
    private static final int Y_SIDES = 1 << Direction.DOWN.ordinal() | 1 << Direction.UP.ordinal();

    /** The sides that call for the plane across the z axis: north and south. */
    private static final int Z_SIDES = 1 << Direction.NORTH.ordinal() | 1 << Direction.SOUTH.ordinal();

    /** The order in which further inner planes are added: the upright ones before the level one. */
    private static final int[] PLANE_ORDER = {Recipe.PLANE_X, Recipe.PLANE_Z, Recipe.PLANE_Y};

    /** Keeps the variants of leaf blocks apart from other fixed numbers made from a position. */
    private static final long VARIANT_SALT = 0x466F_6C69_6167_65L;

    private ShapeRules() {
    }

    /**
     * The variant of a position: a number from 0 to 15 that never changes
     * for the position and has no visible pattern from one block to the
     * next. It turns and shifts the pieces of a leaf block.
     */
    public static int variant(int x, int y, int z) {
        return PosHash.bits(PosHash.hash(VARIANT_SALT, x, y, z), 4);
    }

    /**
     * Whether a leaf spot has a mini branch: it is drawn when the spot is
     * bare, with buds, under its last and first leaves, and through its
     * leaves while most of them are gone in autumn.
     *
     * <p>With "more twigs", a spot that touches no wood gets one only if
     * it hangs on to a leaf spot deeper in the crown: its main twig starts
     * in the middle of that spot, and there has to be something there for
     * it to grow from.</p>
     *
     * @param settings the player's choice ({@link ShapeSettings#winterTwigs})
     * @param surround what stands around the block ({@link Surround})
     * @param depth    the block's supply number of Dynamic Trees ({@link Surround#depth})
     */
    public static boolean twigSpot(ShapeSettings settings, int surround, int depth) {
        switch (settings.winterTwigs()) {
            case NEAR:
                return Surround.wood(surround) != null;
            case MORE:
                return Surround.wood(surround) != null
                        || depth >= settings.twigDepth() && Surround.heart(surround) != null;
            case NONE:
            default:
                return false;
        }
    }

    /**
     * Where the mini branch of a leaf spot starts: at the block's side
     * next to wood that comes that far ({@link MiniBranch#FLUSH}), inside
     * the wood next to a branch whose bark lies behind the side
     * ({@link MiniBranch#REACH}), and in the middle of the leaf spot it
     * hangs on to where it touches no wood ({@link MiniBranch#DEEP}).
     *
     * @param surround what stands around the block ({@link Surround})
     */
    public static int twigBase(int surround) {
        if (Surround.wood(surround) == null) {
            return MiniBranch.DEEP;
        }
        return Surround.woodReaches(surround) ? MiniBranch.FLUSH : MiniBranch.REACH;
    }

    /**
     * Where the stick starts that carries a clump at the far end of its
     * block: at the block's side, or inside the wood next to a branch
     * whose bark lies behind the side.
     */
    private static int stemBase(int surround) {
        return Surround.wood(surround) != null && !Surround.woodReaches(surround) ? MiniBranch.REACH : MiniBranch.FLUSH;
    }

    /**
     * The recipe of one leaf block in full leaf.
     *
     * @param settings the shape and its numbers
     * @param surround what stands around the block ({@link Surround})
     * @param variant  the position's variant, 0 to 15
     * @return the recipe
     */
    public static int recipe(ShapeSettings settings, int surround, int variant) {
        return recipe(settings, surround, variant, LeafStage.FULL, false);
    }

    /**
     * The recipe of one leaf block.
     *
     * @param settings the shape and its numbers
     * @param surround what stands around the block ({@link Surround}); at a
     *                 time of the year when leaf blocks may look bare, read
     *                 with their looks in mind
     * @param variant  the position's variant, 0 to 15
     * @param stage    the stage of the leaf calendar the block is at
     * @param twigSpot whether the spot has a mini branch ({@link #twigSpot});
     *                 only looked at in the stages that draw one
     * @return the recipe
     */
    public static int recipe(ShapeSettings settings, int surround, int variant, LeafStage stage, boolean twigSpot) {
        int open = Surround.open(surround);
        int recipe = Recipe.withStage(Recipe.start(open, variant), stage);
        if (open == 0) {
            return recipe;
        }
        if (settings.shape() == LeafShape.PLAIN) {
            if (stage.leafless()) {
                return twigSpot ? leafless(settings, recipe, surround, stage) : recipe;
            }
            return recipe | Recipe.FACES_BIT;
        }
        Direction support = Surround.loaded(surround) ? null : Surround.support(surround);
        boolean sheltered = Surround.sheltered(surround);
        switch (stage) {
            case THINNER:
                return mostly(settings, recipe, surround, variant, support, sheltered, twigSpot, true);
            case LAST:
                return few(settings, recipe, surround, variant, support, sheltered, twigSpot, settings.lastSize());
            case BARE:
            case BUDS:
                return twigSpot ? leafless(settings, recipe, surround, stage) : recipe;
            case FIRST:
                return few(settings, recipe, surround, variant, support, sheltered, twigSpot, settings.firstSize());
            case HALF:
                return mostly(settings, recipe, surround, variant, support, sheltered, false, false);
            case FULL:
            case THIN:
            default:
                return sheltered ? recipe | Recipe.FACES_BIT : inLeaf(settings, recipe, open, variant, support, stemBase(surround));
        }
    }

    /**
     * A spot with a mini branch and no leaves: the mini branch, and in
     * the days before the first leaves a bud on the end of its main twig.
     */
    private static int leafless(ShapeSettings settings, int recipe, int surround, LeafStage stage) {
        int result = Recipe.withTwig(recipe, Surround.grow(surround), twigBase(surround));
        if (stage == LeafStage.BUDS && settings.budSize() != 0) {
            result = Recipe.withClump(result, settings.budSize(), null);
        }
        return result;
    }

    /**
     * A spot with its last leaves of autumn or its first of spring: a
     * small clump. On a spot with a mini branch the clump sits on the end
     * of the main twig; elsewhere against the side the block hangs on to.
     * If that side is a leaf block with no leaves to speak of, the clump
     * would hang in the air with nothing near it, so the spot shows
     * nothing: the last leaves of a tree hang on its twigs and on what is
     * still in leaf, and the first ones come out there.
     * A spot that hangs on to nothing shows upright planes of leaves.
     *
     * @param wanted the side of the clump ({@link ShapeSettings#lastSize}
     *               or {@link ShapeSettings#firstSize})
     */
    private static int few(ShapeSettings settings, int recipe, int surround, int variant, @Nullable Direction support,
                           boolean sheltered, boolean twigSpot, int wanted) {
        int size = smallClump(settings, Recipe.open(recipe), wanted);
        if (twigSpot) {
            return Recipe.withClump(Recipe.withTwig(recipe, Surround.grow(surround), twigBase(surround)), size, null);
        }
        if (support != null) {
            return Surround.supportBare(surround) ? recipe : Recipe.withClump(recipe, size, support);
        }
        return sheltered ? recipe | uprightPlane(variant) : recipe | Recipe.PLANE_X | Recipe.PLANE_Z;
    }

    /**
     * A spot with most of its leaves gone in autumn, or half grown in
     * spring: a clump against the side the block hangs on to. In autumn a
     * spot with a mini branch shows its main twig through the thinned
     * leaves.
     *
     * @param twigSpot whether the spot has a mini branch
     * @param fall     true in autumn ({@link LeafStage#THINNER}), false in
     *                 spring ({@link LeafStage#HALF})
     */
    private static int mostly(ShapeSettings settings, int recipe, int surround, int variant, @Nullable Direction support,
                              boolean sheltered, boolean twigSpot, boolean fall) {
        int open = Recipe.open(recipe);
        if (sheltered) {
            return recipe | Recipe.FACES_BIT;
        }
        if (support == null) {
            // Nothing to shrink toward. Half-grown leaves have no card pictures, so spring takes the shell.
            return fall ? inLeaf(settings, recipe, open, variant, null, MiniBranch.FLUSH)
                    : shell(recipe, open, variant, settings.maxFaces(), 3);
        }
        int result;
        if (leafClump(settings, Integer.bitCount(open)) != 0) {
            // A clump in full leaf: the same clump in the same place, so that it does not jump.
            result = inLeaf(settings, recipe, open, variant, support, stemBase(surround));
        } else {
            result = Recipe.withClump(recipe, fall ? settings.fallSize() : settings.halfSize(), support);
        }
        if (fall && twigSpot && Recipe.twig(result) == null) {
            result = Recipe.withTwig(result, Surround.grow(surround), twigBase(surround));
        }
        return result;
    }

    /** The pieces of a block in leaf: the rules of the shapes. */
    private static int inLeaf(ShapeSettings settings, int recipe, int open, int variant, @Nullable Direction support,
                              int stemBase) {
        if (settings.fastLeaves()) {
            return shell(recipe, open, variant, settings.maxFaces(), 1);
        }
        int openCount = Integer.bitCount(open);
        int size = support == null ? 0 : leafClump(settings, openCount);
        switch (settings.shape()) {
            case TUFTS:
                if (size != 0) {
                    return clump(recipe, open, size, support, openCount >= settings.clumpSides(), settings.maxFaces(), stemBase);
                }
                return shell(recipe, open, variant, settings.maxFaces(), 3);
            case MIXED:
                if (size != 0) {
                    return clump(recipe, open, size, support, true, settings.maxFaces(), stemBase);
                }
                return bushy(recipe, open, openCount, variant, settings);
            case BUSHY:
                return bushy(recipe, open, openCount, variant, settings);
            case SHELL:
            default:
                return shell(recipe, open, variant, settings.maxFaces(), 3);
        }
    }

    /**
     * The side of the clump a block with a side to hang on to is drawn as
     * in full leaf, or 0 if it is drawn as a whole block.
     *
     * @param openCount how many of its sides are open
     */
    private static int leafClump(ShapeSettings settings, int openCount) {
        if (settings.fastLeaves()) {
            return 0;
        }
        switch (settings.shape()) {
            case TUFTS:
                if (openCount >= settings.clumpSides()) {
                    return settings.tipSize();
                }
                return openCount >= settings.tipSides() ? settings.tuftSize() : 0;
            case MIXED:
                return openCount >= settings.clumpSides() ? settings.clumpSize() : 0;
            default:
                return 0;
        }
    }

    /**
     * The side of a clump of autumn or spring: the size wanted, but not
     * larger than the clump the spot is in full leaf (a spot must not
     * shrink when its leaves are grown, nor grow while it loses them).
     */
    private static int smallClump(ShapeSettings settings, int open, int wanted) {
        int grown = leafClump(settings, Integer.bitCount(open));
        return grown == 0 ? wanted : Math.min(wanted, grown);
    }

    /** One of the two upright inner planes, picked by the position's variant. */
    private static int uprightPlane(int variant) {
        return (variant & 1) == 0 ? Recipe.PLANE_X : Recipe.PLANE_Z;
    }

    /**
     * Outer faces, and as many of the inner planes behind open sides as
     * are wanted and fit under the limit.
     *
     * @param mostPlanes how many inner planes are wanted at most
     */
    private static int shell(int recipe, int open, int variant, int maxFaces, int mostPlanes) {
        int result = recipe | Recipe.FACES_BIT;
        int room = Math.min(mostPlanes, (maxFaces - Integer.bitCount(open)) / Recipe.FACES_PER_PLANE);
        if (room <= 0) {
            return result;
        }
        // The plane the eye needs most comes first, then the other upright one, then the level one.
        int first = onePlane(open, variant);
        result |= first;
        room--;
        int wanted = allPlanes(open) & ~first;
        for (int plane : PLANE_ORDER) {
            if (room > 0 && (wanted & plane) != 0) {
                result |= plane;
                room--;
            }
        }
        return result;
    }

    /** Outer faces, one card, one inner plane, a second card on a tip: as far as they fit under the limit. */
    private static int bushy(int recipe, int open, int openCount, int variant, ShapeSettings settings) {
        int result = recipe | Recipe.FACES_BIT;
        int room = settings.maxFaces() - openCount;
        int cards = 0;
        if (room >= Recipe.FACES_PER_CARD) {
            cards = 1;
            room -= Recipe.FACES_PER_CARD;
        }
        if (room >= Recipe.FACES_PER_PLANE) {
            result |= onePlane(open, variant);
            room -= Recipe.FACES_PER_PLANE;
        }
        if (cards == 1 && openCount >= settings.tipSides() && room >= Recipe.FACES_PER_CARD) {
            cards = 2;
        }
        return Recipe.withCards(result, cards);
    }

    /**
     * A clump. If a stick is wanted and fits under the limit, the stick
     * grows away from the side the block hangs on to and the clump sits
     * at its far end; otherwise the clump sits against the side the block
     * hangs on to.
     *
     * @param stemBase where the stick starts ({@link #stemBase})
     */
    private static int clump(int recipe, int open, int size, Direction support, boolean twig, int maxFaces, int stemBase) {
        Direction far = support.getOpposite();
        if (twig && Recipe.clumpFaces(open, far) + Recipe.FACES_PER_STEM <= maxFaces) {
            return Recipe.withTwig(Recipe.withClump(recipe, size, far), far, stemBase);
        }
        return Recipe.withClump(recipe, size, support);
    }

    /** An inner plane behind each pair of opposite sides of which at least one is open. */
    static int allPlanes(int open) {
        int planes = 0;
        if ((open & X_SIDES) != 0) {
            planes |= Recipe.PLANE_X;
        }
        if ((open & Y_SIDES) != 0) {
            planes |= Recipe.PLANE_Y;
        }
        if ((open & Z_SIDES) != 0) {
            planes |= Recipe.PLANE_Z;
        }
        return planes;
    }

    /**
     * One inner plane: an upright one if a side wall is open (across x or
     * across z; where both would do, the variant picks), else the level one.
     */
    static int onePlane(int open, int variant) {
        boolean x = (open & X_SIDES) != 0;
        boolean z = (open & Z_SIDES) != 0;
        if (x && z) {
            return (variant & 1) == 0 ? Recipe.PLANE_X : Recipe.PLANE_Z;
        }
        if (x) {
            return Recipe.PLANE_X;
        }
        if (z) {
            return Recipe.PLANE_Z;
        }
        return Recipe.PLANE_Y;
    }
}
