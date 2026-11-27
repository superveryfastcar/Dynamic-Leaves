package io.github.superveryfastcar.foliage.shape;

import io.github.superveryfastcar.foliage.util.PosHash;
import net.minecraft.core.Direction;

/**
 * Decides which pieces a leaf block is drawn from, given its surroundings
 * and the shape the player chose. Plain arithmetic on whole numbers: the
 * same inputs always give the same {@link Recipe}.
 *
 * <h2>The rules</h2>
 * <ul>
 *   <li>A leaf block with no open side is buried in the crown and shows
 *       nothing, in every shape.</li>
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
     * The recipe of one leaf block.
     *
     * @param settings the shape and its numbers; the shape must not be
     *                 {@link LeafShape#PLAIN} (plain leaves are not drawn
     *                 from a recipe at all)
     * @param surround what stands around the block ({@link Surround})
     * @param variant  the position's variant, 0 to 15
     * @return the recipe
     */
    public static int recipe(ShapeSettings settings, int surround, int variant) {
        int open = Surround.open(surround);
        int recipe = Recipe.start(open, variant);
        if (open == 0) {
            return recipe;
        }
        if (settings.fastLeaves()) {
            return shell(recipe, open, variant, settings.maxFaces(), 1);
        }
        int openCount = Integer.bitCount(open);
        Direction support = Surround.loaded(surround) ? null : Surround.support(surround);
        switch (settings.shape()) {
            case TUFTS:
                if (openCount >= settings.tipSides() && support != null) {
                    boolean outermost = openCount >= settings.clumpSides();
                    return clump(recipe, open, outermost ? settings.tipSize() : settings.tuftSize(), support, outermost, settings.maxFaces());
                }
                return shell(recipe, open, variant, settings.maxFaces(), 3);
            case MIXED:
                if (openCount >= settings.clumpSides() && support != null) {
                    return clump(recipe, open, settings.clumpSize(), support, true, settings.maxFaces());
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
