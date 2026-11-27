package io.github.superveryfastcar.foliage.shape;

import io.github.superveryfastcar.seasons.client.leaves.LeafThinning;

/**
 * Works out the pixels of the leaf pictures for the stages of the leaf
 * calendar, from the pixels of a leaf texture: fewer leaves for autumn,
 * few and lighter ones for the young leaves of spring.
 *
 * <p>Plain arithmetic on pixel arrays, so it can be checked without a
 * screen. Which leaves go first is decided by Seasons' own thinning
 * ({@link LeafThinning}: in clumps with ragged edges), so that the trees
 * Foliage draws thin the same way as the ones Seasons draws.</p>
 *
 * <p>Pixels are whole numbers in the order the game's images use (opacity
 * in the top byte, then blue, green, red), one row after another.</p>
 */
public final class LeafPictures {

    /** In how many parts a share is counted when it is handed to Seasons' thinning. */
    private static final int PARTS = 1000;

    private LeafPictures() {
    }

    /**
     * The pixels of one of the leaf pictures of the year.
     *
     * @param which  the picture
     * @param pixels the leaf texture, {@code size} by {@code size}
     * @param size   the texture's width and height
     * @param seed   makes different textures thin differently
     * @param shares the numbers the pictures are made by
     * @return the new pixels, {@code size} by {@code size}
     */
    public static int[] leaf(LeafPicture which, int[] pixels, int size, long seed, PictureShares shares) {
        if (which == LeafPicture.FULL) {
            return pixels.clone();
        }
        float[] order = fallOrder(pixels, size, seed);
        switch (which) {
            case THIN:
                return thinned(pixels, order, shares.thin());
            case THINNER:
                return thinned(pixels, order, shares.thinner());
            case LAST:
                return thinned(pixels, order, shares.last());
            case YOUNG:
                return lightened(thinned(pixels, order, shares.youngGone()), shares.youngLight());
            case HALF:
            default:
                return lightened(thinned(pixels, order, shares.halfGone()), shares.halfLight());
        }
    }

    /**
     * The pixels of the round card picture that goes with one of the leaf
     * pictures: the card ({@link CardPicture}) cut from that picture, so
     * that the cards of a crown thin out together with its other leaves.
     *
     * @param which  the picture
     * @param pixels the leaf texture, {@code size} by {@code size}
     * @param size   the texture's width and height
     * @param seed   makes different textures thin, and their cards' outlines, differ
     * @param shares the numbers the pictures are made by
     * @return the card's pixels, {@code 2 * size} by {@code 2 * size}
     */
    public static int[] card(LeafPicture which, int[] pixels, int size, long seed, PictureShares shares) {
        return CardPicture.make(leaf(which, pixels, size, seed, shares), size, seed);
    }

    /**
     * The order in which the leaf pixels of a picture fall, for
     * {@link #thinned}.
     *
     * @param pixels the picture, {@code size} by {@code size}
     * @param size   the picture's width and height
     * @param seed   makes different pictures thin differently
     */
    public static float[] fallOrder(int[] pixels, int size, long seed) {
        return LeafThinning.fallOrder(pixels, size, seed);
    }

    /**
     * A picture with a share of its leaves gone. The leaves that are gone
     * at a smaller share are also gone at every larger one, so a crown that
     * thins step by step never gets a leaf back.
     *
     * @param pixels the full picture
     * @param order  its fall order from {@link #fallOrder}
     * @param gone   the share of the leaf pixels that are gone, 0 to 1
     * @return the new pixels; removed leaves keep their color and are only
     *         made fully see-through
     */
    public static int[] thinned(int[] pixels, float[] order, float gone) {
        int parts = Math.max(0, Math.min(PARTS, Math.round(gone * PARTS)));
        return parts == 0 ? pixels.clone() : LeafThinning.stage(pixels, order, null, parts, PARTS);
    }

    /**
     * A picture made lighter or darker: every color of every pixel is
     * multiplied by a factor, the opacity stays. Young leaves are lighter
     * than grown ones.
     *
     * @param pixels the picture
     * @param factor 1 leaves the picture as it is, more makes it lighter
     */
    public static int[] lightened(int[] pixels, float factor) {
        int[] out = new int[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            int result = pixel & 0xFF000000;
            for (int shift = 0; shift < 24; shift += 8) {
                int channel = Math.min(255, Math.round((pixel >>> shift & 0xFF) * factor));
                result |= channel << shift;
            }
            out[i] = result;
        }
        return out;
    }

    /** The share of a picture's pixels that are shown (at least half opaque), 0 to 1. */
    public static float coverage(int[] pixels) {
        int shown = 0;
        for (int pixel : pixels) {
            if (pixel >>> 24 >= 128) {
                shown++;
            }
        }
        return pixels.length == 0 ? 0.0F : shown / (float) pixels.length;
    }
}
