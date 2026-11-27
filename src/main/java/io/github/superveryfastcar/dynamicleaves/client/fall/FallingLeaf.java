package io.github.superveryfastcar.dynamicleaves.client.fall;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;

/**
 * One leaf on its way down from a crown, in a player's game only.
 *
 * <h2>How it moves</h2>
 * A leaf falls slowly and does not fall straight: it swings from side to
 * side on its way, a little further out each time, and turns as it swings,
 * the way a leaf does. The swing is a circle in the end, so a leaf drifts
 * away from the tree while it falls. Every leaf has its own direction, its
 * own pace and its own turn, all drawn once when it is made, so no two
 * look alike.
 *
 * <p>A leaf does not pass through blocks. One that meets a trunk or a
 * branch from the side slides along it and goes on falling; one that comes
 * to rest on something, the ground or a branch below it, is gone, because
 * leaves do not pile up here (the leaves lying on the ground are the
 * ground's own look). A leaf that has found nothing to rest on is gone
 * after about fifteen seconds of falling.</p>
 *
 * <h2>How many there are</h2>
 * {@link FallingLeaves} decides when a leaf is made, and keeps count: a
 * leaf tells it when it is made and when it is gone, so that the player's
 * limit holds.
 */
public final class FallingLeaf extends TextureSheetParticle {

    /** How fast a leaf falls at most, in blocks per tick. */
    private static final double FALL_SPEED = 0.07;

    /** How much faster it gets per tick, until it falls at {@link #FALL_SPEED}. */
    private static final double FALL_RISE = 0.004;

    /** How wide a leaf's swing grows, in blocks per tick of swinging. */
    private static final double SWING_GROWTH = 0.0018;

    /** The widest swing, in blocks per tick. */
    private static final double SWING_MOST = 0.045;

    /** How long a leaf lives at most, in ticks (about 15 seconds). */
    private static final int LIFETIME = 300;

    /** How far a leaf falls before it may be stopped by the ground, in ticks. */
    private static final int SETTLE_AFTER = 3;

    /** The sizes a leaf is drawn at, in blocks. */
    private static final float SMALLEST = 0.055F;
    private static final float LARGEST = 0.085F;

    /** Where in its swing the leaf is, and how fast it swings. */
    private float swing;
    private final float swingPace;

    /** How fast the leaf turns, and in which direction. */
    private final float turnPace;

    /** Whether this leaf is counted by {@link FallingLeaves}. */
    private boolean counted = true;

    /**
     * Makes a leaf.
     *
     * @param level  the player's world
     * @param x      where the leaf starts
     * @param y      where the leaf starts
     * @param z      where the leaf starts
     * @param color  the color of the tree's leaves at that spot, as the
     *               game's packed color (red, green and blue in one number)
     * @param sprite the picture of the leaf
     */
    FallingLeaf(ClientLevel level, double x, double y, double z, int color, TextureAtlasSprite sprite) {
        super(level, x, y, z);
        setSprite(sprite);
        setColor((color >> 16 & 0xFF) / 255.0F, (color >> 8 & 0xFF) / 255.0F, (color & 0xFF) / 255.0F);
        lifetime = LIFETIME;
        // The game's own slowing-down is not wanted: the swing below is the
        // whole movement, and friction would eat it.
        friction = 1.0F;
        gravity = 0.0F;
        hasPhysics = true;
        float size = Mth.lerp(random.nextFloat(), SMALLEST, LARGEST);
        quadSize = size;
        setSize(size, size);
        swing = random.nextFloat() * Mth.TWO_PI;
        swingPace = Mth.lerp(random.nextFloat(), 0.06F, 0.12F) * (random.nextBoolean() ? 1.0F : -1.0F);
        turnPace = Mth.lerp(random.nextFloat(), 0.02F, 0.06F) * (random.nextBoolean() ? 1.0F : -1.0F);
        roll = random.nextFloat() * Mth.TWO_PI;
        oRoll = roll;
    }

    /**
     * A leaf is drawn from the game's block pictures, cut out, not
     * blended, as the crumbs of a block that is broken are.
     *
     * <p>The pictures of the leaves sit in the block atlas and not in the
     * one for particles, because the game only lets a mod into the
     * particle atlas through a particle type of its own, and a particle
     * type is an entry in a registry that a server and a client must
     * agree on. These leaves are each player's own business, so they are
     * drawn the way the game draws the crumbs of a broken block: the same
     * cut-out drawing, with the block pictures bound.</p>
     */
    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.TERRAIN_SHEET;
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        if (age++ >= lifetime) {
            remove();
            return;
        }
        // Falls faster until it reaches its falling speed, which is slow.
        yd = Math.max(-FALL_SPEED, yd - FALL_RISE);
        // Swings sideways, a little wider every tick.
        swing += swingPace;
        double width = Math.min(SWING_MOST, age * SWING_GROWTH);
        xd = Math.cos(swing) * width;
        zd = Math.sin(swing) * width;
        oRoll = roll;
        roll += turnPace;
        move(xd, yd, zd);
        if (onGround && age > SETTLE_AFTER) {
            // Leaves do not pile up: the ones on the ground are the ground's own look.
            remove();
        }
    }

    /** Tells {@link FallingLeaves} when the leaf is gone, so that it counts right. */
    @Override
    public void remove() {
        super.remove();
        if (counted) {
            counted = false;
            FallingLeaves.gone();
        }
    }
}
