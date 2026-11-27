package io.github.superveryfastcar.foliage.shape;

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
 */
public record PictureShares(float thin, float thinner, float last, float youngGone, float youngLight,
                            float halfGone, float halfLight) {

    /** The lightest and darkest a picture may be made. */
    public static final float MIN_LIGHT = 0.5F;
    public static final float MAX_LIGHT = 2.0F;

    /**
     * The values the mod ships with: a third, half and 85 percent of the
     * leaves gone in autumn (leaf spots also get smaller as their leaves
     * fall, so the second picture can stay fuller than "two thirds gone"); half of the leaves out and 15 percent
     * lighter at first in spring, then 85 percent out and 7 percent lighter.
     */
    public static final PictureShares DEFAULT = new PictureShares(0.33F, 0.5F, 0.85F, 0.5F, 1.15F, 0.15F, 1.07F);

    /** How the numbers are read from an atlas file. Every one of them may be left out. */
    public static final Codec<PictureShares> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Codec.FLOAT.optionalFieldOf("thin", DEFAULT.thin).forGetter(PictureShares::thin),
                    Codec.FLOAT.optionalFieldOf("thinner", DEFAULT.thinner).forGetter(PictureShares::thinner),
                    Codec.FLOAT.optionalFieldOf("last", DEFAULT.last).forGetter(PictureShares::last),
                    Codec.FLOAT.optionalFieldOf("young_gone", DEFAULT.youngGone).forGetter(PictureShares::youngGone),
                    Codec.FLOAT.optionalFieldOf("young_light", DEFAULT.youngLight).forGetter(PictureShares::youngLight),
                    Codec.FLOAT.optionalFieldOf("half_gone", DEFAULT.halfGone).forGetter(PictureShares::halfGone),
                    Codec.FLOAT.optionalFieldOf("half_light", DEFAULT.halfLight).forGetter(PictureShares::halfLight))
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
    }

    /** A lightness forced into its range; a value that is not a number becomes 1 (the texture's own color). */
    private static float light(float value) {
        return Float.isNaN(value) ? 1.0F : Math.max(MIN_LIGHT, Math.min(MAX_LIGHT, value));
    }

    private static float share(float value) {
        return Float.isNaN(value) ? 0.0F : Math.max(0.0F, Math.min(1.0F, value));
    }
}
