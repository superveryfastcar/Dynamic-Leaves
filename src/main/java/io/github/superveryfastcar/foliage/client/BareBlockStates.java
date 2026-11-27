package io.github.superveryfastcar.foliage.client;

import io.github.superveryfastcar.foliage.Foliage;
import io.github.superveryfastcar.foliage.block.BareLeavesBlock;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/**
 * A resource pack that exists only in memory and holds one small file per
 * bare block: its block state file.
 *
 * <h2>Why</h2>
 * The game wants a block state file for every block, which says by which
 * model each state of the block is drawn, and complains in the log about
 * every state without one. Foliage makes its bare blocks from whatever
 * leaf kinds the installed tree packs bring, so it cannot ship those files
 * ready-made. This pack hands them out instead.
 *
 * <p>Every file says the same: "all states are drawn by the game's empty
 * model". The real model, the one of the leaf, is put in place afterwards
 * by {@code LeafModels}. A bare block whose leaf Foliage leaves alone
 * keeps the empty model and is not drawn.</p>
 *
 * <h2>Where it sits</h2>
 * At the bottom of the player's resource packs, always on, and not shown
 * in the list. Any real resource pack that brings a block state file for
 * a bare block wins over this one.
 */
public final class BareBlockStates implements PackResources {

    /** The pack's id in the game's list of packs. */
    private static final String ID = Foliage.MOD_ID + "_bare_blocks";

    /** The folder of block state files inside a pack's assets, and their ending. */
    private static final String FOLDER = "blockstates";
    private static final String ENDING = ".json";

    /** What every file holds. */
    private static final byte[] FILE =
            "{\"variants\":{\"\":{\"model\":\"minecraft:block/air\"}}}".getBytes(StandardCharsets.UTF_8);

    private final PackLocationInfo location;

    /** The files of the pack; worked out at first use. */
    @Nullable
    private Set<ResourceLocation> files;

    private BareBlockStates(PackLocationInfo location) {
        this.location = location;
    }

    /**
     * Adds the pack to the player's resource packs. The game asks for
     * packs of two sorts; this is one of the sort that holds pictures and
     * models, not data.
     */
    public static void addPack(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) {
            return;
        }
        PackLocationInfo info = new PackLocationInfo(ID, Component.literal("Foliage bare blocks"), PackSource.BUILT_IN, Optional.empty());
        Pack.ResourcesSupplier supplier = new Pack.ResourcesSupplier() {
            @Override
            public PackResources openPrimary(PackLocationInfo location) {
                return new BareBlockStates(location);
            }

            @Override
            public PackResources openFull(PackLocationInfo location, Pack.Metadata metadata) {
                return new BareBlockStates(location);
            }
        };
        // Always on (required), below every other pack.
        Pack pack = Pack.readMetaAndCreate(info, supplier, PackType.CLIENT_RESOURCES,
                new PackSelectionConfig(true, Pack.Position.BOTTOM, false));
        if (pack == null) {
            Foliage.LOGGER.warn("Foliage: could not add the block state files of the bare blocks; the game will name them as missing");
            return;
        }
        Pack hidden = pack.hidden();
        event.addRepositorySource(packs -> packs.accept(hidden));
    }

    /** The block state files of all bare blocks, by their place in a pack. */
    private Set<ResourceLocation> files() {
        Set<ResourceLocation> known = files;
        if (known == null) {
            known = new HashSet<>();
            for (Block block : BuiltInRegistries.BLOCK) {
                if (block instanceof BareLeavesBlock) {
                    ResourceLocation name = BuiltInRegistries.BLOCK.getKey(block);
                    known.add(ResourceLocation.fromNamespaceAndPath(name.getNamespace(), FOLDER + "/" + name.getPath() + ENDING));
                }
            }
            files = known;
        }
        return known;
    }

    private static IoSupplier<InputStream> file() {
        return () -> new ByteArrayInputStream(FILE);
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getRootResource(String... elements) {
        return null;
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getResource(PackType type, ResourceLocation name) {
        return type == PackType.CLIENT_RESOURCES && files().contains(name) ? file() : null;
    }

    @Override
    public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
        if (type != PackType.CLIENT_RESOURCES) {
            return;
        }
        for (ResourceLocation name : files()) {
            if (name.getNamespace().equals(namespace) && name.getPath().startsWith(path + "/")) {
                output.accept(name, file());
            }
        }
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        if (type != PackType.CLIENT_RESOURCES) {
            return Set.of();
        }
        Set<String> namespaces = new HashSet<>();
        for (ResourceLocation name : files()) {
            namespaces.add(name.getNamespace());
        }
        return namespaces;
    }

    /** What stands in for the pack's {@code pack.mcmeta}: a description and the game's current pack format. */
    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T> T getMetadataSection(MetadataSectionSerializer<T> deserializer) {
        if (deserializer.getMetadataSectionName().equals(PackMetadataSection.TYPE.getMetadataSectionName())) {
            return (T) new PackMetadataSection(Component.literal("Block state files for the bare blocks of Foliage"),
                    SharedConstants.getCurrentVersion().getPackVersion(PackType.CLIENT_RESOURCES), Optional.empty());
        }
        return null;
    }

    @Override
    public PackLocationInfo location() {
        return location;
    }

    @Override
    public void close() {
    }
}
