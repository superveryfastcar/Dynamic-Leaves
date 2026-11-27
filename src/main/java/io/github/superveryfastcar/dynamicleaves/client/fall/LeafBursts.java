package io.github.superveryfastcar.dynamicleaves.client.fall;

import io.github.superveryfastcar.dynamicleaves.season.LeafClock;
import io.github.superveryfastcar.dynamicleaves.season.LeafSchedule;
import io.github.superveryfastcar.dynamicleaves.season.LeafStage;
import io.github.superveryfastcar.dynamicleaves.shape.LeafLook;
import io.github.superveryfastcar.dynamicleaves.shape.Surround;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import javax.annotation.Nullable;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Vector3f;

/**
 * Lets a leaf block give off a few leaves in the moment it changes while
 * the player is looking: when it thins, when it shrinks, when it goes
 * bare. Without this a spot that changes is simply another picture from
 * one frame to the next. With it, the eye sees leaves come off where the
 * crown just got thinner, and reads the change as leaves falling.
 *
 * <h2>When</h2>
 * The leaves of a tree change in steps, a few per day
 * ({@link io.github.superveryfastcar.dynamicleaves.season.SeasonSettings#stepsPerDay}).
 * At each step in autumn, {@code client.ClientLeafClock} hands this class
 * the calendar before the step and the one after. Every leaf spot can be
 * asked what it showed before and what it shows now, so nothing has to be
 * remembered from one step to the next and nothing is sent by a server.
 *
 * <h2>Where</h2>
 * Only the leaf spots within the distance of the falling leaves
 * ({@link FallSettings#distance}) are looked at, and of those only the
 * ones the player is looking toward. The space around the player is gone
 * through in pieces of 16 by 16 by 16 blocks, nearest first and a few per
 * tick, so the leaves start near the player first, as the landscape is
 * drawn again nearest first. A piece without leaf blocks is told by its
 * list of block kinds and costs next to nothing.
 *
 * <h2>What it costs</h2>
 * Nothing outside autumn, and nothing with {@link FallSettings#burst} at
 * 0. In autumn a look through the pieces around the player a few times a
 * day, spread over a few ticks each time. The leaves themselves are the
 * same as those of {@link FallingLeaves} and share their limit
 * ({@link FallSettings#most}), so a whole forest that changes at once
 * still never holds more leaves than the player allows.
 *
 * <h2>Thread rule</h2>
 * Everything here runs on the game's main thread.
 */
public final class LeafBursts {

    /** How many pieces of 16 by 16 by 16 blocks that hold leaf blocks are searched per tick. */
    private static final int SEARCHED_PER_TICK = 4;

    /** How many pieces are looked at per tick at most, searched or not. */
    private static final int LOOKED_AT_PER_TICK = 48;

    /**
     * How far from the middle of the view a leaf spot may lie to count as
     * "in view", as the cosine of the angle (60 degrees). The view of the
     * game is wider than it is high and wider still with a high field of
     * view, so this is a wide cone on purpose.
     */
    private static final double VIEW_COSINE = 0.5;

    /** A leaf spot nearer than this (in blocks) counts as in view wherever the player looks: leaves drift into view. */
    private static final double ALWAYS_NEAR = 4.0;

    /** The sides a leaf can leave a leaf block by: below, and the four around. Not above, where it would lie on the block. */
    private static final Direction[] WAYS_OUT = {Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

    /** How far outside a leaf block's side a leaf starts, in blocks. */
    private static final double OUTSIDE = 0.06;

    /** The step being worked on: the calendar before it and after it. Null when there is nothing to do. */
    @Nullable
    private static LeafClock before;
    @Nullable
    private static LeafClock after;

    /** The world the step belongs to. */
    private static WeakReference<ClientLevel> world = new WeakReference<>(null);

    /** The pieces still to look at (as {@link SectionPos#asLong}), nearest first, and how many of them are done. */
    private static long[] pieces = new long[0];
    private static int pieceCount;
    private static int nextPiece;

    /** For sorting the pieces by distance. */
    private static long[] sortKeys = new long[0];

    /** For the tests and the self test: leaves started and time spent since the game started. */
    private static long made;
    private static long spots;
    private static long nanos;

    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();
    private static final BlockPos.MutableBlockPos NEAR = new BlockPos.MutableBlockPos();
    private static final Direction[] FREE = new Direction[WAYS_OUT.length];

    private LeafBursts() {
    }

    /** How many leaves this class has started since the game started. */
    public static long made() {
        return made;
    }

    /** How many leaf spots have given off leaves since the game started. */
    public static long spots() {
        return spots;
    }

    /** How much time this class has spent since the game started, in billionths of a second. */
    public static long nanos() {
        return nanos;
    }

    /** Whether a step is still being worked on. */
    public static boolean busy() {
        return after != null;
    }

    /** The player has left a world: nothing of it is worked on any more. */
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        forget();
    }

    private static void forget() {
        before = null;
        after = null;
        pieceCount = 0;
        nextPiece = 0;
        world = new WeakReference<>(null);
    }

    /**
     * The leaf calendar of the player's game has moved on by a step that
     * may change what leaf spots show. Called by
     * {@code client.ClientLeafClock} at the start of a tick.
     *
     * @param was the calendar before the step, or null if there was none
     * @param now the calendar after it, or null if there is none
     */
    public static void onStep(@Nullable LeafClock was, @Nullable LeafClock now) {
        forget();
        FallSettings settings = FallingLeaves.settings();
        if (was == null || now == null || !settings.enabled() || settings.burst() <= 0 || settings.most() <= 0) {
            return;
        }
        // Only the passing of time in autumn. A change of the rules, or a
        // jump back in time, redraws the forest without any leaves flying.
        if (!now.falling() || !now.hasKinds() || now.step() <= was.step()
                || !now.settings().equals(was.settings()) || !now.rules().equals(was.rules())) {
            return;
        }
        Minecraft game = Minecraft.getInstance();
        ClientLevel level = game.level;
        if (level == null || game.options.particles().get() == ParticleStatus.MINIMAL) {
            return;
        }
        Vec3 eye = game.gameRenderer.getMainCamera().getPosition();
        int reach = settings.distance();
        int minX = SectionPos.blockToSectionCoord(Mth.floor(eye.x) - reach);
        int maxX = SectionPos.blockToSectionCoord(Mth.floor(eye.x) + reach);
        int minY = Math.max(level.getMinSection(), SectionPos.blockToSectionCoord(Mth.floor(eye.y) - reach));
        int maxY = Math.min(level.getMaxSection() - 1, SectionPos.blockToSectionCoord(Mth.floor(eye.y) + reach));
        int minZ = SectionPos.blockToSectionCoord(Mth.floor(eye.z) - reach);
        int maxZ = SectionPos.blockToSectionCoord(Mth.floor(eye.z) + reach);
        int count = Math.max(0, maxX - minX + 1) * Math.max(0, maxY - minY + 1) * Math.max(0, maxZ - minZ + 1);
        if (count == 0) {
            return;
        }
        if (pieces.length < count) {
            pieces = new long[count];
            sortKeys = new long[count];
        }
        // Sorted by distance with one sort of whole numbers: the distance in
        // the high half, the piece's place in the list in the low half.
        long[] found = new long[count];
        int index = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    double dx = SectionPos.sectionToBlockCoord(x) + 8 - eye.x;
                    double dy = SectionPos.sectionToBlockCoord(y) + 8 - eye.y;
                    double dz = SectionPos.sectionToBlockCoord(z) + 8 - eye.z;
                    found[index] = SectionPos.asLong(x, y, z);
                    sortKeys[index] = (long) (dx * dx + dy * dy + dz * dz) << 20 | index;
                    index++;
                }
            }
        }
        Arrays.sort(sortKeys, 0, count);
        for (int i = 0; i < count; i++) {
            pieces[i] = found[(int) (sortKeys[i] & 0xFFFFF)];
        }
        pieceCount = count;
        nextPiece = 0;
        before = was;
        after = now;
        world = new WeakReference<>(level);
    }

    /** Called at the end of every tick of the game: looks at the next few pieces of the step being worked on. */
    public static void onTick(ClientTickEvent.Post event) {
        LeafClock now = after;
        LeafClock was = before;
        if (now == null || was == null) {
            return;
        }
        Minecraft game = Minecraft.getInstance();
        ClientLevel level = game.level;
        if (level == null || world.get() != level) {
            forget();
            return;
        }
        if (game.isPaused()) {
            return;
        }
        FallSettings settings = FallingLeaves.settings();
        ParticleStatus wanted = game.options.particles().get();
        if (!settings.enabled() || settings.burst() <= 0 || wanted == ParticleStatus.MINIMAL) {
            forget();
            return;
        }
        long start = System.nanoTime();
        int perSpot = wanted == ParticleStatus.DECREASED ? Math.max(1, settings.burst() / 2) : settings.burst();
        Camera camera = game.gameRenderer.getMainCamera();
        int searched = 0;
        int lookedAt = 0;
        while (nextPiece < pieceCount && searched < SEARCHED_PER_TICK && lookedAt < LOOKED_AT_PER_TICK
                && FallingLeaves.alive() < settings.most()) {
            long piece = pieces[nextPiece++];
            lookedAt++;
            if (search(level, piece, was, now, camera, settings, perSpot)) {
                searched++;
            }
        }
        if (nextPiece >= pieceCount || FallingLeaves.alive() >= settings.most()) {
            // Done, or the air is as full of leaves as the player allows: the rest of this step goes without.
            forget();
        }
        nanos += System.nanoTime() - start;
    }

    /**
     * Looks through one piece of 16 by 16 by 16 blocks for leaf spots
     * that have just changed, and starts leaves at those in view.
     *
     * @return true if the piece holds leaf blocks and was searched block by block
     */
    private static boolean search(ClientLevel level, long piece, LeafClock was, LeafClock now, Camera camera,
                                  FallSettings settings, int perSpot) {
        int sectionX = SectionPos.x(piece);
        int sectionY = SectionPos.y(piece);
        int sectionZ = SectionPos.z(piece);
        LevelChunk chunk = level.getChunkSource().getChunk(sectionX, sectionZ, ChunkStatus.FULL, false);
        if (chunk == null) {
            return false;
        }
        int index = chunk.getSectionIndexFromSectionY(sectionY);
        if (index < 0 || index >= chunk.getSectionsCount()) {
            return false;
        }
        LevelChunkSection section = chunk.getSection(index);
        if (section.hasOnlyAir() || !section.maybeHas(state -> state.getBlock() instanceof LeavesBlock)) {
            return false;
        }
        Vec3 eye = camera.getPosition();
        Vector3f view = camera.getLookVector();
        double reach = settings.distance();
        int baseX = SectionPos.sectionToBlockCoord(sectionX);
        int baseY = SectionPos.sectionToBlockCoord(sectionY);
        int baseZ = SectionPos.sectionToBlockCoord(sectionZ);
        LeafLook look = LeafLook.forThread();
        RandomSource random = level.random;
        for (int dy = 0; dy < 16; dy++) {
            for (int dz = 0; dz < 16; dz++) {
                for (int dx = 0; dx < 16; dx++) {
                    BlockState state = section.getBlockState(dx, dy, dz);
                    if (!(state.getBlock() instanceof LeavesBlock)) {
                        continue;
                    }
                    int x = baseX + dx;
                    int y = baseY + dy;
                    int z = baseZ + dz;
                    double toX = x + 0.5 - eye.x;
                    double toY = y + 0.5 - eye.y;
                    double toZ = z + 0.5 - eye.z;
                    double distance = Math.sqrt(toX * toX + toY * toY + toZ * toZ);
                    if (distance > reach) {
                        continue;
                    }
                    if (distance > ALWAYS_NEAR && toX * view.x() + toY * view.y() + toZ * view.z() < VIEW_COSINE * distance) {
                        continue;
                    }
                    if (!lostLeaves(look, was, now, level, state, x, y, z)) {
                        continue;
                    }
                    if (burst(level, state, x, y, z, perSpot, settings.most(), random)) {
                        spots++;
                    }
                    if (FallingLeaves.alive() >= settings.most()) {
                        return true;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Whether a leaf spot shows fewer leaves after a step than before it:
     * it has thinned, shrunk or gone bare.
     */
    private static boolean lostLeaves(LeafLook look, LeafClock was, LeafClock now, ClientLevel level, BlockState state,
                                      int x, int y, int z) {
        // The stages of autumn follow each other in the order they are numbered: full, thin, thinner, last, bare.
        LeafStage stage = look.stage(now, level, state, x, y, z);
        if (stage == LeafStage.FULL || stage.ordinal() > LeafStage.BARE.ordinal()) {
            return false;
        }
        LeafSchedule schedule = was.schedule(state.getBlock());
        if (schedule == null) {
            return false;
        }
        return was.stage(schedule, x, y, z, Surround.depth(state)).ordinal() < stage.ordinal();
    }

    /**
     * Starts a few leaves at a leaf spot: anywhere in its block if the
     * spot is bare already (a bare block lets everything through), else
     * just outside one of its sides that have free air next to them.
     *
     * @param count how many leaves to start
     * @param most  how many leaves may be on their way at once
     * @return true if at least one leaf was started
     */
    private static boolean burst(ClientLevel level, BlockState state, int x, int y, int z, int count, int most, RandomSource random) {
        CURSOR.set(x, y, z);
        boolean through = state.getCollisionShape(level, CURSOR).isEmpty();
        int free = 0;
        if (!through) {
            for (Direction way : WAYS_OUT) {
                NEAR.setWithOffset(CURSOR, way);
                if (level.getBlockState(NEAR).getCollisionShape(level, NEAR).isEmpty()) {
                    FREE[free++] = way;
                }
            }
            if (free == 0) {
                // Buried in the crown: a leaf would have nowhere to go, and nobody could see it.
                return false;
            }
        }
        boolean any = false;
        for (int i = 0; i < count && FallingLeaves.alive() < most; i++) {
            double leafX = x + random.nextDouble();
            double leafY = y + random.nextDouble();
            double leafZ = z + random.nextDouble();
            if (!through) {
                Direction way = FREE[random.nextInt(free)];
                switch (way) {
                    case DOWN -> leafY = y - OUTSIDE;
                    case NORTH -> leafZ = z - OUTSIDE;
                    case SOUTH -> leafZ = z + 1 + OUTSIDE;
                    case WEST -> leafX = x - OUTSIDE;
                    default -> leafX = x + 1 + OUTSIDE;
                }
            }
            FallingLeaves.startAt(level, state, CURSOR, leafX, leafY, leafZ, random);
            made++;
            any = true;
        }
        return any;
    }
}
