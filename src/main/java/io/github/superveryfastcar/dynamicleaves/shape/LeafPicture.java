package io.github.superveryfastcar.dynamicleaves.shape;

import io.github.superveryfastcar.dynamicleaves.season.LeafStage;
import javax.annotation.Nullable;

/**
 * The leaf pictures a kind of leaves is drawn with through the year. All
 * but the first are made from the leaf texture when the game loads its
 * pictures ({@link LeafPictures}).
 */
public enum LeafPicture {

    /** The leaf texture as it is. */
    FULL(null, "_dynamicleaves_card"),

    /** About a third of the leaves gone. */
    THIN("_dynamicleaves_thin", "_dynamicleaves_card_thin"),

    /** About half of the leaves gone. */
    THINNER("_dynamicleaves_thinner", "_dynamicleaves_card_thinner"),

    /** The last leaves: about a fifth of them, in the heart of the picture. */
    LAST("_dynamicleaves_last", null),

    /** The first small leaves of spring: few, and lighter than grown ones. */
    YOUNG("_dynamicleaves_young", null),

    /** Half-grown leaves: nearly all there, a little lighter than grown ones. */
    HALF("_dynamicleaves_half", null);

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
     * {@link #forStage}, but for one stage: a bud is a clump too, the
     * smallest of all, and it is drawn with the lighter green of
     * half-grown leaves, nearly without holes.
     *
     * <p>A clump shows the middle of its picture, and the leaves of a
     * picture go from the outside in ({@link LeafPictures}). So the small
     * clump of last leaves, drawn with the picture of the last leaves, is
     * a ragged tuft around the end of its twig and not a few specks in
     * the air.</p>
     */
    public static LeafPicture forClump(LeafStage stage) {
        return stage == LeafStage.BUDS ? HALF : forStage(stage);
    }
}
