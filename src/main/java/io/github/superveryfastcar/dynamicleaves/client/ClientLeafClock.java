package io.github.superveryfastcar.dynamicleaves.client;

import io.github.superveryfastcar.calendar.api.Calendar;
import io.github.superveryfastcar.calendar.api.CalendarRules;
import io.github.superveryfastcar.dynamicleaves.client.fall.LeafBursts;
import io.github.superveryfastcar.dynamicleaves.season.LeafCalendar;
import io.github.superveryfastcar.dynamicleaves.season.LeafClock;
import io.github.superveryfastcar.dynamicleaves.season.SeasonSettings;
import io.github.superveryfastcar.seasons.api.client.SeasonsClientApi;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.LevelReader;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * The leaf calendar of the player's game: the {@link LeafClock} the leaf
 * models draw by, kept up to date once per tick.
 *
 * <h2>How it moves on</h2>
 * The leaves do not change all the time but in steps, a few per day
 * ({@link SeasonSettings#stepsPerDay}). At the start of every tick this
 * class looks at the date of the world the player is in. When the date has
 * reached a new step, or the rules have changed, it puts a new clock in
 * place. If that changes what any leaf spot shows, it asks Seasons to draw
 * the landscape again, gently and nearest first
 * ({@link SeasonsClientApi#requestRedraw}). Seasons does that at the end
 * of the same tick, together with its own change of colors if one is due,
 * so the landscape is not drawn twice. It also tells {@link LeafBursts},
 * which lets the leaf spots that just lost leaves in view of the player
 * give off a few, so that the change does not come as a sudden swap.
 *
 * <p>In summer and in winter a new step changes nothing, and nothing is
 * drawn again.</p>
 *
 * <h2>Thread rule</h2>
 * The clock is written on the game's main thread and read by the threads
 * that build chunks. A clock never changes once made; the one in use is
 * swapped as a whole.
 */
public final class ClientLeafClock {

    /** The clock in use, or null outside a world (and while the world's date is not known yet). */
    @Nullable
    private static volatile LeafClock clock;

    private ClientLeafClock() {
    }

    /**
     * The leaf calendar at this moment, or null if there is none: outside
     * a world, or while the date has not arrived from the server. Without
     * a clock every leaf spot is drawn in full leaf.
     */
    @Nullable
    public static LeafClock clock() {
        return clock;
    }

    /**
     * Where the leaf models read biomes from, to know where there is a
     * winter: the world the player is in, or null outside a world.
     *
     * <p>Called on the threads that build chunks. Reading a biome of the
     * player's world from there is what the game itself does for the
     * colors of grass and leaves.</p>
     */
    @Nullable
    public static LevelReader biomes() {
        return Minecraft.getInstance().level;
    }

    /** Runs at the start of every tick of the game. */
    public static void onTick(ClientTickEvent.Pre event) {
        ClientLevel level = Minecraft.getInstance().level;
        LeafClock before = clock;
        LeafClock now = level == null ? null : clockOf(level, before);
        if (now == before) {
            return;
        }
        clock = now;
        // What leaves show without a clock is what they show in summer.
        boolean changed = now != null ? now.looksDifferentFrom(before) : before.looksDifferentFrom(null);
        if (changed && level != null) {
            SeasonsClientApi.requestRedraw();
            // Leaf spots that lose leaves by this step, near the player and in view, give off a few.
            LeafBursts.onStep(before, now);
        }
    }

    /** Forgets the rules a server sent, when the player leaves it. */
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        LeafCalendar.setSentSettings(null);
        clock = null;
    }

    /**
     * The clock for the world's date right now: the one in use if it still
     * holds, else a new one. Null while the date is not known.
     */
    @Nullable
    private static LeafClock clockOf(ClientLevel level, @Nullable LeafClock before) {
        Calendar calendar = Calendar.of(level);
        if (!calendar.isSynced()) {
            return null;
        }
        SeasonSettings settings = LeafCalendar.clientSettings();
        CalendarRules rules = calendar.rules();
        long step = LeafClock.stepOf(settings, calendar.date().dayOfYear(), calendar.tickOfDay());
        if (before != null && before.step() == step && before.settings().equals(settings) && before.rules().equals(rules)) {
            return before;
        }
        return LeafClock.of(settings, rules, step);
    }
}
