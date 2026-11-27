package io.github.superveryfastcar.foliage.dev;

import com.dtteam.dynamictrees.block.leaves.DynamicLeavesBlock;
import com.dtteam.dynamictrees.tree.TreeHelper;
import com.dtteam.dynamictrees.tree.species.Species;
import com.dtteam.dynamictrees.worldgen.JoCode;
import com.mojang.blaze3d.platform.NativeImage;
import io.github.superveryfastcar.foliage.Foliage;
import io.github.superveryfastcar.foliage.block.BareLeavesBlock;
import io.github.superveryfastcar.foliage.block.Twins;
import io.github.superveryfastcar.foliage.client.ClientLeafClock;
import io.github.superveryfastcar.foliage.client.fall.FallingLeaves;
import io.github.superveryfastcar.foliage.client.model.LeafModels;
import io.github.superveryfastcar.foliage.client.sprite.LeafPartsSource;
import io.github.superveryfastcar.foliage.season.LeafCalendar;
import io.github.superveryfastcar.foliage.season.LeafClock;
import io.github.superveryfastcar.foliage.season.LeafStage;
import io.github.superveryfastcar.foliage.shape.LeafPicture;
import io.github.superveryfastcar.foliage.shape.LeafShape;
import io.github.superveryfastcar.foliage.shape.Recipe;
import io.github.superveryfastcar.foliage.shape.ShapeSettings;
import io.github.superveryfastcar.foliage.shape.WinterTwigs;
import io.github.superveryfastcar.foliage.world.LeafWalker;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Development aid: checks Foliage in a real game client with nobody at the
 * keyboard, for example on a build machine that has no screen.
 *
 * <pre>./gradlew runClient -Pfoliage.selfTest=true</pre>
 *
 * <p>The automatic tests (the {@code gametest} package) run on a server
 * and can only check the rules. This fills the gap: it shows that the mod
 * loads in a real client (with Sodium, if Sodium is in {@code run/mods}),
 * that the leaf models take the place of Dynamic Trees' own, that every
 * shape is really drawn, how many faces the chunk builder gets per leaf
 * block, and that the trees go through the year: thinner day by day at the
 * end of autumn, bare with twigs in winter, in bud and then in leaf again
 * at the start of spring. It checks the bare blocks as a player's game
 * sees them: that every spot holds the block its stage asks for, the same
 * block as on the server, and that the ground under bare crowns has the
 * full light of the sky. It saves pictures of the same scene in every
 * shape and on every day of the two changes for a human to judge the
 * look.</p>
 *
 * <p>It does nothing unless the system property
 * {@code foliage.dev.selfTest} is {@code true} (the Gradle flag above sets
 * it), and the whole {@code dev} package is left out of the released jar.</p>
 *
 * <h2>What it does</h2>
 * <ol>
 *   <li>Gets the game to the title screen, creates a fresh flat creative
 *       world and enters it.</li>
 *   <li>Plants a fixed scene with Dynamic Trees' own command: a big oak, a
 *       birch and a dark oak in front, rows of forest behind them, and one
 *       tree of every other kind off to the east.</li>
 *   <li>Works through its list of looks ({@link #SHOTS}): first every
 *       shape in summer, then the year, day by day. For each one it sets
 *       the date and the look, has the landscape drawn again, waits until
 *       that is done, checks numbers, and saves a picture from some of the
 *       camera spots ({@link #VIEWS}) into the game's {@code screenshots}
 *       folder. Once, at the first day of winter, it does not ask for the
 *       landscape to be drawn again and checks that this happens on its
 *       own.</li>
 *   <li>Writes one line per check into the log, each starting with
 *       {@code [Foliage self test]}, and at the end a line with
 *       {@code RESULT:}. Then it closes the game.</li>
 * </ol>
 */
@EventBusSubscriber(modid = Foliage.MOD_ID, value = Dist.CLIENT)
public final class DevSelfTest {

    /** True when the self test was asked for on the command line. */
    private static final boolean ENABLED = Boolean.getBoolean("foliage.dev.selfTest");

    /** True if Sodium is loaded too. Then its chunk builder is the one being checked. */
    private static final boolean SODIUM = ModList.get().isLoaded("sodium");

    /** Name of the throwaway world. It is deleted and created again on every run. */
    private static final String WORLD_NAME = "foliage-self-test";

    /** Client ticks to wait after entering the world before the first look, so everything has loaded. */
    private static final int SETTLE_TICKS = 160;

    /** Give up and close the game this long after entering the world, whatever happened. */
    private static final long GIVE_UP_NANOS = 2_700_000_000_000L;

    /**
     * Client ticks to wait for the landscape to be drawn again on its own
     * after the date changed: Seasons spreads that over 20 seconds.
     */
    private static final int GENTLE_REDRAW_TICKS = 520;

    /** One of the largest oaks Dynamic Trees knows, as its growth code. */
    private static final String BIG_OAK = "JOJxOOKPz+vnSx8nSR+beJnzzz89x98+txuJXvzePt6WJ9y98mOOJ67+SPkxJ+fy8";

    /** The corners of the box around the big oak in which its leaf blocks are counted one by one. */
    private static final BlockPos OAK_FROM = new BlockPos(-14, -60, -27);
    private static final BlockPos OAK_TO = new BlockPos(14, -24, 1);

    /** A camera spot: its name and the arguments of the teleport command (place, then where to look). */
    private record View(String name, String where) {
    }

    private static final List<View> VIEWS = List.of(
            new View("ground", "0.5 -60 6.5 180 -16"),
            new View("close", "5.5 -55 -5.5 144 -6"),
            new View("above", "0.5 -44 12.5 180 22"),
            new View("under", "3.5 -60 -9.5 144 -52"),
            new View("kinds", "56.5 -54 14.5 180 -8"));

    /** What the leaf calendar does on the date of a look. */
    private enum Time {
        /** Between the two changes: every tree in full leaf. */
        FULL,
        /** The last days of autumn: leaves fall. */
        FALL,
        /** Winter: every tree that sheds is bare. */
        BARE,
        /** The first days of spring: leaves come back. */
        RETURN
    }

    /**
     * One look to check and photograph.
     *
     * @param name     for the log and the pictures' file names
     * @param settings the shape settings to draw by
     * @param fancy    the game's own graphics setting: true for see-through
     *                 leaves ("fancy"), false for leaves that are not ("fast")
     * @param date     the calendar date to show it at
     * @param time     what the leaf calendar does on that date
     * @param views    from how many of the camera spots a picture is saved
     *                 (the first ones of {@link #VIEWS})
     * @param gentle   true: the landscape is not asked to be drawn again
     *                 for this look; it has to happen on its own because
     *                 the date changed
     * @param tracked  true for the looks that make up the walk through the
     *                 year (same shape, one date after another); the checks
     *                 compare each with the one before
     */
    private record Shot(String name, ShapeSettings settings, boolean fancy, String date, Time time, int views,
                        boolean gentle, boolean tracked) {

        /** A look in summer, photographed from every camera spot. */
        static Shot summer(String name, ShapeSettings settings, boolean fancy) {
            return new Shot(name, settings, fancy, SUMMER, Time.FULL, VIEWS.size(), false, false);
        }

        /** A day of the walk through the year, in the mod's own shape. */
        static Shot day(String date, Time time, int views) {
            return new Shot(date.replace(' ', '-'), ShapeSettings.DEFAULT, true, date, time, views, false, true);
        }

        /** The same date in another look, beside the walk through the year. */
        static Shot beside(String name, String date, Time time, ShapeSettings settings, boolean fancy) {
            return new Shot(date.replace(' ', '-') + "-" + name, settings, fancy, date, time, 2, false, false);
        }
    }

    private static final String SUMMER = "summer 7";

    private static final List<Shot> SHOTS = List.of(
            // Every shape, in summer.
            Shot.summer("plain", ShapeSettings.DEFAULT.withShape(LeafShape.PLAIN), true),
            Shot.summer("shell", ShapeSettings.DEFAULT.withShape(LeafShape.SHELL), true),
            Shot.summer("bushy", ShapeSettings.DEFAULT.withShape(LeafShape.BUSHY), true),
            Shot.summer("tufts", ShapeSettings.DEFAULT.withShape(LeafShape.TUFTS), true),
            Shot.summer("mixed", ShapeSettings.DEFAULT.withShape(LeafShape.MIXED), true),
            Shot.summer("mixed-rich", ShapeSettings.DEFAULT.withShape(LeafShape.MIXED).withMaxFaces(ShapeSettings.MAX_FACES), true),
            Shot.summer("bushy-rich", ShapeSettings.DEFAULT.withShape(LeafShape.BUSHY).withMaxFaces(ShapeSettings.MAX_FACES), true),
            Shot.summer("tufts-rich", ShapeSettings.DEFAULT.withShape(LeafShape.TUFTS).withMaxFaces(ShapeSettings.MAX_FACES), true),
            Shot.summer("mixed-fewest-faces", ShapeSettings.DEFAULT.withShape(LeafShape.MIXED).withFastLeaves(true), true),
            Shot.summer("mixed-fast-graphics", ShapeSettings.DEFAULT.withShape(LeafShape.MIXED), false),
            // The year. With the Calendar mod's 14-day seasons the leaves fall on days 8 to 14 of autumn.
            Shot.day("autumn 7", Time.FULL, 2),
            Shot.day("autumn 8", Time.FALL, 3),
            Shot.day("autumn 9", Time.FALL, 3),
            Shot.day("autumn 10", Time.FALL, 3),
            Shot.day("autumn 11", Time.FALL, 3),
            Shot.beside("plain", "autumn 11", Time.FALL, ShapeSettings.DEFAULT.withShape(LeafShape.PLAIN), true),
            Shot.beside("tufts", "autumn 11", Time.FALL, ShapeSettings.DEFAULT.withShape(LeafShape.TUFTS), true),
            Shot.beside("fast-graphics", "autumn 11", Time.FALL, ShapeSettings.DEFAULT, false),
            Shot.day("autumn 12", Time.FALL, 3),
            Shot.day("autumn 13", Time.FALL, 3),
            Shot.day("autumn 14", Time.FALL, 3),
            // The first day of winter is reached without asking for a redraw: it has to come on its own.
            new Shot("winter-1-on-its-own", ShapeSettings.DEFAULT, true, "winter 1", Time.BARE, 1, true, true),
            Shot.day("winter 1", Time.BARE, VIEWS.size()),
            Shot.beside("more-twigs", "winter 1", Time.BARE, ShapeSettings.DEFAULT.withWinterTwigs(WinterTwigs.MORE), true),
            Shot.beside("no-twigs", "winter 1", Time.BARE, ShapeSettings.DEFAULT.withWinterTwigs(WinterTwigs.NONE), true),
            Shot.beside("plain", "winter 1", Time.BARE, ShapeSettings.DEFAULT.withShape(LeafShape.PLAIN), true),
            Shot.beside("fast-graphics", "winter 1", Time.BARE, ShapeSettings.DEFAULT, false),
            // The leaves come back on days 1 to 7 of spring.
            Shot.day("spring 1", Time.RETURN, 3),
            Shot.day("spring 2", Time.RETURN, 3),
            Shot.day("spring 3", Time.RETURN, 3),
            Shot.day("spring 4", Time.RETURN, 3),
            Shot.beside("plain", "spring 4", Time.RETURN, ShapeSettings.DEFAULT.withShape(LeafShape.PLAIN), true),
            Shot.beside("fast-graphics", "spring 4", Time.RETURN, ShapeSettings.DEFAULT, false),
            Shot.day("spring 5", Time.RETURN, 3),
            Shot.day("spring 6", Time.RETURN, 3),
            Shot.day("spring 7", Time.RETURN, 3),
            Shot.day("spring 8", Time.FULL, 3));

    /**
     * The most faces per leaf block a shape may hand to the chunk builder
     * over the whole scene, with the mod's own limit of 6 per block. The
     * unmodded game hands over 6 for every leaf block.
     */
    private static final double MOST_FACES = 5.0;

    /** The most of those that may stand at a slant. */
    private static final double MOST_SLANTED = 2.0;

    /** The same two numbers with the limit per block raised as far as it goes. */
    private static final double MOST_FACES_RICH = 6.5;
    private static final double MOST_SLANTED_RICH = 3.0;

    /** What went wrong so far, for the last line of the log. */
    private static final List<String> FAILURES = new ArrayList<>();

    /** How many checks held so far. */
    private static int passed;

    // The driver's state.
    private static boolean worldRequested;
    private static boolean finished;
    private static int ticksInWorld;
    private static long enteredNanos;
    /** The look being worked on, as a place in {@link #SHOTS}; -1 before the first. */
    private static int shot = -1;
    /** The camera spot being worked on, as a place in {@link #VIEWS}. */
    private static int view;
    /** 0 = next look and its date, 1 = switch the look, 2 = wait for the redraw, check, take pictures. */
    private static int phase;
    /** Ticks left to wait before the next thing. */
    private static int wait;
    /** For how many ticks in a row the chunk builder has had nothing to do. */
    private static int quiet;
    private static long redrawStart;
    private static String lastDate = "";

    /** The "ground" picture of the plain look, kept to compare the shapes with. */
    @Nullable
    private static int[] plainPicture;

    /** Faces per leaf block of the plain look, kept to compare the shapes with. */
    private static double plainFaces;

    // The walk through the year: what the look before showed.
    /** The share of the big oak's leaf blocks that were in full leaf, and that were bare; -1 before the first. */
    private static double fullBefore = -1.0;
    private static double bareBefore = -1.0;
    /** The share of the "ground" picture that was open sky; -1 before the first. */
    private static double skyBefore = -1.0;
    /** The same share in summer, in the mod's own shape. */
    private static double skyInSummer = -1.0;

    private DevSelfTest() {
    }

    // ------------------------------------------------------------------
    // The driver
    // ------------------------------------------------------------------

    /** Runs once per client tick and moves the self test on by one small step. */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!ENABLED || finished) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.options.pauseOnLostFocus = false;
        if (!worldRequested && minecraft.screen instanceof AccessibilityOnboardingScreen) {
            minecraft.options.onboardAccessibility = false;
            minecraft.setScreen(new TitleScreen());
            return;
        }
        if (!worldRequested && minecraft.screen instanceof TitleScreen) {
            worldRequested = true;
            deleteOldWorld(minecraft);
            LevelSettings settings = new LevelSettings(WORLD_NAME, GameType.CREATIVE, false,
                    Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
            minecraft.createWorldOpenFlows().createFreshLevel(
                    WORLD_NAME, settings, new WorldOptions(0L, false, false),
                    registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                            .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                    minecraft.screen);
            return;
        }
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (minecraft.player == null || minecraft.level == null || server == null) {
            return;
        }
        minecraft.options.hideGui = true;
        if (!minecraft.player.getAbilities().flying) {
            minecraft.player.getAbilities().flying = true;
            minecraft.player.onUpdateAbilities();
        }
        ticksInWorld++;
        if (ticksInWorld == 1) {
            enteredNanos = System.nanoTime();
            minecraft.options.graphicsMode().set(GraphicsStatus.FANCY);
            // No clouds: they drift, and the checks measure how much sky shows through the crowns.
            minecraft.options.cloudStatus().set(CloudStatus.OFF);
            minecraft.options.renderDistance().set(10);
            minecraft.options.fov().set(70);
            server.execute(() -> buildScene(server));
            return;
        }
        if (System.nanoTime() - enteredNanos > GIVE_UP_NANOS) {
            expect("the self test finishes in time", false);
            finish(minecraft);
            return;
        }
        if (ticksInWorld < SETTLE_TICKS) {
            return;
        }
        if (ticksInWorld == SETTLE_TICKS) {
            checkSetUp(minecraft);
        }
        if (--wait > 0) {
            return;
        }
        switch (phase) {
            case 0 -> {
                shot++;
                view = 0;
                if (shot >= SHOTS.size()) {
                    finish(minecraft);
                    return;
                }
                Shot current = SHOTS.get(shot);
                // From here to the check: what the falling leaves and the
                // walker do for this look, the date's own swaps included.
                // The date is set below and the server works on it while
                // this look waits, so the count has to start before that.
                leavesBefore = FallingLeaves.made();
                walkedBefore = walkerCounters(server);
                if (current.gentle()) {
                    // Counted from before the date changes: what is built from here on, nobody asked for.
                    LeafModels.setSettings(current.settings());
                    run(server, "tp @a " + VIEWS.get(0).where());
                    LeafModels.resetCounters();
                }
                if (!current.date().equals(lastDate)) {
                    lastDate = current.date();
                    run(server, "calendar set " + current.date(), "time set 6000", "kill @e[type=minecraft:bee]");
                    wait = current.gentle() ? 80 + GENTLE_REDRAW_TICKS : 80;
                }
                phase = 1;
            }
            case 1 -> {
                Shot current = SHOTS.get(shot);
                if (!current.gentle()) {
                    LeafModels.setSettings(current.settings());
                    minecraft.options.graphicsMode().set(current.fancy() ? GraphicsStatus.FANCY : GraphicsStatus.FAST);
                    run(server, "tp @a " + VIEWS.get(0).where());
                    LeafModels.resetCounters();
                    minecraft.levelRenderer.allChanged();
                }
                redrawStart = System.nanoTime();
                quiet = 0;
                wait = 20;
                phase = 2;
            }
            case 2 -> {
                // Wait until the server has swapped the blocks the date asks for and the
                // chunk builder has caught up, then a little longer.
                quiet = minecraft.levelRenderer.hasRenderedAllSections() && walkerCounters(server)[PENDING] == 0 ? quiet + 1 : 0;
                if (quiet < 30) {
                    return;
                }
                Shot current = SHOTS.get(shot);
                if (view == 0) {
                    checkShot(minecraft, current);
                }
                String name = String.format(Locale.ROOT, "foliage-%02d-%s-%s%s.png", shot + 1, current.name(),
                        VIEWS.get(view).name(), SODIUM ? "-sodium" : "");
                Screenshot.grab(minecraft.gameDirectory, name, minecraft.getMainRenderTarget(), message -> { });
                log("saved " + name);
                view++;
                if (view >= current.views()) {
                    phase = 0;
                    wait = 2;
                } else {
                    run(server, "tp @a " + VIEWS.get(view).where());
                    quiet = 0;
                    wait = 30;
                }
            }
            default -> finish(minecraft);
        }
    }

    // ------------------------------------------------------------------
    // The scene
    // ------------------------------------------------------------------

    /** Sets the game rules and plants the trees. Runs on the server thread. */
    private static void buildScene(IntegratedServer server) {
        run(server, "weather clear", "gamerule doDaylightCycle false", "gamerule doWeatherCycle false",
                "gamerule randomTickSpeed 0", "gamerule doMobSpawning false", "time set 6000", "calendar set " + SUMMER);
        // In front: the three kinds that will shed their leaves.
        run(server, "dt settree 0 -61 -13 dynamictrees:oak " + BIG_OAK + " 0 0",
                "dt settree -12 -61 -10 dynamictrees:birch " + storedCode("dynamictrees:birch", 0) + " 1 0",
                "dt settree 13 -61 -11 dynamictrees:dark_oak " + storedCode("dynamictrees:dark_oak", 0) + " 2 0");
        // Behind them: rows of forest, each tree a different stored shape.
        String[] kinds = {"dynamictrees:oak", "dynamictrees:birch", "dynamictrees:oak", "dynamictrees:dark_oak"};
        int planted = 0;
        for (int z = -30; z >= -66; z -= 9) {
            for (int x = -44 + (z & 1) * 4; x <= 44; x += 9) {
                String kind = kinds[planted % kinds.length];
                run(server, "dt settree " + x + " -61 " + z + " " + kind + " " + storedCode(kind, 1 + (planted * 7) % 40) + " " + (planted % 4) + " 0");
                planted++;
            }
        }
        // Off to the east: one tree of every other kind, to see their leaves in the new shapes.
        String[] others = {"dynamictrees:spruce", "dynamictrees:jungle", "dynamictrees:acacia", "dynamictrees:cherry",
                "dynamictrees:mangrove", "dynamictrees:azalea"};
        int x = 36;
        for (String kind : others) {
            String code = storedCode(kind, 0);
            if (code == null) {
                log("no stored shape for " + kind + ", not planted");
                continue;
            }
            run(server, "dt settree " + x + " -61 -8 " + kind + " " + code + " 0 0");
            x += 9;
        }
        log("planted 3 trees in front, " + planted + " behind and the other kinds to the east");
        removeBees(server);
    }

    /**
     * Takes the bee nests that Dynamic Trees hangs into oaks out of the
     * scene, with their bees. A bee that flies past the camera changes how
     * much sky a picture shows, and the pictures are compared by that.
     */
    private static void removeBees(IntegratedServer server) {
        // Queued like the commands that plant the trees, so that it runs after them.
        server.execute(() -> {
            ServerLevel level = server.overworld();
            int nests = 0;
            for (int chunkX = -6; chunkX <= 7; chunkX++) {
                for (int chunkZ = -7; chunkZ <= 2; chunkZ++) {
                    LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                    if (chunk == null) {
                        continue;
                    }
                    for (BlockPos pos : List.copyOf(chunk.getBlockEntitiesPos())) {
                        if (chunk.getBlockEntity(pos) instanceof BeehiveBlockEntity) {
                            level.removeBlock(pos, false);
                            nests++;
                        }
                    }
                }
            }
            log("took " + nests + " bee nests out of the scene");
        });
        run(server, "kill @e[type=minecraft:bee]");
    }

    /**
     * One of the tree shapes Dynamic Trees has stored for a species, as
     * its growth code, counted from the largest. Null if it has none.
     */
    @Nullable
    private static String storedCode(String species, int rank) {
        Species found = Species.REGISTRY.get(ResourceLocation.parse(species));
        if (found == null || !found.isValid()) {
            return null;
        }
        List<JoCode> codes = new ArrayList<>(found.getJoCodes());
        if (codes.isEmpty()) {
            return null;
        }
        codes.sort(Comparator.comparingInt((JoCode code) -> code.toString().length()).reversed());
        return codes.get(Math.min(rank, codes.size() - 1)).toString();
    }

    /** Runs commands as the server console, on the server thread. */
    private static void run(IntegratedServer server, String... commands) {
        server.execute(() -> {
            for (String command : commands) {
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
            }
        });
    }

    /**
     * The counters of the server's walker (see {@link LeafWalker#counters}),
     * and as a seventh number how many chunks still wait for a look. Asked
     * on the server thread; the caller waits for the answer.
     */
    private static long[] walkerCounters(IntegratedServer server) {
        return server.submit(() -> {
            LeafWalker walker = LeafWalker.of(server.overworld());
            long[] counters = Arrays.copyOf(walker.counters(), LeafWalker.COUNTERS + 1);
            counters[PENDING] = walker.pending();
            return counters;
        }).join();
    }

    /** Where {@link #walkerCounters} puts the chunks the walker still has to look at. */
    private static final int PENDING = LeafWalker.COUNTERS;

    /**
     * Holds what the player's game has at some spots against what the
     * server has there. Asked on the server thread; the caller waits for
     * the answer.
     *
     * @param spots  the spots
     * @param states what the player's game holds there, in the same order
     * @return for each spot: 0 if both hold the same block state, 1 if
     *         they hold the same block with another supply number or
     *         mark, 2 if they hold different blocks
     */
    private static byte[] againstServer(IntegratedServer server, List<BlockPos> spots, List<BlockState> states) {
        return server.submit(() -> {
            ServerLevel level = server.overworld();
            byte[] result = new byte[spots.size()];
            for (int i = 0; i < result.length; i++) {
                BlockState there = level.getBlockState(spots.get(i));
                BlockState here = states.get(i);
                result[i] = (byte) (there == here ? 0 : there.getBlock() == here.getBlock() ? 1 : 2);
            }
            return result;
        }).join();
    }

    // ------------------------------------------------------------------
    // The checks
    // ------------------------------------------------------------------

    /** Once, before the first look: the models and pictures are in place. */
    private static void checkSetUp(Minecraft minecraft) {
        log("Sodium " + (SODIUM ? "is" : "is not") + " loaded");
        expect("the leaf models of Dynamic Trees are wrapped (" + LeafModels.wrappedCount() + " models)", LeafModels.wrappedCount() >= 10);
        TextureAtlas atlas = minecraft.getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS);
        for (String kind : List.of("oak", "birch", "dark_oak", "spruce", "jungle", "acacia", "mangrove", "cherry", "azalea", "flowering_azalea")) {
            ResourceLocation texture = ResourceLocation.withDefaultNamespace("block/" + kind + "_leaves");
            TextureAtlasSprite leaf = atlas.getSprite(texture);
            TextureAtlasSprite card = atlas.getSprite(LeafPartsSource.cardName(texture));
            boolean made = card.contents().name().equals(LeafPartsSource.cardName(texture));
            expect("a leaf card was made for " + kind, made);
            if (made) {
                expect("the card of " + kind + " is twice as wide and tall as its leaf texture",
                        card.contents().width() == 2 * leaf.contents().width() && card.contents().height() == 2 * leaf.contents().height());
                float leafShare = shownShare(leaf.contents().byMipLevel[0]);
                float cardShare = shownShare(card.contents().byMipLevel[0]);
                expect(String.format(Locale.ROOT, "the card of %s is a round patch of its leaves (texture %.2f full, card %.2f)", kind, leafShare, cardShare),
                        cardShare > 0.38F * leafShare && cardShare < 0.58F * leafShare);
            }
        }
        // The leaf pictures of the year, for the kinds that shed.
        for (String kind : List.of("oak", "birch", "dark_oak")) {
            ResourceLocation texture = ResourceLocation.withDefaultNamespace("block/" + kind + "_leaves");
            float full = shownShare(atlas.getSprite(texture).contents().byMipLevel[0]);
            float[] shares = new float[LeafPicture.COUNT];
            float[] far = new float[LeafPicture.COUNT];
            boolean allMade = true;
            for (int number = 1; number < LeafPicture.COUNT; number++) {
                ResourceLocation name = LeafPartsSource.leafName(texture, LeafPicture.byNumber(number));
                TextureAtlasSprite sprite = atlas.getSprite(name);
                if (!sprite.contents().name().equals(name)) {
                    allMade = false;
                    continue;
                }
                NativeImage[] sizes = sprite.contents().byMipLevel;
                shares[number] = shownShare(sizes[0]);
                // The copy a quarter as wide (4 by 4 for the usual textures): the
                // ones smaller still have too few pixels to show a tenth of them.
                far[number] = shownShare(sizes[Math.min(2, sizes.length - 1)]);
            }
            expect("the leaf pictures of the year were made for " + kind, allMade);
            if (allMade) {
                expect(String.format(Locale.ROOT, "%s loses its leaves picture by picture (full %.2f, then %.2f, %.2f, %.2f)", kind, full,
                                shares[LeafPicture.THIN.ordinal()], shares[LeafPicture.THINNER.ordinal()], shares[LeafPicture.LAST.ordinal()]),
                        shares[LeafPicture.THIN.ordinal()] < full && shares[LeafPicture.THINNER.ordinal()] < shares[LeafPicture.THIN.ordinal()]
                                && shares[LeafPicture.LAST.ordinal()] < shares[LeafPicture.THINNER.ordinal()] && shares[LeafPicture.LAST.ordinal()] > 0.03F);
                expect(String.format(Locale.ROOT, "%s grows them back picture by picture (%.2f, then %.2f)", kind,
                                shares[LeafPicture.YOUNG.ordinal()], shares[LeafPicture.HALF.ordinal()]),
                        shares[LeafPicture.YOUNG.ordinal()] < shares[LeafPicture.HALF.ordinal()] && shares[LeafPicture.HALF.ordinal()] < full);
                // Seen from afar the game uses smaller copies of a picture; the few last leaves must not fade out of them.
                expect(String.format(Locale.ROOT, "the last leaves of %s stay in the small copies of their picture (%.2f full size, %.2f a quarter as wide)",
                                kind, shares[LeafPicture.LAST.ordinal()], far[LeafPicture.LAST.ordinal()]),
                        far[LeafPicture.LAST.ordinal()] > 0.5F * shares[LeafPicture.LAST.ordinal()]);
            }
            for (LeafPicture which : List.of(LeafPicture.THIN, LeafPicture.THINNER)) {
                ResourceLocation name = LeafPartsSource.cardName(texture, which);
                expect("a thinned leaf card (" + which + ") was made for " + kind, atlas.getSprite(name).contents().name().equals(name));
            }
        }
        for (String twigs : List.of("twigs_brown", "twigs_birch", "twigs_dark")) {
            ResourceLocation name = ResourceLocation.fromNamespaceAndPath(Foliage.MOD_ID, "block/" + twigs + "_0");
            expect("the twig pictures " + twigs + " are loaded", atlas.getSprite(name).contents().name().equals(name));
            ResourceLocation buds = LeafPartsSource.budTwigs(ResourceLocation.fromNamespaceAndPath(Foliage.MOD_ID, "block/" + twigs)).withSuffix("_0");
            expect("and the same twigs with buds", atlas.getSprite(buds).contents().name().equals(buds));
        }
        // The leaves that drift down are drawn from the block pictures
        // (FallingLeaf.getRenderType). If one of them were not in there,
        // the game would hand out its "missing picture" instead, and every
        // leaf in the air would be a black and purple square.
        for (int number = 0; number < FallingLeaves.pictures(); number++) {
            ResourceLocation name = FallingLeaves.picture(number);
            TextureAtlasSprite sprite = atlas.getSprite(name);
            boolean there = sprite.contents().name().equals(name);
            float share = there ? shownShare(sprite.contents().byMipLevel[0]) : 0.0F;
            expect(String.format(Locale.ROOT, "the picture of a leaf in the air (%s) is among the block pictures and shows a leaf (%.2f of it)",
                            name.getPath(), share),
                    there && share > 0.10F && share < 0.90F);
        }
        int leaves = 0;
        boolean ours = true;
        boolean onSeasons = false;
        for (BlockPos pos : BlockPos.betweenClosed(OAK_FROM, OAK_TO)) {
            if (minecraft.level.getBlockState(pos).getBlock() instanceof DynamicLeavesBlock) {
                leaves++;
                ours &= LeafModels.drawsAt(minecraft.level, pos);
                onSeasons |= LeafModels.onSeasonsModelAt(minecraft.level, pos);
            }
        }
        expect("the big oak stands (" + leaves + " leaf blocks)", leaves > 300);
        expect("every leaf block of the big oak is drawn by a leaf model of Foliage", ours);
        expect("Seasons' own bare-tree look is out of the way for them", !onSeasons);
        // The bare blocks: every state of every one is drawn by the model of its leaf.
        int bareStates = 0;
        int bareOurs = 0;
        for (BareLeavesBlock bare : Twins.bareBlocks()) {
            for (BlockState state : bare.getStateDefinition().getPossibleStates()) {
                bareStates++;
                bareOurs += LeafModels.draws(state) ? 1 : 0;
            }
        }
        expect("every plain kind of leaves has a bare block (" + Twins.bareBlocks().size() + ")", Twins.bareBlocks().size() >= 12);
        expect("every state of every bare block is drawn by the model of its leaves (" + bareOurs + " of " + bareStates + ")",
                bareStates > 0 && bareOurs == bareStates);
        expect("the world's rules of the leaf calendar arrived from the server", LeafCalendar.hasSentSettings());
        expect("the leaf clock runs", ClientLeafClock.clock() != null);
    }

    /** How many leaves had been made when the look being worked on started. */
    private static long leavesBefore;

    /** The walker's counters when the look being worked on started. */
    private static long[] walkedBefore = new long[LeafWalker.COUNTERS + 1];

    /** The most leaves that were on their way at once, over the whole run. */
    private static int mostLeavesAlive;

    /** How many leaves came down over all the looks of the days when leaves fall. */
    private static long leavesWhileFalling;

    /** The share of a picture's pixels that are shown (at least half opaque). */
    private static float shownShare(NativeImage image) {
        int shown = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (image.getPixelRGBA(x, y) >>> 24 >= 128) {
                    shown++;
                }
            }
        }
        return shown / (float) (image.getWidth() * image.getHeight());
    }

    /** After the redraw of a look, from the first camera spot. */
    private static void checkShot(Minecraft minecraft, Shot current) {
        ClientLevel level = minecraft.level;
        String name = current.name();
        boolean plain = current.settings().shape() == LeafShape.PLAIN;
        Time time = current.time();

        // The leaf clock of the player's game knows what time of year it is.
        LeafClock clock = ClientLeafClock.clock();
        expect(name + ": the leaf clock is at the right time of year (" + clock + ")", clock != null
                && clock.allFull() == (time == Time.FULL) && clock.allBare() == (time == Time.BARE));

        // What the chunk builder was handed, over everything it built.
        long[] counted = LeafModels.counters();
        double faces = counted[1] / (double) Math.max(1, counted[0]);
        double slanted = counted[2] / (double) Math.max(1, counted[0]);
        log(String.format(Locale.ROOT, "%s at %s: leaf blocks %d, faces %d, faces per leaf block %.2f, slanted %.2f; recipes kept %d; %s",
                name, current.date(), counted[0], counted[1], faces, slanted, LeafModels.recipeCount(), sodiumCounters()));
        if (current.gentle()) {
            expect(name + ": after the date changed, the landscape was drawn again on its own (" + counted[0] + " leaf blocks)", counted[0] > 15000);
        } else {
            expect(name + ": the chunk builder drew leaf blocks (" + counted[0] + ")", counted[0] > 2000);
        }

        // The big oak, leaf block by leaf block: the model hands out what its recipe says.
        int oakLeaves = 0;
        int ours = 0;
        int agree = 0;
        long oakFaces = 0;
        int[] stages = new int[LeafStage.values().length];
        // Which of its two blocks each spot holds, and how bright it is there.
        int bareBlocks = 0;
        BitSet wrongBlock = new BitSet();
        long lightAtLeaves = 0;
        int darkestBare = 15;
        List<BlockPos> spots = new ArrayList<>();
        List<BlockState> held = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(OAK_FROM, OAK_TO)) {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof DynamicLeavesBlock)) {
                continue;
            }
            oakLeaves++;
            spots.add(pos.immutable());
            held.add(state);
            int recipe = LeafModels.recipeAt(level, pos);
            int handedOut = LeafModels.faceCountAt(level, pos);
            oakFaces += Math.max(0, handedOut);
            LeafStage stage = LeafStage.FULL;
            if (recipe != Recipe.NONE) {
                ours++;
                agree += handedOut == Recipe.quadCount(recipe) ? 1 : 0;
                stage = Recipe.stage(recipe);
            } else {
                agree += handedOut == 6 ? 1 : 0;
            }
            stages[stage.ordinal()]++;
            boolean isBare = state.getBlock() instanceof BareLeavesBlock;
            bareBlocks += isBare ? 1 : 0;
            wrongBlock.set(spots.size() - 1, isBare != stage.bareBlock());
            int sky = level.getBrightness(LightLayer.SKY, pos);
            if (isBare) {
                darkestBare = Math.min(darkestBare, sky);
            } else {
                lightAtLeaves += sky;
            }
        }
        // The ground under the crowns: every column with leaf spots and no wood in it.
        int ground = 0;
        int shaded = 0;
        BlockPos.MutableBlockPos column = new BlockPos.MutableBlockPos();
        for (int x = OAK_FROM.getX(); x <= OAK_TO.getX(); x++) {
            columns:
            for (int z = OAK_FROM.getZ(); z <= OAK_TO.getZ(); z++) {
                boolean leaves = false;
                for (int y = OAK_FROM.getY(); y <= OAK_TO.getY(); y++) {
                    BlockState state = level.getBlockState(column.set(x, y, z));
                    if (TreeHelper.isBranch(state)) {
                        continue columns;
                    }
                    leaves |= state.getBlock() instanceof DynamicLeavesBlock;
                }
                if (leaves) {
                    ground++;
                    shaded += level.getBrightness(LightLayer.SKY, column.set(x, OAK_FROM.getY(), z)) < 15 ? 1 : 0;
                }
            }
        }
        int leafBlocks = oakLeaves - bareBlocks;
        double lightAtLeaf = leafBlocks == 0 ? 15.0 : lightAtLeaves / (double) leafBlocks;
        long[] walked = walkerCounters(minecraft.getSingleplayerServer());
        log(String.format(Locale.ROOT, "%s: of the big oak's %d spots %d hold the bare block; sky light at the leaf blocks left %.1f on average, "
                        + "at the darkest bare block %d; %d of %d ground columns under the crowns in shade; the server's walker so far: %d made bare, "
                        + "%d given leaves, %d chunks and %d sections looked at, %d rounds, %.1f ms",
                name, oakLeaves, bareBlocks, lightAtLeaf, darkestBare, shaded, ground, walked[0], walked[1], walked[2], walked[3], walked[4], walked[5] / 1.0e6));
        // The player's game against the server. Dynamic Trees does not send a leaf block's new supply
        // number to the players when only the number changed (its own leaves look the same with any
        // number). Such a spot may be a step ahead or behind in the player's game until its block is
        // swapped, which sends the whole block. Those spots are counted, and left out of the next check.
        // The falling leaves, and what the walker did for this one look.
        long leaves = FallingLeaves.made() - leavesBefore;
        int aliveNow = FallingLeaves.alive();
        mostLeavesAlive = Math.max(mostLeavesAlive, aliveNow);
        long[] step = new long[LeafWalker.COUNTERS];
        for (int i = 0; i < step.length; i++) {
            step[i] = walked[i] - walkedBefore[i];
        }
        log(String.format(Locale.ROOT, "%s: %d leaves came down for this look, %d on their way; the walker swapped %d and %d, "
                        + "looked at %d chunks in %d rounds, counted %d sections, searched %d, in %.1f ms"
                        + " (%.2f ms per round), %d lots of snow moved down, %d fruit dropped",
                name, leaves, aliveNow, step[LeafWalker.TO_BARE], step[LeafWalker.TO_LEAF], step[LeafWalker.CHUNKS_LOOKED],
                step[LeafWalker.ROUNDS], step[LeafWalker.SECTIONS_COUNTED], step[LeafWalker.SECTIONS_SEARCHED],
                step[LeafWalker.NANOS] / 1.0e6,
                step[LeafWalker.ROUNDS] > 0 ? step[LeafWalker.NANOS] / 1.0e6 / step[LeafWalker.ROUNDS] : 0.0,
                step[LeafWalker.SNOW_LANDED], step[LeafWalker.FRUIT_DROPPED]));
        expect(name + ": no more leaves are on their way than the settings allow (" + aliveNow + ")",
                aliveNow <= FallingLeaves.settings().most());
        switch (time) {
            // How many leaves one look catches is a matter of luck: they
            // start at the few places the look happens to draw, and on the
            // first day of the fall only a handful of spots in the scene
            // are losing leaves at all. So the leaves of all the days are
            // counted together and held against zero once, in finish().
            case FALL -> leavesWhileFalling += leaves;
            case FULL, BARE -> expect(name + ": no leaf comes down outside the days they fall (" + leaves + ")", leaves == 0);
            default -> { }
        }
        if (time == Time.BARE && step[LeafWalker.TO_BARE] == 0 && step[LeafWalker.TO_LEAF] == 0) {
            // A look at a forest that is already bare: nothing is wrong,
            // and the sections are done by their count alone.
            expect(name + ": a round over a forest that is already bare searches no section block by block ("
                            + step[LeafWalker.SECTIONS_SEARCHED] + " searched, " + step[LeafWalker.SECTIONS_COUNTED] + " counted)",
                    step[LeafWalker.SECTIONS_SEARCHED] == 0);
        }

        byte[] onServer = againstServer(minecraft.getSingleplayerServer(), spots, held);
        int otherBlock = 0;
        int otherNumber = 0;
        int wrong = 0;
        for (int i = 0; i < onServer.length; i++) {
            otherBlock += onServer[i] == 2 ? 1 : 0;
            otherNumber += onServer[i] == 1 ? 1 : 0;
            wrong += onServer[i] == 0 && wrongBlock.get(i) ? 1 : 0;
        }
        expect(name + ": the player's game holds the same block as the server at every spot (" + otherBlock + " differ)", otherBlock == 0);
        expect(name + ": few spots have a supply number the player's game was not told (" + otherNumber + " of " + oakLeaves + ")", otherNumber * 50 <= oakLeaves);
        expect(name + ": every spot of the big oak holds the block its stage asks for (" + wrong + " do not)", wrong == 0);
        switch (time) {
            case FULL -> expect(name + ": in full leaf most of the ground under the crowns is in shade (" + shaded + " of " + ground + " columns)",
                    bareBlocks == 0 && shaded * 2 >= ground);
            case BARE -> {
                expect(name + ": under the bare crowns the ground has the full light of the sky (" + shaded + " of " + ground + " columns in shade)",
                        bareBlocks == oakLeaves && shaded == 0);
                expect(name + ": no spot inside a bare crown is dark (sky light " + darkestBare + " at the darkest)", darkestBare >= 13);
            }
            default -> {
                if (bareBlocks * 5 >= oakLeaves * 3) {
                    expect(String.format(Locale.ROOT, "%s: with most of the crown bare, the leaves left are lit by the sky (%.1f on average)", name, lightAtLeaf),
                            lightAtLeaf >= 12.0);
                }
            }
        }
        double perOakLeaf = oakFaces / (double) Math.max(1, oakLeaves);
        StringBuilder stageReport = new StringBuilder();
        for (LeafStage stage : LeafStage.values()) {
            if (stages[stage.ordinal()] > 0) {
                stageReport.append(' ').append(stage.name().toLowerCase(Locale.ROOT)).append(' ').append(stages[stage.ordinal()]);
            }
        }
        log(String.format(Locale.ROOT, "%s: the big oak has %d leaf blocks, %.2f faces each; by stage:%s", name, oakLeaves, perOakLeaf, stageReport));
        double full = stages[LeafStage.FULL.ordinal()] / (double) Math.max(1, oakLeaves);
        double bare = stages[LeafStage.BARE.ordinal()] / (double) Math.max(1, oakLeaves);

        expect(name + ": the model hands out exactly the faces of the recipe (" + agree + " of " + oakLeaves + ")", agree == oakLeaves);
        if (plain && time == Time.FULL) {
            expect(name + ": in full leaf the plain look is left to the model the leaves had before", ours == 0);
        } else if (!plain) {
            expect(name + ": every leaf block of the big oak is drawn by a recipe", ours == oakLeaves);
        }
        switch (time) {
            case FULL -> expect(name + ": every leaf block of the big oak is in full leaf", full == 1.0);
            case BARE -> expect(name + ": every leaf block of the big oak is bare", bare == 1.0);
            case FALL -> expect(name + ": the big oak shows only stages of autumn", stages[LeafStage.BUDS.ordinal()]
                    + stages[LeafStage.FIRST.ordinal()] + stages[LeafStage.HALF.ordinal()] == 0);
            case RETURN -> expect(name + ": the big oak shows only stages of spring", stages[LeafStage.THIN.ordinal()]
                    + stages[LeafStage.THINNER.ordinal()] + stages[LeafStage.LAST.ordinal()] == 0);
            default -> { }
        }

        if (time == Time.FULL && !current.tracked()) {
            checkSummerShot(minecraft, current, counted, faces, slanted);
            return;
        }

        // A day of the year: never more faces than the unmodded game, far fewer in winter.
        boolean rich = current.settings().maxFaces() > ShapeSettings.DEFAULT.maxFaces();
        if (!rich) {
            expect(String.format(Locale.ROOT, "%s: at most 6 faces per leaf block (%.2f)", name, faces), faces <= 6.0);
        }
        if (!current.fancy()) {
            expect(String.format(Locale.ROOT, "%s: only outer faces are drawn (%.2f per leaf block, none at a slant)", name, faces),
                    faces < 3.5 && counted[2] == 0);
        } else if (time == Time.BARE) {
            expect(String.format(Locale.ROOT, "%s: bare trees need few faces (%.2f per leaf block)", name, faces), faces < 2.0);
            if (current.settings().winterTwigs() == WinterTwigs.NONE) {
                // The other kinds of trees keep their leaves and their cards; the big oak has nothing left.
                expect(name + ": without twigs the big oak draws nothing", oakFaces == 0);
            } else {
                expect(String.format(Locale.ROOT, "%s: the big oak shows twigs (%.2f faces per leaf block)", name, perOakLeaf), oakFaces > 0 && perOakLeaf < 2.0);
            }
        }

        // The picture: how much open sky shows.
        int[] now = picture(minecraft);
        double sky = skyShare(now);
        log(String.format(Locale.ROOT, "%s: open sky covers %.1f %% of the picture; of the big oak %.0f %% in full leaf, %.0f %% bare", name,
                100 * sky, 100 * full, 100 * bare));
        if (!current.tracked()) {
            return;
        }
        // The walk through the year: each day against the day before.
        if (time == Time.FULL && skyInSummer < 0) {
            skyInSummer = sky;
        }
        if (fullBefore >= 0) {
            switch (time) {
                case FALL -> {
                    expect(String.format(Locale.ROOT, "%s: the big oak has no more leaves than the day before (in full leaf %.2f after %.2f, bare %.2f after %.2f)",
                            name, full, fullBefore, bare, bareBefore), full <= fullBefore && bare >= bareBefore);
                    expect(String.format(Locale.ROOT, "%s: no less sky shows than the day before (%.3f after %.3f)", name, sky, skyBefore), sky >= skyBefore - 0.004);
                }
                case BARE -> {
                    expect(String.format(Locale.ROOT, "%s: much more sky shows than in summer (%.3f against %.3f)", name, sky, skyInSummer), sky > skyInSummer + 0.03);
                    if (!current.gentle() && bareBefore == 1.0) {
                        // The look before this one was the same day, drawn again on its own.
                        expect(String.format(Locale.ROOT, "%s: drawn again on request, the picture shows the same sky as drawn on its own (%.3f and %.3f)",
                                name, sky, skyBefore), Math.abs(sky - skyBefore) < 0.003);
                    }
                }
                case RETURN -> {
                    expect(String.format(Locale.ROOT, "%s: the big oak has no fewer leaves than the day before (in full leaf %.2f after %.2f, bare %.2f after %.2f)",
                            name, full, fullBefore, bare, bareBefore), full >= fullBefore && bare <= bareBefore);
                    expect(String.format(Locale.ROOT, "%s: no more sky shows than the day before (%.3f after %.3f)", name, sky, skyBefore), sky <= skyBefore + 0.004);
                }
                case FULL -> expect(String.format(Locale.ROOT, "%s: as much sky shows as in summer (%.3f against %.3f)", name, sky, skyInSummer),
                        Math.abs(sky - skyInSummer) < 0.02);
                default -> { }
            }
        }
        fullBefore = full;
        bareBefore = bare;
        skyBefore = sky;
    }

    /** The checks of a shape in summer: its face counts, and its picture against the plain look. */
    private static void checkSummerShot(Minecraft minecraft, Shot current, long[] counted, double faces, double slanted) {
        String name = current.name();
        if (current.settings().shape() == LeafShape.PLAIN) {
            plainFaces = faces;
            plainPicture = picture(minecraft);
            expect(String.format(Locale.ROOT, "plain: about 6 faces per leaf block (%.2f)", faces), faces > 5.0 && faces <= 6.0);
            expect(name + ": nothing drawn at a slant", counted[2] == 0);
            return;
        }
        if (!current.fancy()) {
            // Leaves that are not see-through: only the outside of the crowns.
            expect(String.format(Locale.ROOT, "%s: only outer faces are drawn (%.2f per leaf block, none at a slant)", name, faces),
                    faces < 3.5 && counted[2] == 0);
            return;
        }
        boolean rich = current.settings().maxFaces() > ShapeSettings.DEFAULT.maxFaces();
        double limit = rich ? MOST_FACES_RICH : MOST_FACES;
        double slantedLimit = rich ? MOST_SLANTED_RICH : MOST_SLANTED;
        expect(String.format(Locale.ROOT, "%s: at most %.1f faces per leaf block (%.2f)", name, limit, faces), faces <= limit);
        if (!rich) {
            expect(String.format(Locale.ROOT, "%s: fewer faces than the plain look (%.2f against %.2f)", name, faces, plainFaces), faces < plainFaces);
        }
        expect(String.format(Locale.ROOT, "%s: at most %.1f slanted faces per leaf block (%.2f)", name, slantedLimit, slanted), slanted <= slantedLimit);
        boolean slantedExpected = !current.settings().fastLeaves() && current.settings().shape() != LeafShape.SHELL
                && (rich || current.settings().shape() != LeafShape.TUFTS);
        if (slantedExpected) {
            expect(name + ": cards or twigs are drawn", counted[2] > 0);
        } else {
            expect(name + ": nothing drawn at a slant", counted[2] == 0);
        }

        // The picture: the shape is really on screen, and the crowns are about as full as before.
        int[] now = picture(minecraft);
        if (plainPicture != null && plainPicture.length == now.length) {
            int changed = 0;
            for (int i = 0; i < now.length; i++) {
                changed += differs(now[i], plainPicture[i]) ? 1 : 0;
            }
            double changedShare = changed / (double) now.length;
            double leafyBefore = leafyShare(plainPicture);
            double leafyNow = leafyShare(now);
            log(String.format(Locale.ROOT, "%s: %.1f %% of the picture differs from plain; leaf green covers %.1f %% (plain %.1f %%); open sky %.1f %%",
                    name, 100 * changedShare, 100 * leafyNow, 100 * leafyBefore, 100 * skyShare(now)));
            expect(name + ": the picture differs from the plain look", changedShare > 0.02);
            expect(name + ": the crowns are about as full as in the plain look", leafyNow > 0.7 * leafyBefore && leafyNow < 1.4 * leafyBefore);
        }
    }

    /**
     * The share of a picture's pixels that are open sky: clearly blue, and
     * bluer than anything else in the scene. It does not depend on the
     * color of the leaves, which changes with the season.
     */
    private static double skyShare(int[] pixels) {
        int sky = 0;
        for (int pixel : pixels) {
            int red = pixel & 0xFF;
            int green = pixel >> 8 & 0xFF;
            int blue = pixel >> 16 & 0xFF;
            sky += blue >= 170 && blue >= green + 20 && green >= red + 8 ? 1 : 0;
        }
        return sky / (double) pixels.length;
    }

    /**
     * What is on screen right now, every second pixel of every second
     * row, as whole numbers in the order the game's images use (blue,
     * green, red from the top byte down, after the opacity).
     */
    private static int[] picture(Minecraft minecraft) {
        try (NativeImage image = Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
            int width = image.getWidth() / 2;
            int height = image.getHeight() / 2;
            int[] pixels = new int[width * height];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    pixels[y * width + x] = image.getPixelRGBA(2 * x, 2 * y);
                }
            }
            return pixels;
        }
    }

    /** Whether two pixels differ by more than a trifle. */
    private static boolean differs(int a, int b) {
        return Math.abs((a & 0xFF) - (b & 0xFF)) + Math.abs((a >> 8 & 0xFF) - (b >> 8 & 0xFF)) + Math.abs((a >> 16 & 0xFF) - (b >> 16 & 0xFF)) > 24;
    }

    /** The share of a picture's pixels that are clearly green: leaves (and the grass, which is the same in every look). */
    private static double leafyShare(int[] pixels) {
        int leafy = 0;
        for (int pixel : pixels) {
            int red = pixel & 0xFF;
            int green = pixel >> 8 & 0xFF;
            int blue = pixel >> 16 & 0xFF;
            leafy += green > red + 8 && green > blue + 8 ? 1 : 0;
        }
        return leafy / (double) pixels.length;
    }

    /** Sodium's own counters (memory for faces, chunk builder), if Sodium is loaded. */
    private static String sodiumCounters() {
        if (!SODIUM) {
            return "no Sodium";
        }
        try {
            Class<?> type = Class.forName("net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer");
            Object renderer = type.getMethod("instance").invoke(null);
            Collection<?> lines = (Collection<?>) type.getMethod("getDebugStrings").invoke(renderer);
            return "Sodium: " + lines;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "Sodium counters not readable: " + e;
        }
    }

    // ------------------------------------------------------------------
    // Reporting
    // ------------------------------------------------------------------

    /** Writes one check into the log and remembers it if it did not hold. */
    private static void expect(String what, boolean holds) {
        if (holds) {
            passed++;
            log("ok: " + what);
        } else {
            FAILURES.add(what);
            log("FAILED: " + what);
        }
    }

    /** Writes the result line and closes the game. */
    private static void finish(Minecraft minecraft) {
        finished = true;
        log("leaves that came down over all the days of the fall: " + leavesWhileFalling
                + "; the most on their way at once: " + mostLeavesAlive);
        long[] walked = walkerCounters(minecraft.getSingleplayerServer());
        log(String.format(Locale.ROOT, "the walker over the whole run: %d spots made bare, %d given their leaves back, "
                        + "%d chunks looked at, %d sections counted, %d of them searched block by block, %d rounds, "
                        + "%.1f ms in all, %d lots of snow moved down, %d fruit dropped",
                walked[LeafWalker.TO_BARE], walked[LeafWalker.TO_LEAF], walked[LeafWalker.CHUNKS_LOOKED],
                walked[LeafWalker.SECTIONS_COUNTED], walked[LeafWalker.SECTIONS_SEARCHED], walked[LeafWalker.ROUNDS],
                walked[LeafWalker.NANOS] / 1.0e6, walked[LeafWalker.SNOW_LANDED], walked[LeafWalker.FRUIT_DROPPED]));
        expect("leaves come down while the leaves fall (" + leavesWhileFalling + " over the days of the fall)",
                leavesWhileFalling > 0);
        if (FAILURES.isEmpty()) {
            log("RESULT: PASSED, " + passed + " checks, " + Math.max(0, shot) + " looks photographed");
        } else {
            log("RESULT: FAILED, " + FAILURES.size() + " of " + (passed + FAILURES.size()) + " checks: " + FAILURES);
        }
        minecraft.stop();
    }

    /** Deletes the world of an earlier run, so that every run starts the same. */
    private static void deleteOldWorld(Minecraft minecraft) {
        LevelStorageSource saves = minecraft.getLevelSource();
        if (!saves.levelExists(WORLD_NAME)) {
            return;
        }
        try (LevelStorageSource.LevelStorageAccess access = saves.createAccess(WORLD_NAME)) {
            access.deleteLevel();
        } catch (IOException e) {
            log("could not delete the old world: " + e);
        }
    }

    private static void log(String text) {
        Foliage.LOGGER.info("[Foliage self test] {}", text);
    }
}
