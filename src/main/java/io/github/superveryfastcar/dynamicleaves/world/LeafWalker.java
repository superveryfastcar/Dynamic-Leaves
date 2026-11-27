package io.github.superveryfastcar.dynamicleaves.world;

import com.dtteam.dynamictrees.block.Growable;
import com.dtteam.dynamictrees.block.fruit.FruitBlock;
import com.dtteam.dynamictrees.tree.TreeHelper;
import io.github.superveryfastcar.calendar.api.Calendar;
import io.github.superveryfastcar.calendar.api.CalendarRules;
import io.github.superveryfastcar.dynamicleaves.block.BareLeavesBlock;
import io.github.superveryfastcar.dynamicleaves.block.Twins;
import io.github.superveryfastcar.dynamicleaves.season.LeafCalendar;
import io.github.superveryfastcar.dynamicleaves.season.LeafClock;
import io.github.superveryfastcar.dynamicleaves.season.SeasonSettings;
import io.github.superveryfastcar.seasons.api.SeasonsApi;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongArrays;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.level.ChunkTicketLevelUpdatedEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Keeps the leaf spots of a world in step with the leaf calendar: swaps a
 * leaf block for its bare block when its spot goes bare, and back when
 * the leaves return. One walker per world, on the server.
 *
 * <h2>What "in step" means</h2>
 * {@link SpotRule} says for every leaf spot and every moment which of its
 * two blocks it holds. The walker looks at chunks and makes the blocks
 * agree with the rule. It remembers nothing about what it did: a spot
 * that is wrong, for whatever reason, is put right the next time its
 * chunk is looked at.
 *
 * <h2>When it looks</h2>
 * <ul>
 *   <li><b>At every step of the leaf calendar</b> (a few times a day) at
 *       every chunk in reach, the ones nearest to the players first. In
 *       summer and in winter the rule does not change from step to step,
 *       but Dynamic Trees may have put leaf blocks into a bare crown (a
 *       sapling that became a tree), and those are found then.</li>
 *   <li><b>When a chunk comes into reach</b>: one that was just made, or
 *       that nobody had been near for a while.</li>
 *   <li><b>In the tick after a bare block noticed a wrong leaf block next
 *       to it</b> ({@link #noticed}): a twig that grew in winter.</li>
 * </ul>
 *
 * <h2>Which chunks are in reach</h2>
 * Only chunks whose changes the server passes on to the players: those it
 * holds at "ticking" level, which covers everything the players can see.
 * A chunk that is merely kept in memory is left alone until it comes
 * into reach. That way no player's game ever holds an old copy of a chunk
 * the walker has changed.
 *
 * <h2>What looking costs</h2>
 * A chunk is made of sections of 16 by 16 by 16 blocks, and each section
 * keeps a short list of the kinds of blocks in it. A section whose list
 * names nothing that could be wrong is skipped by that list alone: one
 * without leaves, a summer forest full of leaf blocks, a section where
 * there is no winter. That takes a few millionths of a second per chunk.
 *
 * <p>The list may still name a kind of block that has since left the
 * section: after the leaves of a forest were swapped, every list there
 * names both the leaf block and the bare block until the chunk is next
 * read from disk. So where the list alone says "maybe", and only one kind
 * of block could be wrong (in summer the bare blocks, in winter the leaf
 * blocks), the section's blocks are counted by kind ({@link #count}).
 * That takes a few hundredths of a thousandth of a second. Only a section
 * that does hold a wrong block, and every section with leaves while the
 * leaves fall or come back, is looked at block by block, which takes
 * about ten times as long.</p>
 *
 * <p>Per tick the walker stops when it has looked at
 * {@link WalkSettings#chunksPerTick} chunks, swapped
 * {@link WalkSettings#changesPerTick} blocks or used
 * {@link WalkSettings#millisPerTick}, and goes on in the next tick.</p>
 *
 * <h2>How a block is swapped</h2>
 * Quietly: the new block is written into the chunk, the players are told,
 * the light is worked out anew (the chunk does that itself), and that is
 * all. No neighbor is told and nothing reacts, because for the tree
 * nothing has changed: the bare block gives Dynamic Trees the same
 * answers as the leaf. Only what rested on a leaf that went bare is
 * looked after ({@link #settle}): snow lying on it falls to whatever is
 * below, fruit hanging under it drops off, and anything else that hung on
 * to it (a vine) is told that its hold is gone.
 *
 * <p>Animals on their way are not asked to think again either, as the
 * game would do for a block placed by hand: with hundreds of swaps in a
 * tick that would cost more than everything else here. A way that leads
 * through a crown gone bare is still good, and an animal that finds new
 * leaves in its way looks for another way as it does for any obstacle.
 * What each spot is for an animal that plans a new way is kept up to
 * date.</p>
 *
 * <h2>Thread rule</h2>
 * Everything here runs on the server's main thread.
 */
public final class LeafWalker {

    /** The server passes block changes on to the players for chunks at this ticket level or below. */
    private static final int REACH_LEVEL = 32;

    /** Chunks that are in reach but not ready yet are asked again after this many ticks. */
    private static final int RETRY_TICKS = 20;

    private static final Direction[] DIRECTIONS = Direction.values();

    private static final Predicate<BlockState> IS_BARE = state -> state.getBlock() instanceof BareLeavesBlock;
    private static final Predicate<Holder<Biome>> HAS_WINTER = biome -> SeasonsApi.hasWinter(biome.value());
    private static final Predicate<Holder<Biome>> HAS_NO_WINTER = biome -> !SeasonsApi.hasWinter(biome.value());

    /** The walkers of the worlds that are running. */
    private static final Map<ServerLevel, LeafWalker> WALKERS = new IdentityHashMap<>();

    /** The settings in use. */
    private static volatile WalkSettings settings = WalkSettings.DEFAULT;

    /** For tests: a leaf calendar to go by instead of the world's date, or null. */
    @Nullable
    private static volatile LeafClock fixedClock;

    private final ServerLevel level;

    /** The chunks in reach, by position. */
    private final Long2ObjectOpenHashMap<ChunkHolder> inReach = new Long2ObjectOpenHashMap<>();

    /** The chunks still to look at, in order, and the same as a set. */
    private final LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
    private final LongOpenHashSet queued = new LongOpenHashSet();

    /** Chunks that were in reach but not ready when their turn came. */
    private final LongArrayList waiting = new LongArrayList();

    /** Chunks in which a wrong block was noticed; they are looked at first in the next tick. */
    private final LongOpenHashSet urgent = new LongOpenHashSet();

    /** Spots under a bare block where fruit has appeared; it is taken away in the next tick. */
    private final LongArrayList fruitUnderBare = new LongArrayList();

    /** The leaf calendar at this moment. */
    @Nullable
    private LeafClock clock;

    /** The clock the last round over all chunks was started for. */
    @Nullable
    private LeafClock walkedFor;

    /** The settings the last round was started with. */
    @Nullable
    private WalkSettings walkedWith;

    /** Where to go on in the chunk at the head of the queue, if the last tick stopped inside it. */
    private long resumeChunk = Long.MIN_VALUE;
    private int resumeSection;

    /** Says of a kind of block whether it is a leaf block that sheds by the clock in use. */
    private final Predicate<BlockState> shedsLeaf = state -> {
        Block block = state.getBlock();
        LeafClock now = clock;
        return block instanceof LeavesBlock && !(block instanceof BareLeavesBlock) && now != null
                && now.schedule(block) != null && Twins.twinOf(block) != null;
    };

    /** Says of a kind of block whether it is a bare block of a kind that does not shed by the clock in use. */
    private final Predicate<BlockState> strayBare = state -> {
        LeafClock now = clock;
        return state.getBlock() instanceof BareLeavesBlock && (now == null || now.schedule(state.getBlock()) == null);
    };

    /** Where a section stands: all of it has a winter, none of it, or some of it. */
    private static final int WINTER_ALL = 0;
    private static final int WINTER_NONE = 1;
    private static final int WINTER_MIXED = 2;

    /** Position holders, to make no garbage. */
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private final BlockPos.MutableBlockPos near = new BlockPos.MutableBlockPos();

    /** How many blocks may still be swapped in this tick. */
    private int changesLeft;

    /** What the last count of a section found ({@link #count}). */
    private int countedBare;
    private int countedLeaves;
    private int countedStray;

    /** Sorts the blocks of a section into those three numbers; kept, to make no garbage. */
    private final PalettedContainer.CountConsumer<BlockState> counter = (state, number) -> {
        if (state.getBlock() instanceof BareLeavesBlock) {
            countedBare += number;
            if (this.strayBare.test(state)) {
                countedStray += number;
            }
        } else if (this.shedsLeaf.test(state)) {
            countedLeaves += number;
        }
    };

    // Counters since the world started, for tests and measurements.
    private long toBare;
    private long toLeaf;
    private long chunksLooked;
    private long sectionsSearched;
    private long rounds;
    private long nanos;
    private long sectionsCounted;
    private long snowLanded;
    private long fruitDropped;

    private LeafWalker(ServerLevel level) {
        this.level = level;
    }

    // ------------------------------------------------------------------
    // Set-up
    // ------------------------------------------------------------------

    /** Hooks the walkers into the game. Called once at start-up. */
    public static void register(IEventBus gameBus) {
        gameBus.addListener(LeafWalker::onLevelTick);
        gameBus.addListener(LeafWalker::onTicketLevel);
        gameBus.addListener((LevelEvent.Unload event) -> {
            if (event.getLevel() instanceof ServerLevel level) {
                WALKERS.remove(level);
            }
        });
        gameBus.addListener((ServerStoppedEvent event) -> WALKERS.clear());
    }

    /** The walker of a world; made at first use. */
    public static LeafWalker of(ServerLevel level) {
        return WALKERS.computeIfAbsent(level, LeafWalker::new);
    }

    /** The settings in use. */
    public static WalkSettings settings() {
        return settings;
    }

    /** Takes new settings into use. The walkers start a new round at their next tick. */
    public static void setSettings(WalkSettings newSettings) {
        settings = newSettings;
    }

    /**
     * For tests: makes every walker go by the given leaf calendar instead
     * of the world's date, or by the date again (null).
     */
    public static void fixClock(@Nullable LeafClock clock) {
        fixedClock = clock;
    }

    private static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            of(level).tick();
        }
    }

    /**
     * The game tells here every time it holds a chunk more tightly or
     * more loosely. A chunk that comes into reach is noted and gets a
     * look; one that goes out of reach is forgotten.
     */
    private static void onTicketLevel(ChunkTicketLevelUpdatedEvent event) {
        LeafWalker walker = of(event.getLevel());
        long pos = event.getChunkPos();
        ChunkHolder holder = event.getChunkHolder();
        if (event.getNewTicketLevel() <= REACH_LEVEL && holder != null) {
            if (walker.inReach.put(pos, holder) == null) {
                walker.enqueue(pos);
            }
        } else {
            walker.inReach.remove(pos);
        }
    }

    // ------------------------------------------------------------------
    // The rule, for one spot
    // ------------------------------------------------------------------

    /**
     * The leaf calendar this world's walker goes by at this moment.
     */
    public LeafClock clock() {
        LeafClock now = clockNow();
        clock = now;
        return now;
    }

    /**
     * The block the leaf calendar wants at a spot right now: the state
     * given, or the other block of its pair with the same supply number.
     * A state that is neither a leaf block nor a bare block of a pair
     * comes back as it is; so does any state outside a running world (a
     * chunk that is being made), where there is no date to go by.
     *
     * <p>Nothing is settled here as it is in {@link #settle}, and nothing
     * needs to be: this is a block coming into a spot that held air, so
     * there is nothing resting on it yet. Snow cannot have been lying
     * above, because snow needs a block under it, and fruit that Dynamic
     * Trees hangs under a bare block afterwards is taken away by
     * {@link #fruitAppeared}.</p>
     *
     * @param accessor the world
     * @param pos      the spot
     * @param state    the block that is to stand there
     */
    public static BlockState fit(LevelAccessor accessor, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        Block twin = Twins.twinOf(block);
        if (twin == null || !(accessor instanceof ServerLevel level) || !level.getServer().isSameThread()) {
            return state;
        }
        LeafClock clock = of(level).clock();
        boolean enabled = settings.enabled();
        // The cheap questions first; the biome is only read if the answer hangs on it.
        boolean bare = enabled && !clock.allFull() && clock.schedule(block) != null
                && SpotRule.bare(clock, true, block, winterAt(level, pos), pos.getX(), pos.getY(), pos.getZ(),
                        state.getValue(LeavesBlock.DISTANCE));
        return bare == (block instanceof BareLeavesBlock) ? state : Twins.swap(state, twin);
    }

    /**
     * Whether a spot stands where there is a winter. The biome is read as
     * the game stores it, in cubes of 4 by 4 by 4 blocks, exactly as the
     * leaf models of the players' games read it.
     */
    private static boolean winterAt(ServerLevel level, BlockPos pos) {
        return SeasonsApi.hasWinter(level.getNoiseBiome(pos.getX() >> 2, pos.getY() >> 2, pos.getZ() >> 2).value());
    }

    /**
     * Told by a bare block that a leaf block has appeared next to it. If
     * that leaf block is wrong by the leaf calendar, its chunk is looked
     * at first thing in the next tick.
     *
     * @param level the world
     * @param pos   where the leaf block stands
     * @param state the leaf block
     */
    public static void noticed(ServerLevel level, BlockPos pos, BlockState state) {
        if (level.getServer().isSameThread() && fit(level, pos, state) != state) {
            of(level).urgent.add(ChunkPos.asLong(pos));
        }
    }

    /**
     * Told by a bare block that fruit has appeared right under it.
     * Dynamic Trees hangs fruit under any leaf of the tree, and a bare
     * block is a leaf to it; the fruit is taken away in the next tick
     * ({@link #clearFruit}).
     *
     * @param level the world
     * @param pos   where the fruit hangs
     */
    public static void fruitAppeared(ServerLevel level, BlockPos pos) {
        if (level.getServer().isSameThread()) {
            of(level).fruitUnderBare.add(pos.asLong());
        }
    }

    // ------------------------------------------------------------------
    // Every tick
    // ------------------------------------------------------------------

    private void tick() {
        WalkSettings limits = settings;
        LeafClock now = clockNow();
        clock = now;
        // A new step of the calendar, new rules or new settings: every chunk in reach gets a look.
        if (now != walkedFor || !limits.equals(walkedWith)) {
            walkedFor = now;
            walkedWith = limits;
            startRound();
        }
        if (!waiting.isEmpty() && level.getGameTime() % RETRY_TICKS == 0) {
            for (int i = 0; i < waiting.size(); i++) {
                enqueue(waiting.getLong(i));
            }
            waiting.clear();
        }
        if (!fruitUnderBare.isEmpty()) {
            clearFruit();
        }
        if (queue.isEmpty() && urgent.isEmpty()) {
            return;
        }
        long started = System.nanoTime();
        long deadline = started + limits.nanosPerTick();
        changesLeft = limits.changesPerTick();
        int chunks = 0;
        if (!urgent.isEmpty()) {
            // Few and small: a chunk or two around a twig that just grew. They go ahead of the queue.
            long[] noticed = urgent.toLongArray();
            urgent.clear();
            for (long pos : noticed) {
                ChunkHolder holder = inReach.get(pos);
                LevelChunk chunk = holder == null ? null : holder.getTickingChunk();
                if (chunk == null) {
                    continue;
                }
                long keptChunk = resumeChunk;
                int keptSection = resumeSection;
                resumeChunk = Long.MIN_VALUE;
                if (!walkChunk(chunk, holder, limits.enabled(), Long.MAX_VALUE)) {
                    // Out of swaps for this tick: the rest of it in the next.
                    urgent.add(pos);
                }
                resumeChunk = keptChunk;
                resumeSection = keptSection;
            }
        }
        while (!queue.isEmpty() && chunks < limits.chunksPerTick()) {
            long pos = queue.firstLong();
            ChunkHolder holder = inReach.get(pos);
            LevelChunk chunk = holder == null ? null : holder.getTickingChunk();
            if (chunk == null) {
                // Out of reach by now, or not ready yet.
                queue.dequeueLong();
                queued.remove(pos);
                if (holder != null) {
                    waiting.add(pos);
                }
                continue;
            }
            chunks++;
            boolean done = walkChunk(chunk, holder, limits.enabled(), deadline);
            if (!done) {
                break;
            }
            queue.dequeueLong();
            queued.remove(pos);
            if (System.nanoTime() >= deadline) {
                break;
            }
        }
        nanos += System.nanoTime() - started;
    }

    /** The leaf calendar at this moment: the one in use if it still holds, else a new one. */
    private LeafClock clockNow() {
        LeafClock fixed = fixedClock;
        if (fixed != null) {
            return fixed;
        }
        SeasonSettings rules = LeafCalendar.serverSettings();
        Calendar calendar = Calendar.of(level);
        CalendarRules calendarRules = calendar.rules();
        long step = LeafClock.stepOf(rules, calendar.date().dayOfYear(), calendar.tickOfDay());
        LeafClock before = clock;
        if (before != null && before.step() == step && before.settings().equals(rules) && before.rules().equals(calendarRules)) {
            return before;
        }
        return LeafClock.of(rules, calendarRules, step);
    }

    /** Puts every chunk in reach into the queue, the ones nearest to a player first. */
    private void startRound() {
        rounds++;
        queue.clear();
        queued.clear();
        waiting.clear();
        resumeChunk = Long.MIN_VALUE;
        long[] all = inReach.keySet().toLongArray();
        List<ServerPlayer> players = level.players();
        if (!players.isEmpty()) {
            int[] playerX = new int[players.size()];
            int[] playerZ = new int[players.size()];
            for (int i = 0; i < playerX.length; i++) {
                ChunkPos at = players.get(i).chunkPosition();
                playerX[i] = at.x;
                playerZ[i] = at.z;
            }
            LongArrays.quickSort(all, (a, b) -> Long.compare(distance(a, playerX, playerZ), distance(b, playerX, playerZ)));
        }
        for (long pos : all) {
            enqueue(pos);
        }
    }

    /** How far a chunk is from the nearest player, as a number that only serves to compare. */
    private static long distance(long chunk, int[] playerX, int[] playerZ) {
        int x = ChunkPos.getX(chunk);
        int z = ChunkPos.getZ(chunk);
        long best = Long.MAX_VALUE;
        for (int i = 0; i < playerX.length; i++) {
            long dx = x - playerX[i];
            long dz = z - playerZ[i];
            best = Math.min(best, dx * dx + dz * dz);
        }
        return best;
    }

    private void enqueue(long pos) {
        if (queued.add(pos)) {
            queue.enqueue(pos);
        }
    }

    // ------------------------------------------------------------------
    // One chunk
    // ------------------------------------------------------------------

    /**
     * Looks at a chunk and swaps what is wrong, as far as this tick's
     * limits allow.
     *
     * @return true if the chunk is done, false if a limit was reached
     *         inside it (the walker then goes on at the same section in
     *         the next tick)
     */
    private boolean walkChunk(LevelChunk chunk, @Nullable ChunkHolder holder, boolean enabled, long deadline) {
        LeafClock now = clock;
        long pos = chunk.getPos().toLong();
        int first = resumeChunk == pos ? resumeSection : 0;
        resumeChunk = Long.MIN_VALUE;
        if (first == 0) {
            chunksLooked++;
        }
        boolean shedding = enabled && now != null && !now.allFull() && now.hasKinds();
        LevelChunkSection[] sections = chunk.getSections();
        for (int index = first; index < sections.length; index++) {
            LevelChunkSection section = sections[index];
            if (section.hasOnlyAir()) {
                continue;
            }
            int winter = look(section, now, shedding);
            if (winter < 0) {
                continue;
            }
            sectionsSearched++;
            boolean finished = search(chunk, holder, section, chunk.getSectionYFromSectionIndex(index) << 4, now, shedding, winter);
            if (!finished || System.nanoTime() >= deadline) {
                // Out of swaps: this section again. Out of time: on with the next one.
                resumeChunk = pos;
                resumeSection = finished ? index + 1 : index;
                return false;
            }
        }
        return true;
    }

    /**
     * Decides from a section's list of block kinds and of biomes, and if
     * need be from a count of its blocks, whether any block in it can be
     * wrong.
     *
     * @return -1 if all are right for sure; else where the section stands
     *         ({@link #WINTER_ALL}, {@link #WINTER_NONE} or
     *         {@link #WINTER_MIXED}), and the blocks must be looked at
     */
    private int look(LevelChunkSection section, @Nullable LeafClock now, boolean shedding) {
        boolean bare = section.maybeHas(IS_BARE);
        if (!shedding) {
            // Nothing sheds: every bare block must become a leaf again.
            return bare && count(section) && countedBare > 0 ? WINTER_NONE : -1;
        }
        boolean leaves = section.maybeHas(shedsLeaf);
        if (!bare && !leaves) {
            return -1;
        }
        PalettedContainerRO<Holder<Biome>> biomes = section.getBiomes();
        boolean someWinter = biomes.maybeHas(HAS_WINTER);
        boolean someNone = biomes.maybeHas(HAS_NO_WINTER);
        int winter = !someWinter ? WINTER_NONE : !someNone ? WINTER_ALL : WINTER_MIXED;
        if (winter == WINTER_NONE) {
            // No winter here: leaves stay, bare blocks have no business.
            return bare && count(section) && countedBare > 0 ? winter : -1;
        }
        if (winter == WINTER_ALL && now.allBare()) {
            // Deep winter: wrong are only leaves of a kind that sheds, and bare blocks of a kind that does not.
            if (!leaves && !section.maybeHas(strayBare)) {
                return -1;
            }
            count(section);
            return countedLeaves > 0 || countedStray > 0 ? winter : -1;
        }
        return winter;
    }

    /**
     * Counts the blocks of a section by kind: bare blocks
     * ({@link #countedBare}), bare blocks of a kind that does not shed
     * ({@link #countedStray}) and leaf blocks of a kind that sheds
     * ({@link #countedLeaves}). Unlike the section's list of kinds, the
     * count is exact.
     *
     * @return true, always (so that it can stand in a condition)
     */
    private boolean count(LevelChunkSection section) {
        countedBare = 0;
        countedLeaves = 0;
        countedStray = 0;
        sectionsCounted++;
        section.getStates().count(counter);
        return true;
    }

    /**
     * Looks at the blocks of a section one by one and swaps the wrong
     * ones.
     *
     * @return false if this tick's limit of swaps was reached before the
     *         end
     */
    private boolean search(LevelChunk chunk, @Nullable ChunkHolder holder, LevelChunkSection section, int baseY,
                           @Nullable LeafClock now, boolean shedding, int winter) {
        Reference2ReferenceOpenHashMap<Block, Block> pairs = Twins.pairs();
        int baseX = chunk.getPos().getMinBlockX();
        int baseZ = chunk.getPos().getMinBlockZ();
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    BlockState state = section.getBlockState(x, y, z);
                    Block block = state.getBlock();
                    if (!(block instanceof LeavesBlock)) {
                        continue;
                    }
                    Block twin = pairs.get(block);
                    if (twin == null) {
                        continue;
                    }
                    boolean isBare = block instanceof BareLeavesBlock;
                    boolean winterHere = winter == WINTER_ALL
                            || winter == WINTER_MIXED && SeasonsApi.hasWinter(section.getNoiseBiome(x >> 2, y >> 2, z >> 2).value());
                    boolean wantBare = shedding && SpotRule.bare(now, true, block, winterHere, baseX + x, baseY + y, baseZ + z,
                            state.getValue(LeavesBlock.DISTANCE));
                    if (wantBare == isBare) {
                        continue;
                    }
                    if (changesLeft <= 0) {
                        return false;
                    }
                    changesLeft--;
                    cursor.set(baseX + x, baseY + y, baseZ + z);
                    swap(chunk, holder, cursor, state, twin, wantBare);
                }
            }
        }
        return true;
    }

    // ------------------------------------------------------------------
    // One block
    // ------------------------------------------------------------------

    /**
     * Swaps the block of a spot for the other of its pair, quietly (see
     * the class notes).
     */
    private void swap(LevelChunk chunk, @Nullable ChunkHolder holder, BlockPos pos, BlockState state, Block twin,
                      boolean toBareBlock) {
        BlockState next = Twins.swap(state, twin);
        if (chunk.setBlockState(pos, next, false) == null) {
            return;
        }
        if (holder != null) {
            holder.blockChanged(pos);
        }
        level.getPathTypeCache().invalidate(pos);
        if (toBareBlock) {
            toBare++;
            settle(chunk, holder, pos, next);
        } else {
            toLeaf++;
        }
    }

    /**
     * Looks after what rested on a leaf block that has just become bare.
     * <ul>
     *   <li>Snow on top of it falls ({@link #land}).</li>
     *   <li>Fruit that hangs under it comes off. Fruit that has grown at
     *       all drops, the way Dynamic Trees drops ripe fruit: what the
     *       fruit is worth at its age falls as an item. Fruit that has
     *       only just set goes quietly, because it is worth nothing and
     *       breaking it would only make a noise.</li>
     *   <li>Anything else next to it that is not part of a tree and not a
     *       solid block is told that the block next to it changed, and
     *       then does what the game has it do: a vine lets go.</li>
     * </ul>
     */
    private void settle(LevelChunk chunk, @Nullable ChunkHolder holder, BlockPos pos, BlockState bare) {
        near.setWithOffset(pos, Direction.UP);
        BlockState above = chunk.getBlockState(near);
        if (above.getBlock() instanceof SnowLayerBlock
                && chunk.setBlockState(near, Blocks.AIR.defaultBlockState(), false) != null) {
            if (holder != null) {
                holder.blockChanged(near);
            }
            level.getPathTypeCache().invalidate(near);
            land(chunk, pos, above);
        }
        near.setWithOffset(pos, Direction.DOWN);
        BlockState below = chunk.getBlockState(near);
        if (below.getBlock() instanceof FruitBlock fruit) {
            fruitDropped++;
            if (fruit.getAge(below) <= 0) {
                // Fruit that has only just set is worth nothing at all,
                // and breaking it would give a crack and a puff of crumbs
                // for an empty hand. It goes quietly.
                level.removeBlock(near.immutable(), false);
            } else {
                ((Growable) fruit).drop(level, near.immutable(), below);
            }
        }
        for (Direction side : DIRECTIONS) {
            near.setWithOffset(pos, side);
            LevelChunk there = (near.getX() >> 4) == chunk.getPos().x && (near.getZ() >> 4) == chunk.getPos().z
                    ? chunk
                    : level.getChunkSource().getChunkNow(near.getX() >> 4, near.getZ() >> 4);
            if (there == null) {
                continue;
            }
            BlockState other = there.getBlockState(near);
            if (other.isAir() || other.getBlock() instanceof LeavesBlock || TreeHelper.isTreePart(other)
                    || other.isCollisionShapeFullBlock(level, near)) {
                continue;
            }
            BlockPos otherPos = near.immutable();
            BlockState updated = other.updateShape(side.getOpposite(), bare, level, otherPos, pos);
            if (updated != other) {
                Block.updateOrDestroy(other, updated, level, otherPos, Block.UPDATE_CLIENTS);
            }
        }
    }

    /**
     * Lets snow fall that lay on a leaf block which has just become
     * bare. It falls straight down, through bare blocks and everything
     * else that does not stop a walker, and lies on the first block that
     * stops it and has a free spot above: a leaf further down, a branch,
     * the ground.
     *
     * <p>A block that stops it but has no free spot above is passed by,
     * and the snow goes on falling. That is the usual case inside a
     * crown: the leaf under the one that just went bare is still a leaf,
     * and the spot above it is the bare block itself, which holds
     * nothing. The snow of such a spot ends up on the ground instead of
     * hanging in the crown or being lost.</p>
     *
     * <p>It is gone where there is nowhere to lie: on a full block whose
     * top is taken (snow already there, a plant), and at the bottom of
     * the world.</p>
     *
     * <p>Without this, land that was last seen in autumn and is brought
     * up to date in winter would get its snow on the crowns, lose it with
     * the leaves and stand on bare ground in a white forest.</p>
     *
     * @param chunk the chunk of the column
     * @param from  the spot of the bare block the snow lay on
     * @param snow  the snow as it lay
     */
    private void land(LevelChunk chunk, BlockPos from, BlockState snow) {
        int bottom = level.getMinBuildHeight();
        BlockPos.MutableBlockPos spot = new BlockPos.MutableBlockPos(from.getX(), from.getY(), from.getZ());
        BlockPos.MutableBlockPos on = new BlockPos.MutableBlockPos();
        // The same question the game's map of "where rain lands" asks of a block.
        while (spot.getY() > bottom) {
            spot.move(Direction.DOWN);
            BlockState state = chunk.getBlockState(spot);
            if (!state.blocksMotion() && state.getFluidState().isEmpty()) {
                continue;
            }
            on.set(spot.getX(), spot.getY() + 1, spot.getZ());
            if (chunk.getBlockState(on).isAir() && snow.canSurvive(level, on)) {
                // Placed the game's way, so that grass under it turns white at its sides.
                level.setBlock(on, snow, Block.UPDATE_ALL);
                snowLanded++;
                return;
            }
            if (state.isCollisionShapeFullBlock(level, spot)) {
                // The ground, or a wall of it: there is no way past, and
                // the spot above is taken. The snow is gone.
                return;
            }
            // A crown, a branch, a plant: the snow falls past it.
        }
    }

    /**
     * Takes away fruit that has appeared under a bare block since the
     * last tick ({@link #fruitAppeared}). It is new, so nothing drops.
     */
    private void clearFruit() {
        long[] spots = fruitUnderBare.toLongArray();
        fruitUnderBare.clear();
        for (long packed : spots) {
            cursor.set(packed);
            if (!level.isLoaded(cursor)) {
                continue;
            }
            if (level.getBlockState(cursor).getBlock() instanceof FruitBlock
                    && level.getBlockState(near.setWithOffset(cursor, Direction.UP)).getBlock() instanceof BareLeavesBlock) {
                level.removeBlock(cursor.immutable(), false);
            }
        }
    }

    // ------------------------------------------------------------------
    // For tests and measurements
    // ------------------------------------------------------------------

    /**
     * Looks at one chunk right now, without any limit, whether or not it
     * is in the queue.
     *
     * @return how many blocks were swapped, or -1 if the chunk is not in
     *         reach
     */
    public int walkNow(ChunkPos pos) {
        ChunkHolder holder = inReach.get(pos.toLong());
        LevelChunk chunk = holder == null ? null : holder.getTickingChunk();
        if (chunk == null) {
            return -1;
        }
        clock = clockNow();
        long before = toBare + toLeaf;
        changesLeft = Integer.MAX_VALUE;
        resumeChunk = Long.MIN_VALUE;
        walkChunk(chunk, holder, settings.enabled(), Long.MAX_VALUE);
        return (int) (toBare + toLeaf - before);
    }

    /**
     * Puts every spot of one loaded chunk right at once, with no limit:
     * the restore sweep ({@link Restore}).
     *
     * <p>The sweep goes by the chunks on disk, and those are the chunks
     * around the players as well. A chunk that players are watching is
     * swapped exactly as in a round of the walker, with every change sent
     * to them; a chunk nobody has is swapped quietly and marked as
     * changed, so the game writes it when it unloads it.</p>
     *
     * <p>The walker's own place in its round is kept, so that a sweep
     * running beside it does not make it start a chunk again.</p>
     *
     * @param chunk a loaded chunk
     * @return how many blocks were swapped
     */
    public int sweep(LevelChunk chunk) {
        ChunkHolder holder = inReach.get(chunk.getPos().toLong());
        clock = clockNow();
        long before = toBare + toLeaf;
        changesLeft = Integer.MAX_VALUE;
        long keptChunk = resumeChunk;
        int keptSection = resumeSection;
        resumeChunk = Long.MIN_VALUE;
        walkChunk(chunk, holder, settings.enabled(), Long.MAX_VALUE);
        resumeChunk = keptChunk;
        resumeSection = keptSection;
        // Every swap has marked the chunk as changed already
        // (LevelChunk.setBlockState does), so nothing to do here.
        return (int) (toBare + toLeaf - before);
    }

    /** How many chunks are still waiting for a look. */
    public int pending() {
        return queue.size() + waiting.size();
    }

    /** How many chunks are in reach. */
    public int inReach() {
        return inReach.size();
    }

    /** Where each counter is in what {@link #counters()} gives. */
    public static final int TO_BARE = 0;
    public static final int TO_LEAF = 1;
    public static final int CHUNKS_LOOKED = 2;
    public static final int SECTIONS_SEARCHED = 3;
    public static final int ROUNDS = 4;
    public static final int NANOS = 5;
    public static final int SECTIONS_COUNTED = 6;
    public static final int SNOW_LANDED = 7;
    public static final int FRUIT_DROPPED = 8;

    /** How many counters there are. */
    public static final int COUNTERS = 9;

    /**
     * The counters since this world started: leaf blocks made bare, bare
     * blocks made leaves, chunks looked at, sections looked at block by
     * block, rounds started, the time spent in billionths of a second,
     * sections whose blocks were counted, snow layers that fell from a
     * leaf going bare and landed, and fruit that dropped from one. The
     * places in the array are the numbers above.
     */
    public long[] counters() {
        return new long[] {toBare, toLeaf, chunksLooked, sectionsSearched, rounds, nanos, sectionsCounted, snowLanded,
                fruitDropped};
    }
}
