package io.github.superveryfastcar.foliage.gametest;

import static io.github.superveryfastcar.foliage.gametest.FoliageGameTests.check;
import static io.github.superveryfastcar.foliage.gametest.FoliageGameTests.failure;

import com.dtteam.dynamictrees.api.network.BranchDestructionData;
import com.dtteam.dynamictrees.block.branch.BranchBlock;
import com.dtteam.dynamictrees.block.leaves.DynamicLeavesBlock;
import com.dtteam.dynamictrees.block.leaves.LeavesProperties;
import com.dtteam.dynamictrees.block.leaves.ScruffyLeavesProperties;
import com.dtteam.dynamictrees.tree.TreeHelper;
import com.dtteam.dynamictrees.tree.species.Species;
import io.github.superveryfastcar.calendar.api.CalendarRules;
import io.github.superveryfastcar.calendar.api.Season;
import io.github.superveryfastcar.foliage.Foliage;
import io.github.superveryfastcar.foliage.FoliageConfig;
import io.github.superveryfastcar.foliage.block.BareLeavesBlock;
import io.github.superveryfastcar.foliage.block.Twins;
import io.github.superveryfastcar.foliage.gametest.FoliageGameTests.Tree;
import io.github.superveryfastcar.foliage.season.LeafClock;
import io.github.superveryfastcar.foliage.season.LeafSchedule;
import io.github.superveryfastcar.foliage.season.LeafStage;
import io.github.superveryfastcar.foliage.season.SeasonSettings;
import io.github.superveryfastcar.foliage.season.TreeOrder;
import io.github.superveryfastcar.foliage.shape.LeafLook;
import io.github.superveryfastcar.foliage.shape.ShapeSettings;
import io.github.superveryfastcar.foliage.world.LeafWalker;
import io.github.superveryfastcar.foliage.world.SpotRule;
import io.github.superveryfastcar.foliage.world.WalkSettings;
import io.github.superveryfastcar.seasons.api.SeasonsApi;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Automatic tests for the bare blocks: the blocks that stand where leaf
 * blocks were while a tree has shed its leaves. They run with the other
 * game tests ({@code ./gradlew runGameTestServer}).
 *
 * <h2>What is tested</h2>
 * <ol>
 *   <li><b>The blocks</b>: every plain kind of leaves has a bare block,
 *       the two know each other, and a bare block is next to nothing for
 *       the game: no collision, no shade, no drops.</li>
 *   <li><b>The rule</b> for one spot, as arithmetic.</li>
 *   <li><b>Real trees</b>: an oak goes bare and gets its leaves back with
 *       nothing lost, Dynamic Trees holds a bare crown exactly as it holds
 *       a crown in leaf, the ground under it is as bright as open land, a
 *       tree grows bare in winter, a bare tree that is cut down drops no
 *       leaves, and the walker that swaps the blocks keeps to its limits.</li>
 * </ol>
 *
 * <h2>How the season is set</h2>
 * The date of the test world is never touched. The walker is handed a
 * leaf calendar made by hand ({@link LeafWalker#fixClock}), and every
 * test hands the date back when it ends, also when it fails.
 *
 * <h2>Why some tests take several ticks</h2>
 * The game works out light on a thread of its own, and the walker mends a
 * wrong block in the tick after it was noticed. A test that looks at
 * light waits until the game says the light is done; one that looks at
 * the walker's mending waits a few ticks.
 *
 * <p>These classes are left out of the released jar (see {@code build.gradle}).</p>
 */
@GameTestHolder(Foliage.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BareBranchGameTests {

    private static final SeasonSettings RULES = SeasonSettings.DEFAULT;
    private static final CalendarRules YEAR = CalendarRules.DEFAULT;
    private static final Direction[] DIRECTIONS = Direction.values();

    private BareBranchGameTests() {
    }

    // ------------------------------------------------------------------
    // Small helpers
    // ------------------------------------------------------------------

    /** The leaf calendar at the start of a day of a season (day 0 is the season's first), with the mod's own rules. */
    private static LeafClock clockOn(Season season, int day) {
        return LeafClock.of(RULES, YEAR, LeafCalendarGameTests.stepOn(RULES, YEAR, season, day));
    }

    /** A block by its name. */
    private static Block block(String name) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(name));
    }

    /** Hands the date and the settings back to the walker, and takes the test's tree away. */
    private static void tidy(@Nullable Tree tree) {
        LeafWalker.fixClock(null);
        LeafWalker.setSettings(FoliageConfig.walkSettings());
        if (tree != null) {
            tree.remove();
        }
    }

    /**
     * Runs a later part of a test. If the part fails, the walker and the
     * world are tidied first, so that the tests after it find them as
     * they should be.
     */
    private static void later(GameTestHelper helper, int ticks, @Nullable Tree tree, Runnable part) {
        helper.runAfterDelay(ticks, () -> {
            try {
                part.run();
            } catch (RuntimeException | Error failed) {
                tidy(tree);
                throw failed;
            }
        });
    }

    /**
     * Runs a later part of a test once the game has worked out the light
     * for every block change made so far. The game does that on a thread
     * of its own, and the test server does not wait between ticks, so a
     * fixed number of ticks would not do.
     */
    private static void whenLit(GameTestHelper helper, Tree tree, Runnable part) {
        ChunkPos at = new ChunkPos(tree.root);
        awaitLight(helper, tree, tree.level.getChunkSource().getLightEngine().waitForPendingTasks(at.x, at.z), part);
    }

    private static void awaitLight(GameTestHelper helper, Tree tree, CompletableFuture<?> lit, Runnable part) {
        later(helper, 1, tree, () -> {
            if (lit.isDone()) {
                part.run();
            } else {
                awaitLight(helper, tree, lit, part);
            }
        });
    }

    /** Every leaf block and bare block around a tree, by position, in a fixed order. */
    private static Map<BlockPos, BlockState> crown(Tree tree) {
        Map<BlockPos, BlockState> found = new LinkedHashMap<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = 0; y <= Tree.HEIGHT; y++) {
            for (int x = -Tree.REACH; x <= Tree.REACH; x++) {
                for (int z = -Tree.REACH; z <= Tree.REACH; z++) {
                    pos.setWithOffset(tree.root, x, y, z);
                    BlockState state = tree.level.getBlockState(pos);
                    if (state.getBlock() instanceof DynamicLeavesBlock) {
                        found.put(pos.immutable(), state);
                    }
                }
            }
        }
        return found;
    }

    /** How many of the blocks are bare blocks. */
    private static int bareCount(Collection<BlockState> states) {
        int count = 0;
        for (BlockState state : states) {
            count += state.getBlock() instanceof BareLeavesBlock ? 1 : 0;
        }
        return count;
    }

    /**
     * For every piece of wood of a tree: how much its neighbors hold it
     * up, counted the way Dynamic Trees counts before it lets a twig rot.
     * A twig tip rots when too few leaf blocks of its own family are
     * around it.
     */
    private static Map<BlockPos, Integer> support(Tree tree) {
        Map<BlockPos, Integer> found = new LinkedHashMap<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = 0; y <= Tree.HEIGHT; y++) {
            for (int x = -Tree.REACH; x <= Tree.REACH; x++) {
                for (int z = -Tree.REACH; z <= Tree.REACH; z++) {
                    pos.setWithOffset(tree.root, x, y, z);
                    BlockState state = tree.level.getBlockState(pos);
                    BranchBlock branch = TreeHelper.getBranch(state);
                    if (branch == null) {
                        continue;
                    }
                    int radius = branch.getRadius(state);
                    int sum = 0;
                    for (Direction side : DIRECTIONS) {
                        BlockPos near = pos.relative(side);
                        BlockState other = tree.level.getBlockState(near);
                        sum += TreeHelper.getTreePart(other).branchSupport(other, tree.level, branch, near, side, radius);
                    }
                    found.put(pos.immutable(), sum);
                }
            }
        }
        return found;
    }

    /**
     * Brings the supply numbers of a crown to rest: every leaf block
     * works out its number from its neighbors again, until none changes.
     * A tree that was just planted may still have a few that are off.
     *
     * <p>The crown is not aged in full for this. A leaf block that ages
     * also grows new leaves into free spots at random and dies where it
     * is too dark, so a crown in leaf never comes to rest; that is how
     * Dynamic Trees shapes a crown, and nothing a test can wait for.</p>
     *
     * @return the crown at rest
     */
    private static Map<BlockPos, BlockState> settle(Tree tree) {
        for (int round = 0; round < 16; round++) {
            boolean changed = false;
            for (Map.Entry<BlockPos, BlockState> spot : crown(tree).entrySet()) {
                BlockState state = spot.getValue();
                DynamicLeavesBlock leaves = (DynamicLeavesBlock) state.getBlock();
                changed |= leaves.updateHydro(tree.level, spot.getKey(), state, false) != state.getValue(LeavesBlock.DISTANCE);
            }
            if (!changed) {
                return crown(tree);
            }
        }
        throw failure("the supply numbers of the " + tree.species + " do not come to rest");
    }

    /**
     * Has the walker look at every chunk that holds one of the spots,
     * right now and without limits.
     *
     * @return how many blocks it swapped
     */
    private static int walk(ServerLevel level, Collection<BlockPos> spots) {
        LongLinkedOpenHashSet chunks = new LongLinkedOpenHashSet();
        for (BlockPos spot : spots) {
            chunks.add(ChunkPos.asLong(spot));
        }
        LeafWalker walker = LeafWalker.of(level);
        int swapped = 0;
        for (long chunk : chunks) {
            int count = walker.walkNow(new ChunkPos(chunk));
            check(count >= 0, "the chunk " + new ChunkPos(chunk) + " of the test's tree is in the walker's reach");
            swapped += count;
        }
        return swapped;
    }

    /**
     * The spots on the ground under a crown, one for each column of leaf
     * blocks that has no wood in it: the lowest air block above whatever
     * lies under the leaves.
     */
    private static List<BlockPos> groundUnder(Tree tree, Collection<BlockPos> leaves) {
        Map<Long, Integer> lowest = new LinkedHashMap<>();
        for (BlockPos leaf : leaves) {
            lowest.merge(ChunkPos.asLong(leaf.getX(), leaf.getZ()), leaf.getY(), Math::min);
        }
        List<BlockPos> spots = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        columns:
        for (Map.Entry<Long, Integer> column : lowest.entrySet()) {
            int x = ChunkPos.getX(column.getKey());
            int z = ChunkPos.getZ(column.getKey());
            for (int y = tree.root.getY(); y <= tree.root.getY() + Tree.HEIGHT; y++) {
                if (TreeHelper.isBranch(tree.level.getBlockState(pos.set(x, y, z)))) {
                    continue columns;
                }
            }
            int y = column.getValue() - 1;
            while (y > tree.level.getMinBuildHeight() && tree.level.getBlockState(pos.set(x, y, z)).isAir()) {
                y--;
            }
            spots.add(new BlockPos(x, y + 1, z));
        }
        return spots;
    }

    /** The light of the sky at each of the spots. */
    private static int[] skyLight(ServerLevel level, List<BlockPos> spots) {
        int[] light = new int[spots.size()];
        for (int i = 0; i < light.length; i++) {
            light[i] = level.getBrightness(LightLayer.SKY, spots.get(i));
        }
        return light;
    }

    /** What the leaf model would draw at each of the spots right now, as recipes. */
    private static int[] recipes(LeafLook look, LeafClock clock, ServerLevel level, List<BlockPos> spots) {
        int[] recipes = new int[spots.size()];
        for (int i = 0; i < recipes.length; i++) {
            BlockPos spot = spots.get(i);
            recipes[i] = look.recipe(ShapeSettings.DEFAULT, clock, level, level, spot, level.getBlockState(spot));
        }
        return recipes;
    }

    // ------------------------------------------------------------------
    // The blocks
    // ------------------------------------------------------------------

    /** Every plain kind of leaves has a bare block; the two know each other, and Dynamic Trees takes both for the same leaves. */
    @GameTest(template = "empty", batch = "foliage_bare_rules")
    public static void everyPlainKindHasItsBareBlock(GameTestHelper helper) {
        // The names are stored in worlds. These must never change.
        for (String name : List.of("oak", "birch", "spruce", "jungle", "acacia", "dark_oak", "cherry", "azalea", "mangrove")) {
            Block leaf = block("dynamictrees:" + name + "_leaves");
            check(leaf instanceof DynamicLeavesBlock, "Dynamic Trees has " + name + " leaves");
            check(block("foliage:bare_" + name + "_leaves") instanceof BareLeavesBlock, "the bare block of " + name + " leaves is foliage:bare_" + name + "_leaves");
            check(Twins.bareOf(leaf) == block("foliage:bare_" + name + "_leaves"), "and it is the twin of the " + name + " leaves");
        }
        check(Twins.bareName(ResourceLocation.parse("dynamictrees:oak_leaves")).toString().equals("foliage:bare_oak_leaves"),
                "a leaf block of Dynamic Trees keeps its plain name");
        check(Twins.bareName(ResourceLocation.parse("dtplus:cactus/saguaro_leaves")).toString().equals("foliage:bare_dtplus_cactus_saguaro_leaves"),
                "a leaf block of another mod has the mod's id in front, and no slashes");

        LeafClock winter = clockOn(Season.WINTER, 3);
        int pairs = 0;
        for (LeavesProperties kind : LeavesProperties.REGISTRY.getAll()) {
            DynamicLeavesBlock leaf = kind.getDynamicLeavesBlock().orElse(null);
            if (leaf == null || leaf.getLeavesProperties() != kind) {
                continue;
            }
            String what = String.valueOf(kind.getRegistryName());
            boolean plain = kind.getClass() == LeavesProperties.class || kind.getClass() == ScruffyLeavesProperties.class;
            BareLeavesBlock bare = Twins.bareOf(leaf);
            check((bare != null) == plain, "exactly the plain kinds of leaves have a bare block: " + what);
            if (bare == null) {
                check(Twins.twinOf(leaf) == null, "a kind without a bare block is in no pair: " + what);
                continue;
            }
            pairs++;
            check(Twins.twinOf(leaf) == bare && Twins.twinOf(bare) == leaf && Twins.bareOf(bare) == null, "the two blocks of a pair know each other: " + what);
            check(bare.leaf() == leaf && bare.kind().leaf() == kind, "the bare block knows its leaf: " + what);
            check(BuiltInRegistries.BLOCK.getKey(bare).equals(Twins.bareName(BuiltInRegistries.BLOCK.getKey(leaf))), "the bare block is named by the rule: " + what);
            check(bare.getDescriptionId().equals("block.foliage.bare_leaves"), "all bare blocks share one name for players: " + what);
            // For Dynamic Trees the bare block is a leaf of the same tree.
            check(bare.getLeavesProperties() == bare.kind() && bare.kind().getDynamicLeavesBlock().orElse(null) == bare, "kind and block belong together: " + what);
            check(bare.kind().getFamily() == kind.getFamily(), "the same tree family: " + what);
            check(bare.kind().getCellKit() == kind.getCellKit(), "water spreads the same way: " + what);
            check(bare.kind().getFlammability() == FoliageConfig.twigsBurn() && bare.kind().getFireSpreadSpeed() == FoliageConfig.twigsSpreadFire(),
                    "it burns as the settings say, not as its leaf does: " + what);
            check(bare.kind().getLightRequirement() == kind.getLightRequirement() && bare.kind().getSmotherLeavesMax() == kind.getSmotherLeavesMax(),
                    "the same numbers for light and for smothering: " + what);
            for (Species species : Species.REGISTRY.getAll()) {
                if (species.isValid()) {
                    check(species.isValidLeafBlock(leaf) == species.isValidLeafBlock(bare),
                            species.getRegistryName() + " takes the bare block for its own exactly if it takes the leaf: " + what);
                    if (species.isValidLeafBlock(leaf)) {
                        check(species.getValidLeafBlock(species.getLeafBlockIndex(bare)) == bare && species.getValidLeafBlock(species.getLeafBlockIndex(leaf)) == leaf,
                                species.getRegistryName() + " keeps the two apart in its list: " + what);
                    }
                }
            }
            // The leaf calendar gives both the same schedule.
            check(winter.schedule(bare) == winter.schedule(leaf), "leaf and bare block have the same schedule: " + what);
            // Swapping keeps what the block state holds.
            for (int supply = 1; supply <= 7; supply++) {
                for (int marks = 0; marks < 4; marks++) {
                    BlockState state = leaf.defaultBlockState().setValue(LeavesBlock.DISTANCE, supply)
                            .setValue(LeavesBlock.PERSISTENT, (marks & 1) != 0).setValue(LeavesBlock.WATERLOGGED, (marks & 2) != 0);
                    BlockState swapped = Twins.swap(state, bare);
                    check(swapped.getBlock() == bare && swapped.getValue(LeavesBlock.DISTANCE) == supply
                                    && swapped.getValue(LeavesBlock.PERSISTENT) == ((marks & 1) != 0) && swapped.getValue(LeavesBlock.WATERLOGGED) == ((marks & 2) != 0),
                            "a swap keeps the supply number and the marks: " + what);
                    check(Twins.swap(swapped, leaf) == state, "and a swap back gives the same block state: " + what);
                }
            }
        }
        check(pairs >= 12 && pairs == Twins.bareBlocks().size(), "all pairs are listed: " + pairs + " found, " + Twins.bareBlocks().size() + " listed");
        check(Twins.twinOf(Blocks.OAK_LEAVES) == null && Twins.twinOf(Blocks.AIR) == null, "the game's own leaves are in no pair");
        Species oak = Species.REGISTRY.get(ResourceLocation.parse("dynamictrees:oak"));
        check(oak != null && oak.isValidLeafBlock(Twins.bareOf(block("dynamictrees:oak_leaves"))), "the oak takes bare oak blocks for its own");
        Foliage.LOGGER.info("Foliage test: {} pairs of a leaf block and a bare block", pairs);
        helper.succeed();
    }

    /** For the game a bare block is next to nothing: nothing bumps into it, it throws no shade, it drops nothing. */
    @GameTest(template = "empty", batch = "foliage_bare_rules", skyAccess = true)
    public static void aBareBlockIsNextToNothing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Block leaf = block("dynamictrees:oak_leaves");
        BareLeavesBlock bare = Twins.bareOf(leaf);
        check(bare != null, "oak leaves have a bare block");
        BlockPos pos = helper.absolutePos(new BlockPos(3, 2, 3));
        int openHeight = level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ());
        check(openHeight <= pos.getY(), "the test's spot is under the open sky");

        // Flags 2 and 16: tell the players, but let no neighbor react. A leaf
        // block in thin air would otherwise be removed by Dynamic Trees.
        BlockState leafState = leaf.defaultBlockState().setValue(LeavesBlock.DISTANCE, 3);
        level.setBlock(pos, leafState, 2 | 16);
        check(level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ()) == pos.getY() + 1, "a leaf block stops rain and snow");
        check(level.getPathTypeCache().getOrCompute(level, pos) == PathType.LEAVES, "animals take a leaf block for leaves");
        int leafBurns = leafState.getFlammability(level, pos, Direction.UP);
        int leafSpreads = leafState.getFireSpreadSpeed(level, pos, Direction.UP);

        BlockState state = Twins.swap(leafState, bare);
        level.setBlock(pos, state, 2 | 16);
        check(level.getBlockState(pos) == state, "the bare block is placed");
        check(state.getCollisionShape(level, pos).isEmpty() && state.getShape(level, pos).isEmpty(), "no collision and no outline");
        check(state.getBlockSupportShape(level, pos).isEmpty(), "nothing rests on it");
        check(!state.blocksMotion() && !state.isSuffocating(level, pos) && !state.isSolidRender(level, pos) && !state.canOcclude(),
                "it is not solid in any sense");
        check(state.canBeReplaced(), "a block can be placed where it is");
        check(state.getPistonPushReaction() == PushReaction.DESTROY, "a piston pushes through it");
        check(state.getLightBlock(level, pos) == 0 && state.propagatesSkylightDown(level, pos), "light passes undimmed");
        check(state.getShadeBrightness(level, pos) == 1.0F, "no shade on the corners of its neighbors");
        check(!state.isRandomlyTicking(), "it never ticks at random");
        check(state.getBlockPathType(level, pos, null) == PathType.OPEN && level.getPathTypeCache().getOrCompute(level, pos) == PathType.OPEN,
                "animals take it for open air");
        check(level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ()) == openHeight, "rain and snow fall through it");
        check(Block.getDrops(state, level, pos, null).isEmpty(), "it drops nothing");
        check(bare.getCloneItemStack(level, pos, state).isEmpty(), "it cannot be picked");
        // Fire: slower than leaves in both ways, but it can burn, or its twig could never catch fire.
        int burns = state.getFlammability(level, pos, Direction.UP);
        int spreads = state.getFireSpreadSpeed(level, pos, Direction.UP);
        check(burns == FoliageConfig.DEFAULT_TWIGS_BURN && spreads == FoliageConfig.DEFAULT_TWIGS_SPREAD_FIRE,
                "its fire numbers are the settings: " + burns + " and " + spreads);
        check(burns > 0 && burns < leafBurns && spreads < leafSpreads,
                "fire eats it, more slowly than its leaf (" + leafBurns + "), and does not jump along it as along leaves (" + leafSpreads + ")");
        check(state.isFlammable(level, pos, Direction.UP), "fire can hold on to it, so it reaches the twigs behind");
        check(state.getFluidState().isEmpty(), "it holds no water unless marked so");
        BlockState wet = state.setValue(LeavesBlock.WATERLOGGED, true);
        check(wet.getLightBlock(level, pos) == 1 && wet.getBlockPathType(level, pos, null) == PathType.WATER, "with water in it, it is water");

        // With nobody keeping to the leaf calendar by hand, the block still belongs to a pair: fit() gives the right one.
        LeafWalker.fixClock(clockOn(Season.SUMMER, 3));
        try {
            check(LeafWalker.fit(level, pos, state) == leafState && LeafWalker.fit(level, pos, leafState) == leafState, "in summer the spot holds the leaf block");
            LeafWalker.fixClock(clockOn(Season.WINTER, 3));
            check(LeafWalker.fit(level, pos, state) == state && LeafWalker.fit(level, pos, leafState) == state, "in winter the bare block");
            BlockState spruce = block("dynamictrees:spruce_leaves").defaultBlockState();
            check(LeafWalker.fit(level, pos, spruce) == spruce, "a kind that keeps its leaves keeps its leaf block");
            BlockState stone = Blocks.STONE.defaultBlockState();
            check(LeafWalker.fit(level, pos, stone) == stone, "any other block is left as it is");
        } finally {
            LeafWalker.fixClock(null);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // The rule for one spot
    // ------------------------------------------------------------------

    /** A spot holds the bare block exactly while its stage is one of the four around winter, in a place with a winter. */
    @GameTest(template = "empty", batch = "foliage_bare_rules")
    public static void theRuleForOneSpot(GameTestHelper helper) {
        for (LeafStage stage : LeafStage.values()) {
            boolean expected = stage == LeafStage.LAST || stage == LeafStage.BARE || stage == LeafStage.BUDS || stage == LeafStage.FIRST;
            check(stage.bareBlock() == expected, "the stage " + stage + (expected ? " has" : " does not have") + " the bare block");
            check(!(stage.bareBlock() && stage.covers()), "a spot with the bare block never covers its neighbors: " + stage);
            check(!stage.leafless() || stage.bareBlock(), "a spot without leaves has the bare block: " + stage);
        }
        Block oak = block("dynamictrees:oak_leaves");
        Block bareOak = Twins.bareOf(oak);
        Block spruce = block("dynamictrees:spruce_leaves");
        LeafClock summer = clockOn(Season.SUMMER, 3);
        LeafClock winter = clockOn(Season.WINTER, 3);
        check(summer.allFull() && winter.allBare(), "summer is all in leaf, winter all bare");
        check(!SpotRule.bare(null, true, oak, true, 1, 70, 1, 3), "without a calendar every spot holds its leaf");
        check(!SpotRule.bare(winter, false, oak, true, 1, 70, 1, 3), "switched off, every spot holds its leaf");
        check(!SpotRule.bare(summer, true, oak, true, 1, 70, 1, 3), "in summer every spot holds its leaf");
        check(!SpotRule.bare(winter, true, oak, false, 1, 70, 1, 3), "where there is no winter every spot holds its leaf");
        check(!SpotRule.bare(winter, true, spruce, true, 1, 70, 1, 3), "a kind that keeps its leaves holds its leaf");
        check(!SpotRule.bare(winter, true, Blocks.STONE, true, 1, 70, 1, 3), "a block that is no leaf is never bare");
        check(SpotRule.bare(winter, true, oak, true, 1, 70, 1, 3) && SpotRule.bare(winter, true, bareOak, true, 1, 70, 1, 3),
                "in winter an oak's spot is bare, asked of either block");

        // Through autumn and spring: the rule is the stage of the spot, and it turns once each way.
        LeafSchedule schedule = winter.schedule(oak);
        check(schedule != null, "oak leaves shed by the mod's own rules");
        long year = LeafCalendarGameTests.stepsPerYear(RULES, YEAR);
        long summerStep = LeafCalendarGameTests.stepOn(RULES, YEAR, Season.SUMMER, 3);
        for (int number = 0; number < 400; number++) {
            int x = number * 7 - 900;
            int y = 64 + number % 23;
            int z = 300 - number * 13;
            int depth = 1 + number % 4;
            int turns = 0;
            boolean before = false;
            for (long step = summerStep; step < summerStep + year; step++) {
                LeafClock clock = LeafClock.of(RULES, YEAR, step);
                boolean bare = SpotRule.bare(clock, true, oak, true, x, y, z, depth);
                check(bare == clock.stage(schedule, x, y, z, depth).bareBlock(), "the rule is the stage of the spot, at step " + step);
                check(bare == SpotRule.bare(clock, true, bareOak, true, x, y, z, depth), "the same for both blocks, at step " + step);
                turns += bare != before ? 1 : 0;
                before = bare;
            }
            check(turns == 2 && !before, "a spot goes bare once and gets its leaf back once a year, spot " + number + ": " + turns + " turns");
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // Real trees
    // ------------------------------------------------------------------

    /**
     * A big oak in winter and back in summer. Every leaf block becomes
     * its bare block with the same supply number, and back; Dynamic Trees
     * holds every twig exactly as before and changes nothing when it ages
     * the bare crown; rain falls through; the ground under the crown gets
     * the full light of the sky, and the old shade back in summer.
     */
    @GameTest(template = "empty", batch = "foliage_bare_oak", skyAccess = true, timeoutTicks = 6000)
    public static void anOakGoesBareAndBack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LeafWalker walker = LeafWalker.of(level);
        LeafClock summerClock = clockOn(Season.SUMMER, 3);
        LeafClock winterClock = clockOn(Season.WINTER, 3);
        LeafWalker.fixClock(summerClock);
        Tree tree = Tree.plant(helper, "dynamictrees:oak", FoliageGameTests.BIG_OAK);
        Map<BlockPos, BlockState> summer;
        Map<BlockPos, Integer> held;
        List<BlockPos> ground;
        try {
            summer = settle(tree);
            held = support(tree);
            ground = groundUnder(tree, summer.keySet());
            check(summer.size() >= 200 && bareCount(summer.values()) == 0, "the oak stands in full leaf: " + summer.size() + " leaf blocks");
            check(held.size() >= 30 && ground.size() >= 30, "it has wood, and ground under its crown: " + held.size() + ", " + ground.size());
            check(walk(level, summer.keySet()) == 0, "in summer the walker changes nothing");
        } catch (RuntimeException | Error failed) {
            tidy(tree);
            throw failed;
        }
        BlockPos someLeaf = summer.keySet().iterator().next();
        int[][] shade = new int[1][];

        // The parts of the test, the last first; each waits for the light of the one before.
        Runnable inLeafAgain = () -> {
            check(Arrays.equals(skyLight(level, ground), shade[0]), "the shade under the crown is exactly as before the winter");
            tidy(tree);
            helper.succeed();
        };

        Runnable bareLight = () -> {
            int[] light = skyLight(level, ground);
            for (int i = 0; i < light.length; i++) {
                check(light[i] == 15, "under the bare crown the ground has the full light of the sky at " + ground.get(i) + ": " + light[i]);
            }
            // Summer again.
            long[] before = walker.counters();
            LeafWalker.fixClock(summerClock);
            int swapped = walk(level, summer.keySet());
            long[] after = walker.counters();
            check(swapped == summer.size() && after[1] - before[1] == swapped && after[0] == before[0],
                    "every bare block is swapped back, once: " + swapped + " of " + summer.size());
            check(crown(tree).equals(summer), "the crown is exactly as it was before the winter");
            check(support(tree).equals(held), "and so is the hold of every piece of wood");
            check(level.getPathTypeCache().getOrCompute(level, someLeaf) == PathType.LEAVES, "animals take the spot for leaves again");
            whenLit(helper, tree, inLeafAgain);
        };

        Runnable inLeaf = () -> {
            shade[0] = skyLight(level, ground);
            int shaded = 0;
            for (int light : shade[0]) {
                shaded += light < 15 ? 1 : 0;
            }
            check(shaded * 2 >= ground.size(), "in leaf, most of the ground under the crown is in shade: " + shaded + " of " + ground.size());
            check(level.getPathTypeCache().getOrCompute(level, someLeaf) == PathType.LEAVES, "animals take a leaf block for leaves");
            for (BlockPos spot : ground) {
                check(level.getHeight(Heightmap.Types.MOTION_BLOCKING, spot.getX(), spot.getZ()) > spot.getY(), "in leaf, the crown keeps rain off the ground at " + spot);
            }

            // Winter.
            long[] before = walker.counters();
            LeafWalker.fixClock(winterClock);
            int swapped = walk(level, summer.keySet());
            long[] after = walker.counters();
            check(swapped == summer.size() && after[0] - before[0] == swapped && after[1] == before[1],
                    "every leaf block is swapped for its bare block, once: " + swapped + " of " + summer.size());
            Map<BlockPos, BlockState> winter = crown(tree);
            check(winter.keySet().equals(summer.keySet()), "the crown has the same spots as before");
            for (Map.Entry<BlockPos, BlockState> spot : summer.entrySet()) {
                BlockState leaf = spot.getValue();
                BlockState now = winter.get(spot.getKey());
                check(now == Twins.swap(leaf, Twins.bareOf(leaf.getBlock())), "the bare block of the same kind with the same supply number, at " + spot.getKey());
                check(LeafWalker.fit(level, spot.getKey(), leaf) == now && LeafWalker.fit(level, spot.getKey(), now) == now, "and it is the block the rule wants, at " + spot.getKey());
            }
            check(support(tree).equals(held), "Dynamic Trees holds every piece of wood exactly as in summer");
            check(level.getPathTypeCache().getOrCompute(level, someLeaf) == PathType.OPEN, "animals now take the spot for open air");
            for (BlockPos spot : ground) {
                check(level.getHeight(Heightmap.Types.MOTION_BLOCKING, spot.getX(), spot.getZ()) == spot.getY(), "rain and snow now reach the ground at " + spot);
            }
            // Dynamic Trees ages the bare crown: nothing may change, no twig may go.
            TreeHelper.ageVolume(level, tree.root, Tree.REACH, Tree.HEIGHT, 3, false);
            check(crown(tree).equals(winter), "ageing a bare crown changes none of its blocks");
            check(support(tree).equals(held), "and loses no wood");
            check(walk(level, summer.keySet()) == 0, "a second look swaps nothing");
            whenLit(helper, tree, bareLight);
        };

        whenLit(helper, tree, inLeaf);
    }

    /**
     * An oak and a birch through autumn and spring, a few steps of the
     * leaf calendar at a time. At every step each spot holds the block
     * its stage asks for, the leaf model draws every spot the same before
     * and after its block is swapped, and no spot ever swaps back and
     * forth.
     */
    @GameTest(template = "empty", batch = "foliage_bare_steps", skyAccess = true)
    public static void stepByStepThroughAutumnAndSpring(GameTestHelper helper) {
        for (String species : List.of("dynamictrees:oak", "dynamictrees:birch")) {
            LeafWalker.fixClock(clockOn(Season.SUMMER, 3));
            Tree tree = Tree.plant(helper, species, species.endsWith("oak") ? FoliageGameTests.BIG_OAK : Tree.storedCode(species, 0));
            try {
                throughAutumnAndSpring(tree);
            } finally {
                tidy(tree);
            }
        }
        helper.succeed();
    }

    private static void throughAutumnAndSpring(Tree tree) {
        ServerLevel level = tree.level;
        LeafWalker walker = LeafWalker.of(level);
        LeafLook look = LeafLook.forThread();
        Map<BlockPos, BlockState> summer = settle(tree);
        List<BlockPos> spots = new ArrayList<>(summer.keySet());
        check(spots.size() >= 40, "the " + tree.species + " has leaves: " + spots.size());

        // Autumn: from before the first leaf falls to the first day of winter.
        long[] counted = walker.counters();
        int bare = 0;
        int partSteps = 0;
        long from = LeafCalendarGameTests.stepOn(RULES, YEAR, Season.AUTUMN, 4);
        long to = LeafCalendarGameTests.stepOn(RULES, YEAR, Season.WINTER, 1);
        for (long step = from; step <= to; step += 2) {
            int now = oneStep(tree, look, summer, spots, step);
            check(now >= bare, "in autumn no spot gets its leaf block back, at step " + step + ": " + now + " after " + bare);
            bare = now;
            partSteps += bare > 0 && bare < spots.size() ? 1 : 0;
        }
        check(bare == spots.size(), "by winter every spot of the " + tree.species + " holds its bare block: " + bare + " of " + spots.size());
        check(partSteps >= 4, "the blocks go a few at a time, not all at once: " + partSteps + " steps with the crown partly bare");
        long[] afterAutumn = walker.counters();
        check(afterAutumn[0] - counted[0] == spots.size() && afterAutumn[1] == counted[1], "each spot was swapped exactly once in autumn");

        // Spring: from the last day of winter until all leaves are back.
        partSteps = 0;
        from = LeafCalendarGameTests.stepOn(RULES, YEAR, Season.WINTER, 13);
        to = LeafCalendarGameTests.stepOn(RULES, YEAR, Season.SPRING, 9) + LeafCalendarGameTests.stepsPerYear(RULES, YEAR);
        for (long step = from; step <= to; step += 2) {
            int now = oneStep(tree, look, summer, spots, step);
            check(now <= bare, "in spring no spot goes bare again, at step " + step + ": " + now + " after " + bare);
            bare = now;
            partSteps += bare > 0 && bare < spots.size() ? 1 : 0;
        }
        check(bare == 0 && crown(tree).equals(summer), "when spring is over the " + tree.species + " is exactly as it was in summer");
        check(partSteps >= 4, "the leaves come back a few at a time: " + partSteps + " steps with the crown partly bare");
        long[] afterSpring = walker.counters();
        check(afterSpring[1] - afterAutumn[1] == spots.size() && afterSpring[0] == afterAutumn[0], "each spot was swapped back exactly once in spring");
    }

    /**
     * Sets the leaf calendar to one step, has the walker put the tree in
     * step, and checks every spot.
     *
     * @return how many spots hold their bare block
     */
    private static int oneStep(Tree tree, LeafLook look, Map<BlockPos, BlockState> summer, List<BlockPos> spots, long step) {
        ServerLevel level = tree.level;
        LeafClock clock = LeafClock.of(RULES, YEAR, step);
        LeafWalker.fixClock(clock);
        // The blocks still stand as the step before left them. The look must not wait for the swap.
        int[] before = recipes(look, clock, level, spots);
        walk(level, spots);
        check(Arrays.equals(before, recipes(look, clock, level, spots)),
                "the leaf model draws every spot the same before and after its block is swapped, at step " + step);
        int bare = 0;
        for (BlockPos spot : spots) {
            BlockState leaf = summer.get(spot);
            BlockState now = level.getBlockState(spot);
            LeafStage stage = look.stage(clock, level, leaf, spot.getX(), spot.getY(), spot.getZ());
            check(look.stage(clock, level, now, spot.getX(), spot.getY(), spot.getZ()) == stage, "both blocks of a spot are at the same stage, at " + spot + ", step " + step);
            boolean isBare = now.getBlock() instanceof BareLeavesBlock;
            check(isBare == stage.bareBlock(), "the spot holds the block its stage " + stage + " asks for, at " + spot + ", step " + step);
            check(now == (isBare ? Twins.swap(leaf, Twins.bareOf(leaf.getBlock())) : leaf), "with the supply number it had in summer, at " + spot + ", step " + step);
            bare += isBare ? 1 : 0;
        }
        check(walk(level, spots) == 0, "a second look swaps nothing, at step " + step);
        return bare;
    }

    /**
     * A young oak on good soil grows through the winter. What Dynamic
     * Trees puts into the bare crown while it grows is bare a tick later,
     * without anybody looking at the chunk; so is a leaf block that
     * something else puts into the crown. No twig is lost.
     */
    @GameTest(template = "empty", batch = "foliage_bare_growth", skyAccess = true, timeoutTicks = 200)
    public static void aTreeGrowsBareInWinter(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LeafWalker walker = LeafWalker.of(level);
        LeafWalker.fixClock(clockOn(Season.SUMMER, 3));
        Tree tree = Tree.plant(helper, "dynamictrees:oak", Tree.storedCode("dynamictrees:oak", 1000), 15);
        int[] woodBefore = new int[1];
        long[][] counted = new long[1][];
        try {
            Map<BlockPos, BlockState> young = settle(tree);
            check(young.size() >= 8, "the young oak has leaves: " + young.size());
            woodBefore[0] = support(tree).size();
            LeafWalker.fixClock(clockOn(Season.WINTER, 3));
            check(walk(level, young.keySet()) == young.size(), "the young oak goes bare");
            counted[0] = walker.counters();
            int grownAsLeaf = 0;
            for (int pulse = 0; pulse < 120; pulse++) {
                TreeHelper.growPulse(level, tree.root);
            }
            Map<BlockPos, BlockState> grown = crown(tree);
            grownAsLeaf = grown.size() - bareCount(grown.values());
            int wood = support(tree).size();
            check(wood > woodBefore[0], "the oak grew in winter: " + woodBefore[0] + " pieces of wood before, " + wood + " after");
            check(grown.size() > young.size(), "and its crown with it: " + young.size() + " spots before, " + grown.size() + " after");
            Foliage.LOGGER.info("Foliage test: a young oak grew from {} to {} pieces of wood and from {} to {} crown spots in winter; {} spots were put down as leaf blocks",
                    woodBefore[0], wood, young.size(), grown.size(), grownAsLeaf);
        } catch (RuntimeException | Error failed) {
            tidy(tree);
            throw failed;
        }

        BlockPos[] planted = new BlockPos[1];
        later(helper, 3, tree, () -> {
            Map<BlockPos, BlockState> crown = crown(tree);
            check(bareCount(crown.values()) == crown.size(), "a tick later every spot of the grown crown is bare: "
                    + (crown.size() - bareCount(crown.values())) + " leaf blocks left of " + crown.size());
            check(walk(level, crown.keySet()) == 0, "and a look at the chunks finds nothing to swap");
            // Somebody puts a leaf block into the bare crown.
            Map.Entry<BlockPos, BlockState> spot = crown.entrySet().iterator().next();
            planted[0] = spot.getKey();
            counted[0] = walker.counters();
            level.setBlock(planted[0], Twins.swap(spot.getValue(), Twins.twinOf(spot.getValue().getBlock())), 3);
            check(!(level.getBlockState(planted[0]).getBlock() instanceof BareLeavesBlock), "a leaf block is put into the bare crown");
        });

        later(helper, 6, tree, () -> {
            check(level.getBlockState(planted[0]).getBlock() instanceof BareLeavesBlock, "a tick later the walker has made it bare");
            check(walker.counters()[0] - counted[0][0] == 1, "and has swapped nothing else");
            // Spring comes: the leaves are back on all that grew, and the tree stays whole when it ages.
            Map<BlockPos, BlockState> crown = crown(tree);
            Map<BlockPos, Integer> held = support(tree);
            LeafWalker.fixClock(clockOn(Season.SUMMER, 3));
            check(walk(level, crown.keySet()) == crown.size(), "in summer every spot gets its leaf block");
            check(support(tree).equals(held), "with every piece of wood held as before");
            tidy(tree);
            helper.succeed();
        });
    }

    /**
     * A bare oak is cut down. Dynamic Trees takes the bare blocks away
     * with the wood as it takes leaves, remembers each as the bare block
     * it was (so that the falling tree is drawn bare), and no leaves, no
     * seeds and no sticks drop out of the bare crown. What it leaves
     * behind of the crown goes by itself.
     */
    @GameTest(template = "empty", batch = "foliage_bare_cut", skyAccess = true, timeoutTicks = 300)
    public static void aBareTreeIsCutDown(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LeafWalker.fixClock(clockOn(Season.SUMMER, 3));
        Tree tree = Tree.plant(helper, "dynamictrees:oak", Tree.storedCode("dynamictrees:oak", 6));
        try {
            Map<BlockPos, BlockState> summer = settle(tree);
            LeafWalker.fixClock(clockOn(Season.WINTER, 3));
            check(walk(level, summer.keySet()) == summer.size(), "the oak goes bare");
            BlockPos cut = tree.root.above();
            BranchBlock trunk = TreeHelper.getBranch(level.getBlockState(cut));
            check(trunk != null, "the oak has a trunk");
            BranchDestructionData felled = trunk.destroyBranchFromNode(level, cut, Direction.DOWN, false, null);
            check(felled.getNumLeaves() * 10 >= summer.size() * 9, "nearly all of the crown falls with the trunk: " + felled.getNumLeaves() + " of " + summer.size());
            for (int index = 0; index < felled.getNumLeaves(); index++) {
                BlockState remembered = felled.getLeavesBlockState(index);
                check(remembered != null && remembered.getBlock() instanceof BareLeavesBlock, "the falling tree remembers a bare block as a bare block");
            }
            check(felled.leavesDrops.isEmpty(), "nothing drops out of a bare crown: " + felled.leavesDrops.size() + " stacks");
            check(support(tree).isEmpty(), "no wood is left standing");
        } catch (RuntimeException | Error failed) {
            tidy(tree);
            throw failed;
        }
        // What is left of the crown has no twig any more, and goes as leaves would.
        helper.succeedWhen(() -> {
            int left = crown(tree).size();
            if (left > 0) {
                throw new GameTestAssertException("bare blocks without a twig go by themselves; " + left + " are still there");
            }
            tidy(tree);
        });
    }

    /**
     * What rests on a leaf block when it goes bare: snow on top goes with
     * the leaves, and a vine on its side lets go. Nothing comes back by
     * itself in summer but the leaf.
     */
    @GameTest(template = "empty", batch = "foliage_bare_rules", skyAccess = true)
    public static void whatRestsOnALeafGoesWithIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Block leaf = block("dynamictrees:oak_leaves");
        BlockPos pos = helper.absolutePos(new BlockPos(3, 1, 3));
        BlockState leafState = leaf.defaultBlockState().setValue(LeavesBlock.DISTANCE, 2);
        LeafWalker.fixClock(clockOn(Season.SUMMER, 3));
        try {
            level.setBlock(pos, leafState, 2 | 16);
            level.setBlock(pos.above(), Blocks.SNOW.defaultBlockState(), 2 | 16);
            level.setBlock(pos.north(), Blocks.VINE.defaultBlockState().setValue(VineBlock.SOUTH, true), 2 | 16);
            level.setBlock(pos.east(), Blocks.STONE.defaultBlockState(), 2 | 16);
            check(walk(level, List.of(pos)) == 0 && level.getBlockState(pos) == leafState && level.getBlockState(pos.above()).is(Blocks.SNOW)
                    && level.getBlockState(pos.north()).is(Blocks.VINE), "in summer all stays as it was placed");
            LeafWalker.fixClock(clockOn(Season.WINTER, 3));
            check(walk(level, List.of(pos)) == 1 && level.getBlockState(pos).getBlock() instanceof BareLeavesBlock, "in winter the leaf block goes bare");
            check(level.getBlockState(pos.above()).isAir(), "the snow on it is gone");
            check(level.getBlockState(pos.north()).isAir(), "the vine on its side has let go");
            check(level.getBlockState(pos.east()).is(Blocks.STONE), "a solid block next to it is left alone");
            LeafWalker.fixClock(clockOn(Season.SUMMER, 3));
            check(walk(level, List.of(pos)) == 1 && level.getBlockState(pos) == leafState, "in summer the leaf is back");
            check(level.getBlockState(pos.above()).isAir() && level.getBlockState(pos.north()).isAir(), "and nothing else");
        } finally {
            LeafWalker.fixClock(null);
            for (BlockPos used : List.of(pos, pos.above(), pos.north(), pos.east())) {
                level.setBlock(used, Blocks.AIR.defaultBlockState(), 2 | 16);
            }
        }
        helper.succeed();
    }

    /**
     * A bare block that has no business where it stands gets its leaf
     * back, whatever the season: its kind was taken off the list of kinds
     * that shed, the list is empty, or shedding is switched off.
     */
    @GameTest(template = "empty", batch = "foliage_bare_rules", skyAccess = true)
    public static void aStrayBareBlockGetsItsLeafBack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Block leaf = block("dynamictrees:oak_leaves");
        BareLeavesBlock bare = Twins.bareOf(leaf);
        BlockPos pos = helper.absolutePos(new BlockPos(3, 1, 3));
        BlockState leafState = leaf.defaultBlockState().setValue(LeavesBlock.DISTANCE, 4);
        BlockState bareState = Twins.swap(leafState, bare);
        long winterStep = LeafCalendarGameTests.stepOn(RULES, YEAR, Season.WINTER, 3);
        SeasonSettings onlyBirch = RULES.withKinds(Map.of(ResourceLocation.parse("dynamictrees:birch_leaves"), new LeafSchedule(0, TreeOrder.INSIDE_OUT)));
        try {
            for (SeasonSettings rules : List.of(onlyBirch, RULES.withKinds(Map.of()), RULES.withEnabled(false))) {
                level.setBlock(pos, bareState, 2 | 16);
                LeafWalker.fixClock(LeafClock.of(RULES, YEAR, winterStep));
                check(walk(level, List.of(pos)) == 0 && level.getBlockState(pos) == bareState, "in winter an oak's bare block stays");
                LeafWalker.fixClock(LeafClock.of(rules, YEAR, winterStep));
                check(walk(level, List.of(pos)) == 1 && level.getBlockState(pos) == leafState,
                        "the oak's bare block gets its leaf back: " + (rules == onlyBirch ? "oaks no longer shed" : rules.enabled() ? "no kind sheds" : "shedding is off"));
                check(LeafWalker.fit(level, pos, bareState) == leafState, "and the rule for one spot says the same");
            }
        } finally {
            LeafWalker.fixClock(null);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
        }
        helper.succeed();
    }

    /**
     * The walker at work by itself, tick after tick, with a low limit:
     * it never swaps more blocks in a tick than the setting allows, and
     * still gets the whole tree done. Then the bare blocks are switched
     * off in the settings, and every leaf comes back in the middle of
     * winter.
     */
    @GameTest(template = "empty", batch = "foliage_bare_limits", skyAccess = true, timeoutTicks = 600)
    public static void theWalkerKeepsToItsLimits(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LeafWalker walker = LeafWalker.of(level);
        int limit = 25;
        LeafWalker.fixClock(clockOn(Season.SUMMER, 3));
        Tree tree = Tree.plant(helper, "dynamictrees:oak", FoliageGameTests.BIG_OAK);
        Map<BlockPos, BlockState> summer;
        try {
            summer = settle(tree);
            check(summer.size() >= 10 * limit, "the oak has many times more leaf blocks than the limit: " + summer.size());
            LeafWalker.setSettings(new WalkSettings(true, 64, limit, 50.0));
            LeafWalker.fixClock(clockOn(Season.WINTER, 3));
        } catch (RuntimeException | Error failed) {
            tidy(tree);
            throw failed;
        }
        long[] last = walker.counters();
        int[] ticks = new int[2];
        boolean[] switchedOff = new boolean[1];
        helper.onEachTick(() -> {
            long[] now = walker.counters();
            long swapped = now[0] + now[1] - last[0] - last[1];
            last[0] = now[0];
            last[1] = now[1];
            if (swapped > limit) {
                tidy(tree);
                throw failure("the walker swapped " + swapped + " blocks in one tick, the limit is " + limit);
            }
            ticks[switchedOff[0] ? 1 : 0] += swapped > 0 ? 1 : 0;
        });
        helper.succeedWhen(() -> {
            Map<BlockPos, BlockState> crown = crown(tree);
            int bare = bareCount(crown.values());
            if (!switchedOff[0]) {
                if (bare < crown.size()) {
                    throw new GameTestAssertException("the walker gets the whole oak bare; " + bare + " of " + crown.size() + " so far");
                }
                check(crown.keySet().equals(summer.keySet()), "the crown has the same spots as before");
                check(ticks[0] >= summer.size() / limit, "it took the walker at least " + summer.size() / limit + " ticks: " + ticks[0]);
                // Winter stays, but bare blocks are switched off: the leaves come back.
                LeafWalker.setSettings(new WalkSettings(false, 64, limit, 50.0));
                switchedOff[0] = true;
            }
            if (bare > 0) {
                throw new GameTestAssertException("switched off, every leaf block comes back; " + bare + " bare blocks so far");
            }
            if (!crown.equals(summer)) {
                tidy(tree);
                throw failure("the crown is exactly as it was before");
            }
            Foliage.LOGGER.info("Foliage test: with a limit of {} swaps per tick the walker took {} ticks to make {} leaf blocks bare and {} ticks to bring them back",
                    limit, ticks[0], summer.size(), ticks[1]);
            tidy(tree);
        });
    }

    /**
     * Part of an oak stands where there is no winter (a desert, laid over
     * the east side of the tree). In winter only the rest of it goes
     * bare; and when the desert is laid over a tree that is bare, the
     * leaves come back there.
     */
    @GameTest(template = "empty", batch = "foliage_bare_biome", skyAccess = true)
    public static void whereThereIsNoWinterLeavesStay(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LeafLook look = LeafLook.forThread();
        LeafClock winterClock = clockOn(Season.WINTER, 3);
        LeafWalker.fixClock(clockOn(Season.SUMMER, 3));
        Tree tree = Tree.plant(helper, "dynamictrees:oak", FoliageGameTests.BIG_OAK);
        BlockPos root = tree.root;
        String east = (root.getX() + 2) + " " + root.getY() + " " + (root.getZ() - 15) + " " + (root.getX() + 15) + " " + (root.getY() + 40) + " " + (root.getZ() + 15);
        try {
            Map<BlockPos, BlockState> summer = settle(tree);
            // First the whole tree bare, then the desert comes.
            LeafWalker.fixClock(winterClock);
            check(walk(level, summer.keySet()) == summer.size(), "the oak goes bare");
            fillBiome(level, east, "minecraft:desert");
            int back = walk(level, summer.keySet());
            int inDesert = 0;
            for (Map.Entry<BlockPos, BlockState> spot : summer.entrySet()) {
                BlockPos pos = spot.getKey();
                boolean winterHere = SeasonsApi.hasWinter(level.getNoiseBiome(pos.getX() >> 2, pos.getY() >> 2, pos.getZ() >> 2).value());
                inDesert += winterHere ? 0 : 1;
                BlockState now = level.getBlockState(pos);
                check((now.getBlock() instanceof BareLeavesBlock) == winterHere, "a spot is bare exactly where there is a winter, at " + pos);
                check(now == (winterHere ? Twins.swap(spot.getValue(), Twins.bareOf(spot.getValue().getBlock())) : spot.getValue()), "with its supply number, at " + pos);
                // The leaf model reads the place the same way.
                check(look.stage(winterClock, level, now, pos.getX(), pos.getY(), pos.getZ()).bareBlock() == winterHere, "and the leaf model draws it so, at " + pos);
            }
            check(inDesert >= summer.size() / 6 && inDesert <= summer.size() * 5 / 6, "the desert covers a good part of the crown, not all: " + inDesert + " of " + summer.size());
            check(back == inDesert, "the walker gave the leaves back exactly there: " + back);
            // The other way round: in summer all in leaf, then winter with the desert in place.
            LeafWalker.fixClock(clockOn(Season.SUMMER, 3));
            check(walk(level, summer.keySet()) == summer.size() - inDesert && crown(tree).equals(summer), "in summer the whole oak is in leaf");
            LeafWalker.fixClock(winterClock);
            check(walk(level, summer.keySet()) == summer.size() - inDesert, "in winter only the part outside the desert goes bare");
        } finally {
            fillBiome(level, east, "minecraft:plains");
            tidy(tree);
        }
        check(SeasonsApi.hasWinter(level.getNoiseBiome(root.getX() >> 2, root.getY() >> 2, root.getZ() >> 2).value()), "the test world has its winter back");
        helper.succeed();
    }

    /** Lays a biome over a box of the world, by the game's own command. */
    private static void fillBiome(ServerLevel level, String box, String biome) {
        MinecraftServer server = level.getServer();
        boolean[] succeeded = {false};
        server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack().withLevel(level).withSuppressedOutput().withCallback((success, value) -> succeeded[0] = success),
                "fillbiome " + box + " " + biome);
        check(succeeded[0], "the game lays " + biome + " over " + box);
    }
}
