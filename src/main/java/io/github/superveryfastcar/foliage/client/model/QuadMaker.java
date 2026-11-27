package io.github.superveryfastcar.foliage.client.model;

import javax.annotation.Nullable;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.client.model.pipeline.QuadBakingVertexConsumer;
import org.joml.Vector3f;

/**
 * Makes the faces of the pieces a leaf block is drawn from: outer faces,
 * inner planes, cards, clumps and twigs (see
 * {@link io.github.superveryfastcar.foliage.shape.Recipe}).
 *
 * <p>All measures are in pixels of a block, 0 to 16 from one side to the
 * other. A face is given by four corners, counter-clockwise as seen from
 * its front; the game only draws a face from its front, so every piece that
 * is seen from both sides is made of two faces back to back.</p>
 *
 * <p>The faces made here are kept by the model and handed out again and
 * again, so nothing in this class runs while a chunk is being built, except
 * the first time a piece is needed.</p>
 */
final class QuadMaker {

    /** The corners of a whole picture (bottom left, bottom right, top right, top left): across and down, in 16ths. */
    private static final float[] PICTURE_U = {0, 16, 16, 0};
    private static final float[] PICTURE_V = {16, 16, 0, 0};

    /** How far a twig's planes stay from the corners of the block. */
    private static final float TWIG_INSET = 0.8F;

    private QuadMaker() {
    }

    /**
     * One outer face of the block's cube.
     *
     * @param sprite the leaf texture
     * @param side   which side of the cube
     * @param turn   by how many quarter turns the picture is turned, so
     *               that neighboring faces do not repeat each other
     * @param tint   the tint number of the leaf block's own faces
     */
    static BakedQuad face(TextureAtlasSprite sprite, Direction side, int turn, int tint) {
        Vector3f[] corners = switch (side) {
            case DOWN -> new Vector3f[] {at(0, 0, 0), at(16, 0, 0), at(16, 0, 16), at(0, 0, 16)};
            case UP -> new Vector3f[] {at(0, 16, 16), at(16, 16, 16), at(16, 16, 0), at(0, 16, 0)};
            case NORTH -> new Vector3f[] {at(16, 0, 0), at(0, 0, 0), at(0, 16, 0), at(16, 16, 0)};
            case SOUTH -> new Vector3f[] {at(0, 0, 16), at(16, 0, 16), at(16, 16, 16), at(0, 16, 16)};
            case WEST -> new Vector3f[] {at(0, 0, 0), at(0, 0, 16), at(0, 16, 16), at(0, 16, 0)};
            case EAST -> new Vector3f[] {at(16, 0, 16), at(16, 0, 0), at(16, 16, 0), at(16, 16, 16)};
        };
        float[] u = new float[4];
        float[] v = new float[4];
        for (int i = 0; i < 4; i++) {
            u[i] = PICTURE_U[i + turn & 3];
            v[i] = PICTURE_V[i + turn & 3];
        }
        return quad(sprite, corners, u, v, tint, true, true);
    }

    /**
     * An inner plane through the middle of the block, seen from both sides.
     *
     * @param sprite the leaf texture
     * @param axis   the axis the plane lies across: 0 = x (it faces west
     *               and east), 1 = y, 2 = z
     * @param turn   by how many quarter turns the picture is turned
     * @param tint   the tint number of the leaf block's own faces
     * @return two faces, back to back
     */
    static BakedQuad[] plane(TextureAtlasSprite sprite, int axis, int turn, int tint) {
        Vector3f[] corners = switch (axis) {
            case 0 -> new Vector3f[] {at(8, 0, 16), at(8, 0, 0), at(8, 16, 0), at(8, 16, 16)};
            case 1 -> new Vector3f[] {at(0, 8, 16), at(16, 8, 16), at(16, 8, 0), at(0, 8, 0)};
            default -> new Vector3f[] {at(0, 0, 8), at(16, 0, 8), at(16, 16, 8), at(0, 16, 8)};
        };
        float[] u = new float[4];
        float[] v = new float[4];
        float[] backU = new float[4];
        float[] backV = new float[4];
        for (int i = 0; i < 4; i++) {
            u[i] = PICTURE_U[i + turn & 3];
            v[i] = PICTURE_V[i + turn & 3];
            backU[i] = PICTURE_U[3 - i + turn & 3];
            backV[i] = PICTURE_V[3 - i + turn & 3];
        }
        return new BakedQuad[] {
                quad(sprite, corners, u, v, tint, true, true),
                quad(sprite, new Vector3f[] {corners[3], corners[2], corners[1], corners[0]}, backU, backV, tint, true, true)};
    }

    /**
     * A leaf card: an upright plane with a round leaf picture that stands
     * at a slant and reaches out past the block, seen from both sides.
     *
     * <p>The card is turned 22.5 degrees off the block's sides, plus a
     * quarter of a quarter turn per variant, and the second card of a
     * block stands across the first. It is never at 45 degrees, where the
     * cards of diagonal neighbors would lie in the same plane and flicker.
     * It also sits a pixel or so off the middle, a different way per
     * variant.</p>
     *
     * @param sprite  the card picture, twice as wide and tall as a leaf texture
     * @param variant the position's variant, 0 to 15
     * @param index   0 for the first card of a block, 1 for the second
     * @param width   how wide the card is, in pixels
     * @param height  how tall the card is, in pixels
     * @param tint    the tint number of the leaf block's own faces
     * @return two faces, back to back
     */
    static BakedQuad[] card(TextureAtlasSprite sprite, int variant, int index, int width, int height, int tint) {
        double angle = Math.toRadians(22.5 + 45.0 * (variant & 3) + 90.0 * index);
        float halfX = (float) Math.cos(angle) * width / 2.0F;
        float halfZ = (float) Math.sin(angle) * width / 2.0F;
        float middleX = 8.0F + ((variant >> 2 & 1) * 2 - 1) * (index == 0 ? 1.0F : -1.5F);
        float middleZ = 8.0F + ((variant >> 3 & 1) * 2 - 1) * (index == 0 ? 1.5F : -1.0F);
        float middleY = 8.0F + index;
        float low = middleY - height / 2.0F;
        float high = middleY + height / 2.0F;
        // The card shows the middle part of its picture at the same size as
        // the leaves on the block's faces: 32 pixels of picture would fill a
        // card 32 wide. Positions in the picture are in 16ths of it.
        float left = (32 - width) / 4.0F;
        float top = (32 - height) / 4.0F;
        Vector3f a = at(middleX - halfX, low, middleZ - halfZ);
        Vector3f b = at(middleX + halfX, low, middleZ + halfZ);
        Vector3f c = at(middleX + halfX, high, middleZ + halfZ);
        Vector3f d = at(middleX - halfX, high, middleZ - halfZ);
        return twoSided(sprite, a, b, c, d, left, 16.0F - left, 16.0F - top, top, tint, false, true);
    }

    /**
     * A clump: a small cube of leaves. It sits against one side of the
     * block (its anchor), in the middle of that side, or in the middle of
     * the block if it has no anchor. Each of its faces shows the part of
     * the leaf texture that lies there.
     *
     * @param sprite the leaf texture
     * @param size   the side of the cube in pixels
     * @param anchor the side of the block the cube sits against, or null
     * @param tint   the tint number of the leaf block's own faces
     * @return six faces, in the order of {@link Direction#ordinal()}, so
     *         that the caller can leave out one that cannot be seen
     */
    static BakedQuad[] clump(TextureAtlasSprite sprite, int size, @Nullable Direction anchor, int tint) {
        // The cube's extent along x, y and z.
        float[] lo = {8.0F - size / 2.0F, 8.0F - size / 2.0F, 8.0F - size / 2.0F};
        float[] hi = {8.0F + size / 2.0F, 8.0F + size / 2.0F, 8.0F + size / 2.0F};
        if (anchor != null) {
            int axis = anchor.getAxis().ordinal();
            if (anchor.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
                hi[axis] = 16.0F;
                lo[axis] = 16.0F - size;
            } else {
                lo[axis] = 0.0F;
                hi[axis] = size;
            }
        }
        float x0 = lo[0];
        float x1 = hi[0];
        float y0 = lo[1];
        float y1 = hi[1];
        float z0 = lo[2];
        float z1 = hi[2];
        // Picture positions: across by x (or z on the west and east faces), down by height (or z on top and bottom).
        float[] acrossX = {x0, x1, x1, x0};
        float[] acrossZ = {z0, z1, z1, z0};
        float[] downY = {16.0F - y0, 16.0F - y0, 16.0F - y1, 16.0F - y1};
        float[] downZ = {z1, z1, z0, z0};
        BakedQuad[] faces = new BakedQuad[6];
        faces[Direction.DOWN.ordinal()] = quad(sprite, new Vector3f[] {at(x0, y0, z0), at(x1, y0, z0), at(x1, y0, z1), at(x0, y0, z1)},
                acrossX, new float[] {z0, z0, z1, z1}, tint, true, true);
        faces[Direction.UP.ordinal()] = quad(sprite, new Vector3f[] {at(x0, y1, z1), at(x1, y1, z1), at(x1, y1, z0), at(x0, y1, z0)},
                acrossX, downZ, tint, true, true);
        faces[Direction.NORTH.ordinal()] = quad(sprite, new Vector3f[] {at(x1, y0, z0), at(x0, y0, z0), at(x0, y1, z0), at(x1, y1, z0)},
                acrossX, downY, tint, true, true);
        faces[Direction.SOUTH.ordinal()] = quad(sprite, new Vector3f[] {at(x0, y0, z1), at(x1, y0, z1), at(x1, y1, z1), at(x0, y1, z1)},
                acrossX, downY, tint, true, true);
        faces[Direction.WEST.ordinal()] = quad(sprite, new Vector3f[] {at(x0, y0, z0), at(x0, y0, z1), at(x0, y1, z1), at(x0, y1, z0)},
                acrossZ, downY, tint, true, true);
        faces[Direction.EAST.ordinal()] = quad(sprite, new Vector3f[] {at(x1, y0, z1), at(x1, y0, z0), at(x1, y1, z0), at(x1, y1, z1)},
                acrossZ, downY, tint, true, true);
        return faces;
    }

    /**
     * A twig: two crossed planes from corner to corner of the block, each
     * with a bare twig picture that grows from the middle of its bottom
     * edge, seen from both sides. The whole cross is turned so that the
     * twig grows in the given direction.
     *
     * <p>Twig pictures have the color of bark and are not tinted with the
     * leaf color. They take no shade from the direction they face, so a
     * twig is evenly lit from all around.</p>
     *
     * @param first  the picture on one plane
     * @param second the picture on the other plane
     * @param grow   the direction the twig grows in
     * @return four faces
     */
    static BakedQuad[] twig(TextureAtlasSprite first, TextureAtlasSprite second, Direction grow) {
        float near = TWIG_INSET;
        float far = 16.0F - TWIG_INSET;
        BakedQuad[] one = twoSided(first, turned(grow, near, 0, near), turned(grow, far, 0, far),
                turned(grow, far, 16, far), turned(grow, near, 16, near), 0, 16, 16, 0, -1, false, false);
        BakedQuad[] other = twoSided(second, turned(grow, near, 0, far), turned(grow, far, 0, near),
                turned(grow, far, 16, near), turned(grow, near, 16, far), 0, 16, 16, 0, -1, false, false);
        return new BakedQuad[] {one[0], one[1], other[0], other[1]};
    }

    // ------------------------------------------------------------------
    // Building blocks
    // ------------------------------------------------------------------

    /** A point given in pixels of a block. */
    private static Vector3f at(float x, float y, float z) {
        return new Vector3f(x / 16.0F, y / 16.0F, z / 16.0F);
    }

    /**
     * A point of a shape that grows upward from the block's bottom face,
     * turned so that the shape grows in the given direction instead.
     */
    private static Vector3f turned(Direction grow, float x, float y, float z) {
        return switch (grow) {
            case DOWN -> at(x, 16 - y, 16 - z);
            case SOUTH -> at(x, 16 - z, y);
            case NORTH -> at(x, z, 16 - y);
            case EAST -> at(y, 16 - x, z);
            case WEST -> at(16 - y, x, z);
            case UP -> at(x, y, z);
        };
    }

    /**
     * A flat piece seen from both sides. The corners go bottom left, bottom
     * right, top right, top left as seen from the front. {@code left} and
     * {@code right} are the picture's edges across, {@code bottom} and
     * {@code top} its edges down, in 16ths of the picture.
     */
    private static BakedQuad[] twoSided(TextureAtlasSprite sprite, Vector3f a, Vector3f b, Vector3f c, Vector3f d,
                                        float left, float right, float bottom, float top, int tint, boolean shade, boolean occlusion) {
        return new BakedQuad[] {
                quad(sprite, new Vector3f[] {a, b, c, d}, new float[] {left, right, right, left},
                        new float[] {bottom, bottom, top, top}, tint, shade, occlusion),
                quad(sprite, new Vector3f[] {d, c, b, a}, new float[] {left, right, right, left},
                        new float[] {top, top, bottom, bottom}, tint, shade, occlusion)};
    }

    /**
     * One face.
     *
     * @param sprite    the picture
     * @param corners   four corners, counter-clockwise as seen from the front
     * @param u         each corner's place across the picture, in 16ths
     * @param v         each corner's place down the picture, in 16ths
     * @param tint      the tint number, or -1 for a picture shown as it is
     * @param shade     whether the face is darkened by the direction it faces
     * @param occlusion whether the face takes soft shadows from its neighbors
     */
    private static BakedQuad quad(TextureAtlasSprite sprite, Vector3f[] corners, float[] u, float[] v,
                                  int tint, boolean shade, boolean occlusion) {
        Vector3f normal = new Vector3f(corners[1]).sub(corners[0]).cross(new Vector3f(corners[2]).sub(corners[0])).normalize();
        QuadBakingVertexConsumer baker = new QuadBakingVertexConsumer();
        baker.setSprite(sprite);
        baker.setTintIndex(tint);
        baker.setShade(shade);
        baker.setHasAmbientOcclusion(occlusion);
        baker.setDirection(Direction.getNearest(normal.x, normal.y, normal.z));
        for (int i = 0; i < 4; i++) {
            baker.addVertex(corners[i].x, corners[i].y, corners[i].z)
                    .setColor(255, 255, 255, 255)
                    .setUv(sprite.getU(u[i] / 16.0F), sprite.getV(v[i] / 16.0F))
                    .setUv2(0, 0)
                    .setNormal(normal.x, normal.y, normal.z);
        }
        return baker.bakeQuad();
    }
}
