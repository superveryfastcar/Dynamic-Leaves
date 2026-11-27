package io.github.superveryfastcar.dynamicleaves.client.model;

import io.github.superveryfastcar.dynamicleaves.shape.LeafPicture;
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
 * @param bark     the bark of the tree these leaves grow on, taken from
 *                 the model of its branches, so that a mini branch is of
 *                 the same wood as the branch it grows from; null if no
 *                 bark could be found (then no mini branches are drawn)
 */
record LeafSprites(TextureAtlasSprite[] leaves, TextureAtlasSprite[] cards, @Nullable TextureAtlasSprite bark) {

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
