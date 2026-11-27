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
 * For a block in full leaf, and in the first two stages of autumn (the
 * same pieces, with thinned leaf pictures):
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
 *       smaller clump on a twig; every other block is drawn as shell.</li>
 *   <li><b>Mixed</b>: an outermost tip becomes a clump on a twig; every
 *       other block is drawn as bushy.</li>
 *   <li><b>Fast leaves</b>: outer faces and one inner plane, whatever the
 *       shape.</li>
 * </ul>
 *
 * <h2>The limit on faces</h2>
 * No leaf block is drawn from more faces than the settings allow
 * ({@link ShapeSettings#maxFaces}). The outer faces of the open sides are
 * always drawn. The other pieces are added one at a time, in the order the
 * rules above name them, and a piece that would go over the limit is left
 * out. With the limit at 6, the number of faces of a plain cube, a block
 * with one or two open sides still gets everything, a block with three or
 * four open sides gets one card and no plane, and a clump comes without
 * its twig.
 *
 * <h2>Clumps</h2>
 * A clump without a twig sits against the side on which the block hangs on
 * to its tree ({@link Surround#support}), so it rests on its branch like a
 * smaller leaf block. A clump with a twig sits at the far end of the block
 * instead: the twig grows from the branch across the gap and spreads out
 * inside the clump, like a spray of leaves at the end of a twig. (A twig
 * under a clump that rests on the branch would stick out of the leaves on
 * the far side like antlers.) A leaf block that touches nothing is never
 * made a clump, and neither is one with something resting on top (a layer
 * of snow), because the snow would hang in the air.
 *
 * <h2>The rules through the year</h2>
 * For every shape but plain:
 * <ul>
 *   <li><b>Last leaves</b> (the end of a spot's autumn): no outer faces any
 *       more. A spot with a twig ({@link #twigSpot}) shows the twig and one
 *       plane of last leaves along it; any other spot two upright planes.</li>
 *   <li><b>Bare</b>: the twig on a spot with a twig, nothing elsewhere.</li>
 *   <li><b>Buds</b>: as bare; the twig carries buds.</li>
 *   <li><b>First leaves</b>: a spot with a twig shows the budding twig and
 *       one plane of young leaves along it. Any other spot shows a small
 *       clump of young leaves against the side it hangs on to
 *       ({@link ShapeSettings#firstSize}), or two upright planes if it has
 *       no such side or carries snow.</li>
 *   <li><b>Half grown</b>: a larger clump against the side the block hangs
 *       on to ({@link ShapeSettings#halfSize}); without such a side, outer
 *       faces and inner planes.</li>
 * </ul>
 * A clump of spring is never larger than the clump the spot will be in
 * full leaf. None of these needs more than six faces, so the limit on
 * faces always holds.
 *
 * <h2>Inside the crown</h2>
 * A block whose six neighbors are all leaf blocks or full blocks
 * ({@link Surround#sheltered}) is buried in summer and costs nothing. While
 * leaves fall or come back, its neighbors show few leaves and it can be
 * seen between them, but never close up and never whole. So it is drawn
 * from fewer pieces than a block at the edge: outer faces only while it is
 * in leaf (no planes, cards or clump), one upright plane for its last or
 * first leaves, two for half-grown ones. Without this, a crown that is
 * losing or growing its leaves would need the most faces of the whole year
 * for blocks that are hardly seen.
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
     * Whether a twig is drawn on a leaf spot when it is bare (and with
     * buds, and under the last and first leaves).
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
                return Surround.wood(surround) != null || depth >= settings.twigDepth();
            case NONE:
            default:
                return false;
        }
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
     * @param twigSpot whether the spot has a twig ({@link #twigSpot}); only
     *                 looked at in the stages that draw one
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
                return twigSpot ? Recipe.withTwig(recipe, Surround.grow(surround)) : recipe;
            }
            return recipe | Recipe.FACES_BIT;
        }
        Direction support = Surround.loaded(surround) ? null : Surround.support(surround);
        boolean sheltered = Surround.sheltered(surround);
        switch (stage) {
            case LAST:
                if (twigSpot) {
                    return twigAndPlane(recipe, surround, variant);
                }
                return sheltered ? recipe | uprightPlane(variant) : recipe | Recipe.PLANE_X | Recipe.PLANE_Z;
            case BARE:
            case BUDS:
                return twigSpot ? Recipe.withTwig(recipe, Surround.grow(surround)) : recipe;
            case FIRST:
                if (twigSpot) {
                    return twigAndPlane(recipe, surround, variant);
                }
                if (sheltered) {
                    return recipe | uprightPlane(variant);
                }
                if (support != null) {
                    return Recipe.withClump(recipe, springClump(settings, open, settings.firstSize()), support);
                }
                return recipe | Recipe.PLANE_X | Recipe.PLANE_Z;
            case HALF:
                if (sheltered) {
                    return recipe | Recipe.PLANE_X | Recipe.PLANE_Z;
                }
                if (support != null) {
                    return Recipe.withClump(recipe, springClump(settings, open, settings.halfSize()), support);
                }
                return shell(recipe, open, variant, settings.maxFaces(), 3);
            case FULL:
            case THIN:
            case THINNER:
            default:
                return sheltered ? recipe | Recipe.FACES_BIT : inLeaf(settings, recipe, open, variant, support);
        }
    }

    /** The pieces of a block in leaf: the rules of the shapes. */
    private static int inLeaf(ShapeSettings settings, int recipe, int open, int variant, @Nullable Direction support) {
        if (settings.fastLeaves()) {
            return shell(recipe, open, variant, settings.maxFaces(), 1);
        }
        int openCount = Integer.bitCount(open);
        int size = support == null ? 0 : leafClump(settings, openCount);
        switch (settings.shape()) {
            case TUFTS:
                if (size != 0) {
                    return clump(recipe, open, size, support, openCount >= settings.clumpSides(), settings.maxFaces());
                }
                return shell(recipe, open, variant, settings.maxFaces(), 3);
            case MIXED:
                if (size != 0) {
                    return clump(recipe, open, size, support, true, settings.maxFaces());
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
     * The side of a clump of spring: the size wanted, but not larger than
     * the clump the spot is in full leaf (a spot must not shrink when its
     * leaves are grown).
     */
    private static int springClump(ShapeSettings settings, int open, int wanted) {
        int grown = leafClump(settings, Integer.bitCount(open));
        return grown == 0 ? wanted : Math.min(wanted, grown);
    }

    /** One of the two upright inner planes, picked by the position's variant. */
    private static int uprightPlane(int variant) {
        return (variant & 1) == 0 ? Recipe.PLANE_X : Recipe.PLANE_Z;
    }

    /**
     * A twig in the direction the block grows in, and one inner plane that
     * the twig lies in, so that the plane's leaves hang along the twig.
     */
    private static int twigAndPlane(int recipe, int surround, int variant) {
        Direction grow = Surround.grow(surround);
        int plane;
        switch (grow.getAxis()) {
            case X:
                plane = Recipe.PLANE_Z;
                break;
            case Z:
                plane = Recipe.PLANE_X;
                break;
            case Y:
            default:
                plane = uprightPlane(variant);
                break;
        }
        return Recipe.withTwig(recipe | plane, grow);
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
     * A clump. If a twig is wanted and fits under the limit, the twig grows
     * away from the side the block hangs on to and the clump sits at the
     * twig's far end; otherwise the clump sits against the side the block
     * hangs on to.
     */
    private static int clump(int recipe, int open, int size, Direction support, boolean twig, int maxFaces) {
        Direction far = support.getOpposite();
        if (twig && Recipe.clumpFaces(open, far) + Recipe.FACES_PER_TWIG <= maxFaces) {
            return Recipe.withTwig(Recipe.withClump(recipe, size, far), far);
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
