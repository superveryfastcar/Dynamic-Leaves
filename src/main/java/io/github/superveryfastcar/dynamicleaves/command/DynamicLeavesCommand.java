package io.github.superveryfastcar.dynamicleaves.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.superveryfastcar.dynamicleaves.DynamicLeaves;
import io.github.superveryfastcar.dynamicleaves.DynamicLeavesConfig;
import io.github.superveryfastcar.dynamicleaves.season.LeafCalendar;
import io.github.superveryfastcar.dynamicleaves.season.LeafClock;
import io.github.superveryfastcar.dynamicleaves.world.LeafWalker;
import io.github.superveryfastcar.dynamicleaves.world.Restore;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * The mod's command, {@code /dynamicleaves}. For the owner of a world (it needs
 * the rights of a command block, like {@code /gamerule}).
 *
 * <pre>
 * /dynamicleaves bare off        the bare blocks are switched off, and every one
 *                          of them becomes a leaf again as players come near
 * /dynamicleaves bare on         switched back on
 * /dynamicleaves restore         every bare block of every world becomes a leaf
 *                          again, land nobody has visited included
 * /dynamicleaves restore 16      the same, but 16 chunks per tick instead of 4
 * /dynamicleaves restore status  how far the sweep is
 * /dynamicleaves restore stop    stops the sweep
 * /dynamicleaves status          what the leaf calendar says today, and what the
 *                          walker has done
 * </pre>
 *
 * <p>{@code /dynamicleaves restore} is the way out before the mod is taken out
 * of a world: see "Taking the mod out of a world" in the README. It
 * switches the bare blocks off by itself and writes that into the
 * settings file, because a sweep would be undone at once otherwise. For
 * the same reason {@code /dynamicleaves bare on} is refused while a sweep
 * runs.</p>
 */
public final class DynamicLeavesCommand {

    private DynamicLeavesCommand() {
    }

    /** Hangs the command into the game. Called when the server builds its commands. */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> command = Commands.literal(DynamicLeaves.MOD_ID)
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS));

        command.then(Commands.literal("bare")
                .then(Commands.literal("on").executes(context -> bare(context.getSource(), true)))
                .then(Commands.literal("off").executes(context -> bare(context.getSource(), false))));

        command.then(Commands.literal("restore")
                .executes(context -> restore(context.getSource(), Restore.DEFAULT_CHUNKS_PER_TICK))
                .then(Commands.argument("chunksPerTick",
                                IntegerArgumentType.integer(Restore.MIN_CHUNKS_PER_TICK, Restore.MAX_CHUNKS_PER_TICK))
                        .executes(context -> restore(context.getSource(),
                                IntegerArgumentType.getInteger(context, "chunksPerTick"))))
                .then(Commands.literal("status").executes(context -> restoreStatus(context.getSource())))
                .then(Commands.literal("stop").executes(context -> restoreStop(context.getSource()))));

        command.then(Commands.literal("status").executes(context -> status(context.getSource())));

        dispatcher.register(command);
    }

    // ------------------------------------------------------------------
    // /dynamicleaves bare on|off
    // ------------------------------------------------------------------

    /**
     * Switches the bare blocks on or off in the settings file. The walker
     * takes it up at once and puts the chunks in reach right.
     */
    private static int bare(CommandSourceStack source, boolean on) {
        if (!DynamicLeavesConfig.SPEC.isLoaded()) {
            source.sendFailure(Component.literal("Dynamic Leaves has not read its settings file yet."));
            return 0;
        }
        if (on && Restore.isRunning()) {
            // The sweep goes through the saved chunks once and does not
            // come back, so the land it has already been through would
            // keep the leaves it just gave back while the land ahead of
            // it loses them again: half a world either way.
            source.sendFailure(Component.literal("A restore is running, which is giving the leaves back. "
                    + "Run /dynamicleaves restore stop first, or wait for it to finish, then /dynamicleaves bare on."));
            return 0;
        }
        if (DynamicLeavesConfig.BARE_BLOCKS.get() == on) {
            source.sendSuccess(() -> Component.literal("Bare blocks are already " + (on ? "on" : "off") + "."), false);
            return 0;
        }
        DynamicLeavesConfig.BARE_BLOCKS.set(on);
        DynamicLeavesConfig.SPEC.save();
        source.sendSuccess(() -> Component.literal(on
                ? "Bare blocks are on: leaf spots that have shed their leaves hold a bare block again. "
                        + "The trees nearest to the players change first."
                : "Bare blocks are off: every bare block becomes a leaf block again as players come near. "
                        + "Run /dynamicleaves restore for the land nobody goes to."), true);
        return 1;
    }

    // ------------------------------------------------------------------
    // /dynamicleaves restore
    // ------------------------------------------------------------------

    /**
     * Starts the sweep that turns every bare block of every world back
     * into a leaf block.
     */
    private static int restore(CommandSourceStack source, int chunksPerTick) {
        MinecraftServer server = source.getServer();
        if (Restore.isRunning()) {
            source.sendFailure(Component.literal("A restore is already running. /dynamicleaves restore status says how far."));
            return 0;
        }
        if (!DynamicLeavesConfig.SPEC.isLoaded()) {
            source.sendFailure(Component.literal("Dynamic Leaves has not read its settings file yet."));
            return 0;
        }
        if (DynamicLeavesConfig.BARE_BLOCKS.get()) {
            // A sweep while the bare blocks are on would be undone behind itself.
            DynamicLeavesConfig.BARE_BLOCKS.set(false);
            DynamicLeavesConfig.SPEC.save();
            source.sendSuccess(() -> Component.literal("Bare blocks switched off first (setting bareBlocks). "
                    + "Run /dynamicleaves bare on to have them back."), true);
        }
        Restore.Listener listener = message -> server.execute(() ->
                source.sendSuccess(() -> Component.literal(message), false));
        if (!Restore.start(server, listener, chunksPerTick)) {
            source.sendFailure(Component.literal("The restore could not be started."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Restoring leaves everywhere, " + chunksPerTick
                + " chunks per tick. This goes through every chunk that was ever saved, so it can take a while. "
                + "/dynamicleaves restore status says how far, /dynamicleaves restore stop ends it."), true);
        return 1;
    }

    /** Says how far the sweep is. */
    private static int restoreStatus(CommandSourceStack source) {
        Restore.Progress at = Restore.progress();
        if (at == null) {
            source.sendSuccess(() -> Component.literal("No restore is running."), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Restoring " + at.level() + ": region " + at.regionsDone() + " of "
                + at.regions() + (at.levelsLeft() > 0 ? ", " + at.levelsLeft() + " more worlds after it" : "")
                + ". " + at.chunksVisited() + " chunks looked at, " + at.blocksRestored() + " blocks restored, "
                + at.seconds() + " seconds so far."), false);
        return 1;
    }

    /** Stops the sweep. */
    private static int restoreStop(CommandSourceStack source) {
        if (!Restore.isRunning()) {
            source.sendSuccess(() -> Component.literal("No restore is running."), false);
            return 0;
        }
        Restore.Progress at = Restore.progress();
        Restore.stop();
        source.sendSuccess(() -> Component.literal("Restore stopped after " + (at == null ? 0 : at.chunksVisited())
                + " chunks. What was restored stays restored."), true);
        return 1;
    }

    // ------------------------------------------------------------------
    // /dynamicleaves status
    // ------------------------------------------------------------------

    /** Says what the leaf calendar shows today and what the walker has done. */
    private static int status(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        LeafClock clock = LeafCalendar.clockOf(level, LeafCalendar.serverSettings());
        LeafWalker walker = LeafWalker.of(level);
        long[] counters = walker.counters();
        source.sendSuccess(() -> Component.literal("Leaf calendar: " + clock
                + (clock.allFull() ? " (every tree in full leaf)" : clock.allBare() ? " (every tree that sheds is bare)"
                        : " (leaves on the move)"))
                .withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal("Bare blocks: "
                + (LeafWalker.settings().enabled() ? "on" : "off")
                + ", " + counters[LeafWalker.TO_BARE] + " made bare and " + counters[LeafWalker.TO_LEAF]
                + " made leaves again since the world started, "
                + counters[LeafWalker.CHUNKS_LOOKED] + " chunks looked at, "
                + counters[LeafWalker.SECTIONS_COUNTED] + " sections counted, "
                + counters[LeafWalker.SECTIONS_SEARCHED] + " sections searched, "
                + counters[LeafWalker.ROUNDS] + " rounds, "
                + counters[LeafWalker.NANOS] / 1_000_000L + " ms spent. "
                + walker.pending() + " of " + walker.inReach() + " chunks in reach still to look at.")
                .withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal("Snow landed from leaves that went bare: "
                + counters[LeafWalker.SNOW_LANDED]
                + ". Fruit taken off spots that went bare: " + counters[LeafWalker.FRUIT_DROPPED] + ".")
                .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }
}
