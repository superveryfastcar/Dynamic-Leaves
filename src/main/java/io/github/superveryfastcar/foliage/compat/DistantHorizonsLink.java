package io.github.superveryfastcar.foliage.compat;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.interfaces.block.IDhApiBlockStateWrapper;
import com.seibel.distanthorizons.api.interfaces.factories.IDhApiWrapperFactory;
import com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper;
import com.seibel.distanthorizons.api.methods.events.DhApiEventRegister;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiChunkProcessingEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiLevelUnloadEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiWorldUnloadEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiEventParam;
import com.seibel.distanthorizons.api.objects.DhApiResult;
import io.github.superveryfastcar.foliage.Foliage;
import io.github.superveryfastcar.foliage.block.BareLeavesBlock;
import io.github.superveryfastcar.foliage.block.Twins;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The link to Distant Horizons itself: hands it the leaf block wherever a
 * bare block stands, so that far crowns are whole all year (see
 * {@link DistantHorizons} for why).
 *
 * <h2>Where it hooks in</h2>
 * Distant Horizons offers other mods a say while it turns a chunk into its
 * own far picture ({@code DhApiChunkProcessingEvent}): for every block it
 * takes, another mod may name a different one. That happens for chunks
 * that are loaded, for chunks Distant Horizons reads from its own store
 * and for chunks it makes itself, so every way into its picture is
 * covered by this one door.
 *
 * <p>Nothing else of Distant Horizons is touched: no setting of it is
 * changed, its store is not written, and what it already holds is left
 * alone. A forest it stored while bare blocks stood there keeps its holes
 * until that chunk passes through here again; the next visit does that.</p>
 *
 * <h2>Thread rule</h2>
 * Distant Horizons calls this on its own threads, several at a time, once
 * per block of a chunk: about 100,000 calls per chunk. So the answer must
 * be cheap and must not lock. It is two reads: one test of the block's
 * class, and for a bare block one lookup in a table that is written once
 * per kind of block and world and read from then on.
 *
 * <h2>One table per world</h2>
 * A wrapper of Distant Horizons belongs to the world it was made for, so
 * the tables are kept apart by world: a server with several dimensions has
 * chunks of two of them in the works at the same time, and a wrapper of
 * the one would be wrong for the other. A world that is unloaded is
 * forgotten, as is everything when a player leaves a world or a server
 * stops, so nothing of a world is held after it is gone.
 */
public final class DistantHorizonsLink {

    /** How many worlds may be kept at once, if ever an unload goes unseen. */
    private static final int MOST_WORLDS = 8;

    /** What is known about one world's blocks: the wrappers made, and the blocks Distant Horizons would not take. */
    private record Known(Map<BlockState, IDhApiBlockStateWrapper> wrappers, Set<BlockState> refused) {

        static Known empty() {
            return new Known(new ConcurrentHashMap<>(), ConcurrentHashMap.newKeySet());
        }
    }

    /** One table per world of Distant Horizons. */
    private static final Map<IDhApiLevelWrapper, Known> PER_WORLD = new ConcurrentHashMap<>();

    private DistantHorizonsLink() {
    }

    /**
     * Signs the link up with Distant Horizons. Called by
     * {@link DistantHorizons#init()} by name, and only when the mod is
     * installed.
     *
     * <p>Distant Horizons answers each sign-up with a yes or a no, and a
     * no is not an exception: it is a field on the answer. Each one is
     * read, and the first no stops the whole link, so that there is no
     * half link. The two handlers that forget a world go first, so that
     * nothing has been put into a table before the one that fills the
     * tables is in place.</p>
     *
     * @throws IllegalStateException if Distant Horizons will not take one
     *                               of the handlers. {@link DistantHorizons#init()}
     *                               catches it, says so in the log, and
     *                               leaves the link asleep.
     */
    public static void hook() {
        took(DhApiEventRegister.on(DhApiLevelUnloadEvent.class, new ForgetWorld()), "forgetting one world");
        took(DhApiEventRegister.on(DhApiWorldUnloadEvent.class, new ForgetEverything()), "forgetting every world");
        took(DhApiEventRegister.on(DhApiChunkProcessingEvent.class, new KeepLeaves()), "keeping the leaves");
        Foliage.LOGGER.debug("Foliage hooked into Distant Horizons {} (API {}.{}).",
                DhApi.getModVersion(), DhApi.getApiMajorVersion(), DhApi.getApiMinorVersion());
    }

    /** Throws unless Distant Horizons took the handler. */
    private static void took(DhApiResult<Void> answer, String what) {
        if (answer == null || !answer.success) {
            throw new IllegalStateException("Distant Horizons would not take Foliage's handler for "
                    + what + (answer == null ? "" : ": " + answer.message));
        }
    }

    /** The handler Distant Horizons calls for every block of a chunk it takes in. */
    private static final class KeepLeaves extends DhApiChunkProcessingEvent {

        @Override
        public void blockOrBiomeChangedDuringChunkProcessing(DhApiEventParam<EventParam> event) {
            EventParam param = event.value;
            if (param == null) {
                return;
            }
            IDhApiBlockStateWrapper wrapper = param.currentBlock;
            if (wrapper == null) {
                return;
            }
            BlockState leaf = leafOf(wrapper);
            if (leaf == null) {
                return;
            }
            IDhApiBlockStateWrapper replacement = wrapperOf(leaf, param.levelWrapper);
            if (replacement != null) {
                param.setBlockOverride(replacement);
            }
        }
    }

    /** Throws away what was made for a world that Distant Horizons has unloaded. */
    private static final class ForgetWorld extends DhApiLevelUnloadEvent {

        @Override
        public void onLevelUnload(DhApiEventParam<EventParam> event) {
            EventParam param = event.value;
            if (param != null && param.levelWrapper != null) {
                PER_WORLD.remove(param.levelWrapper);
            }
        }
    }

    /** Throws everything away: the player has left the world, or the server has stopped. */
    private static final class ForgetEverything extends DhApiWorldUnloadEvent {

        @Override
        public void onWorldUnload(DhApiEventParam<EventParam> event) {
            PER_WORLD.clear();
        }
    }

    /**
     * The leaf block a bare block stands in for, or null if the block is
     * not a bare block of Foliage.
     *
     * <p>The block itself is asked for, not its name: Distant Horizons
     * holds the game's own block behind its wrapper.</p>
     */
    @Nullable
    private static BlockState leafOf(IDhApiBlockStateWrapper wrapper) {
        Object wrapped = wrapper.getWrappedMcObject();
        if (!(wrapped instanceof BlockState state) || !(state.getBlock() instanceof BareLeavesBlock)) {
            return null;
        }
        Block twin = Twins.twinOf(state.getBlock());
        return twin == null ? null : Twins.swap(state, twin);
    }

    /**
     * Distant Horizons' own wrapper of a block, made once per block and
     * world and kept in that world's table.
     */
    @Nullable
    private static IDhApiBlockStateWrapper wrapperOf(BlockState leaf, IDhApiLevelWrapper level) {
        if (level == null) {
            return null;
        }
        if (PER_WORLD.size() >= MOST_WORLDS && !PER_WORLD.containsKey(level)) {
            // Should not happen: a world is forgotten when it is unloaded.
            // If one is ever missed, the tables are built again rather than
            // kept for a world that is long gone.
            PER_WORLD.clear();
        }
        Known known = PER_WORLD.computeIfAbsent(level, world -> Known.empty());
        IDhApiBlockStateWrapper made = known.wrappers().get(leaf);
        if (made != null) {
            return made;
        }
        if (known.refused().contains(leaf)) {
            return null;
        }
        IDhApiWrapperFactory factory = DhApi.Delayed.wrapperFactory;
        if (factory == null) {
            return null;
        }
        try {
            IDhApiBlockStateWrapper fresh = factory.getBlockStateWrapper(new Object[] {leaf}, level);
            if (fresh != null) {
                known.wrappers().put(leaf, fresh);
            }
            return fresh;
        } catch (RuntimeException e) {
            // Should not happen: the block is the game's own. If it does,
            // the bare block goes into the far picture as it is, which
            // looks as it did before this link.
            Foliage.LOGGER.warn("Distant Horizons would not take the leaf block {}; far crowns may show holes.",
                    leaf, e);
            known.refused().add(leaf);
            return null;
        }
    }
}
