package io.github.superveryfastcar.dynamicleaves.shape;

import io.github.superveryfastcar.dynamicleaves.util.PosHash;

/**
 * Works out the picture on a leaf card: a round, ragged patch of leaves
 * made from a leaf texture. Plain arithmetic on pixel arrays, so it can be
 * checked without a running game.
 *
 * <h2>How the picture is made</h2>
 * The card is twice as wide and tall as the leaf texture. The texture is
 * laid over it four times, shifted by half its width so that no seam runs
 * through the middle. Then everything outside a round outline is cut away.
 * The outline is not a clean circle: its radius wobbles around the card,
 * and near the rim about a third of the leaf pixels are dropped, so the
 * edge of the card looks like loose leaves and not like a disc.
 *
 * <p>Outline and rim are worked out on a 32 by 32 grid laid over the card,
 * so a texture from a resource pack with more pixels gets a card of the
 * same shape, only finer inside.</p>
 *
 * <p>Pixels are whole numbers in the order the game's images use (opacity
 * in the top byte, then blue, green, red), one row after another.</p>
 */
public final class CardPicture {

    /** The grid the outline is worked out on. */
    private static final int GRID = 32;

    /** How many points around the card set the outline's radius. */
    private static final int OUTLINE_POINTS = 24;

    /** The outline's radius in grid cells, and how far it wobbles to either side. */
    private static final float RADIUS = 13.0F;
    private static final float WOBBLE = 2.2F;

    /** How deep the loose rim is, in grid cells, and the share of leaf pixels dropped there. */
    private static final float RIM = 3.0F;
    private static final float RIM_DROPPED = 0.35F;

    /** From this opacity on (of 255) a texture pixel counts as leaf. */
    private static final int LEAF_ALPHA = 128;

    private CardPicture() {
    }

    /**
     * Makes the picture of a card.
     *
     * @param pixels the leaf texture, {@code size} by {@code size}
     * @param size   the texture's width and height in pixels
     * @param seed   makes the cards of different textures differ in outline
     * @return the card, {@code 2 * size} by {@code 2 * size}: leaf pixels
     *         fully opaque, everything else fully see-through
     */
    public static int[] make(int[] pixels, int size, long seed) {
        int cardSize = 2 * size;
        int[] card = new int[cardSize * cardSize];
        float[] outline = new float[OUTLINE_POINTS];
        for (int i = 0; i < OUTLINE_POINTS; i++) {
            outline[i] = RADIUS + (PosHash.unit(PosHash.hash(seed, i, 0)) * 2.0F - 1.0F) * WOBBLE;
        }
        for (int y = 0; y < cardSize; y++) {
            for (int x = 0; x < cardSize; x++) {
                int source = pixels[Math.floorMod(y + size / 2, size) * size + Math.floorMod(x + size / 2, size)];
                if (source >>> 24 < LEAF_ALPHA) {
                    continue;
                }
                // The grid cell this pixel lies in, and its place seen from the middle of the card.
                int gridX = x * GRID / cardSize;
                int gridY = y * GRID / cardSize;
                float dx = gridX + 0.5F - GRID / 2.0F;
                float dy = gridY + 0.5F - GRID / 2.0F;
                // Where around the card the cell lies, in outline points, and the radius allowed there.
                float around = (float) ((Math.atan2(dy, dx) + Math.PI) / (2.0 * Math.PI) * OUTLINE_POINTS);
                int point = (int) around % OUTLINE_POINTS;
                float between = around - (int) around;
                float limit = outline[point] * (1.0F - between) + outline[(point + 1) % OUTLINE_POINTS] * between;
                float distance = (float) Math.sqrt(dx * dx + dy * dy);
                if (distance > limit) {
                    continue;
                }
                if (distance > limit - RIM && PosHash.unit(PosHash.hash(seed ^ 0x51ED270BL, gridX, gridY)) < RIM_DROPPED) {
                    continue;
                }
                card[y * cardSize + x] = source | 0xFF000000;
            }
        }
        return card;
    }

    /** The share of a picture's pixels that are not fully see-through, 0 to 1. */
    public static float coverage(int[] pixels) {
        int shown = 0;
        for (int pixel : pixels) {
            if (pixel >>> 24 != 0) {
                shown++;
            }
        }
        return pixels.length == 0 ? 0.0F : shown / (float) pixels.length;
    }
}
