package io.github.superveryfastcar.foliage.shape;

import io.github.superveryfastcar.seasons.client.leaves.LeafThinning;
import java.util.Arrays;

/**
 * Works out the pixels of the leaf pictures for the stages of the leaf
 * calendar, from the pixels of a leaf texture: fewer leaves for autumn,
 * few and lighter ones for the young leaves of spring.
 *
 * <p>Plain arithmetic on pixel arrays, so it can be checked without a
 * screen.</p>
 *
 * <h2>Which leaves go first</h2>
 * Two things decide, mixed by one number ({@link PictureShares#edge}):
 * <ul>
 *   <li>Seasons' own thinning ({@link LeafThinning}): in clumps with
 *       ragged edges, wherever they happen to lie in the picture.</li>
 *   <li>Where a leaf is in the picture. The picture has a heart: a round
 *       patch in its middle ({@link PictureShares#heart}). The leaves
 *       outside the heart go first, all alike, and inside it a leaf goes
 *       the later the nearer it is to the middle.</li>
 * </ul>
 * The second is what makes a leaf block thin from its outside: what is
 * left sits together in the middle of each face, around the twig, and the
 * crown reads as tufts of leaves that get smaller. Without it the last
 * leaves of a picture are specks scattered over the whole face.
 *
 * <p>That all leaves outside the heart count alike is on purpose. If the
 * leaves went strictly by their distance from the middle, the corners of
 * every face would empty first, and a crown would show a regular grid of
 * holes where its leaf blocks meet. As it is, the outer part of a face
 * thins evenly and only the heart stays dense.</p>
 *
 * <p>The small cubes of leaves show the middle of the picture
 * ({@code client.model.QuadMaker}), so they keep their leaves longest. A
 * card thins from its own rim inward in the same way.</p>
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
        float[] order = edgeOrder(fallOrder(pixels, size, seed), size, shares.edge(), shares.heart());
        return made(which, pixels, order, shares);
    }

    /** One of the pictures of the year from a full picture and its fall order: thinned, and lightened for spring. */
    private static int[] made(LeafPicture which, int[] pixels, float[] order, PictureShares shares) {
        switch (which) {
            case FULL:
                return pixels.clone();
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
     * pictures: the card ({@link CardPicture}) of the full texture, with
     * as large a share of its leaves gone as the leaf picture has, so
     * that the cards of a crown thin out together with its other leaves.
     * A card thins as a whole: every one of its pixels falls in the turn
     * its texture pixel has, mixed with where it is on the card, so a
     * card loses its rim first and keeps its own heart, and not the heart
     * of each copy of the texture on it.
     *
     * @param which  the picture
     * @param pixels the leaf texture, {@code size} by {@code size}
     * @param size   the texture's width and height
     * @param seed   makes different textures thin, and their cards' outlines, differ
     * @param shares the numbers the pictures are made by
     * @return the card's pixels, {@code 2 * size} by {@code 2 * size}
     */
    public static int[] card(LeafPicture which, int[] pixels, int size, long seed, PictureShares shares) {
        int[] card = CardPicture.make(pixels, size, seed);
        if (which == LeafPicture.FULL) {
            return card;
        }
        // The card shows the texture shifted by half its width (see CardPicture): each card pixel takes the turn of its texture pixel.
        float[] turns = fallOrder(pixels, size, seed);
        int cardSize = 2 * size;
        float[] order = new float[card.length];
        int leaves = 0;
        for (int y = 0; y < cardSize; y++) {
            for (int x = 0; x < cardSize; x++) {
                int at = y * cardSize + x;
                float turn = turns[Math.floorMod(y + size / 2, size) * size + Math.floorMod(x + size / 2, size)];
                order[at] = card[at] >>> 24 == 0 ? Float.NaN : turn;
                leaves += Float.isNaN(order[at]) ? 0 : 1;
            }
        }
        // The turns of the texture are evenly spread over the texture, not over the part of it on the card: spread them again.
        return made(which, card, edgeOrder(ranked(order, leaves), cardSize, shares.edge(), shares.heart()), shares);
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
     * A fall order in which the outer leaves of a picture go before the
     * ones in its heart.
     *
     * @param order a fall order: per pixel a number from 0 (falls first)
     *              to just under 1, evenly spread over the leaf pixels,
     *              and no number for a pixel that is not a leaf
     * @param size  the picture's width and height
     * @param edge  how much the place in the picture counts, 0 to 1: 0
     *              gives the order back as it is, 1 makes the leaves
     *              outside the heart go before any inside it, and in
     *              between each leaf's turn is the two mixed
     *              ({@link PictureShares#edge})
     * @param heart how far the heart of the picture reaches from its
     *              middle, as a share of the way to the picture's side
     *              ({@link PictureShares#heart})
     * @return the new order, evenly spread again
     */
    public static float[] edgeOrder(float[] order, int size, float edge, float heart) {
        if (!(edge > 0.0F) || !(heart > 0.0F)) {
            return order;
        }
        int leaves = 0;
        float middle = size / 2.0F;
        float reach = heart * middle;
        float weight = Math.min(1.0F, edge);
        float[] mixed = new float[order.length];
        for (int at = 0; at < order.length; at++) {
            if (Float.isNaN(order[at])) {
                mixed[at] = Float.NaN;
                continue;
            }
            leaves++;
            float dx = at % size + 0.5F - middle;
            float dy = at / size + 0.5F - middle;
            // 1 in the very middle, 0 at the rim of the heart and everywhere outside it.
            float inward = Math.max(0.0F, 1.0F - (float) Math.sqrt(dx * dx + dy * dy) / reach);
            // A trace of the old turn stays even at full weight, so that leaves outside the heart never go row by row.
            mixed[at] = weight * inward + Math.max(1.0e-3F, 1.0F - weight) * order[at];
        }
        return ranked(mixed, leaves);
    }

    /**
     * Spreads numbers evenly: the smallest becomes 0, the next 1 divided
     * by their count, and so on to just under 1. Equal numbers keep the
     * order of their places. What is not a number stays that way.
     *
     * @param values the numbers
     * @param count  how many of them are numbers
     */
    private static float[] ranked(float[] values, int count) {
        Integer[] places = new Integer[count];
        int found = 0;
        for (int at = 0; at < values.length; at++) {
            if (!Float.isNaN(values[at])) {
                places[found++] = at;
            }
        }
        Arrays.sort(places, (a, b) -> {
            int byValue = Float.compare(values[a], values[b]);
            return byValue != 0 ? byValue : Integer.compare(a, b);
        });
        float[] ranks = new float[values.length];
        Arrays.fill(ranks, Float.NaN);
        for (int rank = 0; rank < count; rank++) {
            ranks[places[rank]] = rank / (float) count;
        }
        return ranks;
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
