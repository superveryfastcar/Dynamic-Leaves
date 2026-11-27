package io.github.superveryfastcar.foliage.client.sprite;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.superveryfastcar.foliage.Foliage;
import io.github.superveryfastcar.foliage.shape.CardPicture;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.atlas.SpriteResourceLoader;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.SpriteSourceType;
import net.minecraft.client.renderer.texture.atlas.sources.LazyLoadedImage;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceMetadata;
import net.neoforged.neoforge.client.event.RegisterSpriteSourceTypesEvent;

/**
 * Makes the extra pictures leaves are drawn with, while the game puts its
 * block pictures together into one large sheet (the block atlas).
 *
 * <p>For every leaf texture listed, it makes the picture of a leaf card
 * ({@link CardPicture}) out of the texture that is loaded at that moment.
 * A resource pack that changes a leaf texture therefore changes its cards
 * with it, and nothing has to be drawn by hand.</p>
 *
 * <h2>How to list a texture</h2>
 * In a file {@code assets/minecraft/atlases/blocks.json} (of this mod, of
 * another mod or of a resource pack; the game joins all of them):
 * <pre>
 * {"sources": [{"type": "foliage:leaf_parts", "leaves": [
 *     {"texture": "minecraft:block/oak_leaves", "twigs": "foliage:block/twigs_brown"}
 * ]}]}
 * </pre>
 * {@code texture} is the leaf texture. {@code twigs} names the set of twig
 * pictures that goes with it: the pictures {@code <twigs>_0},
 * {@code <twigs>_1} and so on, which must be ordinary block textures. It
 * may be left out; then the brown twigs are used.
 *
 * @param leaves the listed leaf textures
 */
public record LeafPartsSource(List<Leaves> leaves) implements SpriteSource {

    /** The name of this kind of source in atlas files. */
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Foliage.MOD_ID, "leaf_parts");

    /** The twig pictures used where none are named. */
    public static final ResourceLocation DEFAULT_TWIGS = ResourceLocation.fromNamespaceAndPath(Foliage.MOD_ID, "block/twigs_brown");

    /** The most twig pictures a set may have ({@code _0} to {@code _7}). */
    public static final int MAX_TWIG_PICTURES = 8;

    /**
     * One listed leaf texture.
     *
     * @param texture the leaf texture, as models name it ({@code minecraft:block/oak_leaves})
     * @param twigs   the set of twig pictures that goes with it
     */
    public record Leaves(ResourceLocation texture, ResourceLocation twigs) {

        static final Codec<Leaves> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        ResourceLocation.CODEC.fieldOf("texture").forGetter(Leaves::texture),
                        ResourceLocation.CODEC.optionalFieldOf("twigs", DEFAULT_TWIGS).forGetter(Leaves::twigs))
                .apply(instance, Leaves::new));
    }

    /** How the source is read from an atlas file. */
    public static final MapCodec<LeafPartsSource> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codec.list(Leaves.CODEC).fieldOf("leaves").forGetter(LeafPartsSource::leaves))
            .apply(instance, LeafPartsSource::new));

    /** This kind of source, as the game knows it. */
    public static final SpriteSourceType TYPE = new SpriteSourceType(CODEC);

    /**
     * The textures listed at the last loading of resources, each with its
     * set of twig pictures. Filled while the atlas is put together (on a
     * worker thread) and taken by the leaf models right after.
     */
    private static final Map<ResourceLocation, ResourceLocation> MADE = new ConcurrentHashMap<>();

    /** Tells the game about this kind of source. */
    public static void register(RegisterSpriteSourceTypesEvent event) {
        event.register(ID, TYPE);
    }

    /** The name of the card picture made for a leaf texture. */
    public static ResourceLocation cardName(ResourceLocation texture) {
        return texture.withSuffix("_foliage_card");
    }

    /**
     * Hands over the textures listed at the last loading of resources,
     * each with its set of twig pictures, and forgets them.
     */
    public static Map<ResourceLocation, ResourceLocation> takeMade() {
        Map<ResourceLocation, ResourceLocation> made = Map.copyOf(MADE);
        MADE.keySet().removeAll(made.keySet());
        return made;
    }

    @Override
    public void run(ResourceManager resourceManager, Output output) {
        for (Leaves entry : leaves) {
            ResourceLocation file = TEXTURE_ID_CONVERTER.idToFile(entry.texture());
            Optional<Resource> resource = resourceManager.getResource(file);
            if (resource.isEmpty()) {
                // Leaves of a mod that is not installed: nothing to do.
                Foliage.LOGGER.debug("Foliage: no texture {} to make a leaf card from", file);
                continue;
            }
            ResourceLocation name = cardName(entry.texture());
            output.add(name, new Card(entry.texture(), new LazyLoadedImage(file, resource.get(), 1), name));
            MADE.put(entry.texture(), entry.twigs());
        }
    }

    @Override
    public SpriteSourceType type() {
        return TYPE;
    }

    /**
     * Makes one card picture when the game asks for it.
     *
     * @param texture the leaf texture the card is made from
     * @param image   that texture's file, opened on first use
     * @param name    the card picture's name
     */
    private record Card(ResourceLocation texture, LazyLoadedImage image, ResourceLocation name) implements SpriteSupplier {

        @Override
        @Nullable
        public SpriteContents apply(SpriteResourceLoader loader) {
            try {
                NativeImage source = image.get();
                int size = source.getWidth();
                if (source.getHeight() != size) {
                    Foliage.LOGGER.warn("Foliage: {} is animated or not square, its leaves get no cards", texture);
                    return null;
                }
                int[] pixels = new int[size * size];
                for (int y = 0; y < size; y++) {
                    for (int x = 0; x < size; x++) {
                        pixels[y * size + x] = source.getPixelRGBA(x, y);
                    }
                }
                // The same outline every time for one texture, a different one per texture.
                int[] card = CardPicture.make(pixels, size, texture.toString().hashCode());
                int cardSize = 2 * size;
                NativeImage picture = new NativeImage(cardSize, cardSize, false);
                for (int y = 0; y < cardSize; y++) {
                    for (int x = 0; x < cardSize; x++) {
                        picture.setPixelRGBA(x, y, card[y * cardSize + x]);
                    }
                }
                return new SpriteContents(name, new FrameSize(cardSize, cardSize), picture, ResourceMetadata.EMPTY);
            } catch (IOException | RuntimeException e) {
                Foliage.LOGGER.error("Foliage: could not make a leaf card from {}", texture, e);
                return null;
            } finally {
                image.release();
            }
        }

        @Override
        public void discard() {
            image.release();
        }
    }
}
