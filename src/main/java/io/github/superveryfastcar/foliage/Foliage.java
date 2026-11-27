package io.github.superveryfastcar.foliage;

import com.mojang.logging.LogUtils;
import io.github.superveryfastcar.foliage.network.FoliageNetwork;
import io.github.superveryfastcar.foliage.season.LeafCalendar;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

/**
 * Entry point of the Foliage mod.
 *
 * <h2>What the mod does</h2>
 * Foliage is an addon for Dynamic Trees. It gives the leaves of those trees
 * depth: instead of one plain cube per leaf block, a crown is built from
 * outer leaf faces, leaf planes inside, round leaf "cards" that reach out
 * past the block, and small clumps of leaves on a twig at the tips. Which
 * of these a leaf block gets depends on where it sits in the crown.
 *
 * <p>It also gives those trees a year: at the end of autumn the leaves
 * thin out and fall, tree by tree and leaf block by leaf block; in winter
 * the trees stand bare, with twigs; at the start of spring the twigs bud,
 * and small leaves come out and grow.</p>
 *
 * <h2>How it does it</h2>
 * What a leaf block shows is worked out from the date, the kind of tree
 * and the block's place, by plain arithmetic that gives the same answer on
 * a server and in every player's game (the {@code season} package). The
 * game client draws by it when it builds the picture of a chunk (the
 * {@code client} package). In this version no block is changed: a leaf
 * block that looks bare is still there. The rules that decide which pieces
 * a leaf block is drawn from are kept apart from the drawing (the
 * {@code shape} package), so that they can be tested without a screen.
 *
 * <h2>Where things live</h2>
 * <ul>
 *   <li>{@code season} package: the leaf calendar. Its rules
 *       ({@code SeasonSettings}), and which stage a leaf spot is at
 *       ({@code LeafClock}).</li>
 *   <li>{@code shape} package: what the six neighbors of a leaf block are,
 *       which pieces a leaf block is drawn from at each stage, and the
 *       pixels of the leaf pictures.</li>
 *   <li>{@code network} package: sends the world's rules to the players.</li>
 *   <li>{@code client.model} package: turns the pieces into faces and
 *       hands them to the game.</li>
 *   <li>{@code client.sprite} package: makes the extra leaf pictures from
 *       whatever leaf textures the game has loaded.</li>
 *   <li>{@code client} package: the player's settings and the player's
 *       leaf clock.</li>
 *   <li>{@link FoliageConfig}: the world's settings.</li>
 *   <li>{@code util} package: fixed random numbers per position.</li>
 * </ul>
 */
@Mod(Foliage.MOD_ID)
public final class Foliage {

    /** The mod id. Must match gradle.properties and neoforge.mods.toml. */
    public static final String MOD_ID = "foliage";

    /** Shared logger. Messages show up in the game log prefixed with the mod name. */
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Called once by NeoForge when the game starts, on both the client and a
     * dedicated server.
     *
     * @param modBus    the event bus for this mod's start-up events
     * @param container this mod's container, used to register the settings file
     */
    public Foliage(IEventBus modBus, ModContainer container) {
        // The rules of the world: config/foliage-common.toml.
        container.registerConfig(ModConfig.Type.COMMON, FoliageConfig.SPEC);
        modBus.addListener((ModConfigEvent.Loading event) -> onConfigChanged(event.getConfig()));
        modBus.addListener((ModConfigEvent.Reloading event) -> onConfigChanged(event.getConfig()));

        // The message that carries those rules to the players.
        modBus.addListener(FoliageNetwork::register);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                FoliageNetwork.sendTo(player);
            }
        });

        // The client side is set up in client.FoliageClient.
    }

    /**
     * Takes the settings file's values into use, when it was read or
     * saved, and tells the players if a running server's rules changed.
     */
    private static void onConfigChanged(ModConfig config) {
        if (config.getSpec() != FoliageConfig.SPEC) {
            return;
        }
        if (!LeafCalendar.setServerSettings(FoliageConfig.seasonSettings())) {
            return;
        }
        // The file is read on a thread of its own; players are written to on the server's thread.
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            server.execute(() -> FoliageNetwork.sendToAll(server));
        }
    }
}
