package io.github.superveryfastcar.foliage.network;

import io.github.superveryfastcar.foliage.season.LeafCalendar;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registers Foliage's network message with NeoForge and sends it.
 */
public final class FoliageNetwork {

    /**
     * Version of Foliage's network format. Raise it whenever
     * {@link LeafCalendarPayload} changes shape.
     */
    private static final String PROTOCOL_VERSION = "1";

    private FoliageNetwork() {
    }

    /**
     * Called once during game start-up.
     *
     * <p>The message is registered as <em>optional</em>: a player without
     * Foliage can still join a world that has it, and a player with Foliage
     * can join a server without it. That player gets no message and draws
     * the leaves by the rules in their own settings file.</p>
     */
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION).optional();
        registrar.playToClient(
                LeafCalendarPayload.TYPE,
                LeafCalendarPayload.STREAM_CODEC,
                (payload, context) -> receive(payload));
    }

    /**
     * What a player's game does with the message: it takes the server's
     * rules into use. Runs on the game's main thread when the message
     * arrives. The player's leaf clock picks the new rules up at its next
     * tick and has the landscape drawn again if they change anything.
     * {@link LeafCalendar} uses no client-only game classes, so referring
     * to it here is safe on a dedicated server.
     *
     * @param payload the message from the server
     */
    public static void receive(LeafCalendarPayload payload) {
        LeafCalendar.setSentSettings(payload.settings());
    }

    /** Sends the world's rules to one player. Done when they join. */
    public static void sendTo(ServerPlayer player) {
        // A player without this mod has no use for the message and is simply left out.
        if (player.connection.hasChannel(LeafCalendarPayload.TYPE)) {
            PacketDistributor.sendToPlayer(player, new LeafCalendarPayload(LeafCalendar.serverSettings()));
        }
    }

    /** Sends the world's rules to every player. Done when the rules were changed. */
    public static void sendToAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendTo(player);
        }
    }
}
