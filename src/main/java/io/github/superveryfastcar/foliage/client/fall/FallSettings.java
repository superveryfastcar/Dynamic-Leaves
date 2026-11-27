package io.github.superveryfastcar.foliage.client.fall;

/**
 * A player's settings for the leaves that drift down from the crowns
 * while the leaves fall ({@link FallingLeaves}). Kept in
 * {@code config/foliage-client.toml}, section {@code fallingLeaves}. Each
 * player has their own; nothing of this is sent anywhere.
 *
 * @param enabled  the setting {@code fallingLeaves}. false: none at all,
 *                 and nothing is looked at
 * @param amount   how many leaves fall, 1 (a few) to 10 (a shower)
 * @param distance how far from the player leaves start, in blocks
 * @param most     how many leaves may be on their way at once
 */
public record FallSettings(boolean enabled, int amount, int distance, int most) {

    /** The limits of the numbers, for the settings file. */
    public static final int MIN_AMOUNT = 1;
    public static final int MAX_AMOUNT = 10;
    public static final int MIN_DISTANCE = 8;
    public static final int MAX_DISTANCE = 48;
    public static final int MIN_MOST = 0;
    public static final int MAX_MOST = 2000;

    /**
     * The values the mod ships with: a steady fall of leaves within the
     * nearest two chunks, and never more than 200 leaves on their way.
     * The game's own weather holds a few hundred drops of rain at a time,
     * so 200 leaves cost about as much as light rain.
     */
    public static final FallSettings DEFAULT = new FallSettings(true, 3, 24, 200);
}
