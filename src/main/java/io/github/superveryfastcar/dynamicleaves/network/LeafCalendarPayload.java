package io.github.superveryfastcar.dynamicleaves.network;

import io.github.superveryfastcar.dynamicleaves.DynamicLeaves;
import io.github.superveryfastcar.dynamicleaves.season.SeasonSettings;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * The message a server sends to a player: the rules of the leaf calendar
 * of its world. With them, the player's game works out for every leaf spot
 * the same stage as the server does.
 *
 * @param settings the rules
 */
public record LeafCalendarPayload(SeasonSettings settings) implements CustomPacketPayload {

    /** The unique name of this message on the network: {@code dynamicleaves:leaf_calendar}. */
    public static final Type<LeafCalendarPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DynamicLeaves.MOD_ID, "leaf_calendar"));

    /** How the message is written to and read from the network: the rules, as {@link SeasonSettings#STREAM_CODEC} packs them. */
    public static final StreamCodec<ByteBuf, LeafCalendarPayload> STREAM_CODEC =
            SeasonSettings.STREAM_CODEC.map(LeafCalendarPayload::new, LeafCalendarPayload::settings);

    /** The message's id, {@link #TYPE}. */
    @Override
    public Type<LeafCalendarPayload> type() {
        return TYPE;
    }
}
