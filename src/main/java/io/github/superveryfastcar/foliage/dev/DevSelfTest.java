package io.github.superveryfastcar.foliage.dev;

import com.dtteam.dynamictrees.block.leaves.DynamicLeavesBlock;
import com.dtteam.dynamictrees.tree.species.Species;
import com.dtteam.dynamictrees.worldgen.JoCode;
import com.mojang.blaze3d.platform.NativeImage;
import io.github.superveryfastcar.foliage.Foliage;
import io.github.superveryfastcar.foliage.client.SeasonsLink;
import io.github.superveryfastcar.foliage.client.model.LeafModels;
import io.github.superveryfastcar.foliage.client.sprite.LeafPartsSource;
import io.github.superveryfastcar.foliage.shape.LeafShape;
import io.github.superveryfastcar.foliage.shape.Recipe;
import io.github.superveryfastcar.foliage.shape.ShapeSettings;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
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
import net.minecraft.resources.ResourceLocation;
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
 * block, and that the models step aside when they should. It saves
 * pictures of the same scene in every shape for a human to judge the
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
 *   <li>Works through its list of looks ({@link #SHOTS}). For each one it
 *       sets the look, has the landscape drawn again, waits until that is
 *       done, checks numbers, and saves a picture from each camera spot
 *       ({@link #VIEWS}) into the game's {@code screenshots} folder.</li>
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
    private static final long GIVE_UP_NANOS = 1_500_000_000_000L;

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
            new View("under", "3.5 -60 -9.5 144 -52"),
            new View("above", "0.5 -44 12.5 180 22"),
            new View("kinds", "56.5 -54 14.5 180 -8"));

    /**
     * One look to check and photograph.
     *
     * @param name     for the log and the pictures' file names
     * @param settings the shape settings to draw by
     * @param fancy    the game's own graphics setting: true for see-through
     *                 leaves ("fancy"), false for leaves that are not ("fast")
     * @param date     the calendar date to show it at
     */
    private record Shot(String name, ShapeSettings settings, boolean fancy, String date) {
    }

    private static final String SUMMER = "summer 7";

    private static final List<Shot> SHOTS = List.of(
            new Shot("plain", ShapeSettings.DEFAULT.withShape(LeafShape.PLAIN), true, SUMMER),
            new Shot("shell", ShapeSettings.DEFAULT.withShape(LeafShape.SHELL), true, SUMMER),
            new Shot("bushy", ShapeSettings.DEFAULT.withShape(LeafShape.BUSHY), true, SUMMER),
            new Shot("tufts", ShapeSettings.DEFAULT.withShape(LeafShape.TUFTS), true, SUMMER),
            new Shot("mixed", ShapeSettings.DEFAULT.withShape(LeafShape.MIXED), true, SUMMER),
            new Shot("mixed-rich", ShapeSettings.DEFAULT.withShape(LeafShape.MIXED).withMaxFaces(ShapeSettings.MAX_FACES), true, SUMMER),
            new Shot("bushy-rich", ShapeSettings.DEFAULT.withShape(LeafShape.BUSHY).withMaxFaces(ShapeSettings.MAX_FACES), true, SUMMER),
            new Shot("tufts-rich", ShapeSettings.DEFAULT.withShape(LeafShape.TUFTS).withMaxFaces(ShapeSettings.MAX_FACES), true, SUMMER),
            new Shot("mixed-fewest-faces", ShapeSettings.DEFAULT.withShape(LeafShape.MIXED).withFastLeaves(true), true, SUMMER),
            new Shot("mixed-fast-graphics", ShapeSettings.DEFAULT.withShape(LeafShape.MIXED), false, SUMMER),
            new Shot("mixed-late-autumn", ShapeSettings.DEFAULT.withShape(LeafShape.MIXED), true, "autumn 13"));

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
                if (!current.date().equals(lastDate)) {
                    lastDate = current.date();
                    run(server, "calendar set " + current.date(), "time set 6000");
                    wait = 80;
                }
                phase = 1;
            }
            case 1 -> {
                Shot current = SHOTS.get(shot);
                LeafModels.setSettings(current.settings());
                minecraft.options.graphicsMode().set(current.fancy() ? GraphicsStatus.FANCY : GraphicsStatus.FAST);
                run(server, "tp @a " + VIEWS.get(0).where());
                LeafModels.resetCounters();
                minecraft.levelRenderer.allChanged();
                redrawStart = System.nanoTime();
                quiet = 0;
                wait = 20;
                phase = 2;
            }
            case 2 -> {
                // Wait until the chunk builder has caught up, then a little longer.
                quiet = minecraft.levelRenderer.hasRenderedAllSections() ? quiet + 1 : 0;
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
                if (view >= VIEWS.size()) {
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
        for (String twigs : List.of("twigs_brown", "twigs_birch", "twigs_dark")) {
            ResourceLocation name = ResourceLocation.fromNamespaceAndPath(Foliage.MOD_ID, "block/" + twigs + "_0");
            expect("the twig pictures " + twigs + " are loaded", atlas.getSprite(name).contents().name().equals(name));
        }
        int leaves = 0;
        for (BlockPos pos : BlockPos.betweenClosed(OAK_FROM, OAK_TO)) {
            if (minecraft.level.getBlockState(pos).getBlock() instanceof DynamicLeavesBlock) {
                leaves++;
            }
        }
        expect("the big oak stands (" + leaves + " leaf blocks)", leaves > 300);
    }

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
        boolean autumn = !current.date().equals(SUMMER);
        boolean steppedAside = plain || autumn;

        // What the chunk builder was handed, over everything it built.
        long[] counted = LeafModels.counters();
        double faces = counted[1] / (double) Math.max(1, counted[0]);
        double slanted = counted[2] / (double) Math.max(1, counted[0]);
        log(String.format(Locale.ROOT, "%s at %s: redrawn in about %.1f s; leaf blocks %d, faces %d, faces per leaf block %.2f, slanted %.2f; recipes kept %d; %s",
                name, current.date(), (System.nanoTime() - redrawStart) / 1.0e9 - 1.5, counted[0], counted[1], faces, slanted,
                LeafModels.recipeCount(), sodiumCounters()));
        expect(name + ": the chunk builder drew leaf blocks (" + counted[0] + ")", counted[0] > 2000);

        // The big oak, leaf block by leaf block: the model hands out what its recipe says.
        int oakLeaves = 0;
        int ours = 0;
        int agree = 0;
        long oakFaces = 0;
        for (BlockPos pos : BlockPos.betweenClosed(OAK_FROM, OAK_TO)) {
            if (!(level.getBlockState(pos).getBlock() instanceof DynamicLeavesBlock)) {
                continue;
            }
            oakLeaves++;
            int recipe = LeafModels.recipeAt(level, pos);
            int handedOut = LeafModels.faceCountAt(level, pos);
            oakFaces += Math.max(0, handedOut);
            if (recipe >= 0) {
                ours++;
                agree += handedOut == Recipe.quadCount(recipe) ? 1 : 0;
            } else {
                agree += handedOut == 6 ? 1 : 0;
            }
        }
        double perOakLeaf = oakFaces / (double) Math.max(1, oakLeaves);
        log(String.format(Locale.ROOT, "%s: the big oak has %d leaf blocks, %.2f faces each", name, oakLeaves, perOakLeaf));

        if (steppedAside) {
            expect(name + ": the leaf models step aside" + (autumn ? " while Seasons thins the leaves" : ""), ours == 0);
            expect(name + ": every leaf block of the big oak is the plain cube (" + agree + " of " + oakLeaves + ")", agree == oakLeaves);
            expect(name + ": nothing drawn at a slant", counted[2] == 0);
            if (autumn) {
                expect(name + ": Seasons thins the leaves", SeasonsLink.thinning());
            }
        } else {
            expect(name + ": every leaf block of the big oak is drawn by a recipe", ours == oakLeaves);
            expect(name + ": the model hands out exactly the faces of the recipe (" + agree + " of " + oakLeaves + ")", agree == oakLeaves);
            expect(name + ": no thinning by Seasons in summer", !SeasonsLink.thinning());
        }

        if (plain) {
            plainFaces = faces;
            plainPicture = picture(minecraft);
            expect(String.format(Locale.ROOT, "plain: about 6 faces per leaf block (%.2f)", faces), faces > 5.0 && faces <= 6.0);
            return;
        }
        if (autumn) {
            expect(String.format(Locale.ROOT, "%s: about 6 faces per leaf block (%.2f)", name, faces), faces > 5.0 && faces <= 6.0);
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
            log(String.format(Locale.ROOT, "%s: %.1f %% of the picture differs from plain; leaf green covers %.1f %% (plain %.1f %%)",
                    name, 100 * changedShare, 100 * leafyNow, 100 * leafyBefore));
            expect(name + ": the picture differs from the plain look", changedShare > 0.02);
            expect(name + ": the crowns are about as full as in the plain look", leafyNow > 0.7 * leafyBefore && leafyNow < 1.4 * leafyBefore);
        }
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
