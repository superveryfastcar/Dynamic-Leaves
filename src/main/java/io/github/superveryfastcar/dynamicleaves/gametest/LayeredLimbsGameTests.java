package io.github.superveryfastcar.dynamicleaves.gametest;

import static io.github.superveryfastcar.dynamicleaves.gametest.DynamicLeavesGameTests.check;

import com.dtteam.dynamictrees.api.worldgen.LevelContext;
import com.dtteam.dynamictrees.systems.growthlogic.GrowthLogicKit;
import com.dtteam.dynamictrees.tree.TreeHelper;
import com.dtteam.dynamictrees.tree.species.Species;
import com.dtteam.dynamictrees.worldgen.DynamicTreeGenerationContext;
import com.dtteam.dynamictrees.worldgen.JoCode;
import com.dtteam.dynamictrees.worldgen.JoCodeRegistry;
import io.github.superveryfastcar.dynamicleaves.DynamicLeaves;
import io.github.superveryfastcar.dynamicleaves.DynamicLeavesConfig;
import io.github.superveryfastcar.dynamicleaves.gametest.DynamicLeavesGameTests.Tree;
import io.github.superveryfastcar.dynamicleaves.tree.LayeredLimbs;
import io.github.superveryfastcar.dynamicleaves.tree.LayeredLogic;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests of the trees' limbs in layers (the {@code tree} package).
 *
 * <p>The owner's wish: limbs leave the trunk level, in layers from low
 * down; nothing but the trunk goes straight up (he first allowed one
 * block, then asked for less than a block, so a limb does not step up at
 * all); a limb has side limbs; the crown keeps its volume. How a tree looks is judged on pictures (see
 * {@code dev.DevTreeShow}); these tests hold what can be counted:</p>
 * <ol>
 *   <li>The shape files are read as written, and junk in them is
 *       skipped.</li>
 *   <li>The growth rule lets a branch off the trunk go level only.</li>
 *   <li>Dynamic Trees really holds this mod's shapes for every species
 *       the mod has files for, and those species grow by the mod's
 *       rule.</li>
 *   <li>Stored shapes, planted in the world, stand whole (Dynamic Trees
 *       throws a tree away whose wood grew together), have limbs on many
 *       heights, no wood off the trunk that stands on other wood, no limb
 *       that sticks out from under the crown, and a crown.</li>
 *   <li>A tree that grows from a young trunk grows level limbs.</li>
 *   <li>The setting turns all of it off and on again.</li>
 * </ol>
 * The tests that plant a tree or change what Dynamic Trees holds have a
 * batch each: see {@link DynamicLeavesGameTests} for why.
 */
@GameTestHolder(DynamicLeaves.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LayeredLimbsGameTests {

    /** The species the mod ships shapes for. */
    private static final List<String> SPECIES = List.of("dynamictrees:oak", "dynamictrees:apple_oak",
            "dynamictrees:swamp_oak", "dynamictrees:birch", "dynamictrees:tall_birch");

    /** The shapes the mod ships per species and radius (see tools/limbs/layers.py, PER_RADIUS). */
    private static final int SHAPES_PER_RADIUS = 20;

    /** The six steps of a tree code, in Dynamic Trees' order: down, up, north, south, west, east. */
    private static final Direction[] STEPS = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH,
            Direction.WEST, Direction.EAST};

    private static final String BASE64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

    private LayeredLimbsGameTests() {
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * The wood blocks a tree code stands for, counted from the soil
     * block. Written here a second time on purpose (the first is the
     * Python tool that wrote the codes, the third is Dynamic Trees): the
     * test holds what the game planted against what the code says.
     */
    private static Set<BlockPos> decode(String code) {
        List<Integer> steps = new ArrayList<>();
        for (char c : code.toCharArray()) {
            int index = BASE64.indexOf(c);
            if (index >= 0) {
                steps.add(index >> 3);
                steps.add(index & 7);
            }
        }
        Set<BlockPos> wood = new HashSet<>();
        decodeFork(steps, 0, BlockPos.ZERO, wood);
        return wood;
    }

    /** One fork of a code: steps until its end (7), with forks inside it (6) read first. */
    private static int decodeFork(List<Integer> steps, int at, BlockPos pos, Set<BlockPos> wood) {
        while (at < steps.size()) {
            int step = steps.get(at);
            if (step == 6) {
                at = decodeFork(steps, at + 1, pos, wood);
            } else if (step == 7) {
                return at + 1;
            } else {
                pos = pos.relative(STEPS[step]);
                wood.add(pos);
                at++;
            }
        }
        return at;
    }

    /** The branch blocks of a planted tree, counted from its soil block. */
    private static Set<BlockPos> wood(Tree tree) {
        Set<BlockPos> wood = new HashSet<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = 1; y <= Tree.HEIGHT; y++) {
            for (int x = -Tree.REACH; x <= Tree.REACH; x++) {
                for (int z = -Tree.REACH; z <= Tree.REACH; z++) {
                    pos.setWithOffset(tree.root, x, y, z);
                    if (TreeHelper.isBranch(tree.level.getBlockState(pos))) {
                        wood.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        return wood;
    }

    /**
     * Whether two sets of wood blocks are the same tree, seen from
     * whichever side: Dynamic Trees turns a shape around its trunk when
     * it plants it.
     */
    private static boolean sameTurned(Set<BlockPos> planted, Set<BlockPos> expected) {
        Set<BlockPos> turned = expected;
        for (int quarter = 0; quarter < 4; quarter++) {
            if (planted.equals(turned)) {
                return true;
            }
            Set<BlockPos> next = new HashSet<>();
            for (BlockPos block : turned) {
                next.add(new BlockPos(-block.getZ(), block.getY(), block.getX()));
            }
            turned = next;
        }
        return false;
    }

    /**
     * For the report of a tree that does not stand as its code says: the
     * pieces of wood that are missing, in the turn that fits best.
     */
    private static String missing(Set<BlockPos> planted, Set<BlockPos> expected) {
        Set<BlockPos> turned = expected;
        Set<BlockPos> best = null;
        for (int quarter = 0; quarter < 4; quarter++) {
            Set<BlockPos> lost = new HashSet<>(turned);
            lost.removeAll(planted);
            if (best == null || lost.size() < best.size()) {
                best = lost;
            }
            Set<BlockPos> next = new HashSet<>();
            for (BlockPos block : turned) {
                next.add(new BlockPos(-block.getZ(), block.getY(), block.getX()));
            }
            turned = next;
        }
        return "; missing, counted from the soil: " + best;
    }

    /**
     * How high above the test area's floor a stored shape is planted, on
     * a block of soil in the air. A game test is walled in with barrier
     * blocks, five blocks high and four blocks from the middle. A limb
     * four blocks long that leaves the trunk under the wall's top would
     * end inside the wall, and Dynamic Trees then leaves that piece out:
     * the tree would not stand as its code says, for a reason that has
     * nothing to do with the tree. Above the wall there is open air.
     */
    private static final int ABOVE_THE_WALL = 6;

    /**
     * Plants a stored shape the way Dynamic Trees plants a tree with new
     * land, above the middle of the test's area ({@link #ABOVE_THE_WALL}).
     * The command the other tests plant with ({@code /dt settree}) does
     * it a little differently: it gives every tree the widest room, and
     * lets a branch end rot by chance instead of by rule.
     *
     * @param radius the room the tree is given, 2 to 8
     */
    private static Tree plantAsNewLand(GameTestHelper helper, String name, String code, int radius) {
        ServerLevel level = helper.getLevel();
        BlockPos root = helper.absolutePos(new BlockPos(3, ABOVE_THE_WALL, 3));
        level.setBlock(root, Blocks.DIRT.defaultBlockState(), 2);
        Species species = species(name);
        species.getJoCode(code).setCareful(false).generate(new DynamicTreeGenerationContext(
                LevelContext.create(level), species, root, root.mutable(), level.getBiome(root), Direction.SOUTH, radius, true));
        return Tree.at(level, name, root);
    }

    /** Whether a wood block is part of the trunk (straight above the soil). */
    private static boolean inTrunk(BlockPos wood) {
        return wood.getX() == 0 && wood.getZ() == 0;
    }

    /** The heights at which a limb leaves the trunk. */
    private static Set<Integer> limbHeights(Set<BlockPos> wood) {
        Set<Integer> heights = new TreeSet<>();
        for (BlockPos block : wood) {
            if (Math.abs(block.getX()) + Math.abs(block.getZ()) == 1) {
                heights.add(block.getY());
            }
        }
        return heights;
    }

    /**
     * How many wood blocks off the trunk stand on another wood block:
     * each is one step up (or down) of a limb.
     */
    private static int steps(Set<BlockPos> wood) {
        int steps = 0;
        for (BlockPos block : wood) {
            if (!inTrunk(block) && wood.contains(block.below())) {
                steps++;
            }
        }
        return steps;
    }

    /** How long the limb is that leaves the trunk at a height toward a side: its wood in a straight line from the trunk. */
    private static int limbLength(Set<BlockPos> wood, int y, Direction side) {
        int length = 0;
        while (wood.contains(new BlockPos(side.getStepX() * (length + 1), y, side.getStepZ() * (length + 1)))) {
            length++;
        }
        return length;
    }

    /**
     * The limbs that stick out from under the crown, as text (empty when
     * there is none). The owner called such a limb a bug. In the lower
     * half of the crown a limb may be at most one block longer than what
     * covers it from above: the limb two heights up on its own side, the
     * limb four heights up less one, or the side limbs of the turned
     * layer right above it (two blocks). The upper half is the crown's
     * roof and gets narrower upward by itself.
     */
    private static String stickingOut(Set<BlockPos> wood) {
        List<Integer> layers = new ArrayList<>(limbHeights(wood));
        StringBuilder found = new StringBuilder();
        if (layers.isEmpty()) {
            return "";
        }
        int lowest = layers.get(0);
        int highest = layers.get(layers.size() - 1);
        for (int y : layers) {
            if ((y - lowest) * 2 > highest - lowest) {
                continue;
            }
            for (Direction side : Direction.Plane.HORIZONTAL) {
                int length = limbLength(wood, y, side);
                int cover = Math.max(Math.max(limbLength(wood, y + 2, side), limbLength(wood, y + 4, side) - 1),
                        layers.contains(y + 1) ? 2 : 0);
                if (length > cover + 1) {
                    found.append(" a limb of ").append(length).append(" at height ").append(y).append(" toward ").append(side)
                            .append(" under a cover of ").append(cover).append(';');
                }
            }
        }
        return found.toString();
    }

    /** The shapes Dynamic Trees holds for a species and radius right now, as their codes. */
    private static Set<String> held(String species, int radius) {
        Set<String> codes = new HashSet<>();
        for (JoCode code : JoCodeRegistry.getCodes(ResourceLocation.parse(species), radius)) {
            codes.add(code.toString());
        }
        return codes;
    }

    /**
     * The mod's shapes of a species and radius, each written the way
     * Dynamic Trees writes a code back (it may end a code differently
     * than the file does), so that the two can be compared.
     */
    private static Set<String> ours(String species, int radius) {
        Set<String> codes = new HashSet<>();
        for (String code : LayeredLimbs.shapes(ResourceLocation.parse(species), radius)) {
            codes.add(new JoCode(code).toString());
        }
        return codes;
    }

    /** The species of a name, checked to exist. */
    private static Species species(String name) {
        Species found = Species.REGISTRY.get(ResourceLocation.parse(name));
        check(found != null && found.isValid(), "Dynamic Trees knows the species " + name);
        return found;
    }

    // ------------------------------------------------------------------
    // 1. Reading the files
    // ------------------------------------------------------------------

    /** A shape file is read line by line; notes, empty lines and junk are skipped. */
    @GameTest(template = "empty", batch = "dynamicleaves_limbs")
    public static void shapeFilesAreReadAsWritten(GameTestHelper helper) {
        Map<Integer, List<String>> read = LayeredLimbs.parse(List.of(
                "# a note",
                "",
                "2:JJxOOJ",
                "2: JOJx  # a note after a shape",
                "5:JJJJxxx+X187+Xj",
                "five:JJJ",          // not a number
                "0:JJJ",             // no such radius
                "3:",                // no code
                ":JJJ",              // no radius
                "4:JJ JJ!",          // not a code
                "*3:JJJ"));          // a root shape, which this mod has none of
        check(read.keySet().equals(Set.of(2, 5)), "shapes for radius 2 and 5 are read, nothing else: " + read.keySet());
        check(read.get(2).equals(List.of("JJxOOJ", "JOJx")), "both shapes of radius 2, in order, without spaces and notes: " + read.get(2));
        check(read.get(5).equals(List.of("JJJJxxx+X187+Xj")), "the shape of radius 5: " + read.get(5));

        check(ResourceLocation.parse("dynamictrees:oak").equals(
                        LayeredLimbs.speciesOf(ResourceLocation.parse("dynamicleaves:tree_shapes/dynamictrees/oak.txt"))),
                "tree_shapes/dynamictrees/oak.txt is for dynamictrees:oak");
        check(ResourceLocation.parse("othermod:fir/tall").equals(
                        LayeredLimbs.speciesOf(ResourceLocation.parse("pack:tree_shapes/othermod/fir/tall.txt"))),
                "a species name may have folders of its own");
        check(LayeredLimbs.speciesOf(ResourceLocation.parse("dynamicleaves:tree_shapes/oak.txt")) == null,
                "a file without a species namespace folder is for no species");
        check(LayeredLimbs.speciesOf(ResourceLocation.parse("dynamicleaves:tree_shapes/dynamictrees/oak.json")) == null,
                "a file that is not .txt is for no species");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 2. The growth rule
    // ------------------------------------------------------------------

    /** Off the trunk the rule takes up and down away, unless wood is there already. */
    @GameTest(template = "empty", batch = "dynamicleaves_limbs")
    public static void aLimbOffTheTrunkGoesLevel(GameTestHelper helper) {
        int up = Direction.UP.ordinal();
        int down = Direction.DOWN.ordinal();
        int east = Direction.EAST.ordinal();
        int north = Direction.NORTH.ordinal();

        int[] weights = {3, 4, 1, 0, 0, 2};
        LayeredLogic.levelOnly(weights, Direction.EAST, false, false);
        check(weights[up] == 0 && weights[down] == 0, "no way up or down");
        check(weights[east] == 2 && weights[north] == 1, "the level weights are left as they were");

        weights = new int[] {3, 4, 1, 0, 0, 2};
        LayeredLogic.levelOnly(weights, Direction.EAST, true, false);
        check(weights[up] == 4 && weights[down] == 0, "wood above may be followed, and only that");
        weights = new int[] {3, 4, 1, 0, 0, 2};
        LayeredLogic.levelOnly(weights, Direction.EAST, false, true);
        check(weights[down] == 3 && weights[up] == 0, "wood below may be followed, and only that");

        // Nothing but up and down had a weight: the limb goes straight on, not up.
        weights = new int[] {2, 5, 0, 0, 0, 0};
        LayeredLogic.levelOnly(weights, Direction.NORTH, false, false);
        check(weights[north] == 1 && weights[up] == 0 && weights[down] == 0,
                "with nowhere level to go the limb goes straight on");
        int sum = 0;
        for (int weight : weights) {
            sum += weight;
        }
        check(sum == 1, "and nowhere else");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 3. What Dynamic Trees holds
    // ------------------------------------------------------------------

    /**
     * By default Dynamic Trees keeps its own shapes. With the setting on,
     * for every species the mod ships shapes for, Dynamic Trees holds
     * exactly those shapes, and the species grows by the mod's rule.
     * The test turns the shapes on itself and puts the setting's state
     * back at the end.
     */
    @GameTest(template = "empty", batch = "dynamicleaves_limbs")
    public static void dynamicTreesHoldsTheLayeredShapes(GameTestHelper helper) {
        check(!DynamicLeavesConfig.layeredLimbs(), "the setting is off by default");
        check(!LayeredLimbs.inPlace(), "so Dynamic Trees' own shapes are in place");
        for (String name : SPECIES) {
            check(species(name).getGrowthLogicKit().getConfigurable() != LayeredLimbs.KIT, name + " grows Dynamic Trees' way by default");
        }
        try {
            LayeredLimbs.use(true);
            holdsTheLayeredShapes();
        } finally {
            LayeredLimbs.use(DynamicLeavesConfig.layeredLimbs());
        }
        helper.succeed();
    }

    /** The checks of {@link #dynamicTreesHoldsTheLayeredShapes} with the shapes turned on. */
    private static void holdsTheLayeredShapes() {
        check(LayeredLimbs.inPlace(), "the shapes are in place");
        for (String name : SPECIES) {
            ResourceLocation id = ResourceLocation.parse(name);
            check(LayeredLimbs.species().contains(id), "the mod has shapes for " + name);
            for (int radius = 2; radius <= 8; radius++) {
                Set<String> ours = ours(name, radius);
                check(ours.size() == SHAPES_PER_RADIUS, name + " has " + SHAPES_PER_RADIUS + " different shapes for radius "
                        + radius + ": " + ours.size());
                check(held(name, radius).equals(ours), "Dynamic Trees holds exactly the mod's shapes for " + name + ", radius " + radius);
            }
            check(species(name).getGrowthLogicKit().getConfigurable() == LayeredLimbs.KIT, name + " grows by the mod's rule");
        }
        check(GrowthLogicKit.REGISTRY.get(LayeredLimbs.KIT_ID) == LayeredLimbs.KIT, "the rule is in Dynamic Trees' registry as dynamicleaves:layered");
        // A tree the mod has no shapes for is left alone.
        check(!LayeredLimbs.species().contains(ResourceLocation.parse("dynamictrees:dark_oak")), "dark oak is left alone");
        check(!held("dynamictrees:dark_oak", 5).isEmpty(), "and Dynamic Trees still holds its shapes");
        check(species("dynamictrees:spruce").getGrowthLogicKit().getConfigurable() != LayeredLimbs.KIT, "a spruce grows its own way");
    }

    // ------------------------------------------------------------------
    // 4. Stored shapes in the world
    // ------------------------------------------------------------------

    /**
     * Plants some of a species' stored shapes, small, middling and
     * large, and checks each.
     *
     * @param fewestLayers on how many heights a full-size tree must have limbs
     */
    private static void checkStoredShapes(GameTestHelper helper, String name, int fewestLayers) {
        ResourceLocation id = ResourceLocation.parse(name);
        int planted = 0;
        for (int radius : new int[] {2, 5, 8}) {
            List<String> codes = LayeredLimbs.shapes(id, radius);
            for (int index = 0; index < codes.size(); index += 4) {
                String code = codes.get(index);
                Set<BlockPos> expected = decode(code);
                Tree tree = plantAsNewLand(helper, name, code, radius);
                try {
                    Set<BlockPos> wood = wood(tree);
                    String which = name + " radius " + radius + " shape " + index;
                    check(sameTurned(wood, expected), which + " stands as its code says: " + wood.size() + " pieces of wood of "
                            + expected.size() + missing(wood, expected));
                    check(steps(wood) == 0, which + ": every limb is level, but " + steps(wood) + " pieces of wood off the trunk stand on other wood");
                    // Measured on the code, which has the tree unturned; the wood in the world was just found to be the same.
                    check(stickingOut(expected).isEmpty(), which + ": no limb sticks out from under the crown, but:" + stickingOut(expected));
                    Set<Integer> layers = limbHeights(wood);
                    if (radius >= 5) {
                        check(layers.size() >= fewestLayers, which + " has limbs on many heights: " + layers);
                        check(tree.leaves.size() >= 150, which + " has a crown: " + tree.leaves.size() + " leaf blocks");
                    } else {
                        check(layers.size() >= 2, which + " has limbs on more than one height: " + layers);
                        check(tree.leaves.size() >= 40, which + " has a crown: " + tree.leaves.size() + " leaf blocks");
                    }
                    planted++;
                } finally {
                    tree.remove();
                }
            }
        }
        DynamicLeaves.LOGGER.info("Dynamic Leaves test: {} stored shapes of {} planted and found whole", planted, name);
    }

    /** Stored oaks stand whole, with level limbs on many heights and a crown. */
    @GameTest(template = "empty", batch = "dynamicleaves_limbs_oak", skyAccess = true)
    public static void storedOaksHaveLimbsInLayers(GameTestHelper helper) {
        checkStoredShapes(helper, "dynamictrees:oak", 4);
        helper.succeed();
    }

    /** Stored birches stand whole, with level limbs on many heights and a crown. */
    @GameTest(template = "empty", batch = "dynamicleaves_limbs_birch", skyAccess = true)
    public static void storedBirchesHaveLimbsInLayers(GameTestHelper helper) {
        checkStoredShapes(helper, "dynamictrees:birch", 4);
        helper.succeed();
    }

    /** Stored tall birches stand whole: a long bare trunk under a crown of level limbs. */
    @GameTest(template = "empty", batch = "dynamicleaves_limbs_tall_birch", skyAccess = true)
    public static void storedTallBirchesHaveLimbsInLayers(GameTestHelper helper) {
        checkStoredShapes(helper, "dynamictrees:tall_birch", 4);
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 5. A tree that grows
    // ------------------------------------------------------------------

    /**
     * With the setting on, a young oak (a bare trunk of four blocks on
     * good soil) is made to grow. Every limb it grows is level: no wood
     * off the trunk stands on other wood. The test turns the shapes on
     * itself and puts the setting's state back at the end.
     */
    @GameTest(template = "empty", batch = "dynamicleaves_limbs_growth", skyAccess = true)
    public static void aGrowingOakGrowsLevelLimbs(GameTestHelper helper) {
        LayeredLimbs.use(true);
        try {
            growLevelLimbs(helper);
        } finally {
            LayeredLimbs.use(DynamicLeavesConfig.layeredLimbs());
        }
        helper.succeed();
    }

    /** The checks of {@link #aGrowingOakGrowsLevelLimbs} with the shapes turned on. */
    private static void growLevelLimbs(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // "JJ" is four steps up: a trunk and nothing else.
        Tree tree = Tree.plant(helper, "dynamictrees:oak", "JJ", 15);
        try {
            Set<BlockPos> before = wood(tree);
            check(before.size() == 4, "the young oak is a trunk of four blocks: " + before.size());
            for (int pulse = 0; pulse < 200; pulse++) {
                TreeHelper.growPulse(level, tree.root);
            }
            Set<BlockPos> wood = wood(tree);
            int offTrunk = 0;
            for (BlockPos block : wood) {
                offTrunk += inTrunk(block) ? 0 : 1;
            }
            check(wood.size() > before.size() + 8, "the oak grew: " + before.size() + " pieces of wood before, " + wood.size() + " after");
            check(offTrunk >= 6, "and it grew limbs: " + offTrunk + " pieces of wood off the trunk");
            check(steps(wood) == 0, "every limb is level: " + steps(wood) + " pieces of wood off the trunk stand on other wood");
            DynamicLeaves.LOGGER.info("Dynamic Leaves test: a young oak grew from {} to {} pieces of wood, {} off the trunk on {} heights, all level",
                    before.size(), wood.size(), offTrunk, limbHeights(wood).size());
        } finally {
            tree.remove();
        }
    }

    // ------------------------------------------------------------------
    // 6. The setting
    // ------------------------------------------------------------------

    /**
     * Turned off, Dynamic Trees holds its own shapes again and the
     * species grow its way; turned on, the mod's are back. Other trees'
     * shapes are untouched by either.
     */
    @GameTest(template = "empty", batch = "dynamicleaves_limbs_switch")
    public static void theSettingTurnsTheShapesOffAndOn(GameTestHelper helper) {
        String oak = "dynamictrees:oak";
        ResourceLocation oakId = ResourceLocation.parse(oak);
        Set<String> darkOakBefore = held("dynamictrees:dark_oak", 5);
        Set<String> spruceBefore = held("dynamictrees:spruce", 4);
        try {
            LayeredLimbs.use(false);
            check(!LayeredLimbs.inPlace(), "turned off");
            for (String name : SPECIES) {
                for (int radius = 2; radius <= 8; radius++) {
                    Set<String> now = held(name, radius);
                    check(!now.isEmpty(), "Dynamic Trees has shapes of its own for " + name + ", radius " + radius);
                    now.retainAll(ours(name, radius));
                    check(now.isEmpty(), "and none of them is the mod's, for " + name + ", radius " + radius);
                }
                check(species(name).getGrowthLogicKit().getConfigurable() == GrowthLogicKit.DEFAULT, name + " grows Dynamic Trees' way again");
            }
            check(held("dynamictrees:dark_oak", 5).equals(darkOakBefore) && held("dynamictrees:spruce", 4).equals(spruceBefore),
                    "other trees' shapes are as they were");
            LayeredLimbs.use(false);
            check(!held(oak, 5).isEmpty(), "turning it off twice changes nothing");

            LayeredLimbs.use(true);
            check(LayeredLimbs.inPlace(), "turned on again");
            for (String name : SPECIES) {
                for (int radius = 2; radius <= 8; radius++) {
                    check(held(name, radius).equals(ours(name, radius)),
                            "the mod's shapes are back for " + name + ", radius " + radius);
                }
                check(species(name).getGrowthLogicKit().getConfigurable() == LayeredLimbs.KIT, name + " grows by the mod's rule again");
            }
            check(JoCodeRegistry.getCodes(oakId, 5).size() == LayeredLimbs.shapes(oakId, 5).size(), "and not twice over");
            check(held("dynamictrees:dark_oak", 5).equals(darkOakBefore) && held("dynamictrees:spruce", 4).equals(spruceBefore),
                    "other trees' shapes are still as they were");
        } finally {
            LayeredLimbs.use(DynamicLeavesConfig.layeredLimbs());
        }
        helper.succeed();
    }
}
