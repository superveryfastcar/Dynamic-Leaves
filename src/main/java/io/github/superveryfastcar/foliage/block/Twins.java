package io.github.superveryfastcar.foliage.block;

import com.dtteam.dynamictrees.block.leaves.DynamicLeavesBlock;
import com.dtteam.dynamictrees.block.leaves.LeavesProperties;
import com.dtteam.dynamictrees.block.leaves.ScruffyLeavesProperties;
import com.dtteam.dynamictrees.tree.species.Species;
import io.github.superveryfastcar.foliage.Foliage;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * The pairs of a leaf block and its bare block: makes the bare blocks when
 * the game starts, and answers which block belongs to which.
 *
 * <h2>Which leaves get a bare block</h2>
 * Every plain leaf kind of Dynamic Trees and of its addons: the kinds that
 * are drawn and behave like the leaves of an oak. Also the "scruffy"
 * kinds (azalea, mangrove, the undergrowth), which only differ in where
 * leaves grow. Not the kinds with a block of their own making: palm
 * fronds, the wart blocks of the Nether trees, solid leaves. Those can
 * still be listed in the leaf calendar; they then change their look, and
 * the block stays.
 *
 * <p>A bare block is made for each such kind whether or not the kind is
 * listed to shed, because the list is a setting that can change while the
 * game runs and blocks cannot be added then. An unused bare block costs
 * nothing.</p>
 *
 * <h2>Names</h2>
 * The bare block of {@code dynamictrees:oak_leaves} is
 * {@code foliage:bare_oak_leaves}. For a leaf block of another mod the
 * mod's id goes in front of the name:
 * {@code foliage:bare_<mod id>_<name>}. Worlds store these names, so the
 * rule must never change.
 *
 * <h2>When</h2>
 * {@link #register} runs while the game registers blocks, as late as a
 * mod can ask for (lowest priority). The game calls every mod in turn
 * once for each priority; Dynamic Trees reads its tree packs at normal
 * priority and registers its leaf blocks at the lowest, and Foliage's
 * turn comes after it. So all leaf kinds and Dynamic Trees' own leaf
 * blocks exist by then. The pairs are worked out later, when every mod's
 * blocks exist: at the first question about a pair. No such question may
 * be asked while the game still registers blocks.
 */
public final class Twins {

    /** The id of Dynamic Trees, whose leaf blocks keep their plain name. */
    private static final String DYNAMIC_TREES = "dynamictrees";

    /** The bare kinds, in the order of their names. Written once, during start-up. */
    private static final List<BareLeavesProperties> MADE = new ArrayList<>();

    /**
     * Leaf block to bare block and bare block to leaf block. Never changed
     * once made; swapped as a whole, so any thread may read it.
     */
    private static volatile Reference2ReferenceOpenHashMap<Block, Block> pairs = new Reference2ReferenceOpenHashMap<>();

    /** The bare blocks that have a leaf block, in the order of their names. */
    private static volatile List<BareLeavesBlock> bareBlocks = List.of();

    /** Whether the pairs have been worked out. */
    private static volatile boolean paired;

    private Twins() {
    }

    // ------------------------------------------------------------------
    // Start-up
    // ------------------------------------------------------------------

    /**
     * Makes a bare block for every leaf kind that gets one. Called by the
     * game once for each of its lists of things; only the list of blocks
     * matters here.
     */
    public static void register(RegisterEvent event) {
        if (!event.getRegistryKey().equals(Registries.BLOCK) || !MADE.isEmpty()) {
            return;
        }
        List<LeavesProperties> kinds = new ArrayList<>(LeavesProperties.REGISTRY.getAll());
        // A fixed order, the same on a server and in every player's game.
        kinds.sort(Comparator.comparing(kind -> kind.getRegistryName().toString()));
        Set<ResourceLocation> taken = new HashSet<>();
        for (LeavesProperties kind : kinds) {
            if (!getsBareBlock(kind)) {
                continue;
            }
            ResourceLocation name = bareName(kind.getBlockRegistryName());
            if (!taken.add(name)) {
                Foliage.LOGGER.warn("Foliage: no bare block for the leaves {}: the name {} is taken", kind.getRegistryName(), name);
                continue;
            }
            BareLeavesProperties bare = new BareLeavesProperties(kind, name);
            event.register(Registries.BLOCK, name, bare::makeBlock);
            MADE.add(bare);
        }
        Foliage.LOGGER.info("Foliage: made bare blocks for {} kinds of leaves", MADE.size());
    }

    /**
     * Whether a leaf kind gets a bare block: a plain or scruffy kind that
     * has a leaf block.
     */
    private static boolean getsBareBlock(LeavesProperties kind) {
        if (!kind.isValid() || kind instanceof BareLeavesProperties) {
            return false;
        }
        Class<?> type = kind.getClass();
        if (type != LeavesProperties.class && type != ScruffyLeavesProperties.class) {
            return false;
        }
        ResourceLocation leafName = kind.getBlockRegistryName();
        if (leafName == null) {
            return false;
        }
        // Dynamic Trees has registered its own blocks by now. An addon that
        // the game loads after Foliage has not; its kinds are taken on trust
        // and checked in resolve().
        return BuiltInRegistries.BLOCK.containsKey(leafName) || !leafName.getNamespace().equals(DYNAMIC_TREES);
    }

    /** The name of the bare block that belongs to a leaf block. See the class notes. */
    public static ResourceLocation bareName(ResourceLocation leafName) {
        String path = leafName.getNamespace().equals(DYNAMIC_TREES)
                ? "bare_" + leafName.getPath()
                : "bare_" + leafName.getNamespace() + "_" + leafName.getPath();
        return ResourceLocation.fromNamespaceAndPath(Foliage.MOD_ID, path.replace('/', '_'));
    }

    /**
     * Works out the pairs and tells the tree species about their bare
     * blocks. Called on the game's main thread once the game has
     * registered everything; calling it again is harmless.
     */
    public static void resolve() {
        pair();
        fitSpecies();
    }

    /**
     * Works out the pairs, now that every mod's blocks exist. Whoever
     * asks for a pair first sets this off, on whatever thread; it touches
     * nothing but this class.
     */
    private static synchronized void pair() {
        if (paired) {
            return;
        }
        Reference2ReferenceOpenHashMap<Block, Block> found = new Reference2ReferenceOpenHashMap<>();
        List<BareLeavesBlock> blocks = new ArrayList<>();
        for (BareLeavesProperties kind : MADE) {
            if (!(kind.getDynamicLeavesBlock().orElse(null) instanceof BareLeavesBlock bare)) {
                continue;
            }
            DynamicLeavesBlock leaf = bare.leaf();
            // A leaf kind without a block of its own, or one that shares another kind's block, has no pair.
            if (leaf == null || leaf instanceof BareLeavesBlock || leaf.getLeavesProperties() != kind.leaf() || found.containsKey(leaf)) {
                continue;
            }
            found.put(leaf, bare);
            found.put(bare, leaf);
            blocks.add(bare);
        }
        pairs = found;
        bareBlocks = List.copyOf(blocks);
        paired = true;
    }

    /**
     * Tells every tree species of Dynamic Trees that the bare blocks of
     * its leaves are leaves of its own. A species keeps a list of the leaf
     * kinds it accepts; a growing twig only counts a block as leaves if
     * its kind is on the list, and a tree that is cut down remembers each
     * of its leaf blocks by its place in that list.
     *
     * <p>The bare kinds are added in the fixed order of their names, so
     * that a server and a player's game number them alike.</p>
     */
    public static synchronized void fitSpecies() {
        List<BareLeavesBlock> blocks = bareBlocks();
        for (Species species : Species.REGISTRY.getAll()) {
            if (!species.isValid()) {
                continue;
            }
            for (BareLeavesBlock bare : blocks) {
                DynamicLeavesBlock leaf = bare.leaf();
                if (leaf != null && species.isValidLeafBlock(leaf) && !species.isValidLeafBlock(bare)) {
                    species.addValidLeafBlocks(bare.kind());
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Questions
    // ------------------------------------------------------------------

    /**
     * The other block of a pair: the bare block of a leaf block, or the
     * leaf block of a bare block. Null for any block that is in no pair.
     */
    @Nullable
    public static Block twinOf(Block block) {
        return pairs().get(block);
    }

    /** The bare block of a leaf block, or null if it has none (or is one itself). */
    @Nullable
    public static BareLeavesBlock bareOf(Block leaf) {
        return pairs().get(leaf) instanceof BareLeavesBlock bare ? bare : null;
    }

    /** All pairs at once, for a caller that looks up many blocks in a row. Must not be changed. */
    public static Reference2ReferenceOpenHashMap<Block, Block> pairs() {
        if (!paired) {
            pair();
        }
        return pairs;
    }

    /** The bare blocks that have a leaf block. */
    public static List<BareLeavesBlock> bareBlocks() {
        if (!paired) {
            pair();
        }
        return bareBlocks;
    }

    /**
     * The same spot as the other block of its pair: the twin's state with
     * the supply number and the two other marks of a leaf block (placed by
     * a player, holds water) carried over.
     *
     * @param state a leaf block or a bare block
     * @param twin  the other block of its pair, from {@link #twinOf}
     */
    public static BlockState swap(BlockState state, Block twin) {
        return twin.defaultBlockState()
                .setValue(LeavesBlock.DISTANCE, state.getValue(LeavesBlock.DISTANCE))
                .setValue(LeavesBlock.PERSISTENT, state.getValue(LeavesBlock.PERSISTENT))
                .setValue(LeavesBlock.WATERLOGGED, state.getValue(LeavesBlock.WATERLOGGED));
    }
}
