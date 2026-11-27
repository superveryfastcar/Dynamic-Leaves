package io.github.superveryfastcar.dynamicleaves.dev;

import io.github.superveryfastcar.dynamicleaves.DynamicLeaves;
import io.github.superveryfastcar.dynamicleaves.world.LeafWalker;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * A photo session for tree shapes, for whoever works on the mod.
 *
 * <p>Tree shapes are judged by eye, and a drawing made outside the game
 * shows neither Dynamic Trees' real branches nor this mod's leaves. This
 * class plants the trees a text file lists on a flat world, photographs
 * them from the camera spots and on the dates the same file lists, and
 * closes the game. It checks nothing: {@link DevSelfTest} does that.</p>
 *
 * <p>Start it with {@code ./gradlew runClient -Pdynamicleaves.show=<file>}. The
 * pictures go into the game's {@code screenshots} folder, named
 * {@code show-<date>-<view>.png}. The file holds one thing per line
 * (lines starting with {@code #} are notes):</p>
 * <pre>
 * tree dynamictrees:oak JJJJOOP07+vU 0 -13 0     species, growth code, x, z, quarter turns
 * view front 0.5 -55 8.5 180 -5                  name, then place and where to look (as for /tp)
 * date summer 7                                  a date of the Calendar mod
 * </pre>
 * <p>The flat world's ground is at y = -61, so a player stands at y = -60.
 * {@code tools/limbs/layers.py show} writes such a file.</p>
 */
@EventBusSubscriber(modid = DynamicLeaves.MOD_ID, value = Dist.CLIENT)
public final class DevTreeShow {

    /** The file that lists trees, views and dates; empty when no session was asked for. */
    private static final String FILE = System.getProperty("dynamicleaves.dev.show", "");

    /** The name of the world the session builds and deletes again on its next run. */
    private static final String WORLD_NAME = "dynamicleaves-tree-show";

    /** Ticks to let the fresh world settle before the first picture. */
    private static final int SETTLE_TICKS = 160;

    /** After this long the session gives up and closes the game (20 minutes). */
    private static final long GIVE_UP_NANOS = 1_200_000_000_000L;

    /** A camera spot: its name and the arguments of the teleport command. */
    private record View(String name, String where) {
    }

    private static final List<String> TREES = new ArrayList<>();
    private static final List<View> VIEWS = new ArrayList<>();
    private static final List<String> DATES = new ArrayList<>();

    private static boolean loaded;
    private static boolean worldRequested;
    private static boolean finished;
    private static int ticksInWorld;
    private static long enteredNanos;
    /** Which date and which view come next. */
    private static int date;
    private static int view;
    /** 0: set the date, 1: wait for the landscape, 2: photograph. */
    private static int phase;
    /** Ticks still to wait before the next step. */
    private static int wait;
    /** Ticks in a row in which nothing was left to draw or to swap. */
    private static int quiet;

    private DevTreeShow() {
    }

    /** Runs once per client tick and moves the session on by one small step. */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (FILE.isBlank() || finished) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.options.pauseOnLostFocus = false;
        if (!loaded) {
            loaded = true;
            if (!load()) {
                finished = true;
                minecraft.stop();
                return;
            }
        }
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
            minecraft.options.cloudStatus().set(CloudStatus.OFF);
            minecraft.options.renderDistance().set(8);
            minecraft.options.fov().set(70);
            run(server, "weather clear", "gamerule doDaylightCycle false", "gamerule doWeatherCycle false",
                    "gamerule randomTickSpeed 0", "gamerule doMobSpawning false", "time set 6000");
            for (String tree : TREES) {
                run(server, tree);
            }
            run(server, "kill @e[type=minecraft:bee]");
            log("planted " + TREES.size() + " trees");
            return;
        }
        if (System.nanoTime() - enteredNanos > GIVE_UP_NANOS) {
            log("RESULT: GAVE UP after 20 minutes");
            finished = true;
            minecraft.stop();
            return;
        }
        if (ticksInWorld < SETTLE_TICKS || --wait > 0) {
            return;
        }
        switch (phase) {
            case 0 -> {
                if (date >= DATES.size()) {
                    log("RESULT: DONE, " + DATES.size() * VIEWS.size() + " pictures");
                    finished = true;
                    minecraft.stop();
                    return;
                }
                view = 0;
                run(server, "calendar set " + DATES.get(date), "time set 6000", "kill @e[type=minecraft:bee]",
                        "tp @a " + VIEWS.get(0).where());
                wait = 80;
                phase = 1;
            }
            case 1 -> {
                // The date's leaf looks are drawn by the client: ask for the whole landscape again.
                minecraft.levelRenderer.allChanged();
                quiet = 0;
                wait = 20;
                phase = 2;
            }
            case 2 -> {
                // Wait until the server has swapped the blocks the date asks for and all of it is drawn.
                boolean swapped = server.submit(() -> LeafWalker.of(server.overworld()).pending() == 0).join();
                quiet = minecraft.levelRenderer.hasRenderedAllSections() && swapped ? quiet + 1 : 0;
                if (quiet < 30) {
                    return;
                }
                String name = String.format(Locale.ROOT, "show-%s-%s.png", DATES.get(date).replace(' ', '-'),
                        VIEWS.get(view).name());
                Screenshot.grab(minecraft.gameDirectory, name, minecraft.getMainRenderTarget(), message -> { });
                log("saved " + name);
                view++;
                if (view >= VIEWS.size()) {
                    date++;
                    phase = 0;
                    wait = 2;
                } else {
                    run(server, "tp @a " + VIEWS.get(view).where());
                    quiet = 0;
                    wait = 30;
                }
            }
            default -> {
                finished = true;
                minecraft.stop();
            }
        }
    }

    /** Reads the session's file. False if it cannot be read or lists nothing to photograph. */
    private static boolean load() {
        try {
            for (String line : Files.readAllLines(Path.of(FILE))) {
                String[] words = line.trim().split("\\s+");
                if (words.length == 6 && words[0].equals("tree")) {
                    // "dt settree" wants the soil block: the flat world's top block at y = -61.
                    TREES.add("dt settree " + words[3] + " -61 " + words[4] + " " + words[1] + " " + words[2]
                            + " " + words[5] + " 0");
                } else if (words.length == 7 && words[0].equals("view")) {
                    VIEWS.add(new View(words[1], String.join(" ", List.of(words).subList(2, 7))));
                } else if (words.length == 3 && words[0].equals("date")) {
                    DATES.add(words[1] + " " + words[2]);
                } else if (!line.isBlank() && !line.trim().startsWith("#")) {
                    log("line not understood, skipped: " + line);
                }
            }
        } catch (IOException e) {
            log("RESULT: could not read " + FILE + ": " + e);
            return false;
        }
        if (TREES.isEmpty() || VIEWS.isEmpty() || DATES.isEmpty()) {
            log("RESULT: " + FILE + " needs at least one tree, one view and one date");
            return false;
        }
        return true;
    }

    /** Runs commands as the server console, on the server thread. */
    private static void run(IntegratedServer server, String... commands) {
        server.execute(() -> {
            for (String command : commands) {
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
            }
        });
    }

    /** Deletes the world of an earlier session, so that every session starts the same. */
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
        DynamicLeaves.LOGGER.info("[Dynamic Leaves tree show] {}", text);
    }
}
