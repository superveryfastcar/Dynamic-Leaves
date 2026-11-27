package io.github.superveryfastcar.foliage.gametest;

import static io.github.superveryfastcar.foliage.gametest.FoliageGameTests.check;
import static io.github.superveryfastcar.foliage.gametest.FoliageGameTests.failure;

import com.dtteam.dynamictrees.api.season.ClimateZoneType;
import com.dtteam.dynamictrees.api.season.SeasonManager;
import com.dtteam.dynamictrees.block.fruit.Fruit;
import com.dtteam.dynamictrees.block.fruit.FruitBlock;
import com.dtteam.dynamictrees.block.leaves.DynamicLeavesBlock;
import com.dtteam.dynamictrees.systems.season.SeasonCompatibilityHandler;
import io.github.superveryfastcar.calendar.api.Calendar;
import io.github.superveryfastcar.calendar.api.CalendarRules;
import io.github.superveryfastcar.calendar.api.Season;
import io.github.superveryfastcar.foliage.Foliage;
import io.github.superveryfastcar.foliage.FoliageConfig;
import io.github.superveryfastcar.foliage.block.BareLeavesBlock;
import io.github.superveryfastcar.foliage.block.Twins;
import io.github.superveryfastcar.foliage.compat.DistantHorizons;
import io.github.superveryfastcar.foliage.gametest.FoliageGameTests.Tree;
import io.github.superveryfastcar.foliage.season.LeafClock;
import io.github.superveryfastcar.foliage.season.SeasonSettings;
import io.github.superveryfastcar.foliage.world.LeafWalker;
import io.github.superveryfastcar.foliage.world.Restore;
import io.github.superveryfastcar.foliage.world.WalkSettings;
import io.github.superveryfastcar.foliage.world.TreeSeasons;
import io.github.superveryfastcar.foliage.world.TreeSeasons.Factor;
import io.github.superveryfastcar.seasons.api.SeasonsApi;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Automatic tests for the year of the trees themselves (version 0.4.0):
 * the season Foliage tells Dynamic Trees, the fruit and the snow of a leaf
 * spot that goes bare, the walker's shortcut over land that is already
 * bare, and the command. They run with the other game tests
 * ({@code ./gradlew runGameTestServer}).
 *
 * <h2>What is tested</h2>
 * <ol>
 *   <li><b>The season source</b> as arithmetic: all three factors for
 *       every season and every climate, that trees rest in winter and
 *       that the setting lets them grow, and that places without a
 *       winter have no seasons at all. Then the live one: the manager
 *       Dynamic Trees asks is Foliage's, and its answers for the test
 *       world's date and place are the ones the arithmetic gives.</li>
 *   <li><b>Fruit</b>: fruit under a leaf that goes bare drops off, and
 *       fruit that appears under a block that is already bare is taken
 *       away in the next tick, quietly.</li>
 *   <li><b>Snow</b>: snow on a leaf that goes bare falls to what is
 *       under it, and lies there; it falls past bare blocks on the way,
 *       because nothing rests on one.</li>
 *   <li><b>The shortcut</b>: a second look at a crown that is already
 *       bare counts the blocks of its sections and searches none of
 *       them.</li>
 *   <li><b>The way out</b>: the sweep of {@code /foliage restore} turns
 *       a bare crown back into leaves and marks the chunk to be written
 *       again; the command starts it, says how far it is, refuses to
 *       have the bare blocks switched back on while it runs, and stops
 *       it; and a region file's table of contents, written by hand here,
 *       names the chunks the sweep will visit and no others.</li>
 *   <li><b>Other mods</b>: the leaf calendar knows when leaves drift down
 *       in a player's game, and the link to Distant Horizons sleeps while
 *       that mod is not installed.</li>
 * </ol>
 *
 * <p>The date of the test world is never touched: the walker is handed a
 * leaf calendar made by hand, as in {@link BareBranchGameTests}. These
 * classes are left out of the released jar (see {@code build.gradle}).</p>
 */
@GameTestHolder(Foliage.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TreeYearGameTests {

    private static final SeasonSettings RULES = SeasonSettings.DEFAULT;
    private static final CalendarRules YEAR = CalendarRules.DEFAULT;

    /** How close two factors must be to count as the same. */
    private static final float CLOSE = 0.0005F;

    private TreeYearGameTests() {
    }

    // ------------------------------------------------------------------
    // Small helpers
    // ------------------------------------------------------------------

    /** The leaf calendar at the start of a day of a season (day 0 is the season's first). */
    private static LeafClock clockOn(Season season, int day) {
        return LeafClock.of(RULES, YEAR, LeafCalendarGameTests.stepOn(RULES, YEAR, season, day));
    }

    /** Hands the date and the settings back to the walker, and takes the test's tree away. */
    private static void tidy(@Nullable Tree tree) {
        LeafWalker.fixClock(null);
        LeafWalker.setSettings(FoliageConfig.walkSettings());
        if (tree != null) {
            tree.remove();
        }
    }

    /** Has the walker look at the chunk of a spot, right now and without limits. */
    private static int walk(ServerLevel level, BlockPos spot) {
        int swapped = LeafWalker.of(level).walkNow(new ChunkPos(spot));
        check(swapped >= 0, "the chunk of " + spot + " is in the walker's reach");
        return swapped;
    }

    /**
     * Has the walker look at every chunk a tree's crown reaches into,
     * right now and without limits. A crown does not stop at a chunk's
     * edge, and {@link #walk} looks at one chunk.
     */
    private static void walkAll(ServerLevel level, Tree tree) {
        Set<ChunkPos> chunks = new LinkedHashSet<>();
        for (BlockPos spot : crown(tree).keySet()) {
            chunks.add(new ChunkPos(spot));
        }
        for (ChunkPos chunk : chunks) {
            check(LeafWalker.of(level).walkNow(chunk) >= 0, "the chunk " + chunk + " is in the walker's reach");
        }
    }

    /** The leaf blocks and bare blocks of a tree, with their positions. */
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

    /**
     * A leaf block of a tree with nothing but air under it for at least
     * two blocks: a spot where fruit can hang, and where something that
     * lay on the leaf can fall when the leaf goes.
     *
     * <p>A crown of limbs in layers is closed like a dome: a leaf block
     * that is open to the sky has leaves under it, and one that is open
     * to the ground has leaves above it. When snow has to lie on the
     * leaf ({@code openAbove}) and no leaf block of the tree is open both
     * ways, the test makes such a spot: it takes the leaf blocks out from
     * under the outermost leaf block that is open to the sky. That
     * changes the test's tree, not what is tested (what the walker does
     * with the things on and under one leaf block).</p>
     *
     * @param openAbove whether there must be air above the leaf block too
     */
    private static BlockPos freeLeafOf(Tree tree, boolean openAbove) {
        ServerLevel level = tree.level;
        BlockPos best = null;
        int bestBelow = 0;
        BlockPos outermost = null;
        int outermostReach = -1;
        for (BlockPos at : crown(tree).keySet()) {
            if (openAbove && !level.getBlockState(at.above()).isAir()) {
                continue;
            }
            int free = 0;
            while (level.getBlockState(at.below(free + 1)).isAir()) {
                free++;
            }
            if (free > bestBelow) {
                bestBelow = free;
                best = at;
            }
            int reach = Math.max(Math.abs(at.getX() - tree.root.getX()), Math.abs(at.getZ() - tree.root.getZ()));
            if (reach > outermostReach) {
                outermostReach = reach;
                outermost = at;
            }
        }
        if (bestBelow >= 2) {
            return best;
        }
        check(openAbove && outermost != null, "the test's tree has a leaf block with air under it");
        // Make the spot: clear the leaves under the outermost leaf block that is open to the sky.
        // Flags 2 | 16: tell the clients, do not wake the neighbors (no leaf is to react to this).
        BlockPos under = outermost.below();
        while (level.getBlockState(under).getBlock() instanceof DynamicLeavesBlock) {
            level.setBlock(under, Blocks.AIR.defaultBlockState(), 2 | 16);
            under = under.below();
        }
        check(level.getBlockState(outermost.below(1)).isAir() && level.getBlockState(outermost.below(2)).isAir(),
                "there is air under the test's leaf block after the leaves under it were taken out, not wood");
        return outermost;
    }

    // ------------------------------------------------------------------
    // The season source, as arithmetic
    // ------------------------------------------------------------------

    /** Where there is no winter there are no seasons: every factor is 1, all year. */
    @GameTest(template = "empty", batch = "foliage_tree_year")
    public static void noWinterMeansNoSeasons(GameTestHelper helper) {
        for (Factor which : Factor.values()) {
            for (float season = 0.0F; season < 4.0F; season += 0.25F) {
                for (boolean inWinter : List.of(false, true)) {
                    float factor = TreeSeasons.factor(which, ClimateZoneType.NONE, season, inWinter, 0.0F);
                    check(Math.abs(factor - 1.0F) < CLOSE,
                            which + " is 1 where there is no winter, at season " + season + ": " + factor);
                }
            }
        }
        helper.succeed();
    }

    /** In winter a tree rests: nothing grows, no seed falls, no fruit comes. */
    @GameTest(template = "empty", batch = "foliage_tree_year")
    public static void treesRestInWinter(GameTestHelper helper) {
        for (ClimateZoneType climate : List.of(ClimateZoneType.TEMPERATE, ClimateZoneType.COLD)) {
            for (float season = 3.0F; season < 4.0F; season += 0.1F) {
                check(TreeSeasons.isWinter(season), season + " is in winter");
                for (Factor which : Factor.values()) {
                    float resting = TreeSeasons.factor(which, climate, season, false, 0.0F);
                    check(resting == 0.0F, which + " is 0 in winter in a " + climate + " place: " + resting);
                }
            }
            // Just before and just after winter it is not 0 everywhere.
            check(TreeSeasons.factor(Factor.SEEDS, climate, 2.0F, false, 0.0F) > 0.0F,
                    "seeds fall at the start of autumn in a " + climate + " place");
        }
        helper.succeed();
    }

    /** With the setting on, winter is like any other part of the year. */
    @GameTest(template = "empty", batch = "foliage_tree_year")
    public static void growthInWinterSwitchesTheRestOff(GameTestHelper helper) {
        boolean any = false;
        for (float season = 3.0F; season < 4.0F; season += 0.1F) {
            float growing = TreeSeasons.factor(Factor.GROWTH, ClimateZoneType.TEMPERATE, season, true, 0.0F);
            float resting = TreeSeasons.factor(Factor.GROWTH, ClimateZoneType.TEMPERATE, season, false, 0.0F);
            check(resting == 0.0F, "a tree rests in winter while the setting is off");
            any |= growing > 0.0F;
        }
        check(any, "with growthInWinter a tree still grows somewhere in winter");
        helper.succeed();
    }

    /** Growth follows the year: highest in summer, lowest in winter, and it comes and goes smoothly. */
    @GameTest(template = "empty", batch = "foliage_tree_year")
    public static void growthFollowsTheYear(GameTestHelper helper) {
        float summer = TreeSeasons.factor(Factor.GROWTH, ClimateZoneType.TEMPERATE, 1.5F, true, 0.0F);
        float spring = TreeSeasons.factor(Factor.GROWTH, ClimateZoneType.TEMPERATE, 0.5F, true, 0.0F);
        float autumn = TreeSeasons.factor(Factor.GROWTH, ClimateZoneType.TEMPERATE, 2.5F, true, 0.0F);
        float winter = TreeSeasons.factor(Factor.GROWTH, ClimateZoneType.TEMPERATE, 3.5F, true, 0.0F);
        check(Math.abs(summer - 1.0F) < CLOSE, "a tree grows at full speed in midsummer: " + summer);
        check(winter < spring && winter < autumn, "it grows slowest in midwinter: " + winter);
        check(spring > 0.0F && autumn > 0.0F, "it still grows in spring and autumn");
        check(winter == 0.0F, "it does not grow in midwinter at all: " + winter);
        // A cold place has a shorter year of growth than a temperate one.
        float coldSpring = TreeSeasons.factor(Factor.GROWTH, ClimateZoneType.COLD, 0.5F, true, 0.0F);
        check(coldSpring < spring, "spring in a cold place is slower than in a temperate one: " + coldSpring);
        // Fruit peaks in summer, as the hint on a seed says.
        float fruitSummer = TreeSeasons.factor(Factor.FRUIT, ClimateZoneType.TEMPERATE, 1.5F, true, 0.0F);
        float fruitSpring = TreeSeasons.factor(Factor.FRUIT, ClimateZoneType.TEMPERATE, 0.2F, true, 0.0F);
        check(fruitSummer > fruitSpring, "fruit comes more readily in summer than at the start of spring");
        helper.succeed();
    }

    /** A kind of tree whose year is shifted gets the shifted curve, but rests in the world's winter all the same. */
    @GameTest(template = "empty", batch = "foliage_tree_year")
    public static void theShiftOfAKindMovesItsCurveOnly(GameTestHelper helper) {
        // Late autumn, where the curve is on its way down and not at its
        // highest or lowest: there a shift shows.
        float plain = TreeSeasons.factor(Factor.SEEDS, ClimateZoneType.TEMPERATE, 2.4F, true, 0.0F);
        float shifted = TreeSeasons.factor(Factor.SEEDS, ClimateZoneType.TEMPERATE, 2.4F, true, 0.2F);
        float asIfLater = TreeSeasons.factor(Factor.SEEDS, ClimateZoneType.TEMPERATE, 2.6F, true, 0.0F);
        check(Math.abs(shifted - asIfLater) < CLOSE,
                "a shift of a fifth of a season answers as a fifth of a season later: " + shifted + " and " + asIfLater);
        check(plain - shifted > CLOSE, "and it makes a difference: " + plain + " against " + shifted);
        // In winter it rests, whatever its shift.
        check(TreeSeasons.factor(Factor.FRUIT, ClimateZoneType.TEMPERATE, 3.2F, false, 1.5F) == 0.0F,
                "a shifted kind rests in the world's winter too");
        // A shift that runs past the end of the year comes back at its start.
        check(Math.abs(TreeSeasons.wrap(3.5F + 1.0F) - 0.5F) < CLOSE, "the year wraps around");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // The season source, live
    // ------------------------------------------------------------------

    /** Dynamic Trees asks Foliage for the season, and gets the answers the arithmetic gives. */
    @GameTest(template = "empty", batch = "foliage_tree_year")
    public static void dynamicTreesAsksFoliage(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SeasonManager manager = SeasonCompatibilityHandler.getSeasonManager();
        check(manager instanceof TreeSeasons, "Dynamic Trees' season source is Foliage's, not " + manager.getClass());
        BlockPos spot = helper.absolutePos(new BlockPos(1, 0, 1));

        // The season is the Calendar's, on Dynamic Trees' scale.
        Calendar calendar = Calendar.of(level);
        Season season = calendar.date().season();
        Float value = manager.getSeasonValue(level, spot);
        check(value != null, "Foliage gives Dynamic Trees a season");
        check(value >= season.ordinal() && value < season.ordinal() + 1.0F,
                "the season number " + value + " lies in " + season);

        // The climate is Seasons' winter rule plus the biome's temperature.
        // The biome is read the way the game stores it, in cubes of 4 by 4
        // by 4 blocks, which is the same reading the leaf blocks go by: a
        // tree must never rest in a wood whose leaves stay.
        ClimateZoneType climate = manager.getClimate(level, spot);
        boolean winterHere = SeasonsApi.hasWinter(level.getNoiseBiome(
                QuartPos.fromBlock(spot.getX()), QuartPos.fromBlock(spot.getY()),
                QuartPos.fromBlock(spot.getZ())).value());
        check(winterHere == (climate != ClimateZoneType.NONE),
                "the place has seasons (" + climate + ") exactly where Seasons says there is a winter (" + winterHere + ")");
        check(climate != ClimateZoneType.TROPICAL && climate != ClimateZoneType.ARID,
                "Foliage never answers with a wet-and-dry climate: " + climate);

        // And the three factors are the arithmetic's.
        boolean inWinter = FoliageConfig.growthInWinter();
        check(close(manager.getGrowthFactor(level, spot, 0.0F),
                        TreeSeasons.factor(Factor.GROWTH, climate, value, inWinter, 0.0F)),
                "growth is what the rule says");
        check(close(manager.getSeedDropFactor(level, spot, 0.0F),
                        TreeSeasons.factor(Factor.SEEDS, climate, value, inWinter, 0.0F)),
                "seed drop is what the rule says");
        check(close(manager.getFruitProductionFactor(level, spot, 0.5F),
                        TreeSeasons.factor(Factor.FRUIT, climate, value, inWinter, 0.5F)),
                "fruit is what the rule says, with a shift too");

        // Nothing of this is stored, so it may be asked from any thread.
        manager.flushMappings();
        manager.clearCache(level);
        check(close(manager.getGrowthFactor(level, spot, 0.0F),
                        TreeSeasons.factor(Factor.GROWTH, climate, value, inWinter, 0.0F)),
                "the answer does not change when Dynamic Trees empties its memory");
        helper.succeed();
    }

    private static boolean close(float one, float other) {
        return Math.abs(one - other) < CLOSE;
    }

    // ------------------------------------------------------------------
    // Fruit and snow
    // ------------------------------------------------------------------

    /** Fruit that hangs under a leaf block drops off when that spot goes bare. */
    @GameTest(template = "empty", batch = "foliage_tree_year", timeoutTicks = 400)
    public static void fruitDropsWhenItsLeafGoesBare(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Tree tree = Tree.plant(helper, "dynamictrees:apple_oak", Tree.storedCode("dynamictrees:apple_oak", 0));
        try {
            BlockPos leaf = freeLeafOf(tree, false);
            BlockPos under = leaf.below();
            // An apple, as Dynamic Trees hangs one: right under a leaf of the tree.
            Fruit apple = Fruit.REGISTRY.get(ResourceLocation.parse("dynamictrees:apple"));
            check(apple != null && apple != Fruit.NULL, "Dynamic Trees has the apple");
            apple.place(level, under, 1.5F);
            BlockState young = level.getBlockState(under);
            check(young.getBlock() instanceof FruitBlock, "an apple hangs under the leaf block");
            // Ripe, so that there is something to fall: an apple that is
            // not ripe yet gives nothing when it comes off, as in the game.
            level.setBlock(under, young.setValue(apple.getAgeProperty(), apple.getMaxAge()), 3);

            long droppedBefore = LeafWalker.of(level).counters()[LeafWalker.FRUIT_DROPPED];
            LeafWalker.fixClock(clockOn(Season.WINTER, 1));
            LeafWalker.setSettings(new WalkSettings(true, 4096, 65536, 50.0));
            walk(level, leaf);

            check(level.getBlockState(leaf).getBlock() instanceof BareLeavesBlock, "the leaf spot is bare now");
            check(!(level.getBlockState(under).getBlock() instanceof FruitBlock),
                    "the apple is gone from under it: " + level.getBlockState(under));
            long dropped = LeafWalker.of(level).counters()[LeafWalker.FRUIT_DROPPED] - droppedBefore;
            check(dropped >= 1, "the walker counted the fruit it dropped: " + dropped);
            // It dropped as an item, it did not simply vanish.
            List<?> items = level.getEntities(EntityType.ITEM, new AABB(under).inflate(3.0), item -> true);
            check(!items.isEmpty(), "the apple lies on the ground");
            tidy(tree);
            helper.succeed();
        } catch (RuntimeException | Error failed) {
            tidy(tree);
            throw failed;
        }
    }

    /**
     * Snow that lay on a leaf block falls to what is under it when the spot goes bare.
     *
     * <p>Under the open sky and in a batch of its own: the tree has to
     * stand whole to have a leaf block that is open to the sky (under a
     * test's lid a tree is cut off, and every leaf block left has the lid
     * or a leaf above it), and a whole crown reaches over the test areas
     * next to it.</p>
     */
    @GameTest(template = "empty", batch = "foliage_tree_year_snow", timeoutTicks = 400, skyAccess = true)
    public static void snowFallsWithTheLeaves(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Tree tree = Tree.plant(helper, "dynamictrees:oak", Tree.storedCode("dynamictrees:oak", 0));
        try {
            BlockPos leaf = freeLeafOf(tree, true);
            BlockPos above = leaf.above();
            level.setBlock(above, Blocks.SNOW.defaultBlockState(), 3);
            check(level.getBlockState(above).getBlock() instanceof SnowLayerBlock, "snow lies on the leaf block");
            // Where the snow should come to rest: the first block below that stops it.
            BlockPos landing = leaf.below();
            while (level.getBlockState(landing).isAir() && landing.getY() > level.getMinBuildHeight()) {
                landing = landing.below();
            }
            landing = landing.above();

            long landedBefore = LeafWalker.of(level).counters()[LeafWalker.SNOW_LANDED];
            LeafWalker.fixClock(clockOn(Season.WINTER, 1));
            LeafWalker.setSettings(new WalkSettings(true, 4096, 65536, 50.0));
            walk(level, leaf);

            check(level.getBlockState(leaf).getBlock() instanceof BareLeavesBlock, "the leaf spot is bare now");
            check(level.getBlockState(above).isAir(), "the snow is gone from where the leaf was");
            long landed = LeafWalker.of(level).counters()[LeafWalker.SNOW_LANDED] - landedBefore;
            check(landed == 1, "the walker counted one layer of snow that came down: " + landed);
            check(level.getBlockState(landing).getBlock() instanceof SnowLayerBlock,
                    "the snow lies on " + landing + " now: " + level.getBlockState(landing));
            tidy(tree);
            helper.succeed();
        } catch (RuntimeException | Error failed) {
            tidy(tree);
            throw failed;
        }
    }

    /**
     * Fruit that Dynamic Trees hangs under a block that is already bare is
     * taken away in the next tick, and quietly: it has only just set, so
     * there is nothing to drop.
     */
    @GameTest(template = "empty", batch = "foliage_tree_year_slow", timeoutTicks = 600)
    public static void fruitUnderABareBlockIsTakenAway(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Tree tree = Tree.plant(helper, "dynamictrees:apple_oak", Tree.storedCode("dynamictrees:apple_oak", 0));
        try {
            BlockPos leaf = freeLeafOf(tree, false);
            BlockPos under = leaf.below();
            LeafWalker.fixClock(clockOn(Season.WINTER, 1));
            LeafWalker.setSettings(new WalkSettings(true, 4096, 65536, 50.0));
            // The whole crown goes bare now, in every chunk it reaches
            // into. The walker's own round over the chunks in reach comes
            // by in the ticks this test waits, and whatever it still found
            // to swap then would drop the apples the tree grew by itself,
            // right next to the one apple that is watched here.
            walkAll(level, tree);
            check(level.getBlockState(leaf).getBlock() instanceof BareLeavesBlock, "the leaf spot is bare");

            long droppedBefore = LeafWalker.of(level).counters()[LeafWalker.FRUIT_DROPPED];
            // Apples the tree had grown by itself came down with its
            // leaves just now and lie about; only what comes on top of
            // them counts.
            AABB around = new AABB(under).inflate(3.0);
            int lyingBefore = level.getEntities(EntityType.ITEM, around, item -> true).size();
            Fruit apple = Fruit.REGISTRY.get(ResourceLocation.parse("dynamictrees:apple"));
            check(apple != null && apple != Fruit.NULL, "Dynamic Trees has the apple");
            // As Dynamic Trees does it, which hangs fruit under any leaf of
            // the tree; a bare block is a leaf to it.
            apple.place(level, under, 1.5F);
            check(level.getBlockState(under).getBlock() instanceof FruitBlock,
                    "an apple hangs under the bare block to begin with");

            helper.runAfterDelay(2, () -> {
                try {
                    check(!(level.getBlockState(under).getBlock() instanceof FruitBlock),
                            "the walker took it away: " + level.getBlockState(under));
                    int lying = level.getEntities(EntityType.ITEM, around, item -> true).size();
                    check(lying <= lyingBefore, "and nothing fell from it, because it had only just set: "
                            + lyingBefore + " items lay about before, " + lying + " after");
                    check(LeafWalker.of(level).counters()[LeafWalker.FRUIT_DROPPED] == droppedBefore,
                            "it does not count as fruit that came off a crown either");
                    tidy(tree);
                    helper.succeed();
                } catch (RuntimeException | Error failed) {
                    tidy(tree);
                    throw failed;
                }
            });
        } catch (RuntimeException | Error failed) {
            tidy(tree);
            throw failed;
        }
    }

    /**
     * Snow from a leaf that goes bare falls past the bare blocks under it
     * instead of stopping on one. Nothing rests on a bare block, so snow
     * that stayed there would hang in the air.
     */
    @GameTest(template = "empty", batch = "foliage_tree_year", timeoutTicks = 400)
    public static void snowFallsPastABareBlock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Tree tree = Tree.plant(helper, "dynamictrees:oak", Tree.storedCode("dynamictrees:oak", 0));
        try {
            LeafWalker.fixClock(clockOn(Season.WINTER, 1));
            LeafWalker.setSettings(new WalkSettings(true, 4096, 65536, 50.0));
            // The whole crown goes bare first, in every chunk it reaches
            // into, so that nothing else moves while the one spot is
            // watched, and so that the spot picked is known to be one that
            // sheds. The root's own chunk alone is not enough: a tree that
            // lands at a chunk's edge has most of its crown next door, and
            // what is left may hold no pair to pick.
            walkAll(level, tree);
            BlockPos spot = topOfABarePair(tree);
            if (spot == null) {
                describeCrown(tree);
            }
            check(spot != null, "the test's tree has two bare spots one above the other");
            BlockState bareState = level.getBlockState(spot);

            // The column around the pair is cleared, so that there is one
            // place the snow can be laid and one place it can come to
            // rest: air over the pair for the snow, air under the pair for
            // it to fall into, and a stone floor under that. Then the leaf
            // block goes back at the upper spot of the pair, with snow on
            // it, and the lower spot stays bare, in the way.
            //
            // Every write here is quiet (nothing is told that its neighbor
            // changed and no shape is asked again), so that nothing but
            // the walker moves anything, and none of them goes over wood
            // (the pair was picked for that). The tree is thrown away at
            // the end of the test.
            Block leafTwin = Twins.twinOf(bareState.getBlock());
            check(leafTwin != null, "the bare block has a leaf twin");
            put(level, spot.above(), Blocks.AIR.defaultBlockState());
            put(level, spot.below(2), Blocks.AIR.defaultBlockState());
            put(level, spot.below(3), Blocks.STONE.defaultBlockState());
            put(level, spot, Twins.swap(bareState, leafTwin));
            put(level, spot.above(), Blocks.SNOW.defaultBlockState());
            check(level.getBlockState(spot.above()).getBlock() instanceof SnowLayerBlock, "snow lies on the leaf block");
            check(level.getBlockState(spot).getBlock() instanceof DynamicLeavesBlock
                            && !(level.getBlockState(spot).getBlock() instanceof BareLeavesBlock),
                    "on a leaf block: " + level.getBlockState(spot));
            check(level.getBlockState(spot.below()).getBlock() instanceof BareLeavesBlock,
                    "a bare block stands under it: " + level.getBlockState(spot.below()));
            check(level.getBlockState(spot.below(2)).isAir(), "with air under that");

            long landedBefore = LeafWalker.of(level).counters()[LeafWalker.SNOW_LANDED];
            check(walk(level, spot) == 1, "the one leaf block went bare again");
            long landed = LeafWalker.of(level).counters()[LeafWalker.SNOW_LANDED] - landedBefore;

            check(level.getBlockState(spot.above()).isAir(), "the snow left the spot it lay on");
            check(!(level.getBlockState(spot).getBlock() instanceof SnowLayerBlock),
                    "it did not stop where the leaf was");
            check(level.getBlockState(spot.below()).getBlock() instanceof BareLeavesBlock,
                    "the bare block under it is still there: " + level.getBlockState(spot.below()));
            check(!(level.getBlockState(spot.below()).getBlock() instanceof SnowLayerBlock),
                    "and the snow did not come to rest on it");
            check(level.getBlockState(spot.below(2)).getBlock() instanceof SnowLayerBlock,
                    "it fell past it and lies on the floor two below: " + level.getBlockState(spot.below(2)));
            check(landed == 1, "the walker counted one layer of snow that came down: " + landed);
            tidy(tree);
            helper.succeed();
        } catch (RuntimeException | Error failed) {
            tidy(tree);
            throw failed;
        }
    }

    /**
     * Writes a block into the world quietly: the players are told and
     * nothing else is. No neighbor hears of it and no shape is asked
     * again, so a tree does not react to scaffolding a test puts up
     * around it ({@code Block.UPDATE_KNOWN_SHAPE} is what stops the shape
     * updates, which a plain {@code UPDATE_CLIENTS} still sends).
     */
    private static void put(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }

    /** Says in the log what the crown looks like, when a test cannot find the spot it wants in it. */
    private static void describeCrown(Tree tree) {
        int all = 0;
        int bare = 0;
        int airAbove = 0;
        int bareBelow = 0;
        for (Map.Entry<BlockPos, BlockState> spot : crown(tree).entrySet()) {
            BlockPos at = spot.getKey();
            all++;
            if (!(spot.getValue().getBlock() instanceof BareLeavesBlock)) {
                continue;
            }
            bare++;
            if (tree.level.getBlockState(at.above()).isAir()) {
                airAbove++;
            }
            if (tree.level.getBlockState(at.below()).getBlock() instanceof BareLeavesBlock) {
                bareBelow++;
            }
        }
        Foliage.LOGGER.info("Foliage test: the crown holds {} spots, {} of them bare, "
                        + "of which {} have air above and {} have a bare block below",
                all, bare, airAbove, bareBelow);
    }

    /**
     * The upper of two spots of a tree that stand one above the other and
     * have both shed their leaves: a spot with a bare block in the way
     * right below it.
     *
     * <p>Three things are asked of the pair, and each one keeps the test
     * from depending on where in the world the plot lands:</p>
     * <ul>
     *   <li>Both hold a bare block, which proves they are spots that shed
     *       and that stand where there is a winter. So the walker will
     *       swap the upper one back to bare and will leave the lower one
     *       alone, whatever the biome under the plot is.</li>
     *   <li>They stand one above the other, so they are in the same
     *       chunk, and a walk of that one chunk reaches both. The test
     *       has walked every chunk of the crown before it asks, so the
     *       pair may be anywhere in the crown.</li>
     *   <li>The spot above the pair and the two spots under it hold air
     *       or leaves, never wood: the test writes over those three, and
     *       a branch that is written over takes its part of the tree
     *       down with it ({@link #airOrLeaves}).</li>
     * </ul>
     *
     * <p>Air around the pair is not asked for, and the test makes the air
     * it needs itself. Asking for a spot with air over it leaves next to
     * nothing to choose from: {@link #crown} looks no higher than
     * {@code Tree.HEIGHT} above the root, so of the 83 spots of a young
     * oak only two come back with air over them, and neither of those has
     * a spot under it.</p>
     */
    @Nullable
    private static BlockPos topOfABarePair(Tree tree) {
        for (Map.Entry<BlockPos, BlockState> spot : crown(tree).entrySet()) {
            BlockPos at = spot.getKey();
            if (spot.getValue().getBlock() instanceof BareLeavesBlock
                    && tree.level.getBlockState(at.below()).getBlock() instanceof BareLeavesBlock
                    && at.getY() - 3 > tree.level.getMinBuildHeight()
                    && airOrLeaves(tree.level, at.above())
                    && airOrLeaves(tree.level, at.below(2))
                    && airOrLeaves(tree.level, at.below(3))) {
                return at;
            }
        }
        return null;
    }

    /**
     * Whether a spot holds air or a leaf block (bare ones too): a spot a
     * test may write over without the tree taking notice. Wood is not
     * such a spot: Dynamic Trees answers a branch that is taken away,
     * however quietly, by breaking off everything that branch held
     * ({@code BranchBlock.onRemove}), crown and all.
     */
    private static boolean airOrLeaves(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.getBlock() instanceof DynamicLeavesBlock;
    }

    // ------------------------------------------------------------------
    // The shortcut over land that is already bare
    // ------------------------------------------------------------------

    /** A second look at a crown that is already bare counts its sections and searches none. */
    @GameTest(template = "empty", batch = "foliage_tree_year", timeoutTicks = 400)
    public static void aBareCrownIsNotSearchedTwice(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Tree tree = Tree.plant(helper, "dynamictrees:oak", Tree.storedCode("dynamictrees:oak", 0));
        try {
            LeafWalker walker = LeafWalker.of(level);
            LeafWalker.fixClock(clockOn(Season.WINTER, 1));
            LeafWalker.setSettings(new WalkSettings(true, 4096, 65536, 50.0));
            int swapped = walk(level, tree.root);
            check(swapped > 0, "the crown went bare: " + swapped + " blocks");

            long[] before = walker.counters();
            int again = walk(level, tree.root);
            long[] after = walker.counters();
            check(again == 0, "the second look swaps nothing: " + again);
            check(after[LeafWalker.SECTIONS_COUNTED] > before[LeafWalker.SECTIONS_COUNTED],
                    "it counted the blocks of the sections: "
                            + (after[LeafWalker.SECTIONS_COUNTED] - before[LeafWalker.SECTIONS_COUNTED]));
            check(after[LeafWalker.SECTIONS_SEARCHED] == before[LeafWalker.SECTIONS_SEARCHED],
                    "and searched none of them block by block: "
                            + (after[LeafWalker.SECTIONS_SEARCHED] - before[LeafWalker.SECTIONS_SEARCHED]));
            tidy(tree);
            helper.succeed();
        } catch (RuntimeException | Error failed) {
            tidy(tree);
            throw failed;
        }
    }

    /** How many looks at a chunk are timed in one go, and how many goes are made; the fastest go counts. */
    private static final int LOOKS = 100;
    private static final int GOES = 7;

    /**
     * What the shortcut is worth, measured where nothing else is going
     * on: the same chunk with the same crown is looked at over and over,
     * first on a day in the middle of the fall, when every section with
     * leaves in it has to be searched block by block, and then in winter,
     * when its blocks are only counted. Nothing is swapped in either, so
     * what is timed is the looking alone.
     *
     * <p>The numbers go to the log, and from there into
     * {@code docs/measurements.md}. What is checked is what the counters
     * say (searched in the fall, only counted in winter) and that the
     * counting is the cheaper of the two; by how much is written down,
     * not demanded, because a time is a matter of the machine.</p>
     */
    @GameTest(template = "empty", batch = "foliage_tree_year", timeoutTicks = 400)
    public static void countingASectionCostsLessThanSearchingIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Tree tree = Tree.plant(helper, "dynamictrees:oak", Tree.storedCode("dynamictrees:oak", 0));
        try {
            LeafWalker walker = LeafWalker.of(level);
            LeafWalker.setSettings(new WalkSettings(true, 4096, 65536, 50.0));
            ChunkPos chunk = new ChunkPos(tree.root);

            // The middle of the fall: part of the crown is bare, part is
            // not, and which is which is asked of every single block.
            int autumn = YEAR.seasonLength(Season.AUTUMN);
            LeafClock fall = clockOn(Season.AUTUMN, autumn - Math.max(1, (int) RULES.fallDays() / 2));
            check(fall.falling() && !fall.allFull() && !fall.allBare(), "the day picked is in the middle of the fall");
            LeafWalker.fixClock(fall);
            int shed = walk(level, tree.root);
            check(shed > 0, "part of the crown has shed by then: " + shed + " blocks");
            check(walk(level, tree.root) == 0, "a second look on the same day swaps nothing");
            long[] searching = timed(walker, chunk);
            check(searching[1] > 0, "in the fall the sections are searched block by block: " + searching[1] + " per look");

            // Winter: every spot that sheds is bare, and a count of the
            // section's blocks by kind shows that at once.
            LeafWalker.fixClock(clockOn(Season.WINTER, 1));
            check(walk(level, tree.root) > 0, "the rest of the crown goes bare in winter");
            check(walk(level, tree.root) == 0, "a second look in winter swaps nothing");
            long[] counting = timed(walker, chunk);
            check(counting[1] == 0, "in winter no section is searched block by block: " + counting[1] + " per look");
            check(counting[2] > 0, "they are counted instead: " + counting[2] + " per look");

            Foliage.LOGGER.info(String.format(Locale.ROOT,
                    "Foliage test: a look at a chunk with an oak's crown in it takes %.1f millionths of a second "
                            + "when the sections with leaves in them are searched block by block (a day in the fall, "
                            + "%d searched per look) and %.1f when their blocks are only counted (winter, %d counted "
                            + "per look): %.1f times less",
                    searching[0] / 1000.0, searching[1], counting[0] / 1000.0, counting[2],
                    searching[0] / (double) Math.max(1L, counting[0])));
            check(counting[0] < searching[0], "counting is the cheaper of the two: " + counting[0]
                    + " against " + searching[0] + " billionths of a second per look");
            tidy(tree);
            helper.succeed();
        } catch (RuntimeException | Error failed) {
            tidy(tree);
            throw failed;
        }
    }

    /**
     * Looks at one chunk {@value #LOOKS} times in a row, {@value #GOES}
     * times over, and gives back what the fastest of those goes took per
     * look, in billionths of a second, with how many sections a look
     * searched block by block and how many it counted. The fastest go
     * stands, because whatever else the machine was doing can only have
     * made a go slower.
     */
    private static long[] timed(LeafWalker walker, ChunkPos chunk) {
        long best = Long.MAX_VALUE;
        long[] before = walker.counters();
        for (int go = 0; go < GOES; go++) {
            long started = System.nanoTime();
            for (int look = 0; look < LOOKS; look++) {
                walker.walkNow(chunk);
            }
            best = Math.min(best, System.nanoTime() - started);
        }
        long[] after = walker.counters();
        long looks = (long) GOES * LOOKS;
        return new long[] {best / LOOKS,
                (after[LeafWalker.SECTIONS_SEARCHED] - before[LeafWalker.SECTIONS_SEARCHED]) / looks,
                (after[LeafWalker.SECTIONS_COUNTED] - before[LeafWalker.SECTIONS_COUNTED]) / looks};
    }

    // ------------------------------------------------------------------
    // The way out
    // ------------------------------------------------------------------

    /** The sweep of /foliage restore turns a bare crown back into leaves. */
    @GameTest(template = "empty", batch = "foliage_tree_year", timeoutTicks = 400)
    public static void theSweepRestoresLeaves(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Tree tree = Tree.plant(helper, "dynamictrees:oak", Tree.storedCode("dynamictrees:oak", 0));
        try {
            LeafWalker walker = LeafWalker.of(level);
            LeafWalker.fixClock(clockOn(Season.WINTER, 1));
            LeafWalker.setSettings(new WalkSettings(true, 4096, 65536, 50.0));
            check(walk(level, tree.root) > 0, "the crown went bare");
            List<BlockPos> bare = new ArrayList<>();
            for (Map.Entry<BlockPos, BlockState> spot : crown(tree).entrySet()) {
                if (spot.getValue().getBlock() instanceof BareLeavesBlock) {
                    bare.add(spot.getKey());
                }
            }
            check(!bare.isEmpty(), "the crown holds bare blocks");

            // As /foliage restore does it: bare blocks off, then the sweep.
            LeafWalker.setSettings(new WalkSettings(false, 4096, 65536, 50.0));
            LevelChunk chunk = level.getChunk(tree.root.getX() >> 4, tree.root.getZ() >> 4);
            // Marked as written, so that the check below can fail: a chunk
            // that has only just been built is waiting to be written anyway.
            chunk.setUnsaved(false);
            check(!chunk.isUnsaved(), "the chunk counts as written to disk before the sweep");
            int restored = walker.sweep(chunk);
            check(restored > 0, "the sweep turned blocks back into leaves: " + restored);
            for (BlockPos spot : bare) {
                BlockState state = level.getBlockState(spot);
                check(state.getBlock() instanceof DynamicLeavesBlock && !(state.getBlock() instanceof BareLeavesBlock),
                        "the spot " + spot + " holds a leaf block again: " + state);
            }
            check(chunk.isUnsaved(), "the sweep marked the chunk to be written to disk again");
            tidy(tree);
            helper.succeed();
        } catch (RuntimeException | Error failed) {
            tidy(tree);
            throw failed;
        }
    }

    /**
     * Every branch of the command is answered and none of them throws.
     * Nothing is started here: {@link #theRestoreCommandRunsAndStops} does
     * that.
     */
    @GameTest(template = "empty", batch = "foliage_tree_year")
    public static void theCommandAnswers(GameTestHelper helper) {
        check(!Restore.isRunning(), "no restore is running");
        check(Restore.progress() == null, "and there is nothing to say about one");
        // Every one of these must be understood and must answer. A command
        // that Brigadier does not know, or that throws, says nothing at all
        // and is caught by the count.
        for (String line : List.of("foliage status", "foliage restore status", "foliage restore stop")) {
            check(said(helper, line) >= 1, "/" + line + " answers");
        }
        helper.succeed();
    }

    /**
     * Runs the command, says what it answered and gives back how many
     * lines it said. A command Brigadier does not understand, or one that
     * throws, says nothing.
     */
    private static int said(GameTestHelper helper, String line) {
        MinecraftServer server = helper.getLevel().getServer();
        int[] lines = {0};
        CommandSourceStack source = new CommandSourceStack(new CommandSource() {
            @Override
            public void sendSystemMessage(Component message) {
                lines[0]++;
                Foliage.LOGGER.info("Foliage test: /{} said: {}", line, message.getString());
            }

            @Override
            public boolean acceptsSuccess() {
                return true;
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return false;
            }
        }, Vec3.atLowerCornerOf(helper.absolutePos(BlockPos.ZERO)), Vec2.ZERO, helper.getLevel(),
                Commands.LEVEL_OWNERS, "Foliage test", Component.literal("Foliage test"), server, null);
        server.getCommands().performPrefixedCommand(source, line);
        return lines[0];
    }

    /**
     * The whole way out, as a player walks it: the sweep starts, says how
     * far it is, refuses to have the bare blocks switched back on while it
     * runs, and stops.
     *
     * <p>It is stopped before the server has ticked once, so no chunk is
     * visited: what the sweep does to a chunk is tested by
     * {@link #theSweepRestoresLeaves}. What is tested here is the command
     * and the state around it.</p>
     *
     * <p>In a batch of its own, because it writes the settings file, and
     * a settings file that is written again hands the walker new settings
     * from under any test that is in the middle of something.</p>
     */
    @GameTest(template = "empty", batch = "foliage_tree_year_command", timeoutTicks = 600)
    public static void theRestoreCommandRunsAndStops(GameTestHelper helper) {
        check(!Restore.isRunning(), "no restore is running to begin with");
        boolean bareBefore = FoliageConfig.BARE_BLOCKS.get();
        try {
            check(said(helper, "foliage restore 1") >= 1, "/foliage restore 1 starts the sweep and says so");
            check(Restore.isRunning(), "the sweep is running now");
            check(!FoliageConfig.BARE_BLOCKS.get(), "it switched the bare blocks off first, or it would undo itself");
            Restore.Progress at = Restore.progress();
            check(at != null, "it says how far it is");
            check(at.regions() >= 0 && at.chunksVisited() == 0, "nothing is visited before the first tick: " + at);
            check(said(helper, "foliage restore status") >= 1, "/foliage restore status answers while it runs");

            // Switching the bare blocks back on now would leave half a
            // world either way, so the command refuses.
            check(said(helper, "foliage bare on") >= 1, "/foliage bare on answers");
            check(!FoliageConfig.BARE_BLOCKS.get(), "and it refused: the bare blocks are still off");

            check(said(helper, "foliage restore") >= 1, "a second /foliage restore answers");
            check(Restore.isRunning(), "and the first sweep is still the one running");

            check(said(helper, "foliage restore stop") >= 1, "/foliage restore stop answers");
            check(!Restore.isRunning(), "the sweep has stopped");
            check(Restore.progress() == null, "and there is nothing to say about one any more");

            // Now it may be switched back on.
            check(said(helper, "foliage bare on") >= 1, "/foliage bare on answers again");
            check(FoliageConfig.BARE_BLOCKS.get(), "and this time it switched them on");
            helper.succeed();
        } finally {
            Restore.stop();
            if (FoliageConfig.SPEC.isLoaded() && FoliageConfig.BARE_BLOCKS.get() != bareBefore) {
                FoliageConfig.BARE_BLOCKS.set(bareBefore);
                FoliageConfig.SPEC.save();
            }
        }
    }

    /**
     * The sweep reads a region file's table of contents right: a region
     * file written by hand here, with three of its 1,024 chunks marked as
     * written, gives those three chunks and no others.
     *
     * <p>This is the one thing the sweep cannot be shown doing in a test
     * world, because a test world has no land nobody has visited. It is
     * also the thing that decides whether the sweep makes new land: a
     * chunk that no entry names is never asked for.</p>
     */
    @GameTest(template = "empty", batch = "foliage_tree_year")
    public static void theRegionHeaderNamesTheChunksThatWereWritten(GameTestHelper helper) {
        // r.X.Z.mca, and nothing else, is a region file.
        check(sameInts(Restore.parseRegionName("r.0.0.mca"), 0, 0), "r.0.0.mca is region 0, 0");
        check(sameInts(Restore.parseRegionName("r.2.-3.mca"), 2, -3), "r.2.-3.mca is region 2, -3");
        for (String notARegion : List.of("level.dat", "r.mca", "r.0.mca", "r.0.0.0.mca", "r.x.0.mca", "c.0.0.mca")) {
            check(Restore.parseRegionName(notARegion) == null, notARegion + " is not a region file");
        }

        Path folder = null;
        try {
            folder = Files.createTempDirectory("foliage-region-test");
            // The first 4,096 bytes are 1,024 entries of four bytes, one per
            // chunk of the region, in rows of 32: three bytes of offset and
            // one of length, all zero for a chunk that was never written.
            byte[] header = new byte[4096];
            for (int index : new int[] {0, 5 + 3 * 32, 1023}) {
                header[index * 4 + 2] = 2;      // somewhere in the file
                header[index * 4 + 3] = 1;      // one sector long
            }
            Path file = folder.resolve("r.2.-3.mca");
            Files.write(file, header);

            long[] written = Restore.chunksWrittenIn(file);
            check(written.length == 3, "three chunks of the region were written: " + written.length);
            List<Long> found = new ArrayList<>();
            for (long pos : written) {
                found.add(pos);
            }
            // Region 2, -3 starts at chunk 64, -96.
            for (int[] expected : List.of(new int[] {64, -96}, new int[] {69, -93}, new int[] {95, -65})) {
                long pos = ChunkPos.asLong(expected[0], expected[1]);
                check(found.contains(pos), "chunk " + expected[0] + ", " + expected[1] + " is named: " + found);
            }

            // A header of nothing but zeros names no chunk at all.
            Path empty = folder.resolve("r.0.0.mca");
            Files.write(empty, new byte[4096]);
            check(Restore.chunksWrittenIn(empty).length == 0, "a region nothing was ever written in names no chunk");

            // A file that is not there, and one whose name is not a
            // region's, give nothing and throw nothing.
            check(Restore.chunksWrittenIn(folder.resolve("r.9.9.mca")).length == 0, "a region file that is not there");
            check(Restore.chunksWrittenIn(file.resolveSibling("level.dat")).length == 0, "a file that is not a region");
            helper.succeed();
        } catch (IOException e) {
            throw failure("the test could not write a region file: " + e);
        } finally {
            if (folder != null) {
                try (Stream<Path> inside = Files.walk(folder)) {
                    inside.sorted(Comparator.reverseOrder()).forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                            // A leftover file in the system's temporary folder is harmless.
                        }
                    });
                } catch (IOException ignored) {
                    // Same.
                }
            }
        }
    }

    /** Whether a pair of numbers read from a region's name is the pair expected. */
    private static boolean sameInts(@Nullable int[] found, int x, int z) {
        return found != null && found.length == 2 && found[0] == x && found[1] == z;
    }

    /** The leaf calendar knows when leaves are falling, which is when leaves drift down in a player's game. */
    @GameTest(template = "empty", batch = "foliage_tree_year")
    public static void leavesFallInAutumnOnly(GameTestHelper helper) {
        check(!clockOn(Season.SUMMER, 3).falling(), "nothing falls in summer");
        check(!clockOn(Season.WINTER, 3).falling(), "nothing falls in winter");
        check(!clockOn(Season.SPRING, 1).falling(), "nothing falls while the leaves come back");
        int autumn = YEAR.seasonLength(Season.AUTUMN);
        check(!clockOn(Season.AUTUMN, 0).falling(), "nothing falls at the start of autumn");
        boolean fell = false;
        for (int day = autumn - (int) RULES.fallDays(); day < autumn; day++) {
            fell |= clockOn(Season.AUTUMN, day).falling();
        }
        check(fell, "leaves fall in the last days of autumn");
        helper.succeed();
    }

    /**
     * Without Distant Horizons installed the link to it sleeps: it is not
     * at work, and putting it in place again does nothing and throws
     * nothing. The link itself can only be tried out with the mod
     * installed, on a real machine; here it is proved that a game without
     * it is untouched.
     */
    @GameTest(template = "empty", batch = "foliage_tree_year")
    public static void distantHorizonsSleepsWithoutTheMod(GameTestHelper helper) {
        check(!ModList.get().isLoaded(DistantHorizons.MOD_ID), "Distant Horizons is not installed in the test game");
        check(!DistantHorizons.linked(), "so the link is not at work");
        DistantHorizons.init();
        check(!DistantHorizons.linked(), "and putting it in place again leaves it asleep");
        helper.succeed();
    }
}
