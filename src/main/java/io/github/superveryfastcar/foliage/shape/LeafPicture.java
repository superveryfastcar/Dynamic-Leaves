package io.github.superveryfastcar.foliage.shape;

import io.github.superveryfastcar.foliage.season.LeafStage;
import javax.annotation.Nullable;

/**
 * The leaf pictures a kind of leaves is drawn with through the year. All
 * but the first are made from the leaf texture when the game loads its
 * pictures ({@link LeafPictures}).
 */
public enum LeafPicture {

    /** The leaf texture as it is. */
    FULL(null, "_foliage_card"),

    /** About a third of the leaves gone. */
    THIN("_foliage_thin", "_foliage_card_thin"),

    /** About half of the leaves gone. */
    THINNER("_foliage_thinner", "_foliage_card_thinner"),

    /** A few last leaves. */
    LAST("_foliage_last", null),

    /** The first small leaves of spring: few, and lighter than grown ones. */
    YOUNG("_foliage_young", null),

    /** Half-grown leaves: nearly all there, a little lighter than grown ones. */
    HALF("_foliage_half", null);

    private static final LeafPicture[] VALUES = values();

    /** How many pictures there are. */
    public static final int COUNT = VALUES.length;

    /** How many of them (the first ones) also come as a round card picture. */
    public static final int CARD_COUNT = 3;

    @Nullable
    private final String suffix;
    @Nullable
    private final String cardSuffix;

    LeafPicture(@Nullable String suffix, @Nullable String cardSuffix) {
        this.suffix = suffix;
        this.cardSuffix = cardSuffix;
    }

    /** The picture with a given number ({@link #ordinal()}). */
    public static LeafPicture byNumber(int number) {
        return VALUES[number];
    }

    /**
     * What is added to a leaf texture's name to get the name of this
     * picture, or null for the leaf texture itself.
     */
    @Nullable
    public String suffix() {
        return suffix;
    }

    /**
     * What is added to a leaf texture's name to get the name of the round
     * card picture that goes with this picture, or null if there is none
     * (cards are only drawn while a spot is in full leaf or thinning).
     */
    @Nullable
    public String cardSuffix() {
        return cardSuffix;
    }

    /**
     * The picture a leaf spot at a stage is drawn with. Stages without
     * leaves ({@link LeafStage#leafless()}) draw no leaf picture; they get
     * {@link #FULL} here so that the answer is never null.
     */
    public static LeafPicture forStage(LeafStage stage) {
        switch (stage) {
            case THIN:
                return THIN;
            case THINNER:
                return THINNER;
            case LAST:
                return LAST;
            case FIRST:
                return YOUNG;
            case HALF:
                return HALF;
            case FULL:
            case BARE:
            case BUDS:
            default:
                return FULL;
        }
    }

    /**
     * The picture a clump of leaves at a stage is drawn with. As
     * {@link #forStage}, but for two stages:
     * <ul>
     *   <li>The last leaves of autumn are a small clump, and the clump
     *       being small already says "few leaves". Drawn with the picture
     *       of the last leaves on top of that, it would be a few specks in
     *       the air. So it keeps the picture of the stage before.</li>
     *   <li>A bud is a clump too, the smallest of all. It is drawn with
     *       the lighter green of half-grown leaves, nearly without
     *       holes.</li>
     * </ul>
     */
    public static LeafPicture forClump(LeafStage stage) {
        switch (stage) {
            case LAST:
                return THINNER;
            case BUDS:
                return HALF;
            default:
                return forStage(stage);
        }
    }
}
