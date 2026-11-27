package io.github.superveryfastcar.foliage.client.sprite;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.superveryfastcar.foliage.Foliage;
import io.github.superveryfastcar.foliage.shape.CardPicture;
import io.github.superveryfastcar.foliage.shape.LeafPicture;
import io.github.superveryfastcar.foliage.shape.LeafPictures;
import io.github.superveryfastcar.foliage.shape.PictureShares;
import io.github.superveryfastcar.seasons.api.client.SeasonsClientApi;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
 * <p>For every leaf texture listed, it makes these pictures out of the
 * texture that is loaded at that moment:</p>
 * <ul>
 *   <li>the leaf pictures of the year ({@link LeafPicture}): the texture
 *       with a third, half and nearly all of its leaves gone for
 *       autumn, and with few and lighter leaves for spring;</li>
 *   <li>the round picture of a leaf card ({@link CardPicture}), in full
 *       leaf and for the first two pictures of autumn.</li>
 * </ul>
 * A resource pack that changes a leaf texture therefore changes all of
 * them with it, and nothing has to be drawn by hand.
 *
 * <p>The thinned pictures are handed to the game through Seasons
 * ({@link SeasonsClientApi#leafSprite}), so that their smaller copies for
 * far away keep their few leaves and do not fade to nothing.</p>
 *
 * <h2>How to list a texture</h2>
 * In a file {@code assets/minecraft/atlases/blocks.json} (of this mod, of
 * another mod or of a resource pack; the game joins all of them):
 * <pre>
 * {"sources": [{"type": "foliage:leaf_parts", "leaves": [
 *     {"texture": "minecraft:block/oak_leaves"}
 * ]}]}
 * </pre>
 * {@code texture} is the leaf texture. (Up to version 0.4 an entry also
 * named a set of flat twig pictures, {@code "twigs"}. Twigs are small
 * branches in the tree's own bark now and need no pictures; a file that
 * still names some is read as before and the name is not used.)
 *
 * <p>Next to {@code leaves}, a source may carry {@code "pictures"}: the
 * numbers the pictures of the year are made by ({@link PictureShares}),
 * for example {@code "pictures": {"thin": 0.25, "young_light": 1.2}}.
 * Numbers that are left out keep the mod's own values.</p>
 *
 * @param leaves   the listed leaf textures
 * @param pictures the numbers the pictures of the year are made by
 */
public record LeafPartsSource(List<Leaves> leaves, PictureShares pictures) implements SpriteSource {

    /** The name of this kind of source in atlas files. */
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Foliage.MOD_ID, "leaf_parts");

    /** How many pictures are made from one leaf texture: the leaf pictures of the year and their cards. */
    private static final int PARTS_PER_TEXTURE = partsPerTexture();

    private static int partsPerTexture() {
        int parts = 0;
        for (int number = 0; number < LeafPicture.COUNT; number++) {
            LeafPicture which = LeafPicture.byNumber(number);
            parts += (which.suffix() != null ? 1 : 0) + (which.cardSuffix() != null ? 1 : 0);
        }
        return parts;
    }

    /**
     * One listed leaf texture.
     *
     * @param texture the leaf texture, as models name it ({@code minecraft:block/oak_leaves})
     */
    public record Leaves(ResourceLocation texture) {

        static final Codec<Leaves> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        ResourceLocation.CODEC.fieldOf("texture").forGetter(Leaves::texture))
                .apply(instance, Leaves::new));
    }

    /** How the source is read from an atlas file. */
    public static final MapCodec<LeafPartsSource> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codec.list(Leaves.CODEC).fieldOf("leaves").forGetter(LeafPartsSource::leaves),
                    PictureShares.CODEC.optionalFieldOf("pictures", PictureShares.DEFAULT).forGetter(LeafPartsSource::pictures))
            .apply(instance, LeafPartsSource::new));

    /** This kind of source, as the game knows it. */
    public static final SpriteSourceType TYPE = new SpriteSourceType(CODEC);

    /**
     * The textures listed at the last loading of resources. Filled while
     * the atlas is put together (on a worker thread) and taken by the leaf
     * models right after.
     */
    private static final Set<ResourceLocation> MADE = ConcurrentHashMap.newKeySet();

    /** Tells the game about this kind of source. */
    public static void register(RegisterSpriteSourceTypesEvent event) {
        event.register(ID, TYPE);
    }

    /** The name of the full card picture made for a leaf texture. */
    public static ResourceLocation cardName(ResourceLocation texture) {
        return cardName(texture, LeafPicture.FULL);
    }

    /**
     * The name of one of the leaf pictures of the year made for a leaf
     * texture; for {@link LeafPicture#FULL} the texture's own name.
     */
    public static ResourceLocation leafName(ResourceLocation texture, LeafPicture which) {
        return which.suffix() == null ? texture : texture.withSuffix(which.suffix());
    }

    /**
     * The name of the card picture that goes with one of the leaf pictures
     * of a leaf texture, or null if that picture has no card.
     */
    @Nullable
    public static ResourceLocation cardName(ResourceLocation texture, LeafPicture which) {
        return which.cardSuffix() == null ? null : texture.withSuffix(which.cardSuffix());
    }

    /**
     * Hands over the textures listed at the last loading of resources and
     * forgets them.
     */
    public static Set<ResourceLocation> takeMade() {
        Set<ResourceLocation> made = Set.copyOf(MADE);
        MADE.removeAll(made);
        return made;
    }

    @Override
    public void run(ResourceManager resourceManager, Output output) {
        for (Leaves entry : leaves) {
            ResourceLocation texture = entry.texture();
            ResourceLocation file = TEXTURE_ID_CONVERTER.idToFile(texture);
            Optional<Resource> resource = resourceManager.getResource(file);
            if (resource.isEmpty()) {
                // Leaves of a mod that is not installed: nothing to do.
                Foliage.LOGGER.debug("Foliage: no texture {} to make leaf pictures from", file);
                continue;
            }
            // All pictures made from this texture read the same opened file.
            LazyLoadedImage image = new LazyLoadedImage(file, resource.get(), PARTS_PER_TEXTURE);
            for (int number = 0; number < LeafPicture.COUNT; number++) {
                LeafPicture which = LeafPicture.byNumber(number);
                if (which.suffix() != null) {
                    ResourceLocation name = leafName(texture, which);
                    output.add(name, new Part(texture, image, name, which, false, pictures));
                }
                ResourceLocation card = cardName(texture, which);
                if (card != null) {
                    output.add(card, new Part(texture, image, card, which, true, pictures));
                }
            }
            MADE.add(texture);
        }
    }

    @Override
    public SpriteSourceType type() {
        return TYPE;
    }

    /**
     * Makes one picture when the game asks for it.
     *
     * @param texture the leaf texture the picture is made from
     * @param image   that texture's file, opened on first use
     * @param name    the picture's name
     * @param which   which of the leaf pictures of the year
     * @param card    true for the round card picture, false for the leaf picture
     * @param shares  the numbers the pictures are made by
     */
    private record Part(ResourceLocation texture, LazyLoadedImage image, ResourceLocation name,
                        LeafPicture which, boolean card, PictureShares shares) implements SpriteSupplier {

        @Override
        @Nullable
        public SpriteContents apply(SpriteResourceLoader loader) {
            try {
                NativeImage source = image.get();
                int size = source.getWidth();
                if (source.getHeight() != size) {
                    // One warning per texture is enough: the full card is always among the parts.
                    if (card && which == LeafPicture.FULL) {
                        Foliage.LOGGER.warn("Foliage: {} is animated or not square, its leaves get no cards "
                                + "and no pictures of their own for autumn and spring", texture);
                    }
                    return null;
                }
                int[] pixels = new int[size * size];
                for (int y = 0; y < size; y++) {
                    for (int x = 0; x < size; x++) {
                        pixels[y * size + x] = source.getPixelRGBA(x, y);
                    }
                }
                // The same outline and the same thinning every time for one texture, different ones per texture.
                long seed = texture.toString().hashCode();
                int[] made = card ? LeafPictures.card(which, pixels, size, seed, shares) : LeafPictures.leaf(which, pixels, size, seed, shares);
                int madeSize = card ? 2 * size : size;
                NativeImage picture = new NativeImage(madeSize, madeSize, false);
                for (int y = 0; y < madeSize; y++) {
                    for (int x = 0; x < madeSize; x++) {
                        picture.setPixelRGBA(x, y, made[y * madeSize + x]);
                    }
                }
                if (which == LeafPicture.FULL) {
                    return new SpriteContents(name, new FrameSize(madeSize, madeSize), picture, ResourceMetadata.EMPTY);
                }
                // Few scattered leaves: the smaller copies for far away must keep them.
                return SeasonsClientApi.leafSprite(name, picture);
            } catch (IOException | RuntimeException e) {
                Foliage.LOGGER.error("Foliage: could not make the leaf picture {} from {}", name, texture, e);
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
