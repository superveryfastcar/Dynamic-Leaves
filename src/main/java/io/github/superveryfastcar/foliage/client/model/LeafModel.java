package io.github.superveryfastcar.foliage.client.model;

import io.github.superveryfastcar.foliage.shape.Recipe;
import io.github.superveryfastcar.foliage.shape.ShapeRules;
import io.github.superveryfastcar.foliage.shape.ShapeSettings;
import io.github.superveryfastcar.foliage.shape.Surround;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

/**
 * Draws one kind of Dynamic Trees leaves in the shape the player chose.
 * It wraps the leaf block's own model and takes its place in the game.
 *
 * <h2>How a leaf block is drawn</h2>
 * The game asks a model twice per block while it builds a chunk:
 * <ol>
 *   <li>{@link #getModelData} is asked once, with the block's position.
 *       Here the model looks at the six neighbors ({@link Surround}), lets
 *       {@link ShapeRules} decide which pieces the block gets (a
 *       {@link Recipe}), and hands back the finished faces of that recipe
 *       as a note for step 2.</li>
 *   <li>{@link #getQuads} is asked seven times, once per side of the block
 *       and once for "no side". It answers the six sides with nothing and
 *       gives all faces of the recipe in the last answer. Faces toward
 *       covered sides are simply not in the recipe, so the game does not
 *       have to work out what is hidden.</li>
 * </ol>
 *
 * <h2>Speed</h2>
 * Both steps run for every leaf block of every chunk that is built, on
 * several threads at once. The faces of a recipe are made once and kept
 * ({@link Built}); two leaf blocks with the same recipe share them. Step 1
 * reads six neighbors, does a little arithmetic and one table lookup, and
 * makes no garbage. Step 2 is one lookup.
 *
 * <h2>When the model steps aside</h2>
 * With the shape set to "plain", or while Seasons shows leaves thinning
 * out, the model does nothing itself and passes every question on to the
 * model it wraps. Anything that asks for faces without step 1 (a leaf
 * block drawn in a hand, by another mod, as a falling block) also gets the
 * wrapped model's answer.
 */
public final class LeafModel extends BakedModelWrapper<BakedModel> {

    /** The note from step 1 to step 2: the finished faces of the block's recipe. */
    static final ModelProperty<Built> BUILT = new ModelProperty<>();

    private static final Direction[] DIRECTIONS = Direction.values();

    /** The answer for the six sides of a block. */
    private static final List<BakedQuad> NOTHING = List.of();

    /**
     * One position holder per chunk-building thread, for looking at the
     * neighbors without making garbage.
     */
    private static final ThreadLocal<BlockPos.MutableBlockPos> CURSOR = ThreadLocal.withInitial(BlockPos.MutableBlockPos::new);

    /** The pictures this kind of leaves is drawn with. */
    private final LeafSprites sprites;

    /** The tint number of the leaf block's own faces; the game colors faces with this number in the leaf color. */
    private final int tint;

    /** Everything built for the settings in use. Replaced as a whole when the settings change. */
    private volatile Cache cache = new Cache(-1, ShapeSettings.DEFAULT);

    /**
     * @param original the leaf block's own model
     * @param sprites  the pictures this kind of leaves is drawn with
     * @param tint     the tint number of the leaf block's own faces
     */
    LeafModel(BakedModel original, LeafSprites sprites, int tint) {
        super(original);
        this.sprites = sprites;
        this.tint = tint;
    }

    /** The model this one wraps. */
    BakedModel original() {
        return originalModel;
    }

    /** The pictures this kind of leaves is drawn with. */
    LeafSprites sprites() {
        return sprites;
    }

    /** How many different recipes were built for the settings in use. */
    int recipeCount() {
        return cache.recipeCount();
    }

    // ------------------------------------------------------------------
    // Step 1: which pieces this position gets
    // ------------------------------------------------------------------

    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData modelData) {
        LeafModels.Active active = LeafModels.active();
        if (active == null) {
            return super.getModelData(level, pos, state, modelData);
        }
        Built built = built(active, recipe(active.settings(), level, pos, state));
        // Leaf blocks carry no notes of their own, so nearly always the
        // ready-made note of the recipe can be handed out as it is.
        return modelData == ModelData.EMPTY ? built.data : modelData.derive().with(BUILT, built).build();
    }

    /**
     * The recipe of the leaf block at a position, for the given settings.
     * Also used by the tests, which compare it with what they see.
     */
    static int recipe(ShapeSettings settings, BlockAndTintGetter level, BlockPos pos, BlockState state) {
        int surround = Surround.read(level, pos, state, CURSOR.get());
        return ShapeRules.recipe(settings, surround, ShapeRules.variant(pos.getX(), pos.getY(), pos.getZ()));
    }

    /** The finished faces of a recipe, built now if this is the first time it is needed. */
    private Built built(LeafModels.Active active, int recipe) {
        Cache current = cache;
        if (current.version != active.version()) {
            current = renew(active);
        }
        Built built = current.table.get(recipe);
        return built != null ? built : current.build(recipe);
    }

    /** Starts over after the settings changed. */
    private synchronized Cache renew(LeafModels.Active active) {
        Cache current = cache;
        if (current.version != active.version()) {
            current = new Cache(active.version(), active.settings());
            cache = current;
        }
        return current;
    }

    // ------------------------------------------------------------------
    // Step 2: the faces
    // ------------------------------------------------------------------

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand,
                                    ModelData data, @Nullable RenderType renderType) {
        Built built = data.get(BUILT);
        if (built == null) {
            List<BakedQuad> quads = super.getQuads(state, side, rand, data, renderType);
            if (LeafModels.COUNTING) {
                LeafModels.count(side == null, quads.size(), 0);
            }
            return quads;
        }
        if (side != null) {
            return NOTHING;
        }
        // Where leaves cannot be see-through (the game's "fast" graphics)
        // only the outside of the block is drawn, as the game does itself.
        boolean solid = renderType == RenderType.solid();
        List<BakedQuad> quads = solid ? built.solid : built.cutout;
        if (LeafModels.COUNTING) {
            LeafModels.count(true, quads.size(), solid ? 0 : Recipe.slantedQuadCount(built.recipe));
        }
        return quads;
    }

    // ------------------------------------------------------------------
    // What is kept
    // ------------------------------------------------------------------

    /**
     * The finished faces of one recipe. Nothing in it changes after it is
     * made, so it can be handed from thread to thread freely.
     */
    static final class Built {

        /** The recipe these faces were built from. */
        final int recipe;

        /** The faces where leaves are see-through: all pieces of the recipe. */
        final List<BakedQuad> cutout;

        /** The faces where leaves are not see-through: the outer faces of the open sides. */
        final List<BakedQuad> solid;

        /** A ready-made note for step 2 that holds this object. */
        final ModelData data;

        Built(int recipe, List<BakedQuad> cutout, List<BakedQuad> solid) {
            this.recipe = recipe;
            this.cutout = cutout;
            this.solid = solid;
            this.data = ModelData.of(BUILT, this);
        }
    }

    /**
     * Everything built for one set of settings: the single pieces, which
     * many recipes share, and the face lists of the recipes seen so far.
     */
    private final class Cache {

        /** The settings' running number in {@link LeafModels}. */
        final int version;
        final ShapeSettings settings;
        final RecipeTable<Built> table = new RecipeTable<>();

        // The pieces, made on first use. Only touched inside build().
        /** Outer faces by side and quarter turn. */
        private final BakedQuad[][] faces = new BakedQuad[6][4];
        /** Inner planes by axis and quarter turn. */
        private final BakedQuad[][][] planes = new BakedQuad[3][4][];
        /** Cards by variant and by first or second card. */
        private final BakedQuad[][][] cards = new BakedQuad[Recipe.VARIANTS][2][];
        /** Clumps by size in pixels and by anchor (0 for none, else 1 plus the side's number); six faces each, by side. */
        private final BakedQuad[][][] clumps = new BakedQuad[16][7][];
        /** Twigs by growing direction and variant. */
        private final BakedQuad[][][] twigs = new BakedQuad[6][Recipe.VARIANTS][];

        Cache(int version, ShapeSettings settings) {
            this.version = version;
            this.settings = settings;
        }

        synchronized int recipeCount() {
            return table.size();
        }

        /** Builds the faces of a recipe, unless another thread just did. */
        synchronized Built build(int recipe) {
            Built built = table.get(recipe);
            if (built == null) {
                built = make(recipe);
                table.put(recipe, built);
            }
            return built;
        }

        private Built make(int recipe) {
            int open = Recipe.open(recipe);
            int variant = Recipe.variant(recipe);
            List<BakedQuad> solid = new ArrayList<>(6);
            for (Direction side : DIRECTIONS) {
                if ((open & 1 << side.ordinal()) != 0) {
                    solid.add(face(side, variant + side.ordinal() & 3));
                }
            }
            List<BakedQuad> cutout = new ArrayList<>(12);
            if (Recipe.faces(recipe)) {
                cutout.addAll(solid);
            }
            // Each plane's picture is turned differently, so that the three do not line up.
            if (Recipe.plane(recipe, Recipe.PLANE_X)) {
                addAll(cutout, plane(0, variant + 2 & 3));
            }
            if (Recipe.plane(recipe, Recipe.PLANE_Y)) {
                addAll(cutout, plane(1, variant + 3 & 3));
            }
            if (Recipe.plane(recipe, Recipe.PLANE_Z)) {
                addAll(cutout, plane(2, variant + 1 & 3));
            }
            if (sprites.card() != null) {
                for (int index = 0; index < Recipe.cards(recipe); index++) {
                    addAll(cutout, card(variant, index));
                }
            }
            int clump = Recipe.clump(recipe);
            if (clump != 0) {
                Direction anchor = Recipe.anchor(recipe);
                BakedQuad[] faces = clump(clump, anchor);
                // The face against a covered side cannot be seen.
                boolean hidden = Recipe.clumpFaces(recipe) == 5;
                for (Direction side : DIRECTIONS) {
                    if (!(hidden && side == anchor)) {
                        cutout.add(faces[side.ordinal()]);
                    }
                }
            }
            Direction grow = Recipe.twig(recipe);
            if (grow != null && sprites.twigs().length > 0) {
                addAll(cutout, twig(grow, variant));
            }
            return new Built(recipe, List.copyOf(cutout), List.copyOf(solid));
        }

        private BakedQuad face(Direction side, int turn) {
            BakedQuad face = faces[side.ordinal()][turn];
            if (face == null) {
                face = QuadMaker.face(sprites.leaf(), side, turn, tint);
                faces[side.ordinal()][turn] = face;
            }
            return face;
        }

        private BakedQuad[] plane(int axis, int turn) {
            BakedQuad[] plane = planes[axis][turn];
            if (plane == null) {
                plane = QuadMaker.plane(sprites.leaf(), axis, turn, tint);
                planes[axis][turn] = plane;
            }
            return plane;
        }

        private BakedQuad[] card(int variant, int index) {
            BakedQuad[] card = cards[variant][index];
            if (card == null) {
                card = QuadMaker.card(sprites.card(), variant, index, settings.cardWidth(), settings.cardHeight(), tint);
                cards[variant][index] = card;
            }
            return card;
        }

        private BakedQuad[] clump(int size, @Nullable Direction anchor) {
            int anchorCode = anchor == null ? 0 : 1 + anchor.ordinal();
            BakedQuad[] clump = clumps[size][anchorCode];
            if (clump == null) {
                clump = QuadMaker.clump(sprites.leaf(), size, anchor, tint);
                clumps[size][anchorCode] = clump;
            }
            return clump;
        }

        private BakedQuad[] twig(Direction grow, int variant) {
            BakedQuad[] twig = twigs[grow.ordinal()][variant];
            if (twig == null) {
                // Two of the twig pictures per twig, a different pair per variant.
                int pictures = sprites.twigs().length;
                twig = QuadMaker.twig(sprites.twigs()[(variant & 3) % pictures],
                        sprites.twigs()[((variant >> 2) + 1 + (variant & 3)) % pictures], grow);
                twigs[grow.ordinal()][variant] = twig;
            }
            return twig;
        }

        private void addAll(List<BakedQuad> list, BakedQuad[] piece) {
            for (BakedQuad quad : piece) {
                list.add(quad);
            }
        }
    }
}
