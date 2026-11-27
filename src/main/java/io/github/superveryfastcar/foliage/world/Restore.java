package io.github.superveryfastcar.foliage.world;

import io.github.superveryfastcar.foliage.Foliage;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Turns every bare block of a whole world back into its leaf block, land
 * that nobody has visited included: the sweep behind
 * {@code /foliage restore}.
 *
 * <h2>Why</h2>
 * A bare block is a block of Foliage. In a world the mod is taken out of,
 * every bare block left standing is lost, and the crown it belonged to
 * has a hole until Dynamic Trees grows a leaf there again. Switching the
 * bare blocks off ({@code bareBlocks = false}) lets the walker
 * ({@link LeafWalker}) put every chunk right as it comes into reach, but
 * only land somebody goes to. This sweep goes to the rest.
 *
 * <h2>How</h2>
 * One world at a time, one region file at a time (a region file holds up
 * to 32 by 32 chunks):
 * <ol>
 *   <li>The world is written to disk first, so that every chunk that is
 *       loaded right now, in reach of a player or not, is in the region
 *       files as it stands.</li>
 *   <li>Then every region file of the world is read. Its first 4,096
 *       bytes say which of its 1,024 chunks have ever been written, and
 *       only those are visited.</li>
 *   <li>Each of those chunks is first opened at the lowest step
 *       ({@code ChunkStatus.EMPTY}), which reads what stands on disk and
 *       makes nothing. Only a chunk that was written as a finished chunk
 *       is then asked for in full, swept ({@link LeafWalker#sweep}) and
 *       left to the game, which writes it back and unloads it again on
 *       its own.</li>
 * </ol>
 * A few chunks per tick, so that the server keeps running; the number
 * can be given with the command. The sweep says how far it is every few
 * seconds, and what it did at the end.
 *
 * <h2>No new land</h2>
 * A region file names the half-made chunks around the land that has been
 * visited as well as the finished ones. Asking for one of those in full
 * would make it: its caves, its trees and its light, and half-made
 * chunks further out with it, so a sweep would go on making land for as
 * long as it ran. No bare block can stand in a chunk that was never
 * finished, so the sweep opens every chunk at the lowest step first,
 * which only reads what is on disk, and leaves the half-made ones alone.
 *
 * <h2>What it costs</h2>
 * Loading a finished chunk is the same work the game does when a player
 * comes near, and writing it back is the same work as saving the world.
 *
 * <h2>The light of a swept chunk</h2>
 * A leaf block dims the light a little and a bare block does not, so the
 * game works the light out anew wherever a block is swapped
 * ({@code LevelChunk.setBlockState} asks the light engine itself, see
 * {@link LeafWalker}). The light engine does that work spread over the
 * ticks that follow, and a chunk this sweep touched may be unloaded again
 * before its turn comes. Then the light written to disk for it is the
 * light of the bare crown, which is a shade too bright under the leaves
 * that came back. Nothing is broken by it: the game works the light of a
 * chunk out again the next time a player comes near it, and the mod is on
 * its way out of the world in any case. It is written down here because
 * it is the one thing a sweep leaves behind.
 *
 * <h2>Thread rule</h2>
 * Everything here runs on the server's main thread, from the end of each
 * tick of the server. Only the reading of the region files' first bytes
 * touches the disk by itself, and that is a handful of bytes per region.
 */
public final class Restore {

    /** How many chunks a sweep visits per tick if nothing else is said. */
    public static final int DEFAULT_CHUNKS_PER_TICK = 4;

    /** The least and the most chunks per tick the command takes. */
    public static final int MIN_CHUNKS_PER_TICK = 1;
    public static final int MAX_CHUNKS_PER_TICK = 64;

    /** The most time one tick of the sweep may take, in billionths of a second. */
    private static final long NANOS_PER_TICK = 20_000_000L;

    /** How often the sweep says how far it is, in ticks. */
    private static final int REPORT_TICKS = 200;

    /** The size of a region file's table of contents, and how many chunks it names. */
    private static final int HEADER_BYTES = 4096;
    private static final int CHUNKS_PER_REGION = 1024;
    private static final int REGION_WIDTH = 32;

    /** The sweep that is running, or null. */
    @Nullable
    private static Restore running;

    /** Where the sweep says how far it is. */
    public interface Listener {
        /** Says how far the sweep is, in words. */
        void say(String message);
    }

    /** How far a sweep is. */
    public record Progress(String level, int levelsLeft, int regionsDone, int regions, long chunksVisited,
                           long blocksRestored, long seconds) {
    }

    private final MinecraftServer server;
    private final Listener listener;
    private final int chunksPerTick;

    /** The worlds still to sweep; the one being swept is the first. */
    private final Deque<ServerLevel> levels = new ArrayDeque<>();

    /** The region files of the world being swept, still to read. */
    private final Deque<Path> regions = new ArrayDeque<>();

    /** The chunks of the region being swept, still to visit. */
    private final LongArrayList chunks = new LongArrayList();
    private int chunkAt;

    private int regions0;
    private int regionsDone;
    private long chunksVisited;
    private long blocksRestored;
    private long startedAt;
    private int ticks;

    private Restore(MinecraftServer server, Listener listener, int chunksPerTick) {
        this.server = server;
        this.listener = listener;
        this.chunksPerTick = chunksPerTick;
    }

    // ------------------------------------------------------------------
    // Starting and stopping
    // ------------------------------------------------------------------

    /**
     * Starts a sweep over every world of a server.
     *
     * @param server        the server
     * @param listener      where the sweep says how far it is
     * @param chunksPerTick how many chunks to visit per tick
     * @return false if a sweep is already running
     */
    public static boolean start(MinecraftServer server, Listener listener, int chunksPerTick) {
        if (running != null) {
            return false;
        }
        Restore restore = new Restore(server, listener, chunksPerTick);
        for (ServerLevel level : server.getAllLevels()) {
            restore.levels.add(level);
        }
        restore.startedAt = System.nanoTime();
        running = restore;
        restore.openLevel();
        return true;
    }

    /** Whether a sweep is running. */
    public static boolean isRunning() {
        return running != null;
    }

    /** How far the sweep is, or null if none is running. */
    @Nullable
    public static Progress progress() {
        Restore restore = running;
        return restore == null ? null : restore.where();
    }

    /** Stops the sweep. Everything already swept stays swept. */
    public static void stop() {
        running = null;
    }

    /** Called when the server stops, so that nothing is left over. */
    public static void serverStopped() {
        running = null;
    }

    /**
     * Called at the end of every tick of the server.
     *
     * @param server the server
     */
    public static void tick(MinecraftServer server) {
        Restore restore = running;
        if (restore != null && restore.server == server) {
            restore.step();
        }
    }

    // ------------------------------------------------------------------
    // One tick of the sweep
    // ------------------------------------------------------------------

    private void step() {
        long deadline = System.nanoTime() + NANOS_PER_TICK;
        int visited = 0;
        while (visited < chunksPerTick) {
            if (chunkAt >= chunks.size() && !nextRegion()) {
                // Counted before the last word, so that it says how many
                // chunks were really looked at.
                chunksVisited += visited;
                finish();
                return;
            }
            long pos = chunks.getLong(chunkAt++);
            visited++;
            visit(levels.peek(), pos);
            if (System.nanoTime() >= deadline) {
                break;
            }
        }
        chunksVisited += visited;
        if (++ticks % REPORT_TICKS == 0) {
            Progress at = where();
            listener.say("Restoring leaves in " + at.level() + ": region " + at.regionsDone() + " of " + at.regions()
                    + ", " + at.chunksVisited() + " chunks looked at, " + at.blocksRestored() + " blocks restored.");
        }
    }

    /**
     * Puts every spot of one chunk right, if that chunk was ever
     * finished, and leaves it to the game again.
     *
     * <p>The chunk is first opened at the lowest step, which reads what
     * stands on disk and makes nothing. A chunk that was written as a
     * finished one comes back as the finished chunk itself (as the game's
     * own stand-in for it, {@link ImposterProtoChunk}, while nobody holds
     * it); a half-made one comes back as what it is and is left alone,
     * because no bare block can stand in a chunk that was never
     * finished. Only then is the chunk asked for in full, which for a
     * chunk that is already finished makes no land (see the class
     * notes).</p>
     */
    private void visit(ServerLevel level, long pos) {
        int x = ChunkPos.getX(pos);
        int z = ChunkPos.getZ(pos);
        ChunkAccess onDisk = level.getChunkSource().getChunk(x, z, ChunkStatus.EMPTY, true);
        if (!(onDisk instanceof LevelChunk) && !(onDisk instanceof ImposterProtoChunk)) {
            return;
        }
        ChunkAccess chunk = level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, true);
        if (chunk instanceof LevelChunk full) {
            blocksRestored += LeafWalker.of(level).sweep(full);
        }
    }

    /**
     * Takes the next region file of the world being swept and lists the
     * chunks in it that have ever been written. Moves on to the next
     * world when a world is done.
     *
     * @return false if there is nothing left to sweep anywhere
     */
    private boolean nextRegion() {
        chunks.clear();
        chunkAt = 0;
        while (true) {
            Path region = regions.poll();
            if (region != null) {
                regionsDone++;
                readRegion(region);
                if (!chunks.isEmpty()) {
                    return true;
                }
                continue;
            }
            levels.poll();
            if (levels.isEmpty()) {
                return false;
            }
            openLevel();
            if (!chunks.isEmpty()) {
                return true;
            }
        }
    }

    /**
     * Starts on the world at the head of the queue: the world is written
     * to disk, and then its region files are listed.
     *
     * <p>The world is written first because the region files are what
     * the sweep goes by. A chunk that is loaded but that nobody is
     * watching any more (it has gone out of reach and has not been
     * written since) would otherwise hold bare blocks that no list names,
     * and would be written back with them after the sweep had said the
     * mod could be taken out.</p>
     */
    private void openLevel() {
        ServerLevel level = levels.peek();
        if (level == null) {
            return;
        }
        level.save(null, true, false);
        regions.clear();
        regionsDone = 0;
        chunks.clear();
        chunkAt = 0;
        Path folder = DimensionType.getStorageFolder(level.dimension(), server.getWorldPath(LevelResource.ROOT))
                .resolve("region");
        List<Path> files = new ArrayList<>();
        if (Files.isDirectory(folder)) {
            try (Stream<Path> found = Files.list(folder)) {
                found.filter(path -> path.getFileName().toString().endsWith(".mca"))
                        .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                        .forEach(files::add);
            } catch (IOException e) {
                Foliage.LOGGER.warn("Foliage cannot read the chunks of {} from disk; only the chunks that are loaded "
                        + "are restored there.", level.dimension().location(), e);
            }
        }
        regions.addAll(files);
        regions0 = files.size();
        listener.say("Restoring leaves in " + name(level) + ": " + files.size() + " region files to go through.");
    }

    /** Puts the chunks of one region file into the queue. */
    private void readRegion(Path file) {
        for (long pos : chunksWrittenIn(file)) {
            chunks.add(pos);
        }
    }

    /**
     * Which chunks of a region file have ever been written, read from its
     * table of contents: 1,024 numbers of four bytes, one per chunk, 0
     * for a chunk that was never written. Nothing else of the file is
     * read, and nothing is unpacked.
     *
     * <p>Public because the tests read a region file written by hand with
     * it.</p>
     *
     * @param file a region file, named {@code r.X.Z.mca}
     * @return the chunks it names, as {@link ChunkPos#asLong}; empty if
     *         the name is not a region's, or the file cannot be read
     */
    public static long[] chunksWrittenIn(Path file) {
        int[] here = parseRegionName(file.getFileName().toString());
        if (here == null) {
            return new long[0];
        }
        LongArrayList found = new LongArrayList();
        try (InputStream in = Files.newInputStream(file);
             DataInputStream data = new DataInputStream(in)) {
            byte[] header = new byte[HEADER_BYTES];
            data.readFully(header);
            for (int index = 0; index < CHUNKS_PER_REGION; index++) {
                int at = index * 4;
                // Three bytes of offset and one of length; all zero means "never written".
                if ((header[at] | header[at + 1] | header[at + 2] | header[at + 3]) == 0) {
                    continue;
                }
                int x = here[0] * REGION_WIDTH + index % REGION_WIDTH;
                int z = here[1] * REGION_WIDTH + index / REGION_WIDTH;
                found.add(ChunkPos.asLong(x, z));
            }
        } catch (IOException e) {
            Foliage.LOGGER.warn("Foliage cannot read {}; the chunks in it are not restored.", file, e);
        }
        return found.toLongArray();
    }

    /**
     * The x and z of a region from its file name, {@code r.X.Z.mca}, or
     * null if the name is not one. Public for the tests.
     */
    @Nullable
    public static int[] parseRegionName(String name) {
        String[] parts = name.split("\\.");
        if (parts.length != 4 || !"r".equals(parts[0])) {
            return null;
        }
        try {
            return new int[] {Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void finish() {
        Progress at = where();
        running = null;
        listener.say("Leaves restored: " + at.chunksVisited() + " chunks looked at, " + at.blocksRestored()
                + " bare blocks turned back into leaves, in " + at.seconds() + " seconds. "
                + "Foliage can be taken out of this world now.");
        Foliage.LOGGER.info("Foliage restored {} blocks in {} chunks.", at.blocksRestored(), at.chunksVisited());
    }

    private Progress where() {
        ServerLevel level = levels.peek();
        return new Progress(level == null ? "-" : name(level), Math.max(0, levels.size() - 1), regionsDone, regions0,
                chunksVisited, blocksRestored, (System.nanoTime() - startedAt) / 1_000_000_000L);
    }

    /** A world's name as a player knows it. */
    private static String name(ServerLevel level) {
        return level.dimension() == Level.OVERWORLD ? "the Overworld" : level.dimension().location().toString();
    }

}
