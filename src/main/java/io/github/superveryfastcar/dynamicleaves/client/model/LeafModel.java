package io.github.superveryfastcar.dynamicleaves.client.model;

import io.github.superveryfastcar.dynamicleaves.block.BareLeavesBlock;
import io.github.superveryfastcar.dynamicleaves.client.ClientLeafClock;
import io.github.superveryfastcar.dynamicleaves.season.LeafClock;
import io.github.superveryfastcar.dynamicleaves.season.LeafStage;
import io.github.superveryfastcar.dynamicleaves.shape.LeafLook;
import io.github.superveryfastcar.dynamicleaves.shape.LeafPicture;
import io.github.superveryfastcar.dynamicleaves.shape.MiniBranch;
import io.github.superveryfastcar.dynamicleaves.shape.Recipe;
import io.github.superveryfastcar.dynamicleaves.shape.ShapeRules;
import io.github.superveryfastcar.dynamicleaves.shape.ShapeSettings;
import io.github.superveryfastcar.dynamicleaves.shape.Surround;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

/**
 * Draws one kind of Dynamic Trees leaves in the shape the player chose and
 * at the stage of the leaf calendar each leaf block is at. It wraps the
 * leaf block's own model and takes its place in the game.
 *
 * <h2>How a leaf block is drawn</h2>
 * The game asks a model twice per block while it builds a chunk:
 * <ol>
 *   <li>{@link #getModelData} is asked once, with the block's position.
 *       Here the model has the block's look worked out ({@link LeafLook}):
 *       the stage of the leaf calendar it is at, its six neighbors
 *       ({@link Surround}) and from them the pieces it gets (a
 *       {@link Recipe}, decided by {@link ShapeRules}). It hands back the
 *       finished faces of that recipe as a note for step 2.</li>
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
 * makes no garbage. Step 2 is one lookup. In the months in which no tree
 * changes, the leaf calendar costs nothing at all.
 *
 * <h2>When the model steps aside</h2>
 * With the shape set to "plain", a leaf block in full leaf is drawn by the
 * model this one wraps. Anything that asks for faces without step 1 (a
 * leaf block drawn in a hand, by another mod, as a falling block) also gets
 * the wrapped model's answer.
 *
 * <h2>Bare blocks</h2>
 * The bare block of a kind of leaves ({@link BareLeavesBlock}) is drawn by
 * the same model as the leaf block, through the same two steps. Asked
 * without step 1, the model answers with nothing for a bare block: that is
 * how the bare spots of a tree that is cut down fall as nothing.
 */
public final class LeafModel extends BakedModelWrapper<BakedModel> {

    /** The note from step 1 to step 2: the finished faces of the block's recipe. */
    static final ModelProperty<Built> BUILT = new ModelProperty<>();

    /**
     * A note for step 2 on a bare block: "draw this one as a whole leaf
     * block, with the model that is wrapped". Set when the leaf calendar
     * has the spot in full leaf and the shape is "plain".
     */
    private static final ModelProperty<Boolean> WHOLE = new ModelProperty<>();
    private static final ModelData WHOLE_DATA = ModelData.of(WHOLE, Boolean.TRUE);

    private static final Direction[] DIRECTIONS = Direction.values();

    /** The answer for the six sides of a block. */
    private static final List<BakedQuad> NOTHING = List.of();

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
        int recipe = recipe(active.settings(), ClientLeafClock.clock(), level, pos, state);
        if (recipe == Recipe.NONE) {
            ModelData plain = super.getModelData(level, pos, state, modelData);
            // A bare block that is to be drawn as a whole leaf block says so; see getQuads.
            if (state.getBlock() instanceof BareLeavesBlock) {
                return plain == ModelData.EMPTY ? WHOLE_DATA : plain.derive().with(WHOLE, Boolean.TRUE).build();
            }
            return plain;
        }
        Built built = built(active, recipe);
        // Leaf blocks carry no notes of their own, so nearly always the
        // ready-made note of the recipe can be handed out as it is.
        return modelData == ModelData.EMPTY ? built.data : modelData.derive().with(BUILT, built).build();
    }

    /**
     * The recipe of the leaf block at a position, for the given settings
     * and moment of the leaf calendar, or {@link Recipe#NONE} if the block
     * is drawn by the wrapped model. Also used by the tests, which compare
     * it with what they see.
     *
     * @param clock the leaf calendar, or null for "everything in full leaf"
     */
    static int recipe(ShapeSettings settings, @Nullable LeafClock clock, BlockAndTintGetter level, BlockPos pos, BlockState state) {
        // A chunk being built knows its blocks but not its biomes; those are read from the player's world.
        LevelReader biomes = clock == null ? null : level instanceof LevelReader world ? world : ClientLeafClock.biomes();
        return LeafLook.forThread().recipe(settings, clock, biomes, level, pos, state);
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
            // Whoever asks about a bare block without having asked first
            // where it stands (Dynamic Trees, for a tree that falls) gets
            // nothing: a bare block is bare unless the leaf calendar says
            // otherwise.
            if (state != null && state.getBlock() instanceof BareLeavesBlock && data.get(WHOLE) == null) {
                return NOTHING;
            }
            List<BakedQuad> quads = super.getQuads(state, side, rand, data, renderType);
            if (LeafModels.COUNTING) {
                LeafModels.count(side == null, quads.size(), 0, 0);
            }
            return quads;
        }
        if (side != null) {
            return NOTHING;
        }
        // Where leaves cannot be see-through (the game's "fast" graphics)
        // only the pieces are drawn that are solid things: see Built.solid.
        boolean solid = renderType == RenderType.solid();
        List<BakedQuad> quads = solid ? built.solid : built.cutout;
        if (LeafModels.COUNTING) {
            LeafModels.count(true, quads.size(), solid ? 0 : Recipe.slantedQuadCount(built.recipe),
                    solid && Recipe.solidWhole(built.recipe) ? 0 : built.bark);
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

        /**
         * The faces where leaves are not see-through (the game's "fast"
         * graphics): a whole block while the spot is in leaf, after that
         * the pieces that are solid things (the outer faces while most
         * leaves are there, the clump, the mini branch) and none of the
         * flat pictures ({@link Recipe#solidQuadCount}).
         */
        final List<BakedQuad> solid;

        /** How many of the faces belong to the mini branch. Only for the self test's counting. */
        final int bark;

        /** A ready-made note for step 2 that holds this object. */
        final ModelData data;

        Built(int recipe, List<BakedQuad> cutout, List<BakedQuad> solid, int bark) {
            this.recipe = recipe;
            this.cutout = cutout;
            this.solid = solid;
            this.bark = bark;
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
        // "By picture" is by the number of the LeafPicture the piece shows.
        /** Outer faces by picture, side and quarter turn. */
        private final BakedQuad[][][] faces = new BakedQuad[LeafPicture.COUNT][6][4];
        /** Inner planes by picture, axis and quarter turn. */
        private final BakedQuad[][][][] planes = new BakedQuad[LeafPicture.COUNT][3][4][];
        /** Cards by picture, variant and by first or second card. */
        private final BakedQuad[][][][] cards = new BakedQuad[LeafPicture.CARD_COUNT][Recipe.VARIANTS][2][];
        /** Clumps by picture, size in pixels and anchor (0 for none, else 1 plus the side's number); six faces each, by side. */
        private final BakedQuad[][][][] clumps = new BakedQuad[LeafPicture.COUNT][16][7][];
        /** Tufts (clumps on the end of a mini branch) by picture, size, growing direction and where they end. */
        private final Map<Integer, BakedQuad[]> tufts = new HashMap<>();
        /** Mini branches by growing direction, by where they start, by variant and by the number of their thinner twigs. */
        private final BakedQuad[][][][][] branches = new BakedQuad[6][4][Recipe.VARIANTS][MiniBranch.MAX_FORKS + 1][];
        /** The sticks that carry a clump, by growing direction, by where they start and by the clump's size. */
        private final BakedQuad[][][][] stems = new BakedQuad[6][4][16][];

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
            LeafStage stage = Recipe.stage(recipe);
            // The stage picks the pictures; the pieces are the recipe's.
            LeafPicture picture = LeafPicture.forStage(stage);
            List<BakedQuad> outer = new ArrayList<>(6);
            for (Direction side : DIRECTIONS) {
                if ((open & 1 << side.ordinal()) != 0) {
                    outer.add(face(picture, side, variant + side.ordinal() & 3));
                }
            }
            List<BakedQuad> cutout = new ArrayList<>(12);
            if (Recipe.faces(recipe)) {
                cutout.addAll(outer);
            }
            // Each plane's picture is turned differently, so that the three do not line up.
            if (Recipe.plane(recipe, Recipe.PLANE_X)) {
                addAll(cutout, plane(picture, 0, variant + 2 & 3));
            }
            if (Recipe.plane(recipe, Recipe.PLANE_Y)) {
                addAll(cutout, plane(picture, 1, variant + 3 & 3));
            }
            if (Recipe.plane(recipe, Recipe.PLANE_Z)) {
                addAll(cutout, plane(picture, 2, variant + 1 & 3));
            }
            if (sprites.card(picture) != null) {
                for (int index = 0; index < Recipe.cards(recipe); index++) {
                    addAll(cutout, card(picture, variant, index));
                }
            }
            // The clump and the mini branch are solid things: they are drawn the same whether leaves are see-through or not.
            List<BakedQuad> body = new ArrayList<>(26);
            int clump = Recipe.clump(recipe);
            Direction grow = Recipe.twig(recipe);
            int base = Recipe.twigBase(recipe);
            if (clump != 0) {
                LeafPicture clumpPicture = LeafPicture.forClump(stage);
                if (Recipe.tuft(recipe)) {
                    addAll(body, tuft(clumpPicture, clump, grow, MiniBranch.tuftEnd(base, variant, clump, MiniBranch.upright(grow))));
                } else {
                    Direction anchor = Recipe.anchor(recipe);
                    BakedQuad[] faces = clump(clumpPicture, clump, anchor);
                    // The face against a covered side cannot be seen.
                    boolean hidden = Recipe.clumpFaces(recipe) == 5;
                    for (Direction side : DIRECTIONS) {
                        if (!(hidden && side == anchor)) {
                            body.add(faces[side.ordinal()]);
                        }
                    }
                }
            }
            int bark = 0;
            if (grow != null && sprites.bark() != null) {
                bark = -body.size();
                addAll(body, Recipe.stemOnly(recipe) ? stem(grow, base, clump)
                        : branch(grow, base, variant, Recipe.forks(recipe, settings.forks())));
                bark += body.size();
            }
            cutout.addAll(body);
            List<BakedQuad> solid;
            if (Recipe.solidWhole(recipe)) {
                solid = outer;
            } else {
                solid = new ArrayList<>(body.size() + 6);
                if (Recipe.solidOuter(recipe)) {
                    solid.addAll(outer);
                }
                solid.addAll(body);
            }
            return new Built(recipe, List.copyOf(cutout), solid.isEmpty() ? NOTHING : List.copyOf(solid), bark);
        }

        private BakedQuad face(LeafPicture picture, Direction side, int turn) {
            BakedQuad[] row = faces[picture.ordinal()][side.ordinal()];
            if (row[turn] == null) {
                row[turn] = QuadMaker.face(sprites.leaf(picture), side, turn, tint);
            }
            return row[turn];
        }

        private BakedQuad[] plane(LeafPicture picture, int axis, int turn) {
            BakedQuad[][] row = planes[picture.ordinal()][axis];
            if (row[turn] == null) {
                row[turn] = QuadMaker.plane(sprites.leaf(picture), axis, turn, tint);
            }
            return row[turn];
        }

        private BakedQuad[] card(LeafPicture picture, int variant, int index) {
            BakedQuad[][] row = cards[picture.ordinal()][variant];
            if (row[index] == null) {
                row[index] = QuadMaker.card(sprites.card(picture), variant, index, settings.cardWidth(), settings.cardHeight(), tint);
            }
            return row[index];
        }

        private BakedQuad[] clump(LeafPicture picture, int size, @Nullable Direction anchor) {
            int anchorCode = anchor == null ? 0 : 1 + anchor.ordinal();
            BakedQuad[][] row = clumps[picture.ordinal()][size];
            if (row[anchorCode] == null) {
                row[anchorCode] = QuadMaker.clump(sprites.leaf(picture), size, anchor, tint);
            }
            return row[anchorCode];
        }

        private BakedQuad[] tuft(LeafPicture picture, int size, Direction grow, int end) {
            int key = picture.ordinal() | size << 4 | grow.ordinal() << 8 | end << 12;
            return tufts.computeIfAbsent(key, k -> QuadMaker.tuft(sprites.leaf(picture), size, grow, end, tint));
        }

        /**
         * A mini branch: the main twig and thinner ones.
         *
         * @param forks how many thinner twigs at most
         */
        private BakedQuad[] branch(Direction grow, int base, int variant, int forks) {
            BakedQuad[][] row = branches[grow.ordinal()][base][variant];
            if (row[forks] == null) {
                List<BakedQuad> quads = new ArrayList<>(MiniBranch.faceCount(base, forks));
                for (MiniBranch.Box box : MiniBranch.boxes(base, variant, forks, MiniBranch.upright(grow))) {
                    addAll(quads, QuadMaker.box(sprites.bark(), box, grow));
                }
                row[forks] = quads.toArray(BakedQuad[]::new);
            }
            return row[forks];
        }

        /**
         * The stick that carries a clump at the far end of its block: it
         * ends a pixel inside the clump.
         *
         * @param clump the side of the clump in pixels
         */
        private BakedQuad[] stem(Direction grow, int base, int clump) {
            BakedQuad[][] row = stems[grow.ordinal()][base];
            if (row[clump] == null) {
                row[clump] = QuadMaker.box(sprites.bark(), MiniBranch.stem(base, 16.0F - clump + 1.0F), grow);
            }
            return row[clump];
        }

        private void addAll(List<BakedQuad> list, BakedQuad[] piece) {
            for (BakedQuad quad : piece) {
                list.add(quad);
            }
        }
    }
}
