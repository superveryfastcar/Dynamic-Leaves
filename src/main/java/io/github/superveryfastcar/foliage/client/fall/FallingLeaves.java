package io.github.superveryfastcar.foliage.client.fall;

import io.github.superveryfastcar.foliage.Foliage;
import io.github.superveryfastcar.foliage.client.ClientLeafClock;
import io.github.superveryfastcar.foliage.season.LeafClock;
import io.github.superveryfastcar.foliage.season.LeafStage;
import io.github.superveryfastcar.foliage.shape.LeafLook;
import java.lang.ref.WeakReference;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Lets single leaves drift down from the crowns while the leaves fall, in
 * each player's game.
 *
 * <h2>Where a leaf comes from</h2>
 * Nothing is sent and nothing is stored. Every tick this class looks at a
 * few places drawn at random from the air around the player, and where it
 * finds a leaf block that is losing its leaves right now (the stages
 * "thin", "thinner" and "a few last leaves" of the leaf calendar) and the
 * block under it is free, one leaf starts there, in the color that tree's
 * leaves have that day.
 *
 * <p>Drawing places at random is the way the game itself finds the places
 * for its own weather and its smoke and dripping water. It costs the same
 * whether one tree or a forest is in view, and it finds the trees nearest
 * to the player most often, because there is more of them close by.</p>
 *
 * <p>On top of this steady fall, a leaf block gives off a few leaves in
 * the moment it thins, shrinks or goes bare in view of the player. That
 * is {@link LeafBursts}; its leaves are made here and count toward the
 * same limit.</p>
 *
 * <h2>What it costs</h2>
 * Outside the days when leaves fall this class does nothing at all: the
 * leaf calendar is asked first, and in summer and winter it answers in one
 * line. While they fall it reads a handful of blocks per tick
 * ({@link io.github.superveryfastcar.foliage.client.FoliageClientConfig}:
 * {@code amount}) and never holds more leaves than the player allows
 * ({@code most}). A player who wants none switches them off.
 *
 * <h2>Thread rule</h2>
 * Everything here runs on the game's main thread, from the end of each
 * tick.
 */
public final class FallingLeaves {

    /** How many places are looked at per tick for each step of {@code amount}. */
    private static final int PLACES_PER_AMOUNT = 8;

    /**
     * The pictures a falling leaf is drawn from, among the game's block
     * pictures (see {@link FallingLeaf#getRenderType()} for why they are
     * there and not among the pictures of particles).
     */
    private static final ResourceLocation[] PICTURES = {
        ResourceLocation.fromNamespaceAndPath(Foliage.MOD_ID, "block/falling_leaf_0"),
        ResourceLocation.fromNamespaceAndPath(Foliage.MOD_ID, "block/falling_leaf_1"),
        ResourceLocation.fromNamespaceAndPath(Foliage.MOD_ID, "block/falling_leaf_2"),
        ResourceLocation.fromNamespaceAndPath(Foliage.MOD_ID, "block/falling_leaf_3"),
    };

    /** How far under a leaf block a leaf starts, in blocks. */
    private static final double BELOW = 0.1;

    /** The settings in use. */
    private static volatile FallSettings settings = FallSettings.DEFAULT;

    /** How many leaves are on their way right now. */
    private static int alive;

    /**
     * The world the count belongs to. The game throws every particle away
     * when a player goes to another world (a portal, a bed in another
     * dimension) without telling the particles themselves, so the count
     * would keep leaves that are long gone and in the end allow none at
     * all. It is held weakly, so that a world that is done with is not
     * kept alive by this.
     */
    private static WeakReference<ClientLevel> counting = new WeakReference<>(null);

    /** For the tests and the self test: how many leaves have been made since the game started. */
    private static long made;

    /** Where the places are drawn from. */
    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();

    private FallingLeaves() {
    }

    /** Takes new settings into use. */
    public static void setSettings(FallSettings newSettings) {
        settings = newSettings;
    }

    /** The settings in use. */
    public static FallSettings settings() {
        return settings;
    }

    /** How many leaves are on their way, and how many have been made since the game started. */
    public static int alive() {
        return alive;
    }

    /** How many leaves have been made since the game started. */
    public static long made() {
        return made;
    }

    /** How many pictures a falling leaf is drawn from. */
    public static int pictures() {
        return PICTURES.length;
    }

    /**
     * The name of one of the pictures, among the game's block pictures.
     * The self test ({@code dev.DevSelfTest}) looks them up in the atlas,
     * because a picture that is not there would be drawn as the game's
     * "missing picture".
     */
    public static ResourceLocation picture(int number) {
        return PICTURES[number];
    }

    /** Told by a leaf that it is gone. */
    static void gone() {
        if (alive > 0) {
            alive--;
        }
    }

    /** The player has left a world: no leaf of it is on its way any more. */
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        alive = 0;
        counting = new WeakReference<>(null);
    }

    /**
     * Called at the end of every tick of the game: looks at a few places
     * and starts a leaf where one is due.
     */
    public static void onTick(ClientTickEvent.Post event) {
        FallSettings now = settings;
        Minecraft game = Minecraft.getInstance();
        ClientLevel level = game.level;
        Entity eye = game.getCameraEntity();
        if (level == null) {
            return;
        }
        if (counting.get() != level) {
            // Another world: every leaf of the one before was thrown away
            // with it.
            alive = 0;
            counting = new WeakReference<>(level);
        }
        if (!now.enabled() || eye == null || game.isPaused()) {
            return;
        }
        // The player's own choice about particles comes first.
        ParticleStatus wanted = game.options.particles().get();
        if (wanted == ParticleStatus.MINIMAL) {
            return;
        }
        LeafClock clock = ClientLeafClock.clock();
        if (clock == null || !clock.falling() || !clock.hasKinds()) {
            return;
        }
        if (alive >= now.most()) {
            return;
        }
        int places = now.amount() * PLACES_PER_AMOUNT;
        if (wanted == ParticleStatus.DECREASED) {
            places = Math.max(1, places / 2);
        }
        RandomSource random = level.random;
        int reach = now.distance();
        int middleX = Mth.floor(eye.getX());
        int middleY = Mth.floor(eye.getEyeY());
        int middleZ = Mth.floor(eye.getZ());
        LeafLook look = LeafLook.forThread();
        for (int i = 0; i < places && alive < now.most(); i++) {
            int x = middleX + random.nextInt(reach * 2 + 1) - reach;
            int z = middleZ + random.nextInt(reach * 2 + 1) - reach;
            // Crowns stand above the player more often than below.
            int y = middleY + random.nextInt(reach + 1) - reach / 3;
            CURSOR.set(x, y, z);
            if (!level.hasChunkAt(CURSOR)) {
                continue;
            }
            BlockState state = level.getBlockState(CURSOR);
            if (!(state.getBlock() instanceof LeavesBlock)) {
                continue;
            }
            LeafStage stage = look.stage(clock, level, state, x, y, z);
            if (stage != LeafStage.THIN && stage != LeafStage.THINNER && stage != LeafStage.LAST) {
                continue;
            }
            CURSOR.set(x, y - 1, z);
            if (!level.getBlockState(CURSOR).isAir()) {
                // Inside the crown: a leaf would be caught at once.
                continue;
            }
            start(level, state, x, y, z, random);
        }
    }

    /** Starts one leaf under a leaf block. */
    private static void start(ClientLevel level, BlockState state, int x, int y, int z, RandomSource random) {
        CURSOR.set(x, y, z);
        startAt(level, state, CURSOR, x + random.nextDouble(), y - BELOW, z + random.nextDouble(), random);
    }

    /**
     * Starts one leaf at a place, in the color the leaves of a leaf block
     * have that day, and counts it. Whoever calls this looks at the
     * player's limit first ({@link #alive}, {@link FallSettings#most}).
     *
     * @param state the leaf block the leaf comes from (or its bare block)
     * @param pos   where that block stands
     * @param x     where the leaf starts
     * @param y     where the leaf starts
     * @param z     where the leaf starts
     */
    static void startAt(ClientLevel level, BlockState state, BlockPos pos, double x, double y, double z, RandomSource random) {
        int color = Minecraft.getInstance().getBlockColors().getColor(state, level, pos, 0);
        if (color == -1) {
            color = BiomeColors.getAverageFoliageColor(level, pos);
        }
        TextureAtlasSprite picture = Minecraft.getInstance()
                .getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
                .apply(PICTURES[random.nextInt(PICTURES.length)]);
        Minecraft.getInstance().particleEngine.add(new FallingLeaf(level, x, y, z, color, picture));
        alive++;
        made++;
    }
}
