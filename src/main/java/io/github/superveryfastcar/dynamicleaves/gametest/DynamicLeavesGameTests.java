package io.github.superveryfastcar.dynamicleaves.gametest;

import com.dtteam.dynamictrees.block.leaves.DynamicLeavesBlock;
import com.dtteam.dynamictrees.tree.TreeHelper;
import com.dtteam.dynamictrees.tree.species.Species;
import com.dtteam.dynamictrees.worldgen.JoCode;
import io.github.superveryfastcar.calendar.api.CalendarRules;
import io.github.superveryfastcar.calendar.api.Season;
import io.github.superveryfastcar.dynamicleaves.DynamicLeaves;
import io.github.superveryfastcar.dynamicleaves.season.LeafClock;
import io.github.superveryfastcar.dynamicleaves.season.LeafStage;
import io.github.superveryfastcar.dynamicleaves.season.SeasonSettings;
import io.github.superveryfastcar.dynamicleaves.shape.CardPicture;
import io.github.superveryfastcar.dynamicleaves.shape.LeafLook;
import io.github.superveryfastcar.dynamicleaves.shape.LeafShape;
import io.github.superveryfastcar.dynamicleaves.shape.MiniBranch;
import io.github.superveryfastcar.dynamicleaves.shape.Recipe;
import io.github.superveryfastcar.dynamicleaves.shape.ShapeRules;
import io.github.superveryfastcar.dynamicleaves.shape.ShapeSettings;
import io.github.superveryfastcar.dynamicleaves.shape.Surround;
import io.github.superveryfastcar.dynamicleaves.shape.WinterTwigs;
import io.github.superveryfastcar.dynamicleaves.util.PosHash;
import io.github.superveryfastcar.seasons.api.SeasonsApi;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Automatic tests for Dynamic Leaves. They run inside a real (headless) Minecraft
 * server with {@code ./gradlew runGameTestServer}, which exits with an error
 * if any test fails.
 *
 * <h2>What is tested</h2>
 * <ol>
 *   <li><b>The rules as arithmetic</b>: how a recipe is packed, what each
 *       shape draws for every possible set of open sides, that the numbers
 *       of the settings are kept in range, that a position's variant is
 *       spread evenly, and how a leaf card's picture is cut.</li>
 *   <li><b>Real trees</b>: an oak, a birch, a dark oak and a spruce of
 *       Dynamic Trees are planted by command, and every one of their leaf
 *       blocks is read the way the leaf model reads it. The tests check
 *       that neighbors agree about the side between them, that twigs point
 *       away from the wood, and how many faces each shape needs per leaf
 *       block (written to the log, and held against a limit). Then each
 *       tree is taken through a whole year of the leaf calendar, step by
 *       step: no leaf block may ever need more faces than the limit,
 *       neighbors must agree at every step about the side between them,
 *       and a kind that does not shed must look the same all year.</li>
 * </ol>
 * What the game client really draws is checked by the self test in the
 * {@code dev} package, which needs a screen (or a pretend one).
 *
 * <h2>Why the tree tests ask for open sky</h2>
 * The game walls every test in with invisible blocks, and puts a lid on
 * top unless the test asks for open sky. A tree planted under the lid
 * would be cut off at four blocks. Each tree test has a batch of its own,
 * so that only one tree stands at a time; the crowns are wider than the
 * space between two tests.
 *
 * <h2>Why nothing depends on where a test stands</h2>
 * The game puts each test at a place of its choosing, and a leaf block's
 * variant depends on its place in the world. So no check here expects a
 * particular variant at a particular block; the checks hold for every
 * variant, or go through all of them.
 *
 * <p>These classes are left out of the released jar (see {@code build.gradle}).</p>
 */
@GameTestHolder(DynamicLeaves.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DynamicLeavesGameTests {

    /** The four shapes that are drawn from recipes. */
    private static final LeafShape[] SHAPES = {LeafShape.SHELL, LeafShape.BUSHY, LeafShape.TUFTS, LeafShape.MIXED};

    private static final Direction[] DIRECTIONS = Direction.values();

    /** How many thinner twigs a mini branch has with the mod's own settings. */
    private static final int FORKS = ShapeSettings.DEFAULT.forks();

    /**
     * The faces the unmodded game draws for every leaf block. With the
     * mod's own settings no leaf block may need more, on any tree.
     */
    private static final int PLAIN_FACES = 6;

    /**
     * The most faces per leaf block, averaged over a tree with a full
     * crown (oak, birch, dark oak and the like), with the mod's own
     * settings. A lone tree is the worst case: in a forest the crowns touch
     * and more sides are covered.
     */
    private static final double MOST_FACES_FULL_CROWN = 5.0;

    /** One of the largest oaks Dynamic Trees knows, as its growth code. */
    static final String BIG_OAK = "JOJxOOKPz+vnSx8nSR+beJnzzz89x98+txuJXvzePt6WJ9y98mOOJ67+SPkxJ+fy8";

    private DynamicLeavesGameTests() {
    }

    // ------------------------------------------------------------------
    // Small helpers
    // ------------------------------------------------------------------

    /** The failures already written to the log, so that each is written once. */
    private static final Set<String> LOGGED_FAILURES = ConcurrentHashMap.newKeySet();

    /**
     * A failed check: writes it to the log, then stops the test. The log
     * matters because the game's own report of a failed test is short.
     */
    static GameTestAssertException failure(String message) {
        if (LOGGED_FAILURES.size() < 1000 && LOGGED_FAILURES.add(message)) {
            DynamicLeaves.LOGGER.warn("Dynamic Leaves test: a check did not hold: {}", message);
        }
        return new GameTestAssertException(message);
    }

    /** Fails the test with a message unless the condition holds. */
    static void check(boolean condition, String message) {
        if (!condition) {
            throw failure(message);
        }
    }

    /** The settings of a shape with the mod's own numbers: at most 6 faces per leaf block. */
    private static ShapeSettings settings(LeafShape shape) {
        return ShapeSettings.DEFAULT.withShape(shape);
    }

    /** The settings of a shape with room for every piece: the highest limit on faces. */
    private static ShapeSettings rich(LeafShape shape) {
        return ShapeSettings.DEFAULT.withShape(shape).withMaxFaces(ShapeSettings.MAX_FACES);
    }

    /** The surroundings of a leaf block with the given open sides and nothing else of note. */
    private static int surround(int open) {
        return Surround.of(open, null, null, false);
    }

    /** The bit of one side in a set of open sides. */
    private static int bit(Direction side) {
        return 1 << side.ordinal();
    }

    // ------------------------------------------------------------------
    // The rules as arithmetic
    // ------------------------------------------------------------------

    /** Whatever is packed into a recipe comes out again unchanged, and the face counts add up. */
    @GameTest(template = "empty", batch = "dynamicleaves_rules")
    public static void recipePacksAndUnpacks(GameTestHelper helper) {
        for (int open = 0; open < 64; open++) {
            for (int variant = 0; variant < Recipe.VARIANTS; variant++) {
                for (int cards = 0; cards <= 2; cards++) {
                    for (int clump = 0; clump <= 15; clump += 5) {
                        for (int twig = -1; twig < 6; twig++) {
                            for (int anchor = -1; anchor < 6; anchor += clump == 0 ? 7 : 1) {
                                Direction anchorSide = anchor < 0 ? null : DIRECTIONS[anchor];
                                int recipe = Recipe.start(open, variant);
                                recipe = Recipe.withCards(recipe, cards);
                                if (clump != 0) {
                                    recipe = Recipe.withClump(recipe, clump, anchorSide);
                                }
                                // Where the mini branch starts goes through all three kinds as the variant does.
                                int base = variant % 3;
                                Direction twigSide = twig < 0 ? null : DIRECTIONS[twig];
                                if (twigSide != null) {
                                    recipe = Recipe.withTwig(recipe, twigSide, base);
                                }
                                recipe |= Recipe.FACES_BIT | Recipe.PLANE_Y;
                                String what = "open " + open + " variant " + variant + " cards " + cards + " clump " + clump
                                        + " anchor " + anchorSide + " twig " + twig;
                                check(recipe >= 0, "a recipe is never negative: " + what);
                                check(Recipe.open(recipe) == open, "open sides kept: " + what);
                                check(Recipe.variant(recipe) == variant, "variant kept: " + what);
                                check(Recipe.cards(recipe) == cards, "cards kept: " + what);
                                check(Recipe.clump(recipe) == clump, "clump kept: " + what);
                                check(Recipe.anchor(recipe) == (clump == 0 ? null : anchorSide), "anchor kept: " + what);
                                check(Recipe.twig(recipe) == twigSide, "twig kept: " + what);
                                check(twigSide == null || Recipe.twigBase(recipe) == base, "the start of the mini branch kept: " + what);
                                // A clump at the far end of the twig makes it a plain stick; one without a side sits on its end.
                                boolean stick = twigSide != null && clump != 0 && anchorSide == twigSide;
                                check(Recipe.stemOnly(recipe) == stick, "a stick under a clump at the far end: " + what);
                                check(Recipe.tuft(recipe) == (twigSide != null && clump != 0 && anchorSide == null), "a clump on the twig's end: " + what);
                                check(Recipe.faces(recipe) && Recipe.plane(recipe, Recipe.PLANE_Y)
                                        && !Recipe.plane(recipe, Recipe.PLANE_X) && !Recipe.plane(recipe, Recipe.PLANE_Z), "planes kept: " + what);
                                // A clump has six faces, less the one against a covered side.
                                boolean anchorCovered = anchorSide != null && (open & bit(anchorSide)) == 0;
                                int clumpFaces = clump == 0 ? 0 : anchorCovered ? 5 : 6;
                                check(Recipe.clumpFaces(recipe) == clumpFaces, "clump faces counted: " + what);
                                int leafFaces = Integer.bitCount(open) + 2 + 2 * cards + clumpFaces;
                                check(Recipe.leafQuadCount(recipe) == leafFaces, "faces of leaves counted: " + what);
                                for (int forks = 0; forks <= MiniBranch.MAX_FORKS; forks++) {
                                    // A main twig has five faces, each thinner twig four more; the thin kind has two at most.
                                    int twigFaces = twigSide == null ? 0 : stick ? 4
                                            : 5 + 4 * Math.min(forks, base == MiniBranch.DEEP ? MiniBranch.DEEP_FORKS : MiniBranch.MAX_FORKS);
                                    check(Recipe.twigFaces(recipe, forks) == twigFaces, "faces of the mini branch counted: " + what + " forks " + forks);
                                    check(Recipe.quadCount(recipe, forks) == leafFaces + twigFaces, "faces counted: " + what + " forks " + forks);
                                    // In full leaf a block is a whole block where leaves cannot be see-through.
                                    check(Recipe.solidQuadCount(recipe, forks) == Integer.bitCount(open), "solid faces counted: " + what);
                                }
                                check(Recipe.slantedQuadCount(recipe) == 2 * cards, "slanted faces counted: " + what);
                            }
                        }
                    }
                }
            }
        }
        helper.succeed();
    }

    /**
     * The boxes of a mini branch: one main twig that comes out of the
     * wood, thinner twigs that leave it at right angles in different
     * directions, the faces that are counted are the faces that are
     * there, no face lies in a plane a clump of leaves could have a
     * face in, and one that stands upright is the short kind.
     */
    @GameTest(template = "empty", batch = "dynamicleaves_rules")
    public static void miniBranchBoxes(GameTestHelper helper) {
        int[] bases = {MiniBranch.FLUSH, MiniBranch.REACH, MiniBranch.DEEP};
        Set<String> looks = new java.util.HashSet<>();
        check(MiniBranch.upright(Direction.UP) && MiniBranch.upright(Direction.DOWN) && !MiniBranch.upright(Direction.NORTH)
                && !MiniBranch.upright(Direction.EAST), "a mini branch that grows up or down is the upright kind, a level one is not");
        // Both kinds of every start: the level one, and the short one that stands upright.
        for (int kind = 0; kind < bases.length * 2; kind++) {
            int base = bases[kind >> 1];
            boolean upright = (kind & 1) != 0;
            boolean deep = base == MiniBranch.DEEP;
            // The short kind is for mini branches that start at the wood; the link of a chain is the same both ways.
            boolean shortKind = upright && !deep;
            float thick = deep ? 1.0F : 2.0F;
            for (int variant = 0; variant < Recipe.VARIANTS; variant++) {
                for (int wanted = 0; wanted <= MiniBranch.MAX_FORKS; wanted++) {
                    String what = "start " + base + (upright ? " upright" : " level") + " variant " + variant + " wanted " + wanted;
                    List<MiniBranch.Box> boxes = MiniBranch.boxes(base, variant, wanted, upright);
                    int forks = Math.min(wanted, deep ? MiniBranch.DEEP_FORKS : MiniBranch.MAX_FORKS);
                    check(MiniBranch.forks(base, wanted) == forks && boxes.size() == 1 + forks, "a main twig and " + forks + " thinner ones: " + what);
                    int faces = 0;
                    for (MiniBranch.Box box : boxes) {
                        faces += Integer.bitCount(box.faces());
                    }
                    check(faces == MiniBranch.faceCount(base, wanted) && faces == 5 + 4 * forks, "five faces for the main twig and four for every thinner one, got " + faces + ": " + what);

                    // The main twig: a square stick up the middle, from the wood to its end.
                    MiniBranch.Box stem = boxes.get(0);
                    check(stem.along() == 1 && stem.strip() == 0, "the main twig is long in the direction the mini branch grows in: " + what);
                    check(stem.x1() - stem.x0() == thick && stem.z1() - stem.z0() == thick && stem.x0() + stem.x1() == 16.0F && stem.z0() + stem.z1() == 16.0F,
                            "it is " + thick + " thick and stands in the middle of the block's side: " + what);
                    float from = base == MiniBranch.REACH ? -MiniBranch.REACH_BACK : deep ? -MiniBranch.DEEP_BACK : 0.0F;
                    check(stem.y0() == from && stem.y1() == MiniBranch.length(base, variant, upright), "it starts where its kind starts and ends at its length: " + what);
                    if (shortKind) {
                        // The owner's rule: nothing goes straight up for more than about two thirds of a block.
                        // What stands on a limb is its stub or the gap to its bark, then the main twig. The
                        // thinnest limb has a stub of seven pixels; the thinnest limb without a stub (two
                        // pixels from its middle to its bark) leaves six pixels to the block's side.
                        float below = base == MiniBranch.REACH ? 6.0F : 7.0F;
                        check(stem.y1() >= 1.0F && below + stem.y1() >= 8.0F && below + stem.y1() <= 32.0F / 3.0F,
                                "an upright mini branch stands half to two thirds of a block above its limb, got " + (below + stem.y1()) + " pixels: " + what);
                    } else {
                        check(stem.y1() >= 8.0F && stem.y1() <= 13.0F, "it reaches past the middle of the block and stays inside it: " + what);
                    }
                    check(!stem.has(Direction.DOWN) && stem.has(Direction.UP), "its near end is in the wood and not drawn, its far end is: " + what);

                    // The thinner twigs.
                    Set<Direction> ways = new java.util.HashSet<>();
                    StringBuilder look = new StringBuilder().append(kind);
                    for (int i = 1; i < boxes.size(); i++) {
                        MiniBranch.Box fork = boxes.get(i);
                        check(fork.along() != 1 && fork.strip() == i, "a thinner twig lies across the main twig and shows a strip of bark of its own: " + what);
                        // On an upright one the thinner twigs are spread over the limb's stub too, so they may
                        // leave below the block's side: two pixels clear of the bark of the thinnest limb.
                        float lowest = !shortKind ? 0.0F : base == MiniBranch.REACH ? -4.0F : -5.0F;
                        check(fork.y1() - fork.y0() == 1.0F && fork.y0() > lowest && fork.y1() < stem.y1(), "it is one pixel thick and leaves the main twig below its end: " + what);
                        boolean alongX = fork.along() == 0;
                        float length = alongX ? fork.x1() - fork.x0() : fork.z1() - fork.z0();
                        float width = alongX ? fork.z1() - fork.z0() : fork.x1() - fork.x0();
                        check(width == 1.0F && length >= 3.0F && length <= 6.0F, "it is one pixel wide and three to six long: " + what);
                        // It touches a side of the main twig, and the face there is the one that is not drawn.
                        Direction toward;
                        if (alongX) {
                            toward = fork.x0() == stem.x1() ? Direction.WEST : fork.x1() == stem.x0() ? Direction.EAST : null;
                            check(fork.z0() >= stem.z0() && fork.z1() <= stem.z1(), "it sits on the side of the main twig: " + what);
                        } else {
                            toward = fork.z0() == stem.z1() ? Direction.NORTH : fork.z1() == stem.z0() ? Direction.SOUTH : null;
                            check(fork.x0() >= stem.x0() && fork.x1() <= stem.x1(), "it sits on the side of the main twig: " + what);
                        }
                        check(toward != null && !fork.has(toward) && !fork.has(toward.getOpposite()) && Integer.bitCount(fork.faces()) == MiniBranch.FORK_FACES,
                                "it starts on the main twig, and only its four long sides are drawn: " + what);
                        check(ways.add(toward), "no two thinner twigs of a mini branch point the same way: " + what);
                        check(fork.x0() >= 0.0F && fork.x1() <= 16.0F && fork.z0() >= 0.0F && fork.z1() <= 16.0F, "it stays inside the block: " + what);
                        look.append(' ').append(toward).append(fork.y0()).append('+').append(length);
                    }
                    if (wanted == MiniBranch.MAX_FORKS) {
                        looks.add(look.append(' ').append(stem.y1()).toString());
                    }

                    // No face in a plane that a clump of leaves can have a face in. Clumps sit in the
                    // middle across the mini branch, 3 to 14 pixels wide, with faces on whole and half
                    // pixels; along it their faces lie on whole pixels.
                    for (MiniBranch.Box box : boxes) {
                        float[][] planes = {{box.x0(), box.x1()}, {box.y0(), box.y1()}, {box.z0(), box.z1()}};
                        Direction[][] sides = {{Direction.WEST, Direction.EAST}, {Direction.DOWN, Direction.UP}, {Direction.NORTH, Direction.SOUTH}};
                        for (int axis = 0; axis < 3; axis++) {
                            for (int end = 0; end < 2; end++) {
                                if (!box.has(sides[axis][end])) {
                                    continue;
                                }
                                float at = planes[axis][end];
                                boolean onHalf = at * 2.0F == Math.round(at * 2.0F);
                                if (axis == 1) {
                                    check(at != Math.round(at), "no face across the mini branch on a whole pixel, got " + at + ": " + what);
                                } else {
                                    check(!onHalf || Math.abs(at - 8.0F) < ShapeSettings.MIN_BUD / 2.0F,
                                            "no face along the mini branch where the side of a clump could be, got " + at + ": " + what);
                                }
                            }
                        }
                    }
                }
                // The clump on the end of the main twig holds that end and stays inside the block.
                for (int size = ShapeSettings.MIN_BUD; size <= ShapeSettings.MAX_CLUMP; size++) {
                    int end = MiniBranch.tuftEnd(base, variant, size, upright);
                    float length = MiniBranch.length(base, variant, upright);
                    check(end <= 16 && end - size >= 0, "a clump of " + size + " on the main twig stays inside the block: start " + base + " variant " + variant + (upright ? " upright" : ""));
                    check(end > length && end - size < length, "and holds the end of the main twig: start " + base + " variant " + variant + " size " + size);
                }
            }
            // The stick that carries a clump at the far end of the block: four sides and no ends.
            MiniBranch.Box stick = MiniBranch.stem(base == MiniBranch.DEEP ? MiniBranch.FLUSH : base, 7.0F);
            check(Integer.bitCount(stick.faces()) == MiniBranch.BARE_STEM_FACES && !stick.has(Direction.DOWN) && !stick.has(Direction.UP)
                    && stick.y1() == 7.0F && stick.y0() == (base == MiniBranch.REACH ? -MiniBranch.REACH_BACK : 0.0F), "a stick has four sides and no ends");
        }
        // Sixteen variants give sixteen different mini branches of each kind.
        check(looks.size() == bases.length * 2 * Recipe.VARIANTS, "every variant gives another mini branch, level and upright, got " + looks.size() + " different ones");
        check(MiniBranch.STEM_FACES == 5 && MiniBranch.FORK_FACES == 4 && MiniBranch.faceCount(MiniBranch.FLUSH, 3) == 17,
                "a whole mini branch is drawn from seventeen faces");
        helper.succeed();
    }

    /** The packed surroundings give back what was put in; a block hangs on to wood first, and a twig grows away from there. */
    @GameTest(template = "empty", batch = "dynamicleaves_rules")
    public static void surroundPacksAndUnpacks(GameTestHelper helper) {
        for (int open = 0; open < 64; open++) {
            for (int wood = -1; wood < 6; wood++) {
                for (int heart = -1; heart < 6; heart++) {
                    for (int loaded = 0; loaded < 2; loaded++) {
                        Direction woodSide = wood < 0 ? null : DIRECTIONS[wood];
                        Direction heartSide = heart < 0 ? null : DIRECTIONS[heart];
                        int packed = Surround.of(open, woodSide, heartSide, loaded == 1);
                        String what = "open " + open + " wood " + woodSide + " heart " + heartSide + " loaded " + loaded;
                        check(!Surround.sheltered(packed), "packed by hand, a block is not sheltered: " + what);
                        check(Surround.open(packed) == open, "open sides kept: " + what);
                        check(Surround.openCount(packed) == Integer.bitCount(open), "open sides counted: " + what);
                        check(Surround.wood(packed) == woodSide, "wood kept: " + what);
                        check(Surround.heart(packed) == heartSide, "heart kept: " + what);
                        check(Surround.loaded(packed) == (loaded == 1), "load kept: " + what);
                        for (Direction side : DIRECTIONS) {
                            check(Surround.isOpen(packed, side) == ((open & bit(side)) != 0), "side " + side + " read back: " + what);
                        }
                        Direction support = Surround.support(packed);
                        if (woodSide != null) {
                            check(support == woodSide, "a block hangs on to the wood first: " + what);
                        } else if (heartSide != null) {
                            check(support == heartSide, "without wood, a block hangs on to the heart of the crown: " + what);
                        } else if (open == 63) {
                            check(support == null, "a block that touches nothing hangs on to nothing: " + what);
                        } else {
                            check(support != null && !Surround.isOpen(packed, support), "else a block hangs on to a covered side: " + what);
                            if (!Surround.isOpen(packed, Direction.DOWN)) {
                                check(support == Direction.DOWN, "and to the one below, if that is covered: " + what);
                            }
                        }
                        check(Surround.grow(packed) == (support == null ? Direction.UP : support.getOpposite()),
                                "a twig grows away from the side the block hangs on to, or up: " + what);
                    }
                }
            }
        }
        helper.succeed();
    }

    /** A leaf block with no open side draws nothing at all, in every shape. */
    @GameTest(template = "empty", batch = "dynamicleaves_rules")
    public static void buriedBlocksDrawNothing(GameTestHelper helper) {
        for (LeafShape shape : SHAPES) {
            for (int fast = 0; fast < 2; fast++) {
                for (int variant = 0; variant < Recipe.VARIANTS; variant++) {
                    int recipe = ShapeRules.recipe(rich(shape).withFastLeaves(fast == 1), surround(0), variant);
                    check(Recipe.quadCount(recipe, FORKS) == 0 && Recipe.solidQuadCount(recipe, FORKS) == 0,
                            shape + " draws nothing for a buried leaf block (fast " + fast + ", variant " + variant + ")");
                }
            }
        }
        helper.succeed();
    }

    /**
     * No leaf block is ever drawn from more faces than the limit allows:
     * every shape, every set of open sides, every limit, every kind of
     * support, with and without snow on top.
     */
    @GameTest(template = "empty", batch = "dynamicleaves_rules")
    public static void noBlockGoesOverTheLimit(GameTestHelper helper) {
        for (LeafShape shape : SHAPES) {
            for (int fast = 0; fast < 2; fast++) {
                for (int limit = ShapeSettings.MIN_FACES; limit <= ShapeSettings.MAX_FACES; limit++) {
                    ShapeSettings settings = settings(shape).withMaxFaces(limit).withFastLeaves(fast == 1);
                    for (int open = 0; open < 64; open++) {
                        for (int wood = -1; wood < 6; wood++) {
                            for (int heart = -1; heart < 6; heart += 3) {
                                for (int loaded = 0; loaded < 2; loaded++) {
                                    int around = Surround.of(open, wood < 0 ? null : DIRECTIONS[wood], heart < 0 ? null : DIRECTIONS[heart], loaded == 1);
                                    for (int variant = 0; variant < Recipe.VARIANTS; variant += 5) {
                                        int recipe = ShapeRules.recipe(settings, around, variant);
                                        String what = shape + " fast " + fast + " limit " + limit + " open " + open + " wood " + wood
                                                + " heart " + heart + " loaded " + loaded + " variant " + variant;
                                        check(Recipe.quadCount(recipe, FORKS) <= limit, Recipe.quadCount(recipe, FORKS) + " faces, over the limit: " + what);
                                        check(Recipe.open(recipe) == open && Recipe.variant(recipe) == variant, "open sides and variant kept: " + what);
                                        // What is drawn fills the block's open sides: outer faces, or a clump instead.
                                        if (open != 0) {
                                            check(Recipe.faces(recipe) != (Recipe.clump(recipe) != 0), "outer faces or a clump, never both or neither: " + what);
                                        }
                                        if (Recipe.twig(recipe) != null) {
                                            check(Recipe.stemOnly(recipe), "in full leaf a twig only as the stick under a clump: " + what);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        helper.succeed();
    }

    /** Shell: outer faces on the open sides, and inner planes behind open sides as far as the limit allows. */
    @GameTest(template = "empty", batch = "dynamicleaves_rules")
    public static void shellRules(GameTestHelper helper) {
        int xSides = bit(Direction.WEST) | bit(Direction.EAST);
        int ySides = bit(Direction.DOWN) | bit(Direction.UP);
        int zSides = bit(Direction.NORTH) | bit(Direction.SOUTH);
        for (int open = 1; open < 64; open++) {
            int count = Integer.bitCount(open);
            int wanted = ((open & xSides) != 0 ? 1 : 0) + ((open & ySides) != 0 ? 1 : 0) + ((open & zSides) != 0 ? 1 : 0);
            for (int variant = 0; variant < Recipe.VARIANTS; variant++) {
                // With room for everything: a plane behind each pair of sides with an open one.
                int all = ShapeRules.recipe(rich(LeafShape.SHELL), surround(open), variant);
                String what = "shell, open " + open + ", variant " + variant;
                check(Recipe.faces(all) && Recipe.open(all) == open, "outer faces on the open sides: " + what);
                check(Recipe.plane(all, Recipe.PLANE_X) == ((open & xSides) != 0), "plane across x: " + what);
                check(Recipe.plane(all, Recipe.PLANE_Y) == ((open & ySides) != 0), "plane across y: " + what);
                check(Recipe.plane(all, Recipe.PLANE_Z) == ((open & zSides) != 0), "plane across z: " + what);
                check(Recipe.cards(all) == 0 && Recipe.clump(all) == 0 && Recipe.twig(all) == null, "nothing else: " + what);
                check(Recipe.slantedQuadCount(all) == 0, "no slanted faces: " + what);
                // With the limit of the unmodded game: as many of those planes as fit.
                int six = ShapeRules.recipe(settings(LeafShape.SHELL), surround(open), variant);
                check(Recipe.faces(six) && Recipe.cards(six) == 0 && Recipe.clump(six) == 0, "outer faces, no cards, no clump at the limit of 6: " + what);
                check(Recipe.planeCount(six) == Math.min(wanted, (6 - count) / 2), "as many planes as fit under 6: " + what);
                check(Recipe.quadCount(all, FORKS) >= Recipe.quadCount(six, FORKS), "the limit never adds faces: " + what);
                for (int plane : new int[] {Recipe.PLANE_X, Recipe.PLANE_Y, Recipe.PLANE_Z}) {
                    check(!Recipe.plane(six, plane) || Recipe.plane(all, plane), "only planes that are wanted: " + what);
                }
                if (Recipe.planeCount(six) == 1 && (open & (xSides | zSides)) != 0) {
                    check(!Recipe.plane(six, Recipe.PLANE_Y), "the one plane that fits stands upright where a side wall is open: " + what);
                }
            }
        }
        helper.succeed();
    }

    /** Bushy: outer faces, a card, one inner plane (upright where a side wall is open), a second card on a tip. */
    @GameTest(template = "empty", batch = "dynamicleaves_rules")
    public static void bushyRules(GameTestHelper helper) {
        ShapeSettings rich = rich(LeafShape.BUSHY);
        ShapeSettings six = settings(LeafShape.BUSHY);
        boolean sawBothPlanes = false;
        for (int open = 1; open < 64; open++) {
            int count = Integer.bitCount(open);
            int planesSeen = 0;
            for (int variant = 0; variant < Recipe.VARIANTS; variant++) {
                // With room for everything.
                int recipe = ShapeRules.recipe(rich, surround(open), variant);
                String what = "bushy, open " + open + ", variant " + variant;
                check(Recipe.faces(recipe) && Recipe.open(recipe) == open, "outer faces on the open sides: " + what);
                check(Recipe.planeCount(recipe) == 1, "exactly one inner plane: " + what);
                boolean sideWallOpen = (open & ~(bit(Direction.DOWN) | bit(Direction.UP))) != 0;
                check(Recipe.plane(recipe, Recipe.PLANE_Y) == !sideWallOpen, "the plane stands upright where a side wall is open: " + what);
                if (Recipe.plane(recipe, Recipe.PLANE_X)) {
                    check((open & (bit(Direction.WEST) | bit(Direction.EAST))) != 0, "a plane across x only behind an open west or east side: " + what);
                    planesSeen |= 1;
                }
                if (Recipe.plane(recipe, Recipe.PLANE_Z)) {
                    check((open & (bit(Direction.NORTH) | bit(Direction.SOUTH))) != 0, "a plane across z only behind an open north or south side: " + what);
                    planesSeen |= 2;
                }
                int expectedCards = count >= rich.tipSides() ? 2 : 1;
                check(Recipe.cards(recipe) == expectedCards, expectedCards + " card(s): " + what);
                check(Recipe.clump(recipe) == 0 && Recipe.twig(recipe) == null, "no clump and no twig: " + what);

                // With the limit of the unmodded game: the card comes before the plane, the second card last.
                int limited = ShapeRules.recipe(six, surround(open), variant);
                check(Recipe.faces(limited) && Recipe.clump(limited) == 0, "outer faces at the limit of 6: " + what);
                int expectedLimitedCards = count <= 4 ? 1 : 0;
                int expectedLimitedPlanes = count <= 2 ? 1 : 0;
                check(Recipe.cards(limited) == expectedLimitedCards, expectedLimitedCards + " card(s) at the limit of 6: " + what);
                check(Recipe.planeCount(limited) == expectedLimitedPlanes, expectedLimitedPlanes + " plane(s) at the limit of 6: " + what);
            }
            sawBothPlanes |= planesSeen == 3;
        }
        check(sawBothPlanes, "where both upright planes would do, the variant picks one or the other");
        // In between: a limit of 7 gives a tip its plane back, 9 its second card.
        int tip = bit(Direction.UP) | bit(Direction.WEST) | bit(Direction.NORTH);
        int seven = ShapeRules.recipe(six.withMaxFaces(7), surround(tip), 0);
        check(Recipe.cards(seven) == 1 && Recipe.planeCount(seven) == 1, "a tip with a limit of 7: one card and the plane");
        int nine = ShapeRules.recipe(six.withMaxFaces(9), surround(tip), 0);
        check(Recipe.cards(nine) == 2 && Recipe.planeCount(nine) == 1, "a tip with a limit of 9: both cards and the plane");
        helper.succeed();
    }

    /** Tufts: tips become clumps on their branch, outermost tips smaller clumps (on a twig if the limit allows), everything else is shell. */
    @GameTest(template = "empty", batch = "dynamicleaves_rules")
    public static void tuftsRules(GameTestHelper helper) {
        ShapeSettings rich = rich(LeafShape.TUFTS);
        ShapeSettings six = settings(LeafShape.TUFTS);
        for (int open = 1; open < 64; open++) {
            int count = Integer.bitCount(open);
            for (Direction wood : DIRECTIONS) {
                int around = Surround.of(open, wood, null, false);
                int recipe = ShapeRules.recipe(rich, around, 5);
                int limited = ShapeRules.recipe(six, around, 5);
                String what = "tufts, open " + open + ", wood " + wood;
                if (count >= rich.clumpSides()) {
                    check(Recipe.clump(recipe) == rich.tipSize(), "an outermost tip is a clump of the tip size: " + what);
                    check(Recipe.twig(recipe) == wood.getOpposite(), "on a twig that grows away from the wood: " + what);
                    check(Recipe.anchor(recipe) == wood.getOpposite(), "the clump sits at the far end of its twig: " + what);
                    check(Recipe.stemOnly(recipe) && Recipe.quadCount(recipe, FORKS) <= rich.maxFaces(), "clump and stick stay under the limit: " + what);
                    check(!Recipe.faces(recipe) && Recipe.planeCount(recipe) == 0 && Recipe.cards(recipe) == 0, "and nothing else: " + what);
                    check(Recipe.clump(limited) == six.tipSize() && Recipe.anchor(limited) == wood && Recipe.twig(limited) == null,
                            "at the limit of 6 the same clump without the twig, against the wood: " + what);
                } else if (count >= rich.tipSides()) {
                    check(Recipe.clump(recipe) == rich.tuftSize() && Recipe.anchor(recipe) == wood, "a tip is a clump of the tuft size against the wood: " + what);
                    check(Recipe.twig(recipe) == null && !Recipe.faces(recipe) && Recipe.planeCount(recipe) == 0, "and nothing else: " + what);
                    check(limited == recipe, "the same at the limit of 6: " + what);
                } else {
                    check(recipe == ShapeRules.recipe(rich(LeafShape.SHELL), around, 5), "everything else is drawn as shell: " + what);
                    check(limited == ShapeRules.recipe(settings(LeafShape.SHELL), around, 5), "also at the limit of 6: " + what);
                }
                // Something lying on top (snow) keeps the block whole.
                int loaded = ShapeRules.recipe(rich, Surround.of(open, wood, null, true), 5);
                check(Recipe.clump(loaded) == 0 && Recipe.faces(loaded), "a block with snow on top stays whole: " + what);
            }
        }
        // A leaf block that touches nothing has nothing to sit against and stays whole.
        int lone = ShapeRules.recipe(rich, surround(63), 0);
        check(Recipe.clump(lone) == 0 && Recipe.faces(lone), "a leaf block that touches nothing stays whole");
        helper.succeed();
    }

    /** Mixed: outermost tips become clumps on their branch (on a twig if the limit allows), everything else is bushy. */
    @GameTest(template = "empty", batch = "dynamicleaves_rules")
    public static void mixedRules(GameTestHelper helper) {
        ShapeSettings rich = rich(LeafShape.MIXED);
        ShapeSettings six = settings(LeafShape.MIXED);
        for (int open = 1; open < 64; open++) {
            int count = Integer.bitCount(open);
            for (int variant = 0; variant < Recipe.VARIANTS; variant++) {
                int around = Surround.of(open, null, Direction.EAST, false);
                int recipe = ShapeRules.recipe(rich, around, variant);
                int limited = ShapeRules.recipe(six, around, variant);
                String what = "mixed, open " + open + ", variant " + variant;
                if (count >= rich.clumpSides()) {
                    check(Recipe.clump(recipe) == rich.clumpSize(), "an outermost tip is a clump: " + what);
                    check(Recipe.twig(recipe) == Direction.WEST, "on a twig that grows away from the heart of the crown: " + what);
                    check(Recipe.anchor(recipe) == Direction.WEST, "the clump sits at the far end of its twig: " + what);
                    check(!Recipe.faces(recipe) && Recipe.planeCount(recipe) == 0 && Recipe.cards(recipe) == 0, "and nothing else: " + what);
                    check(Recipe.clump(limited) == six.clumpSize() && Recipe.anchor(limited) == Direction.EAST && Recipe.twig(limited) == null,
                            "at the limit of 6 the same clump without the twig, against the heart of the crown: " + what);
                    // The clump's face against a covered side is not drawn.
                    check(Recipe.quadCount(limited, FORKS) == ((open & bit(Direction.EAST)) == 0 ? 5 : 6), "five faces against a covered side, else six: " + what);
                } else {
                    check(recipe == ShapeRules.recipe(rich(LeafShape.BUSHY), around, variant), "everything else is drawn as bushy: " + what);
                    check(limited == ShapeRules.recipe(settings(LeafShape.BUSHY), around, variant), "also at the limit of 6: " + what);
                }
                int loaded = ShapeRules.recipe(rich, Surround.of(open, null, Direction.EAST, true), variant);
                check(Recipe.clump(loaded) == 0 && Recipe.faces(loaded), "a block with snow on top stays whole: " + what);
            }
        }
        helper.succeed();
    }

    /** The "fewest faces" setting: outer faces and one inner plane if it fits, whatever the shape. */
    @GameTest(template = "empty", batch = "dynamicleaves_rules")
    public static void fastLeavesRules(GameTestHelper helper) {
        for (LeafShape shape : SHAPES) {
            for (int open = 1; open < 64; open++) {
                int count = Integer.bitCount(open);
                for (int variant = 0; variant < Recipe.VARIANTS; variant++) {
                    int around = Surround.of(open, Direction.DOWN, null, false);
                    String what = shape + " with fewest faces, open " + open + ", variant " + variant;
                    int recipe = ShapeRules.recipe(rich(shape).withFastLeaves(true), around, variant);
                    check(Recipe.quadCount(recipe, FORKS) == count + 2 && Recipe.planeCount(recipe) == 1, "outer faces and one plane: " + what);
                    check(Recipe.slantedQuadCount(recipe) == 0 && Recipe.clump(recipe) == 0, "no slanted faces, no clump: " + what);
                    int limited = ShapeRules.recipe(settings(shape).withFastLeaves(true), around, variant);
                    check(Recipe.quadCount(limited, FORKS) == (count <= 4 ? count + 2 : count), "at the limit of 6, the plane only where it fits: " + what);
                }
            }
        }
        helper.succeed();
    }

    /** Numbers outside their range are pulled back into it. */
    @GameTest(template = "empty", batch = "dynamicleaves_rules")
    public static void settingsStayInRange(GameTestHelper helper) {
        ShapeSettings wild = new ShapeSettings(null, false, 99, 9, 2, 500, -3, 0, 99, 1, null, 99, 99, 1, 99, 1, 2, 99);
        check(wild.shape() == LeafShape.MIXED, "a missing shape becomes the mod's own");
        check(wild.maxFaces() == ShapeSettings.MAX_FACES, "the limit on faces at most " + ShapeSettings.MAX_FACES);
        check(wild.withMaxFaces(1).maxFaces() == ShapeSettings.MIN_FACES, "and at least " + ShapeSettings.MIN_FACES);
        check(wild.tipSides() == 6, "tip sides at most 6, got " + wild.tipSides());
        check(wild.clumpSides() == 6, "clump sides not below tip sides, got " + wild.clumpSides());
        check(wild.cardWidth() == ShapeSettings.MAX_CARD && wild.cardHeight() == ShapeSettings.MIN_CARD, "card sizes in range");
        check(wild.tuftSize() == ShapeSettings.MIN_CLUMP && wild.tipSize() == ShapeSettings.MAX_CLUMP
                && wild.clumpSize() == ShapeSettings.MIN_CLUMP, "clump sizes in range");
        check(wild.winterTwigs() == WinterTwigs.NEAR, "a missing choice of winter twigs becomes the mod's own");
        check(wild.twigDepth() == ShapeSettings.MAX_DEPTH, "the twig depth at most " + ShapeSettings.MAX_DEPTH);
        check(wild.firstSize() == ShapeSettings.MAX_CLUMP && wild.halfSize() == ShapeSettings.MAX_CLUMP,
                "the clumps of spring in range, the half-grown one not smaller than the first: " + wild.firstSize() + ", " + wild.halfSize());
        check(wild.twigForks() == MiniBranch.MAX_FORKS && wild.withTwigForks(-4).twigForks() == 0, "the thinner twigs of a mini branch: 0 to " + MiniBranch.MAX_FORKS);
        check(wild.forks() == wild.twigForks() && wild.withFastLeaves(true).forks() == 0, "with the fewest faces a mini branch has no thinner twigs");
        check(wild.budSize() == ShapeSettings.MIN_BUD && wild.withBudSize(0).budSize() == 0 && wild.withBudSize(99).budSize() == ShapeSettings.MAX_BUD,
                "a bud is never as thin as its twig, and 0 means none: " + wild.budSize());
        check(wild.lastSize() == ShapeSettings.MAX_CLUMP && wild.fallSize() == ShapeSettings.MAX_CLUMP,
                "the clumps of autumn in range, the first not smaller than the last: " + wild.fallSize() + ", " + wild.lastSize());
        // Every clump size in range fits into a recipe.
        for (int size = ShapeSettings.MIN_CLUMP; size <= ShapeSettings.MAX_CLUMP; size++) {
            check(Recipe.clump(Recipe.withClump(Recipe.start(63, 0), size, Direction.DOWN)) == size, "clump size " + size + " fits into a recipe");
        }
        ShapeSettings standard = ShapeSettings.DEFAULT;
        check(standard.equals(new ShapeSettings(standard.shape(), standard.fastLeaves(), standard.maxFaces(), standard.tipSides(),
                standard.clumpSides(), standard.cardWidth(), standard.cardHeight(), standard.tuftSize(), standard.tipSize(), standard.clumpSize(),
                standard.winterTwigs(), standard.twigDepth(), standard.firstSize(), standard.halfSize(),
                standard.twigForks(), standard.budSize(), standard.fallSize(), standard.lastSize())),
                "the mod's own numbers are all in range");
        check(standard.maxFaces() == 6, "the mod's own limit is the 6 faces of the unmodded game");
        check(standard.withShape(LeafShape.SHELL).shape() == LeafShape.SHELL && standard.withFastLeaves(true).fastLeaves()
                && standard.withMaxFaces(9).maxFaces() == 9 && standard.withWinterTwigs(WinterTwigs.MORE).winterTwigs() == WinterTwigs.MORE,
                "changed copies");
        helper.succeed();
    }

    /** A position's variant never changes, and all 16 variants are about equally common. */
    @GameTest(template = "empty", batch = "dynamicleaves_rules")
    public static void variantsAreSpreadEvenly(GameTestHelper helper) {
        int[] counts = new int[Recipe.VARIANTS];
        int sameAsNeighbor = 0;
        int size = 24;
        for (int x = -size; x < size; x++) {
            for (int y = -size; y < size; y++) {
                for (int z = -size; z < size; z++) {
                    int variant = ShapeRules.variant(x, y, z);
                    check(variant >= 0 && variant < Recipe.VARIANTS, "a variant is between 0 and 15");
                    check(variant == ShapeRules.variant(x, y, z), "a position's variant never changes");
                    counts[variant]++;
                    if (variant == ShapeRules.variant(x + 1, y, z)) {
                        sameAsNeighbor++;
                    }
                }
            }
        }
        int total = 8 * size * size * size;
        int expected = total / Recipe.VARIANTS;
        for (int variant = 0; variant < Recipe.VARIANTS; variant++) {
            check(Math.abs(counts[variant] - expected) < expected / 10,
                    "variant " + variant + " comes up " + counts[variant] + " times, expected about " + expected);
        }
        check(Math.abs(sameAsNeighbor - expected) < expected / 10,
                "a neighbor has the same variant about one time in 16: " + sameAsNeighbor + " of " + total);
        helper.succeed();
    }

    /** A leaf card is a round, ragged patch cut from the leaf texture, the same every time. */
    @GameTest(template = "empty", batch = "dynamicleaves_rules")
    public static void cardPictureIsARoundPatch(GameTestHelper helper) {
        for (int size : new int[] {16, 32}) {
            // A made-up leaf texture: about two thirds of its pixels are leaf, in a fixed scatter.
            int[] texture = new int[size * size];
            int leafPixels = 0;
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    boolean leaf = PosHash.unit(PosHash.hash(7L, x, y)) < 0.66F;
                    texture[y * size + x] = leaf ? 0xFF000000 | 0x30A040 + x : 0x00FFFFFF;
                    leafPixels += leaf ? 1 : 0;
                }
            }
            float textureCoverage = leafPixels / (float) (size * size);
            int[] card = CardPicture.make(texture, size, 11L);
            int cardSize = 2 * size;
            check(card.length == cardSize * cardSize, "a card is twice as wide and tall as the texture");
            check(Arrays.equals(card, CardPicture.make(texture, size, 11L)), "the same texture and seed give the same card");
            check(!Arrays.equals(card, CardPicture.make(texture, size, 12L)), "another seed gives another outline");
            for (int y = 0; y < cardSize; y++) {
                for (int x = 0; x < cardSize; x++) {
                    int pixel = card[y * cardSize + x];
                    check(pixel == 0 || pixel >>> 24 == 255, "a card pixel is fully opaque or empty");
                    if (pixel != 0) {
                        int source = texture[Math.floorMod(y + size / 2, size) * size + Math.floorMod(x + size / 2, size)];
                        check(source >>> 24 == 255 && (source & 0xFFFFFF) == (pixel & 0xFFFFFF), "a card pixel has the color of the leaf texture there");
                    }
                    double fromMiddle = Math.hypot(x + 0.5 - size, y + 0.5 - size) / size;
                    if (fromMiddle > 0.99) {
                        check(pixel == 0, "nothing of a card lies outside its round outline (" + x + "," + y + " of " + cardSize + ")");
                    }
                }
            }
            // The four corner pixels of the middle are well inside the outline: the texture shows there.
            int inside = 0;
            int insideLeaf = 0;
            for (int y = cardSize / 2 - size / 4; y < cardSize / 2 + size / 4; y++) {
                for (int x = cardSize / 2 - size / 4; x < cardSize / 2 + size / 4; x++) {
                    inside++;
                    insideLeaf += card[y * cardSize + x] != 0 ? 1 : 0;
                }
            }
            check(Math.abs(insideLeaf / (float) inside - textureCoverage) < 0.12F,
                    "the middle of a card is as full as the texture: " + insideLeaf + " of " + inside + ", texture " + textureCoverage);
            float coverage = CardPicture.coverage(card);
            // A disc of radius 13 on a 32 grid covers 52 % of the square; the rim is loose.
            check(coverage > 0.40F * textureCoverage && coverage < 0.56F * textureCoverage,
                    "a card covers about half of what the texture would, got " + coverage + " for texture " + textureCoverage);
        }
        check(CardPicture.coverage(new int[0]) == 0.0F, "an empty picture covers nothing");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // Real trees
    // ------------------------------------------------------------------

    /** A big oak: a wide crown with many buried leaf blocks. */
    @GameTest(template = "empty", batch = "dynamicleaves_tree_oak", skyAccess = true)
    public static void aBigOak(GameTestHelper helper) {
        Tree tree = Tree.plant(helper, "dynamictrees:oak", BIG_OAK);
        try {
            tree.checkAll(true);
            tree.checkYear(true);
        } finally {
            tree.remove();
        }
        helper.succeed();
    }

    /** A birch: a narrow crown, nearly every leaf block at the edge. */
    @GameTest(template = "empty", batch = "dynamicleaves_tree_birch", skyAccess = true)
    public static void aBirch(GameTestHelper helper) {
        Tree tree = Tree.plant(helper, "dynamictrees:birch", Tree.storedCode("dynamictrees:birch", 0));
        try {
            tree.checkAll(false);
            tree.checkYear(true);
        } finally {
            tree.remove();
        }
        helper.succeed();
    }

    /** A dark oak: thick limbs and a broad, flat crown. */
    @GameTest(template = "empty", batch = "dynamicleaves_tree_dark_oak", skyAccess = true)
    public static void aDarkOak(GameTestHelper helper) {
        Tree tree = Tree.plant(helper, "dynamictrees:dark_oak", Tree.storedCode("dynamictrees:dark_oak", 0));
        try {
            tree.checkAll(true);
            tree.checkYear(true);
        } finally {
            tree.remove();
        }
        helper.succeed();
    }

    /** A spruce: thin layers of leaves around a single trunk. */
    @GameTest(template = "empty", batch = "dynamicleaves_tree_spruce", skyAccess = true)
    public static void aSpruce(GameTestHelper helper) {
        Tree tree = Tree.plant(helper, "dynamictrees:spruce", Tree.storedCode("dynamictrees:spruce", 0));
        try {
            tree.checkAll(false);
            tree.checkYear(false);
        } finally {
            tree.remove();
        }
        helper.succeed();
    }

    /**
     * Every other kind of tree, one after another, in several of the
     * shapes Dynamic Trees has stored for it. Acacia, mangrove and azalea
     * have thin crowns like the spruce.
     */
    @GameTest(template = "empty", batch = "dynamicleaves_tree_others", skyAccess = true)
    public static void everyOtherKind(GameTestHelper helper) {
        for (String kind : List.of("dynamictrees:jungle", "dynamictrees:acacia", "dynamictrees:cherry", "dynamictrees:mangrove", "dynamictrees:azalea")) {
            for (int rank : new int[] {0, 12}) {
                Tree tree = Tree.plant(helper, kind, Tree.storedCode(kind, rank));
                try {
                    tree.checkAll(false);
                    tree.checkYear(false);
                } finally {
                    tree.remove();
                }
            }
        }
        helper.succeed();
    }

    /** Snow lying on a leaf block keeps it whole, read from real blocks. */
    @GameTest(template = "empty", batch = "dynamicleaves_rules")
    public static void snowOnTopIsSeen(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Block oakLeaves = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(ResourceLocation.parse("dynamictrees:oak_leaves"));
        check(oakLeaves instanceof DynamicLeavesBlock, "Dynamic Trees' oak leaves exist");
        BlockPos leaf = helper.absolutePos(new BlockPos(3, 2, 3));
        // Flags 2 and 16: tell the clients, but let no neighbor react. A leaf
        // block in thin air would otherwise be removed by Dynamic Trees.
        level.setBlock(leaf, oakLeaves.defaultBlockState(), 2 | 16);
        BlockState state = level.getBlockState(leaf);
        check(state.is(oakLeaves), "the leaf block is placed");

        int alone = Surround.read(level, leaf, state);
        check(Surround.open(alone) == 63 && !Surround.loaded(alone), "a leaf block in thin air is open on all sides with nothing on top");
        check(Surround.wood(alone) == null && Surround.heart(alone) == null && Surround.support(alone) == null
                        && Surround.grow(alone) == Direction.UP,
                "with no wood and no leaves around it hangs on to nothing, and a twig would grow up");
        int aloneRecipe = ShapeRules.recipe(settings(LeafShape.MIXED), alone, 0);
        check(Recipe.clump(aloneRecipe) == 0 && Recipe.quadCount(aloneRecipe, FORKS) == 6, "mixed draws it as a whole block with six faces");

        // On top of a better supplied leaf block it is an outermost tip: a clump that sits on that block.
        level.setBlock(leaf.below(), oakLeaves.defaultBlockState().setValue(LeavesBlock.DISTANCE, 4), 2 | 16);
        level.setBlock(leaf, oakLeaves.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1), 2 | 16);
        state = level.getBlockState(leaf);
        int tip = Surround.read(level, leaf, state);
        check(Surround.openCount(tip) == 5 && !Surround.isOpen(tip, Direction.DOWN), "the leaf block below covers the bottom side");
        check(Surround.heart(tip) == Direction.DOWN && Surround.support(tip) == Direction.DOWN && Surround.grow(tip) == Direction.UP,
                "the better supplied leaf block marks the heart; the tip hangs on to it and a twig grows away from it");
        int tipRecipe = ShapeRules.recipe(settings(LeafShape.MIXED), tip, 0);
        check(Recipe.clump(tipRecipe) == ShapeSettings.DEFAULT.clumpSize() && Recipe.anchor(tipRecipe) == Direction.DOWN,
                "mixed draws it as a clump sitting on the block below");
        check(Recipe.quadCount(tipRecipe, FORKS) == 5, "from five faces: the one against the block below is left out");

        level.setBlock(leaf.above(), Blocks.SNOW.defaultBlockState(), 2 | 16);
        int snowed = Surround.read(level, leaf, state);
        check(Surround.loaded(snowed), "a layer of snow on top is seen");
        check(Surround.isOpen(snowed, Direction.UP), "a layer of snow does not cover the top side");
        int recipe = ShapeRules.recipe(settings(LeafShape.MIXED), snowed, 0);
        check(Recipe.clump(recipe) == 0 && Recipe.faces(recipe), "with snow on top the block stays whole");

        // A full block on a side covers it and is no load.
        level.setBlock(leaf.above(), Blocks.STONE.defaultBlockState(), 2 | 16);
        level.setBlock(leaf.east(), oakLeaves.defaultBlockState().setValue(LeavesBlock.DISTANCE, 2), 2 | 16);
        int covered = Surround.read(level, leaf, level.getBlockState(leaf));
        check(!Surround.isOpen(covered, Direction.UP) && !Surround.loaded(covered), "a full block covers the top side and is no load");
        check(!Surround.isOpen(covered, Direction.EAST), "another leaf block covers the east side");
        check(Surround.heart(covered) == Direction.DOWN, "the best supplied neighbor stays the heart");
        check(Surround.openCount(covered) == 3, "three sides stay open");

        for (BlockPos pos : List.of(leaf, leaf.above(), leaf.east(), leaf.below())) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
        }
        helper.succeed();
    }

    /**
     * A Dynamic Trees tree planted for a test, with all its leaf blocks
     * found and read. Also used by the tests of the bare blocks.
     */
    static final class Tree {

        /** How far from the trunk, and how high, leaf blocks are looked for. */
        static final int REACH = 20;
        static final int HEIGHT = 56;

        final ServerLevel level;
        final String species;
        /** The block the tree stands on. */
        final BlockPos root;
        /** Where the tree had leaf blocks when it was planted. */
        final List<BlockPos> leaves = new ArrayList<>();

        private Tree(ServerLevel level, String species, BlockPos root) {
            this.level = level;
            this.species = species;
            this.root = root;
        }

        /**
         * One of the tree shapes Dynamic Trees has stored for a species, as
         * its growth code, counted from the largest.
         */
        static String storedCode(String species, int rank) {
            Species found = Species.REGISTRY.get(ResourceLocation.parse(species));
            check(found != null && found.isValid(), "Dynamic Trees knows the species " + species);
            List<JoCode> codes = new ArrayList<>(found.getJoCodes());
            check(!codes.isEmpty(), "Dynamic Trees has stored tree shapes for " + species);
            codes.sort(Comparator.comparingInt((JoCode code) -> code.toString().length()).reversed());
            return codes.get(Math.min(rank, codes.size() - 1)).toString();
        }

        /** Plants a full-grown tree in the middle of the test's area and finds its leaf blocks. Its soil is spent: it grows no more. */
        static Tree plant(GameTestHelper helper, String species, String code) {
            return plant(helper, species, code, 0);
        }

        /**
         * Plants a tree in the middle of the test's area and finds its leaf
         * blocks.
         *
         * @param fertility how much the soil under the tree still has to
         *                  give, 0 to 15; a tree on spent soil (0) does not
         *                  grow
         */
        static Tree plant(GameTestHelper helper, String species, String code, int fertility) {
            ServerLevel level = helper.getLevel();
            MinecraftServer server = level.getServer();
            BlockPos root = helper.absolutePos(new BlockPos(3, 0, 3));
            level.setBlock(root, Blocks.DIRT.defaultBlockState(), 2);
            boolean[] succeeded = {false};
            server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack().withLevel(level).withSuppressedOutput()
                            .withCallback((success, value) -> succeeded[0] = success),
                    "dt settree " + root.getX() + " " + root.getY() + " " + root.getZ() + " " + species + " " + code + " 0 " + fertility);
            check(succeeded[0], "Dynamic Trees plants a " + species);
            return at(level, species, root);
        }

        /**
         * A tree that already stands, planted by whatever means, with its
         * leaf blocks found.
         *
         * @param root the block the tree stands on
         */
        static Tree at(ServerLevel level, String species, BlockPos root) {
            Tree tree = new Tree(level, species, root);
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int y = 0; y <= HEIGHT; y++) {
                for (int x = -REACH; x <= REACH; x++) {
                    for (int z = -REACH; z <= REACH; z++) {
                        pos.setWithOffset(root, x, y, z);
                        if (level.getBlockState(pos).getBlock() instanceof DynamicLeavesBlock) {
                            tree.leaves.add(pos.immutable());
                        }
                    }
                }
            }
            return tree;
        }

        /** Takes the tree out of the world again, so that the next test finds the place empty. */
        void remove() {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int y = 0; y <= HEIGHT; y++) {
                for (int x = -REACH; x <= REACH; x++) {
                    for (int z = -REACH; z <= REACH; z++) {
                        pos.setWithOffset(root, x, y, z);
                        BlockState state = level.getBlockState(pos);
                        if (state.getBlock() instanceof DynamicLeavesBlock || TreeHelper.isBranch(state) || TreeHelper.isRooty(state)
                                || pos.equals(root)) {
                            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
                        }
                    }
                }
            }
        }

        /**
         * All checks on a real tree.
         *
         * @param fullCrown whether the tree has a full, rounded crown with
         *                  leaf blocks buried inside it; then the shapes
         *                  must also save faces against the unmodded game
         */
        void checkAll(boolean fullCrown) {
            check(leaves.size() >= 40, "the " + species + " has leaves: " + leaves.size());
            int buried = 0;
            int nextToWood = 0;
            for (BlockPos leaf : leaves) {
                BlockState state = level.getBlockState(leaf);
                int around = Surround.read(level, leaf, state);
                buried += Surround.open(around) == 0 ? 1 : 0;
                check(Surround.sheltered(around) == (Surround.open(around) == 0),
                        "in full leaf, a block is sheltered exactly if it is buried, at " + leaf);
                check(Surround.sheltered(Surround.read(level, leaf, state, new BlockPos.MutableBlockPos(), Surround.NO_LEAF_COVERS))
                        == Surround.sheltered(around), "whether a block is sheltered does not depend on its neighbors' leaves, at " + leaf);
                // Neighbors agree about the side between them.
                for (Direction side : DIRECTIONS) {
                    BlockPos near = leaf.relative(side);
                    BlockState other = level.getBlockState(near);
                    if (other.getBlock() instanceof LeavesBlock) {
                        check(!Surround.isOpen(around, side), "no open side toward another leaf block at " + leaf + " " + side);
                        check(!Surround.isOpen(Surround.read(level, near, other), side.getOpposite()),
                                "and the other leaf block agrees, at " + near);
                    } else if (other.isAir()) {
                        check(Surround.isOpen(around, side), "a side toward air is open at " + leaf + " " + side);
                    }
                }
                // Twigs point away from the wood.
                Direction wood = Surround.wood(around);
                if (wood != null) {
                    nextToWood++;
                    check(TreeHelper.isBranch(level.getBlockState(leaf.relative(wood))), "the wood side really has a branch, at " + leaf);
                    check(Surround.grow(around) == wood.getOpposite(), "a twig grows away from the branch, at " + leaf);
                } else {
                    for (Direction side : DIRECTIONS) {
                        check(!TreeHelper.isBranch(level.getBlockState(leaf.relative(side))), "a branch next to a leaf block is noticed, at " + leaf);
                    }
                    Direction heart = Surround.heart(around);
                    if (heart != null) {
                        BlockState inner = level.getBlockState(leaf.relative(heart));
                        check(inner.getBlock() instanceof LeavesBlock
                                        && inner.getValue(LeavesBlock.DISTANCE) > state.getValue(LeavesBlock.DISTANCE),
                                "the heart side has a better supplied leaf block, at " + leaf);
                    }
                }
            }
            check(nextToWood > 0, "some leaf blocks of the " + species + " touch a branch");
            if (fullCrown) {
                check(buried * 20 >= leaves.size(), "a full crown has buried leaf blocks: " + buried + " of " + leaves.size());
            }

            // How many faces each shape needs, against the unmodded game's 6 per leaf block.
            int[] byOpenSides = new int[7];
            for (BlockPos leaf : leaves) {
                byOpenSides[Surround.openCount(Surround.read(level, leaf, level.getBlockState(leaf)))]++;
            }
            StringBuilder report = new StringBuilder();
            List<String> wrong = new ArrayList<>();
            for (LeafShape shape : SHAPES) {
                double[] faces = faces(settings(shape));
                double[] rich = faces(rich(shape));
                report.append(String.format(Locale.ROOT, "; %s %.2f (%.2f slanted, most %d; without limit %.2f)",
                        shape.name().toLowerCase(Locale.ROOT), faces[0], faces[1], (int) faces[2], rich[0]));
                if (faces[2] > PLAIN_FACES) {
                    wrong.add(shape + " draws a leaf block of the " + species + " from " + (int) faces[2] + " faces");
                }
                if (fullCrown && faces[0] > MOST_FACES_FULL_CROWN) {
                    wrong.add(shape + " needs " + faces[0] + " faces per leaf block of the " + species + ", limit " + MOST_FACES_FULL_CROWN);
                }
                if (rich[2] > ShapeSettings.MAX_FACES) {
                    wrong.add(shape + " without limit draws a leaf block of the " + species + " from " + (int) rich[2] + " faces");
                }
            }
            double[] fast = faces(settings(LeafShape.MIXED).withFastLeaves(true));
            report.append(String.format(Locale.ROOT, "; fewest faces %.2f", fast[0]));
            double[] solid = faces(null);
            report.append(String.format(Locale.ROOT, "; not see-through %.2f", solid[0]));
            DynamicLeaves.LOGGER.info("Dynamic Leaves test: {} with {} leaf blocks, by open sides 0 to 6: {}: faces per leaf block (unmodded 6.00){}",
                    species, leaves.size(), Arrays.toString(byOpenSides), report);
            check(wrong.isEmpty(), String.join("; ", wrong));
            check(fast[2] <= PLAIN_FACES && fast[1] == 0.0, "with fewest faces, no leaf block over 6 faces and nothing at a slant");
            check(fast[0] <= faces(settings(LeafShape.SHELL))[0], "fewest faces needs no more than shell");
            check(solid[0] <= fast[0], "leaves that are not see-through need the fewest of all");
        }

        /**
         * The tree through a whole year of the leaf calendar, with the
         * mod's own rules and the Calendar mod's own seasons, one step
         * after another. The date of the world is not touched: a clock is
         * made by hand for every step.
         *
         * @param sheds whether this kind of tree loses its leaves
         */
        void checkYear(boolean sheds) {
            SeasonSettings rules = SeasonSettings.DEFAULT;
            CalendarRules year = CalendarRules.DEFAULT;
            check(SeasonsApi.hasWinter(level.getNoiseBiome(root.getX() >> 2, root.getY() >> 2, root.getZ() >> 2).value()),
                    "the test world has a winter where the " + species + " stands");
            LeafLook look = LeafLook.forThread();
            int count = leaves.size();
            BlockState[] states = new BlockState[count];
            for (int i = 0; i < count; i++) {
                states[i] = level.getBlockState(leaves.get(i));
            }
            long steps = (long) year.yearLength() * rules.stepsPerDay();
            long winterStart = (long) (year.firstDayOfYear(Season.WINTER) - 1) * rules.stepsPerDay();
            StringBuilder report = new StringBuilder();
            // Every shape, also the plain one, at the limit of 6; and the mixed shape with more twigs and without a limit.
            List<ShapeSettings> looks = new ArrayList<>();
            for (LeafShape shape : LeafShape.values()) {
                looks.add(settings(shape));
            }
            looks.add(rich(LeafShape.MIXED).withWinterTwigs(WinterTwigs.MORE));
            for (ShapeSettings shape : looks) {
                boolean plain = shape.shape() == LeafShape.PLAIN;
                int[] summer = new int[count];
                for (int i = 0; i < count; i++) {
                    summer[i] = look.recipe(shape, null, null, level, leaves.get(i), states[i]);
                    check(plain ? summer[i] == Recipe.NONE : summer[i] == ShapeRules.recipe(shape,
                                    Surround.read(level, leaves.get(i), states[i]), ShapeRules.variant(leaves.get(i).getX(), leaves.get(i).getY(), leaves.get(i).getZ())),
                            "without a clock a leaf block is drawn as in full leaf, at " + leaves.get(i));
                }
                double summerFaces = 0;
                double winterFaces = 0;
                double mostFaces = 0;
                double mostLeafFaces = 0;
                long mostAt = 0;
                int twigs = 0;
                // A kind that keeps its leaves is the same at every step; a few steps show it.
                for (long step = 0; step < steps; step += sheds ? 1 : 37) {
                    LeafClock clock = LeafClock.of(rules, year, step);
                    String when = species + ", " + shape.shape() + ", step " + step;
                    long faces = 0;
                    long leafFaces = 0;
                    int bare = 0;
                    int twigsNow = 0;
                    for (int i = 0; i < count; i++) {
                        BlockPos leaf = leaves.get(i);
                        LeafStage stage = look.stage(clock, level, states[i], leaf.getX(), leaf.getY(), leaf.getZ());
                        int recipe = look.recipe(shape, clock, level, level, leaf, states[i]);
                        if (!sheds || clock.allFull()) {
                            check(stage == LeafStage.FULL && recipe == summer[i], "in full leaf, and drawn as without a clock, at " + leaf + ": " + when);
                        }
                        if (recipe == Recipe.NONE) {
                            check(plain && stage == LeafStage.FULL, "only a plain block in full leaf is left to the model it had before: " + when);
                            faces += PLAIN_FACES;
                            leafFaces += PLAIN_FACES;
                            continue;
                        }
                        check(Recipe.stage(recipe) == stage, "the recipe carries the stage of the block, at " + leaf + ": " + when);
                        // The limit is about the leaves, and the stick under a clump in full leaf. A mini branch is counted apart.
                        int quads = Recipe.leafQuadCount(recipe) + (Recipe.stemOnly(recipe) ? Recipe.FACES_PER_STEM : 0);
                        check(quads <= shape.maxFaces(), "never more faces of leaves than the limit, got " + quads + " at " + leaf + ": " + when);
                        leafFaces += quads;
                        faces += Recipe.quadCount(recipe, shape.forks());
                        bare += stage == LeafStage.BARE ? 1 : 0;
                        twigsNow += Recipe.twig(recipe) != null && stage == LeafStage.BARE ? 1 : 0;
                        if (plain || !sheds) {
                            continue;
                        }
                        // Neighbors agree: a side toward a leaf block is open exactly if that block does not cover.
                        for (Direction side : DIRECTIONS) {
                            BlockPos near = leaf.relative(side);
                            BlockState other = level.getBlockState(near);
                            if (other.getBlock() instanceof LeavesBlock) {
                                boolean covers = look.stage(clock, level, other, near.getX(), near.getY(), near.getZ()).covers();
                                check((Recipe.open(recipe) >> side.ordinal() & 1) == (covers ? 0 : 1),
                                        "a side toward a leaf block is open exactly if that block has lost its leaves, at " + leaf + " " + side + ": " + when);
                            }
                        }
                    }
                    double perBlock = faces / (double) count;
                    if (clock.allFull()) {
                        summerFaces = perBlock;
                    }
                    if (step >= winterStart) {
                        check(!sheds || bare == count, "in winter every leaf block of a tree that sheds is bare: " + when);
                        winterFaces = perBlock;
                        twigs = twigsNow;
                    }
                    if (perBlock > mostFaces) {
                        mostFaces = perBlock;
                        mostAt = step;
                    }
                    mostLeafFaces = Math.max(mostLeafFaces, leafFaces / (double) count);
                }
                check(mostLeafFaces <= Math.max(PLAIN_FACES, shape.maxFaces()), "the year's most faces of leaves per leaf block stay within the limit");
                report.append(String.format(Locale.ROOT, "; %s%s summer %.2f, winter %.2f (%d mini branches), most %.2f on day %d (leaves alone at most %.2f)",
                        shape.shape().name().toLowerCase(Locale.ROOT), shape.maxFaces() > PLAIN_FACES ? " without limit, more twigs" : "",
                        summerFaces, winterFaces, twigs, mostFaces, mostAt / rules.stepsPerDay() + 1, mostLeafFaces));
            }
            DynamicLeaves.LOGGER.info("Dynamic Leaves test: {} through the year, {} leaf blocks, faces per leaf block{}", species, count, report);
        }

        /**
         * The faces of this tree's leaf blocks for the given settings: the
         * average per leaf block, the average of those that stand at a
         * slant, and the most any one block needs. With no settings: the
         * faces where leaves are not see-through.
         */
        private double[] faces(ShapeSettings settings) {
            long faces = 0;
            long slanted = 0;
            int most = 0;
            for (BlockPos leaf : leaves) {
                int around = Surround.read(level, leaf, level.getBlockState(leaf));
                int variant = ShapeRules.variant(leaf.getX(), leaf.getY(), leaf.getZ());
                int count;
                if (settings == null) {
                    count = Recipe.solidQuadCount(ShapeRules.recipe(ShapeSettings.DEFAULT, around, variant), FORKS);
                } else {
                    int recipe = ShapeRules.recipe(settings, around, variant);
                    count = Recipe.quadCount(recipe, settings.forks());
                    slanted += Recipe.slantedQuadCount(recipe);
                }
                faces += count;
                most = Math.max(most, count);
            }
            return new double[] {faces / (double) leaves.size(), slanted / (double) leaves.size(), most};
        }
    }
}
