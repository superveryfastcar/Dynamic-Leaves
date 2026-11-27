package io.github.superveryfastcar.foliage.client.model;

import com.dtteam.dynamictrees.block.leaves.DynamicLeavesBlock;
import io.github.superveryfastcar.foliage.Foliage;
import io.github.superveryfastcar.foliage.client.SeasonsLink;
import io.github.superveryfastcar.foliage.client.sprite.LeafPartsSource;
import io.github.superveryfastcar.foliage.shape.LeafShape;
import io.github.superveryfastcar.foliage.shape.ShapeSettings;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.atomic.LongAdder;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * The leaf models as a whole: the settings they draw by, and putting them
 * in place of Dynamic Trees' own leaf models when the game loads its
 * resources.
 *
 * <h2>Thread rule</h2>
 * Leaf models are asked on the chunk-building threads, many at once. They
 * read the settings as one object that never changes ({@link Active});
 * when the player changes a setting, a new object takes its place and the
 * landscape is drawn again.
 */
public final class LeafModels {

    /**
     * The settings the models draw by, with a running number that goes up
     * with every change, so that a model can tell that what it built is out
     * of date.
     */
    public record Active(ShapeSettings settings, int version) {
    }

    /**
     * Whether the models count the blocks and faces they hand out. Only
     * the developer's self test asks for it; in a normal game the counting
     * code is never run.
     */
    static final boolean COUNTING = Boolean.getBoolean("foliage.dev.selfTest");

    private static final LongAdder BLOCKS = new LongAdder();
    private static final LongAdder FACES = new LongAdder();
    private static final LongAdder SLANTED = new LongAdder();

    /** The settings in use. */
    private static volatile Active current = new Active(ShapeSettings.DEFAULT, 0);

    /** The models that were put in place at the last loading of resources. */
    private static volatile List<LeafModel> models = List.of();

    private LeafModels() {
    }

    // ------------------------------------------------------------------
    // Settings
    // ------------------------------------------------------------------

    /** The settings in use. */
    public static ShapeSettings settings() {
        return current.settings();
    }

    /**
     * Takes new settings into use and has the landscape drawn again, if
     * they differ from the ones in use.
     */
    public static synchronized void setSettings(ShapeSettings settings) {
        Active before = current;
        if (settings.equals(before.settings())) {
            return;
        }
        current = new Active(settings, before.version() + 1);
        redraw();
    }

    /**
     * The settings to draw by right now, or null while the models step
     * aside: with the shape set to plain, and while Seasons shows leaves
     * thinning out (until this mod draws the bare months itself).
     */
    @Nullable
    static Active active() {
        Active active = current;
        return active.settings().shape() == LeafShape.PLAIN || SeasonsLink.thinning() ? null : active;
    }

    /** Has every chunk on screen built again, on the game's main thread. */
    private static void redraw() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        minecraft.execute(() -> {
            if (minecraft.level != null && minecraft.levelRenderer != null) {
                minecraft.levelRenderer.allChanged();
            }
        });
    }

    // ------------------------------------------------------------------
    // Putting the models in place
    // ------------------------------------------------------------------

    /**
     * Wraps the model of every Dynamic Trees leaf block that is drawn as a
     * plain cube with one leaf texture. Called by the game each time it has
     * loaded its models, also after a change of resource packs.
     *
     * <p>Leaf blocks that are drawn some other way are left alone and
     * named in the log.</p>
     */
    public static void wrap(ModelEvent.ModifyBakingResult event) {
        Map<ResourceLocation, ResourceLocation> twigsByTexture = LeafPartsSource.takeMade();
        // The game often shares one finished model between many block
        // states (all states of a leaf block, for one). Each is looked at
        // and wrapped once.
        Map<BakedModel, BakedModel> decided = new IdentityHashMap<>();
        List<LeafModel> wrapped = new ArrayList<>();
        TreeSet<String> done = new TreeSet<>();
        TreeSet<String> skipped = new TreeSet<>();
        TreeSet<String> withoutCards = new TreeSet<>();
        RandomSource random = RandomSource.create();
        for (Block block : BuiltInRegistries.BLOCK) {
            if (!(block instanceof DynamicLeavesBlock leaves)
                    || !(leaves.getLeavesProperties().getPrimitiveLeaves().getBlock() instanceof LeavesBlock)) {
                continue;
            }
            String name = BuiltInRegistries.BLOCK.getKey(block).toString();
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                ModelResourceLocation location = BlockModelShaper.stateToModelLocation(state);
                BakedModel model = event.getModels().get(location);
                if (model == null || model instanceof LeafModel) {
                    continue;
                }
                BakedModel replacement = decided.get(model);
                if (replacement == null) {
                    LeafModel leafModel = wrapOne(event, model, state, random, twigsByTexture);
                    if (leafModel != null) {
                        wrapped.add(leafModel);
                        if (leafModel.sprites().card() == null) {
                            withoutCards.add(name);
                        }
                    }
                    replacement = leafModel != null ? leafModel : model;
                    decided.put(model, replacement);
                }
                if (replacement != model) {
                    event.getModels().put(location, replacement);
                    done.add(name);
                } else {
                    skipped.add(name);
                }
            }
        }
        models = List.copyOf(wrapped);
        Foliage.LOGGER.info("Foliage: new leaf shapes for {} kinds of Dynamic Trees leaves: {}", done.size(), done);
        if (!withoutCards.isEmpty()) {
            Foliage.LOGGER.info("Foliage: no leaf cards for {} (their texture is not listed in an atlas source of type "
                    + "foliage:leaf_parts, or is not square)", withoutCards);
        }
        if (!skipped.isEmpty()) {
            Foliage.LOGGER.info("Foliage: left alone, because not drawn as a plain cube with one texture: {}", skipped);
        }
    }

    /**
     * Looks at one leaf model and wraps it if it is a plain cube: exactly
     * one face on each of the six sides, all with the same picture and
     * tint, and nothing else.
     *
     * @return the wrapper, or null if the model is drawn some other way
     */
    @Nullable
    private static LeafModel wrapOne(ModelEvent.ModifyBakingResult event, BakedModel model, BlockState state,
                                     RandomSource random, Map<ResourceLocation, ResourceLocation> twigsByTexture) {
        TextureAtlasSprite leaf = null;
        int tint = 0;
        try {
            random.setSeed(42L);
            if (!model.getQuads(state, null, random, ModelData.EMPTY, null).isEmpty()) {
                return null;
            }
            for (Direction side : Direction.values()) {
                random.setSeed(42L);
                List<BakedQuad> quads = model.getQuads(state, side, random, ModelData.EMPTY, null);
                if (quads.size() != 1) {
                    return null;
                }
                BakedQuad quad = quads.get(0);
                if (leaf == null) {
                    leaf = quad.getSprite();
                    tint = quad.getTintIndex();
                } else if (quad.getSprite() != leaf || quad.getTintIndex() != tint) {
                    return null;
                }
            }
        } catch (RuntimeException e) {
            // A model from another mod that cannot be asked this way: leave it alone.
            Foliage.LOGGER.debug("Foliage: could not look at the model of {}", state, e);
            return null;
        }
        if (leaf == null) {
            return null;
        }
        ResourceLocation texture = leaf.contents().name();
        ResourceLocation twigBase = twigsByTexture.get(texture);
        TextureAtlasSprite card = twigBase == null ? null : sprite(event, LeafPartsSource.cardName(texture));
        if (twigBase == null) {
            twigBase = LeafPartsSource.DEFAULT_TWIGS;
        }
        List<TextureAtlasSprite> twigs = new ArrayList<>();
        for (int number = 0; number < LeafPartsSource.MAX_TWIG_PICTURES; number++) {
            TextureAtlasSprite twig = sprite(event, twigBase.withSuffix("_" + number));
            if (twig == null) {
                break;
            }
            twigs.add(twig);
        }
        return new LeafModel(model, new LeafSprites(leaf, card, twigs.toArray(TextureAtlasSprite[]::new)), tint);
    }

    /** A picture from the block atlas, or null if the atlas has none of that name. */
    @Nullable
    @SuppressWarnings("deprecation")
    private static TextureAtlasSprite sprite(ModelEvent.ModifyBakingResult event, ResourceLocation name) {
        TextureAtlasSprite sprite = event.getTextureGetter().apply(new Material(TextureAtlas.LOCATION_BLOCKS, name));
        return sprite.contents().name().equals(name) ? sprite : null;
    }

    // ------------------------------------------------------------------
    // For tests
    // ------------------------------------------------------------------

    /** How many leaf models were put in place at the last loading of resources. */
    public static int wrappedCount() {
        return models.size();
    }

    /** How many different recipes all leaf models together built for the settings in use. */
    public static int recipeCount() {
        int count = 0;
        for (LeafModel model : models) {
            count += model.recipeCount();
        }
        return count;
    }

    /**
     * The recipe the leaf model would draw the block at a position by, or
     * -1 if the block's model is not one of ours or the models step aside.
     * See {@link io.github.superveryfastcar.foliage.shape.Recipe}.
     */
    public static int recipeAt(BlockAndTintGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        Active active = active();
        if (!(model instanceof LeafModel) || active == null) {
            return -1;
        }
        return LeafModel.recipe(active.settings(), level, pos, state);
    }

    /**
     * How many faces the leaf model really hands out for the block at a
     * position where leaves are see-through, asked the way the game asks
     * it, or -1 if the block's model is not one of ours.
     */
    public static int faceCountAt(BlockAndTintGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        if (!(model instanceof LeafModel)) {
            return -1;
        }
        ModelData data = model.getModelData(level, pos, state, ModelData.EMPTY);
        RandomSource random = RandomSource.create(42L);
        int count = model.getQuads(state, null, random, data, null).size();
        for (Direction side : Direction.values()) {
            count += model.getQuads(state, side, random, data, null).size();
        }
        return count;
    }

    /** Counts one answer of a leaf model. Only called when {@link #COUNTING} is on. */
    static void count(boolean block, int faces, int slanted) {
        if (block) {
            BLOCKS.increment();
        }
        FACES.add(faces);
        SLANTED.add(slanted);
    }

    /** Sets the counters back to zero. */
    public static void resetCounters() {
        BLOCKS.reset();
        FACES.reset();
        SLANTED.reset();
    }

    /**
     * The counters since the last reset: leaf blocks handed to the chunk
     * builder, their faces, and how many of those stand at a slant. All
     * zero unless the game runs as the developer's self test.
     */
    public static long[] counters() {
        return new long[] {BLOCKS.sum(), FACES.sum(), SLANTED.sum()};
    }
}
