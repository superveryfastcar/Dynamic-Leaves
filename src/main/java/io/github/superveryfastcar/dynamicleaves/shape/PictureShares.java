package io.github.superveryfastcar.dynamicleaves.shape;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The numbers the leaf pictures of the year are made by: how many of a
 * texture's leaves are gone in each picture, and how much lighter young
 * leaves are. They are read from the atlas file that lists the leaf
 * textures (see {@code client.sprite.LeafPartsSource}), because the
 * pictures are made while the game loads its textures. A resource pack can
 * change them there.
 *
 * @param thin       the share of the leaves that are gone in the first
 *                   picture of autumn, 0 to 1
 * @param thinner    the share gone in the second picture of autumn
 * @param last       the share gone in the picture of the last leaves
 * @param youngGone  the share of the leaves that are not out yet in the
 *                   picture of the first leaves of spring
 * @param youngLight how much lighter those first leaves are: 1 is the
 *                   texture's own color, 1.15 is 15 percent lighter
 * @param halfGone   the share not out yet in the picture of half-grown leaves
 * @param halfLight  how much lighter half-grown leaves are
 * @param edge       how much the leaves of a picture go from its outside
 *                   in, 0 to 1. 0: wherever they happen to lie, in ragged
 *                   clumps, so the last leaves are specks all over a leaf
 *                   block's face. 1: every leaf outside the heart of the
 *                   picture goes before any inside it. In between, each
 *                   leaf's turn is the two mixed
 * @param heart      how far the heart of a picture reaches from its
 *                   middle: the round patch whose leaves go last, as a
 *                   share of the way to the picture's side. 0.75 is a
 *                   patch 12 pixels across on a texture of 16
 */
public record PictureShares(float thin, float thinner, float last, float youngGone, float youngLight,
                            float halfGone, float halfLight, float edge, float heart) {

    /** The smallest and largest heart: below, no leaf would be in it; above, it would hold the whole picture and its corners. */
    public static final float MIN_HEART = 0.1F;
    public static final float MAX_HEART = 1.5F;

    /** The lightest and darkest a picture may be made. */
    public static final float MIN_LIGHT = 0.5F;
    public static final float MAX_LIGHT = 2.0F;

    /**
     * The values the mod ships with: a third, half and 80 percent of the
     * leaves gone in autumn (leaf spots also get smaller as their leaves
     * fall, so the pictures can stay fuller than they sound); half of the
     * leaves out and 15 percent lighter at first in spring, then 85
     * percent out and 7 percent lighter; leaves that go from the outside
     * in for six parts in ten, around a heart that reaches three quarters
     * of the way to the picture's side.
     */
    public static final PictureShares DEFAULT = new PictureShares(0.33F, 0.5F, 0.8F, 0.5F, 1.15F, 0.15F, 1.07F, 0.6F, 0.75F);

    /** How the numbers are read from an atlas file. Every one of them may be left out. */
    public static final Codec<PictureShares> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Codec.FLOAT.optionalFieldOf("thin", DEFAULT.thin).forGetter(PictureShares::thin),
                    Codec.FLOAT.optionalFieldOf("thinner", DEFAULT.thinner).forGetter(PictureShares::thinner),
                    Codec.FLOAT.optionalFieldOf("last", DEFAULT.last).forGetter(PictureShares::last),
                    Codec.FLOAT.optionalFieldOf("young_gone", DEFAULT.youngGone).forGetter(PictureShares::youngGone),
                    Codec.FLOAT.optionalFieldOf("young_light", DEFAULT.youngLight).forGetter(PictureShares::youngLight),
                    Codec.FLOAT.optionalFieldOf("half_gone", DEFAULT.halfGone).forGetter(PictureShares::halfGone),
                    Codec.FLOAT.optionalFieldOf("half_light", DEFAULT.halfLight).forGetter(PictureShares::halfLight),
                    Codec.FLOAT.optionalFieldOf("edge", DEFAULT.edge).forGetter(PictureShares::edge),
                    Codec.FLOAT.optionalFieldOf("heart", DEFAULT.heart).forGetter(PictureShares::heart))
            .apply(instance, PictureShares::new));

    /**
     * Forces every number into its allowed range, and the pictures of
     * autumn into their order: each has at least as many leaves gone as the
     * one before, and the young leaves are at most as many as the
     * half-grown ones. A crown that changes step by step then never gets a
     * leaf back in autumn and never loses one in spring.
     */
    public PictureShares {
        thin = share(thin);
        thinner = Math.max(thin, share(thinner));
        last = Math.max(thinner, share(last));
        halfGone = share(halfGone);
        youngGone = Math.max(halfGone, share(youngGone));
        youngLight = light(youngLight);
        halfLight = light(halfLight);
        edge = share(edge);
        heart = Float.isNaN(heart) ? DEFAULT_HEART : Math.max(MIN_HEART, Math.min(MAX_HEART, heart));
    }

    /** The heart a number that is not a number is read as. */
    private static final float DEFAULT_HEART = 0.75F;

    /** A lightness forced into its range; a value that is not a number becomes 1 (the texture's own color). */
    private static float light(float value) {
        return Float.isNaN(value) ? 1.0F : Math.max(MIN_LIGHT, Math.min(MAX_LIGHT, value));
    }

    private static float share(float value) {
        return Float.isNaN(value) ? 0.0F : Math.max(0.0F, Math.min(1.0F, value));
    }
}
