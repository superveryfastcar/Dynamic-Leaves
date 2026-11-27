package io.github.superveryfastcar.foliage;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
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
 * <h2>How it does it</h2>
 * Everything in this version happens in the game client, when it builds the
 * picture of a chunk (the {@code client} package). No block is changed, and
 * a server does nothing for it. The rules that decide what a leaf block
 * shows are plain arithmetic kept apart from the drawing (the {@code shape}
 * package), so that they can be tested without a screen.
 *
 * <h2>Where things live</h2>
 * <ul>
 *   <li>{@code shape} package: what the six neighbors of a leaf block are,
 *       and which pieces a leaf block is drawn from.</li>
 *   <li>{@code client.model} package: turns those pieces into faces and
 *       hands them to the game.</li>
 *   <li>{@code client.sprite} package: makes the extra leaf pictures from
 *       whatever leaf textures the game has loaded.</li>
 *   <li>{@code client} package: the player's settings.</li>
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
     * @param container this mod's container
     */
    public Foliage(IEventBus modBus, ModContainer container) {
        // Nothing to set up on the server side in this version. The client
        // side is set up in client.FoliageClient.
    }
}
