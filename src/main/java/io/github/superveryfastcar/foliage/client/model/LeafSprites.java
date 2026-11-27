package io.github.superveryfastcar.foliage.client.model;

import io.github.superveryfastcar.foliage.shape.LeafPicture;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * The pictures one kind of leaf block is drawn from.
 *
 * @param leaves   the leaf pictures of the year, by the number of the
 *                 {@link LeafPicture}; the first is the leaf texture of the
 *                 block itself. None is missing: where a picture could not
 *                 be made, the leaf texture itself stands in.
 * @param cards    the round pictures for leaf cards, by the number of the
 *                 {@link LeafPicture} (only the first
 *                 {@link LeafPicture#CARD_COUNT}); all null if none could
 *                 be made for this texture (then no cards are drawn)
 * @param twigs    the bare twig pictures, growing from the middle of their
 *                 bottom edge; empty if there are none (then no twigs are drawn)
 * @param budTwigs the same twigs with buds on them, one per bare twig
 *                 picture; where a twig has no picture with buds, the bare
 *                 one stands in
 */
record LeafSprites(TextureAtlasSprite[] leaves, TextureAtlasSprite[] cards, TextureAtlasSprite[] twigs,
                   TextureAtlasSprite[] budTwigs) {

    /** The leaf texture of the block itself. */
    TextureAtlasSprite leaf() {
        return leaves[0];
    }

    /** The round card picture in full leaf, or null if this texture has no cards. */
    @Nullable
    TextureAtlasSprite card() {
        return cards[0];
    }

    /** One of the leaf pictures of the year. */
    TextureAtlasSprite leaf(LeafPicture which) {
        return leaves[which.ordinal()];
    }

    /** The round card picture that goes with one of the leaf pictures, or null if there is none. */
    @Nullable
    TextureAtlasSprite card(LeafPicture which) {
        return which.ordinal() < cards.length ? cards[which.ordinal()] : null;
    }
}
