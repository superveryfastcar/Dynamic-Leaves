package io.github.superveryfastcar.foliage;

import com.mojang.logging.LogUtils;
import io.github.superveryfastcar.foliage.block.Twins;
import io.github.superveryfastcar.foliage.command.FoliageCommand;
import io.github.superveryfastcar.foliage.compat.DistantHorizons;
import io.github.superveryfastcar.foliage.network.FoliageNetwork;
import io.github.superveryfastcar.foliage.season.LeafCalendar;
import io.github.superveryfastcar.foliage.world.LeafWalker;
import io.github.superveryfastcar.foliage.world.Restore;
import io.github.superveryfastcar.foliage.world.TreeSeasons;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
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
 * {@code client} package). The rules that decide which pieces a leaf
 * block is drawn from are kept apart from the drawing (the {@code shape}
 * package), so that they can be tested without a screen.
 *
 * <p>The server follows the same arithmetic with the blocks themselves:
 * while a leaf spot has few leaves or none, it holds a bare block in
 * place of the leaf block (the {@code block} package), which lets light,
 * weather and players through but keeps the tree in shape. A walker goes
 * over the chunks near the players and swaps the two as the calendar
 * moves on (the {@code world} package). What a spot looks like never
 * depends on which of the two blocks is there.</p>
 *
 * <h2>Where things live</h2>
 * <ul>
 *   <li>{@code season} package: the leaf calendar. Its rules
 *       ({@code SeasonSettings}), and which stage a leaf spot is at
 *       ({@code LeafClock}).</li>
 *   <li>{@code shape} package: what the six neighbors of a leaf block are,
 *       which pieces a leaf block is drawn from at each stage, and the
 *       pixels of the leaf pictures.</li>
 *   <li>{@code block} package: the bare block, and which leaf block it
 *       belongs to.</li>
 *   <li>{@code world} package: swaps leaf blocks and bare blocks on the
 *       server, and tells Dynamic Trees what the season is.</li>
 *   <li>{@code network} package: sends the world's rules to the players.</li>
 *   <li>{@code command} package: the command {@code /foliage}.</li>
 *   <li>{@code compat} package: the link to Distant Horizons.</li>
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

        // The bare blocks: made when the game registers blocks, as late as
        // possible, so that Dynamic Trees' leaf blocks exist (see Twins);
        // paired with their leaf blocks when all blocks exist.
        modBus.addListener(EventPriority.LOWEST, RegisterEvent.class, Twins::register);
        modBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(Twins::resolve));
        // Data packs can give a tree species other leaves: tell the species about their bare blocks again.
        NeoForge.EVENT_BUS.addListener((ServerAboutToStartEvent event) -> Twins.fitSpecies());
        NeoForge.EVENT_BUS.addListener((TagsUpdatedEvent event) -> Twins.fitSpecies());

        // The walker that keeps the blocks in step with the leaf calendar.
        LeafWalker.register(NeoForge.EVENT_BUS);

        // The season of the trees themselves: Dynamic Trees asks Foliage
        // what the season is, so that its trees rest in winter and bear
        // fruit in season. Offered once the settings have been read, and
        // put in place again before a world starts, in case Dynamic Trees
        // picked another source in between.
        modBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(() -> {
            TreeSeasons.offer();
            TreeSeasons.install();
        }));
        NeoForge.EVENT_BUS.addListener((ServerAboutToStartEvent event) -> TreeSeasons.install());

        // The link to Distant Horizons, if it is installed: far crowns
        // keep their leaves all year.
        modBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(DistantHorizons::init));

        // The command /foliage, and the sweep behind /foliage restore.
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> FoliageCommand.register(event.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> Restore.tick(event.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> Restore.serverStopped());

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
        LeafWalker.setSettings(FoliageConfig.walkSettings());
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
