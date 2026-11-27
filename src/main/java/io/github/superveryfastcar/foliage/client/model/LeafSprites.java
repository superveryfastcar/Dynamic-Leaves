package io.github.superveryfastcar.foliage.client.model;

import javax.annotation.Nullable;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * The pictures one kind of leaf block is drawn from.
 *
 * @param leaf  the leaf texture of the block itself
 * @param card  the round picture for leaf cards, or null if none could be
 *              made for this texture (then no cards are drawn)
 * @param twigs the bare twig pictures, growing from the middle of their
 *              bottom edge; empty if there are none (then no twigs are drawn)
 */
record LeafSprites(TextureAtlasSprite leaf, @Nullable TextureAtlasSprite card, TextureAtlasSprite[] twigs) {
}
