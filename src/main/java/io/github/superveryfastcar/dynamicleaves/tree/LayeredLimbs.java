package io.github.superveryfastcar.dynamicleaves.tree;

import com.dtteam.dynamictrees.event.RegistryEvent;
import com.dtteam.dynamictrees.systems.growthlogic.GrowthLogicKit;
import com.dtteam.dynamictrees.systems.growthlogic.GrowthLogicKitConfiguration;
import com.dtteam.dynamictrees.tree.species.Species;
import com.dtteam.dynamictrees.worldgen.JoCode;
import com.dtteam.dynamictrees.worldgen.JoCodeRegistry;
import com.dtteam.dynamictrees.worldgen.RootsJoCode;
import io.github.superveryfastcar.dynamicleaves.DynamicLeaves;
import io.github.superveryfastcar.dynamicleaves.DynamicLeavesConfig;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Gives trees limbs in layers: level limbs that leave the trunk at many
 * heights, each with side limbs, instead of branches that climb.
 *
 * <h2>What there is to change</h2>
 * The wood of a tree is Dynamic Trees' own, and it comes about in two
 * ways:
 * <ul>
 *   <li>A tree planted with new land is put down whole from a stored
 *       shape: Dynamic Trees keeps a list of shapes per species and per
 *       "radius" (the room the tree has, 2 to 8) and picks one. Such a
 *       tree never grows.</li>
 *   <li>A tree grown from a seed is shaped step by step by its species'
 *       growth rule (the "growth logic kit").</li>
 * </ul>
 * This class changes both, for every species it has shapes for: it
 * replaces the species' stored shapes by its own, and gives the species
 * the growth rule {@link LayeredLogic}. Trees that already stand keep
 * their wood; those that still grow, grow on by the new rule.
 *
 * <h2>Where the shapes come from</h2>
 * From data files {@code data/<any>/tree_shapes/<species namespace>/<species name>.txt},
 * for example {@code data/dynamicleaves/tree_shapes/dynamictrees/oak.txt}, in
 * the format of Dynamic Trees' own shape files: one shape per line as
 * {@code radius:code}, lines starting with {@code #} are notes. The mod's
 * own files are written by {@code tools/limbs/layers.py}. A data pack can
 * replace them or add files for other species.
 *
 * <h2>When it happens</h2>
 * Dynamic Trees reads its shapes and species whenever the game loads its
 * data (at the start of a world and on {@code /reload}). This class
 * reads its files in the same round, right after Dynamic Trees, and then
 * puts its shapes and its growth rule in place. It keeps what Dynamic
 * Trees had, so that the setting {@code layeredLimbs} can turn the new
 * shapes off again while the game runs.
 */
public final class LayeredLimbs {

    /** The name of the growth rule in Dynamic Trees' registry. */
    public static final ResourceLocation KIT_ID = ResourceLocation.fromNamespaceAndPath(DynamicLeaves.MOD_ID, "layered");

    /** The growth rule for trees with limbs in layers. */
    public static final GrowthLogicKit KIT = new LayeredLogic(KIT_ID);

    /** The data folder the shape files are in. */
    private static final String FOLDER = "tree_shapes";

    /** This mod's shapes as read from the data files: species, then radius, then codes. */
    private static Map<ResourceLocation, Map<Integer, List<String>>> shapes = Map.of();

    /** Dynamic Trees' own shapes of the species this mod has shapes for, as it last loaded them. */
    private static final Map<ResourceLocation, Map<Integer, List<JoCode>>> ORIGINAL_SHAPES = new HashMap<>();

    /** The growth rule each of those species had before it got this mod's. */
    private static final Map<ResourceLocation, GrowthLogicKitConfiguration> ORIGINAL_KITS = new HashMap<>();

    /** Whether this mod's shapes are in place right now. */
    private static boolean inPlace;

    private LayeredLimbs() {
    }

    /**
     * Hooks this class into the game. Called once from the mod's
     * constructor.
     *
     * @param modBus  the mod's own event bus (Dynamic Trees announces its
     *                registries there)
     * @param gameBus the game's event bus
     */
    public static void register(IEventBus modBus, IEventBus gameBus) {
        // Dynamic Trees sends one event per registry of its own, all of the same class.
        // The bus tells them apart by class only, so the listener gets all and picks its own.
        modBus.addListener(RegistryEvent.class, LayeredLimbs::registerKit);
        // As late as possible: Dynamic Trees adds its own loader in the same event, and
        // loaders finish in the order they were added. Ours has to come after it.
        gameBus.addListener(EventPriority.LOWEST, (AddReloadListenerEvent event) -> event.addListener(new Loader()));
    }

    /** Puts the growth rule into Dynamic Trees' registry of growth rules. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerKit(RegistryEvent event) {
        if (event.isEntryOfType(GrowthLogicKit.class)) {
            ((RegistryEvent<GrowthLogicKit>) event).getRegistry().register(KIT);
        }
    }

    /** Whether this mod's shapes and growth rule are in place right now. */
    public static boolean inPlace() {
        return inPlace;
    }

    /** The species this mod has shapes for, as read from the data files. */
    public static List<ResourceLocation> species() {
        return List.copyOf(shapes.keySet());
    }

    /** This mod's shapes of a species for a radius, as their codes. Empty if it has none. */
    public static List<String> shapes(ResourceLocation species, int radius) {
        return List.copyOf(shapes.getOrDefault(species, Map.of()).getOrDefault(radius, List.of()));
    }

    /**
     * Follows the settings file: called when it was read or saved. With
     * a world running, the change is made on the server's thread, where
     * Dynamic Trees reads the shapes.
     */
    public static void settingChanged() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            server.execute(() -> use(DynamicLeavesConfig.layeredLimbs()));
        }
        // Without a world nothing is loaded yet: the next load reads the setting itself.
    }

    /**
     * Puts this mod's shapes and growth rule in place, or Dynamic Trees'
     * own back. Does nothing if that is how things are already.
     *
     * @param layered true for limbs in layers
     */
    public static synchronized void use(boolean layered) {
        if (layered == inPlace || shapes.isEmpty()) {
            return;
        }
        // The registry of shapes can only be added to or emptied as a whole. So: note
        // down everything in it, empty it, and put everything back, with the shapes of
        // our species swapped.
        Map<ResourceLocation, Map<Integer, List<JoCode>>> all = new HashMap<>();
        Map<ResourceLocation, Map<Integer, List<JoCode>>> roots = new HashMap<>();
        for (Species species : Species.REGISTRY) {
            ResourceLocation name = species.getRegistryName();
            copy(JoCodeRegistry.getCodes(name, false), all, name);
            copy(JoCodeRegistry.getCodes(name, true), roots, name);
        }
        int swapped = 0;
        for (Map.Entry<ResourceLocation, Map<Integer, List<String>>> entry : shapes.entrySet()) {
            ResourceLocation name = entry.getKey();
            Species species = Species.REGISTRY.get(name);
            if (species == null || !species.isValid()) {
                continue;                              // a file for a species that is not installed
            }
            if (layered) {
                Map<Integer, List<JoCode>> ours = new HashMap<>();
                for (Map.Entry<Integer, List<String>> byRadius : entry.getValue().entrySet()) {
                    List<JoCode> codes = new ArrayList<>();
                    for (String code : byRadius.getValue()) {
                        // As Dynamic Trees makes them when it reads its own files.
                        codes.add(species.getJoCode(code).setCareful(false));
                    }
                    ours.put(byRadius.getKey(), codes);
                }
                all.put(name, ours);
            } else if (ORIGINAL_SHAPES.containsKey(name)) {
                all.put(name, ORIGINAL_SHAPES.get(name));
            }
            setKit(species, layered);
            swapped++;
        }
        JoCodeRegistry.clear();
        all.forEach((name, byRadius) -> byRadius.forEach((radius, codes) ->
                codes.forEach(code -> JoCodeRegistry.register(name, radius, code))));
        roots.forEach((name, byRadius) -> byRadius.forEach((radius, codes) ->
                codes.forEach(code -> JoCodeRegistry.registerRoot(name, radius, (RootsJoCode) code))));
        inPlace = layered;
        DynamicLeaves.LOGGER.info("Tree shapes: {} species now have {}", swapped,
                layered ? "limbs in layers" : "Dynamic Trees' own shapes again");
    }

    /** Copies a species' shapes out of the registry's own lists. */
    private static void copy(Map<Integer, List<JoCode>> from, Map<ResourceLocation, Map<Integer, List<JoCode>>> to,
                             ResourceLocation name) {
        if (from.isEmpty()) {
            return;
        }
        Map<Integer, List<JoCode>> byRadius = new HashMap<>();
        from.forEach((radius, codes) -> byRadius.put(radius, new ArrayList<>(codes)));
        to.put(name, byRadius);
    }

    /**
     * Gives a species the growth rule for limbs in layers, or the one it
     * had before. A species with a growth rule of its own that is not
     * Dynamic Trees' plain one (a conifer, a jungle tree) keeps it: this
     * rule would take away what makes that tree what it is.
     */
    private static void setKit(Species species, boolean layered) {
        ResourceLocation name = species.getRegistryName();
        GrowthLogicKit now = species.getGrowthLogicKit().getConfigurable();
        if (layered) {
            if (now == KIT) {
                return;
            }
            if (now != GrowthLogicKit.DEFAULT) {
                DynamicLeaves.LOGGER.info("Tree shapes: {} keeps its own way of growing ({})", name, now.getRegistryName());
                return;
            }
            ORIGINAL_KITS.put(name, species.getGrowthLogicKit());
            species.setGrowthLogicKit(KIT);
        } else if (now == KIT) {
            GrowthLogicKitConfiguration before = ORIGINAL_KITS.get(name);
            species.setGrowthLogicKit(before != null ? before : GrowthLogicKitConfiguration.getDefault());
        }
    }

    /**
     * Called when the game has loaded its data and Dynamic Trees has
     * just read its own shapes anew: takes over the files read, notes
     * down Dynamic Trees' shapes of the same species, and puts things in
     * place as the setting says.
     */
    private static synchronized void loaded(Map<ResourceLocation, Map<Integer, List<String>>> read) {
        shapes = read;
        ORIGINAL_SHAPES.clear();
        for (ResourceLocation name : read.keySet()) {
            copy(JoCodeRegistry.getCodes(name, false), ORIGINAL_SHAPES, name);
        }
        // Dynamic Trees' registry holds its own shapes again, whatever was there before.
        inPlace = false;
        DynamicLeaves.LOGGER.info("Tree shapes: read shapes with limbs in layers for {} species", read.size());
        if (DynamicLeavesConfig.layeredLimbs()) {
            use(true);
            return;
        }
        // Turned off: make sure no species still grows by our rule from before this load.
        for (ResourceLocation name : read.keySet()) {
            Species species = Species.REGISTRY.get(name);
            if (species != null && species.isValid()) {
                setKit(species, false);
            }
        }
    }

    /**
     * Reads one shape file: lines {@code radius:code}. Lines that are
     * empty, start with {@code #} or are not a shape are skipped; what
     * follows a {@code #} on a line is a note.
     *
     * @return the codes per radius; kept apart from the game so that it
     *         can be tested by itself
     */
    public static Map<Integer, List<String>> parse(List<String> lines) {
        Map<Integer, List<String>> byRadius = new TreeMap<>();
        for (String raw : lines) {
            int note = raw.indexOf('#');
            String line = (note < 0 ? raw : raw.substring(0, note)).replace(" ", "").trim();
            int colon = line.indexOf(':');
            if (colon < 1 || colon == line.length() - 1) {
                continue;
            }
            int radius;
            try {
                radius = Integer.parseInt(line.substring(0, colon));
            } catch (NumberFormatException e) {
                continue;
            }
            String code = line.substring(colon + 1);
            if (radius < 1 || !code.chars().allMatch(LayeredLimbs::isCodeChar)) {
                continue;
            }
            byRadius.computeIfAbsent(radius, r -> new ArrayList<>()).add(code);
        }
        return byRadius;
    }

    /** Whether a character can be part of a tree code (the 64 characters of base 64). */
    private static boolean isCodeChar(int c) {
        return c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '+' || c == '/';
    }

    /**
     * The species a shape file is for:
     * {@code <any>:tree_shapes/dynamictrees/oak.txt} is for
     * {@code dynamictrees:oak}. Null if the path is not of that form.
     */
    public static ResourceLocation speciesOf(ResourceLocation file) {
        String path = file.getPath();
        String start = FOLDER + "/";
        if (!path.startsWith(start) || !path.endsWith(".txt")) {
            return null;
        }
        String rest = path.substring(start.length(), path.length() - ".txt".length());
        int slash = rest.indexOf('/');
        if (slash < 1 || slash == rest.length() - 1) {
            return null;
        }
        return ResourceLocation.tryBuild(rest.substring(0, slash), rest.substring(slash + 1));
    }

    /** Reads the shape files whenever the game loads its data. */
    private static final class Loader extends SimplePreparableReloadListener<Map<ResourceLocation, Map<Integer, List<String>>>> {

        /** Off the main thread: reads and parses the files. */
        @Override
        protected Map<ResourceLocation, Map<Integer, List<String>>> prepare(ResourceManager manager, ProfilerFiller profiler) {
            Map<ResourceLocation, Map<Integer, List<String>>> read = new HashMap<>();
            Map<ResourceLocation, Resource> files = manager.listResources(FOLDER, path -> path.getPath().endsWith(".txt"));
            for (Map.Entry<ResourceLocation, Resource> file : files.entrySet()) {
                ResourceLocation species = speciesOf(file.getKey());
                if (species == null) {
                    DynamicLeaves.LOGGER.warn("Tree shapes: {} is not at tree_shapes/<namespace>/<species>.txt, skipped", file.getKey());
                    continue;
                }
                try (BufferedReader reader = file.getValue().openAsReader()) {
                    Map<Integer, List<String>> byRadius = parse(reader.lines().toList());
                    if (!byRadius.isEmpty()) {
                        // Two packs with a file for the same species: add them up.
                        Map<Integer, List<String>> have = read.computeIfAbsent(species, s -> new TreeMap<>());
                        byRadius.forEach((radius, codes) -> have.computeIfAbsent(radius, r -> new ArrayList<>()).addAll(codes));
                    }
                } catch (IOException e) {
                    DynamicLeaves.LOGGER.warn("Tree shapes: could not read {}: {}", file.getKey(), e.toString());
                }
            }
            return read;
        }

        /** On the main thread, after Dynamic Trees' own loader: puts the shapes in place. */
        @Override
        protected void apply(Map<ResourceLocation, Map<Integer, List<String>>> read, ResourceManager manager, ProfilerFiller profiler) {
            loaded(read);
        }
    }
}
