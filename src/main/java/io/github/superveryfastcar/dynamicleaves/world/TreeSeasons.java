package io.github.superveryfastcar.dynamicleaves.world;

import com.dtteam.dynamictrees.api.season.ClimateZoneType;
import com.dtteam.dynamictrees.api.season.SeasonGrowthCalculator;
import com.dtteam.dynamictrees.api.season.SeasonManager;
import com.dtteam.dynamictrees.config.DTConfigs;
import com.dtteam.dynamictrees.systems.season.ActiveSeasonGrowthCalculator;
import com.dtteam.dynamictrees.systems.season.SeasonCompatibilityHandler;
import io.github.superveryfastcar.calendar.api.Calendar;
import io.github.superveryfastcar.calendar.api.CalendarDate;
import io.github.superveryfastcar.calendar.api.Season;
import io.github.superveryfastcar.dynamicleaves.DynamicLeaves;
import io.github.superveryfastcar.dynamicleaves.DynamicLeavesConfig;
import io.github.superveryfastcar.seasons.api.SeasonsApi;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.fml.ModList;

/**
 * Tells Dynamic Trees what the season is, so that its trees rest in
 * winter and bear fruit in its season.
 *
 * <h2>What Dynamic Trees asks</h2>
 * Dynamic Trees has one place where a season mod signs in. Whoever is
 * signed in is asked, for a place in the world:
 * <ul>
 *   <li><b>The season</b> as a number from 0 to 4: 0 at the start of
 *       spring, 1 at the start of summer, 2 of autumn, 3 of winter. Taken
 *       here from the Calendar mod, the same date everything else in the
 *       pack goes by.</li>
 *   <li><b>The climate</b> of the place: whether it has seasons at all,
 *       and if so, whether it is a cold one. Taken here from Seasons,
 *       which decides where there is a winter
 *       ({@link SeasonsApi#hasWinter}), and from the biome's own
 *       temperature.</li>
 *   <li><b>Three factors</b>, each from 0 to 1: how fast trees grow, how
 *       readily they drop seeds, and how readily they bear fruit. A
 *       factor of 0 means a tree does not grow, drop or bear at all.</li>
 * </ul>
 *
 * <h2>The answers</h2>
 * <ul>
 *   <li><b>Where there is no winter</b> (deserts, jungles, savannas, and
 *       every world but the Overworld) the place has no seasons: all
 *       three factors are 1 all year, as in a game without any season
 *       mod. Those trees keep their leaves too.</li>
 *   <li><b>In winter</b>, where there is one, all three factors are 0:
 *       a tree that rests, rests. Nothing grows, no seed falls from a
 *       crown by itself, and fruit that still hangs withers (Dynamic
 *       Trees takes fruit away once it is well out of its season). The
 *       setting {@code growthInWinter} switches this off, and then the
 *       year's own curve stands below.</li>
 *   <li><b>In the rest of the year</b> the factors are the ones Dynamic
 *       Trees itself works out from the season number
 *       ({@link ActiveSeasonGrowthCalculator}, its own curves): growth
 *       picks up through spring, is at its highest through summer and
 *       falls off through autumn; seeds come mostly in late summer and
 *       early autumn; fruit follows the growth. They are used as they
 *       are, so a tree pack that moves a tree's fruit to another part of
 *       the year keeps working.</li>
 * </ul>
 *
 * <h2>Signing in</h2>
 * Dynamic Trees picks its season source when it reads its own settings
 * file, by the setting {@code preferredSeasonMod} in
 * {@code config/dynamictrees-common.toml}:
 * <ul>
 *   <li>{@code "*"} (the default) or {@code "dynamicleaves"}: Dynamic Leaves signs in
 *       ({@link #install}), before any other season mod that may be
 *       installed, and says so in the log
 *       ({@link #otherSeasonMod} says why).</li>
 *   <li>another mod id: that mod's source is left in place, and Dynamic Leaves
 *       says so in the log once.</li>
 *   <li>{@code "!"}: seasons are switched off in Dynamic Trees, and
 *       Dynamic Leaves leaves it so.</li>
 * </ul>
 * Dynamic Leaves' own setting {@code seasonSource} (section {@code trees})
 * keeps it out of this altogether.
 *
 * <h2>Thread rule</h2>
 * Every answer is worked out from the date and the biome when it is
 * asked, and nothing is stored, so any thread may ask. Dynamic Trees
 * asks while it makes new land, on its worker threads, and in the
 * players' games (the hint on a seed tells which season its tree bears
 * in). A worker thread never makes the server's thread do work for it:
 * see {@link #biomeAt} for how the biome is read away from the server's
 * own thread.
 */
public final class TreeSeasons implements SeasonManager {

    /** The number of seasons in a year, which is Dynamic Trees' scale for the season. */
    private static final float SEASONS = 4.0F;

    /** Dynamic Trees' own curves of the year. Holds nothing but the curves. */
    private static final SeasonGrowthCalculator CURVES = new ActiveSeasonGrowthCalculator();

    /** Dynamic Trees' value of {@code preferredSeasonMod} that means "the first season mod there is". */
    private static final String ANY = "*";

    /** Dynamic Trees' value of {@code preferredSeasonMod} that means "no seasons". */
    private static final String NONE = "!";

    /** The biome temperature below which a place with a winter counts as a cold one. Dynamic Trees' own line. */
    private static final float COLD = 0.2F;

    /** The season number winter starts at, on Dynamic Trees' scale. */
    private static final float WINTER_START = 3.0F;

    /** The season mods Dynamic Trees brings a season source of itself (see {@link #otherSeasonMod}). */
    private static final String[] OTHER_SEASON_MODS = { "sereneseasons" };

    /** What was last said in the log about signing in, so that it is said once and not every time. */
    @Nullable
    private static volatile String reported;

    // ------------------------------------------------------------------
    // Signing in
    // ------------------------------------------------------------------

    /**
     * Offers Dynamic Leaves to Dynamic Trees as its season source, so that it
     * can be chosen by name ({@code preferredSeasonMod = "dynamicleaves"}) and
     * whenever Dynamic Trees picks a source again. Called once, after the
     * settings files have been read.
     */
    public static void offer() {
        if (DynamicLeavesConfig.seasonSource()) {
            SeasonCompatibilityHandler.registerSeasonManager(DynamicLeaves.MOD_ID, TreeSeasons::new);
        }
    }

    /**
     * Makes Dynamic Leaves the season source of Dynamic Trees, unless its
     * settings say another mod or no seasons at all, or Dynamic Leaves' own
     * setting {@code seasonSource} is off. Called after the settings
     * files have been read and again before a world starts.
     *
     * <p>{@link #offer} is done again here, because the setting
     * {@code seasonSource} may have been switched on since: a setting
     * that is switched on and then needs the game restarted to take
     * effect is a setting that looks broken.</p>
     */
    public static void install() {
        if (!DynamicLeavesConfig.seasonSource()) {
            say("Dynamic Leaves does not tell Dynamic Trees the season (setting seasonSource is off).");
            return;
        }
        offer();
        String preferred = preferredSeasonMod();
        if (!ANY.equals(preferred) && !DynamicLeaves.MOD_ID.equals(preferred)) {
            say(NONE.equals(preferred)
                    ? "Dynamic Trees has seasons switched off (preferredSeasonMod = \"!\"), so its trees grow all year."
                    : "Dynamic Trees takes its season from \"" + preferred + "\", so Dynamic Leaves does not tell it the season.");
            return;
        }
        if (!(SeasonCompatibilityHandler.getSeasonManager() instanceof TreeSeasons)) {
            SeasonCompatibilityHandler.setSeasonManager(new TreeSeasons());
        }
        String other = ANY.equals(preferred) ? otherSeasonMod() : null;
        say("Dynamic Leaves tells Dynamic Trees the season: its trees rest in winter"
                + (DynamicLeavesConfig.growthInWinter() ? " (switched off: growthInWinter)" : "")
                + " and bear fruit in season."
                + (other == null ? "" : " It is used before \"" + other + "\", which is installed too, because"
                        + " the leaves of those trees go by this same date; to have \"" + other + "\" tell Dynamic"
                        + " Trees the season instead, set preferredSeasonMod = \"" + other + "\" in"
                        + " config/dynamictrees-common.toml."));
    }

    /**
     * Another season mod that Dynamic Trees knows how to take its season
     * from, if one is installed, or null. Only used to say in the log
     * that Dynamic Leaves is being used before it, and how to change that.
     *
     * <p>Dynamic Trees brings one of these with it
     * ({@code SeasonCompatibilityHandler.registerBuiltInSeasonManagers}),
     * and with {@code preferredSeasonMod = "*"} it would pick whichever
     * signed in first, which is a matter of the order the mods happen to
     * be loaded in. Dynamic Leaves does not leave that to chance: the leaves of
     * the trees go by the Calendar date, and a tree that rested by
     * another mod's reckoning would stand bare in the wrong week. A
     * player who wants the other mod says so by name.</p>
     */
    @Nullable
    private static String otherSeasonMod() {
        for (String id : OTHER_SEASON_MODS) {
            if (ModList.get().isLoaded(id)) {
                return id;
            }
        }
        return null;
    }

    /**
     * Which season source Dynamic Trees is set to use, or {@link #ANY}
     * while its settings file has not been read.
     */
    private static String preferredSeasonMod() {
        try {
            String value = DTConfigs.COMMON.preferredSeasonMod.get();
            return value == null ? ANY : value;
        } catch (RuntimeException e) {
            // The settings file is not read yet; install() runs again later.
            return ANY;
        }
    }

    /** Writes a line to the log, unless it was the last line written here. */
    private static void say(String message) {
        if (!message.equals(reported)) {
            reported = message;
            DynamicLeaves.LOGGER.info(message);
        }
    }

    // ------------------------------------------------------------------
    // The season
    // ------------------------------------------------------------------

    /**
     * The season of a world as Dynamic Trees counts it: 0 at the start of
     * spring, 1 of summer, 2 of autumn, 3 of winter, and up to just
     * under 4 at the end of winter. Within a season it moves on evenly,
     * however long that season is.
     *
     * @param level any world, on a server or in a player's game
     * @param pos   not used: the whole world has one date
     */
    @Override
    public Float getSeasonValue(Level level, BlockPos pos) {
        return seasonValue(level);
    }

    /**
     * {@link #getSeasonValue} without the unused position: the season of
     * the world from the Calendar mod, on Dynamic Trees' scale. The two
     * mods count the seasons in the same order ({@link Season}), so the
     * season's own number is the whole part and how far it has gone is
     * the rest.
     */
    public static float seasonValue(Level level) {
        Calendar calendar = Calendar.of(level);
        // The date is taken once and the time of day once, and the
        // answer is worked out from those two. Asking the calendar for
        // the season and then for how far it has gone reads the date
        // twice, and a tree asked in the moment of a midnight that ends a
        // season would get the one season's number with the other's
        // progress.
        CalendarDate date = calendar.date();
        double intoSeason = (date.dayOfSeason() - 1 + calendar.dayProgress()) / Math.max(1, date.seasonLength());
        return date.season().ordinal() + (float) Math.min(0.999, Math.max(0.0, intoSeason));
    }

    /**
     * Where a place stands in the year:
     * <ul>
     *   <li>{@link ClimateZoneType#NONE} where there is no winter: the
     *       place has no seasons, and Dynamic Trees then uses a factor of
     *       1 for everything, all year.</li>
     *   <li>{@link ClimateZoneType#COLD} where there is a winter and the
     *       biome is a cold one (below {@value #COLD}): a short year of
     *       growth.</li>
     *   <li>{@link ClimateZoneType#TEMPERATE} where there is a winter
     *       otherwise.</li>
     * </ul>
     * Dynamic Trees' two other climates (tropical and arid, which have a
     * wet and a dry season instead of four seasons) are never used: those
     * are the places Seasons counts as having no winter.
     *
     * @param level the world, or a part of it being made
     * @param pos   the foot of the tree
     */
    @Override
    public ClimateZoneType getClimate(LevelAccessor level, BlockPos pos) {
        Biome biome = biomeAt(level, pos);
        if (biome == null || !SeasonsApi.hasWinter(biome)) {
            return ClimateZoneType.NONE;
        }
        return biome.getBaseTemperature() < COLD ? ClimateZoneType.COLD : ClimateZoneType.TEMPERATE;
    }

    /**
     * The biome at a place, read the way the game stores it: in cubes of
     * 4 by 4 by 4 blocks, from the chunk the place is in. This is the
     * same reading the leaves themselves go by
     * ({@link io.github.superveryfastcar.dynamicleaves.shape.LeafLook}), so a
     * tree never rests in a wood whose leaves stay, nor the other way
     * round.
     *
     * <h3>Why the thread matters</h3>
     * Reading the stored biome means reading the chunk, and a server
     * hands out a chunk on its own thread only: a worker thread that asks
     * for one is made to wait until the server's thread has time (see
     * {@code ServerChunkCache.getChunk}). Dynamic Trees asks for the
     * climate while it makes new land, on its worker threads, so on a
     * server away from its own thread the biome is worked out from the
     * world's shape instead, without any chunk. That is the same answer
     * for land that is being made, because its biomes are being worked
     * out from that same shape right then.
     *
     * <p>In a player's game and on the server's own thread the stored
     * biome is read, which also gives the right answer where a world has
     * been changed since it was made (a biome brush, another mod).</p>
     */
    @Nullable
    private static Biome biomeAt(LevelAccessor level, BlockPos pos) {
        int cubeX = QuartPos.fromBlock(pos.getX());
        int cubeY = QuartPos.fromBlock(pos.getY());
        int cubeZ = QuartPos.fromBlock(pos.getZ());
        if (level instanceof ServerLevel server && !server.getServer().isSameThread()) {
            return server.getUncachedNoiseBiome(cubeX, cubeY, cubeZ).value();
        }
        return level.getNoiseBiome(cubeX, cubeY, cubeZ).value();
    }

    // ------------------------------------------------------------------
    // The three factors
    // ------------------------------------------------------------------

    /**
     * How fast a tree grows, from 0 (not at all) to 1.
     *
     * @param level  the world
     * @param pos    the foot of the tree
     * @param offset how far this kind of tree's year is shifted against
     *               the world's, in seasons (a tree pack may set this)
     */
    @Override
    public float getGrowthFactor(Level level, BlockPos pos, float offset) {
        return factor(level, pos, offset, Factor.GROWTH);
    }

    /** How readily a tree lets a seed fall by itself, from 0 to 1. */
    @Override
    public float getSeedDropFactor(Level level, BlockPos pos, float offset) {
        return factor(level, pos, offset, Factor.SEEDS);
    }

    /** How readily a tree bears new fruit, from 0 to 1. */
    @Override
    public float getFruitProductionFactor(Level level, BlockPos pos, float offset) {
        return factor(level, pos, offset, Factor.FRUIT);
    }

    /** Which of the three factors is wanted. */
    public enum Factor {
        /** How fast a tree grows. */
        GROWTH,
        /** How readily it lets a seed fall by itself. */
        SEEDS,
        /** How readily it bears new fruit. */
        FRUIT
    }

    /** One of the three factors, for a place and a moment of this world. */
    private float factor(Level level, BlockPos pos, float offset, Factor which) {
        return factor(which, getClimate(level, pos), seasonValue(level), DynamicLeavesConfig.growthInWinter(), offset);
    }

    /**
     * One of the three factors, as plain arithmetic: 1 where there are no
     * seasons, 0 in winter where trees rest, else Dynamic Trees' own curve
     * of the year. The whole rule is here, so that it can be checked
     * without a world.
     *
     * @param which          which of the three factors
     * @param climate        where the place stands in the year
     *                       ({@link #getClimate})
     * @param season         the world's season, 0 to just under
     *                       {@value #SEASONS}
     * @param growthInWinter the setting: whether trees grow in winter too
     * @param offset         how far this kind of tree's year is shifted
     *                       against the world's, in seasons
     * @return the factor, 0 to 1
     */
    public static float factor(Factor which, ClimateZoneType climate, float season, boolean growthInWinter,
                               float offset) {
        if (climate == ClimateZoneType.NONE) {
            return 1.0F;
        }
        if (!growthInWinter && isWinter(season)) {
            return 0.0F;
        }
        float shifted = wrap(season + offset);
        return switch (which) {
            case GROWTH -> CURVES.calcGrowthRate(shifted, climate);
            case SEEDS -> CURVES.calcSeedDropRate(shifted, climate);
            case FRUIT -> CURVES.calcFruitProductionRate(shifted, climate);
        };
    }

    /** A season number brought back into the year, 0 to just under {@value #SEASONS}. */
    public static float wrap(float season) {
        float value = season % SEASONS;
        return value < 0.0F ? value + SEASONS : value;
    }

    /**
     * Whether a season number is in winter. The shift of a kind of tree
     * does not count here: a tree rests when the world is in winter, and
     * its shift only moves the curves of the rest of the year.
     */
    public static boolean isWinter(float season) {
        return wrap(season) >= WINTER_START;
    }

    /**
     * When in the year this kind of tree bears most fruit, on the same
     * scale as {@link #getSeasonValue}, or null where there are no
     * seasons. Dynamic Trees uses it for the hint on a seed, which tells
     * the player which season the tree's fruit comes in.
     */
    @Override
    public Float getPeakFruitProductionSeasonValue(Level level, BlockPos pos, float offset) {
        ClimateZoneType climate = getClimate(level, pos);
        Float peak = CURVES.getPeakFruitProductionSeasonValue(climate);
        return peak == null ? null : wrap(peak + offset);
    }

    /**
     * Whether snow melts at a place. Snow in this pack is Seasons'
     * business, which lays it and melts it by the temperature of the day;
     * the answer here is only what Dynamic Trees would make of the
     * season, and nothing in Dynamic Trees 1.7.2 asks it.
     */
    @Override
    public boolean shouldSnowMelt(Level level, BlockPos pos) {
        Biome biome = biomeAt(level, pos);
        return biome == null || !SeasonsApi.hasWinter(biome) || !isWinter(seasonValue(level));
    }

    // ------------------------------------------------------------------
    // Nothing is stored
    // ------------------------------------------------------------------

    /** Dynamic Trees ticks its season source every tick. Nothing to do: the date is read when it is asked for. */
    @Override
    public void updateTick(Level level, long dayTime) {
    }

    /** Nothing is remembered per world. */
    @Override
    public void flushMappings() {
    }

    /** Nothing is remembered per world. */
    @Override
    public void clearCache(Level level) {
    }
}
