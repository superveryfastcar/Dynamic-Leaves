package io.github.superveryfastcar.dynamicleaves.season;

import io.github.superveryfastcar.calendar.api.Calendar;
import javax.annotation.Nullable;
import net.minecraft.world.level.LevelReader;

/**
 * The rules of the leaf calendar that are in use ({@link SeasonSettings}),
 * and the clock made from them.
 *
 * <h2>Whose rules</h2>
 * The rules belong to the world: they are read from this game's settings
 * file ({@code config/dynamicleaves-common.toml}) and, on a server, sent to every
 * player who joins and again whenever the file changes
 * ({@code network.DynamicLeavesNetwork}). A player's game draws the leaves by
 * the rules the server sent. If the server never sent any (it runs without
 * Dynamic Leaves), the player's game uses its own file.
 *
 * <p>Read from any thread; each of the two values is swapped as a whole.</p>
 */
public final class LeafCalendar {

    /** The rules from this game's own settings file. */
    private static volatile SeasonSettings serverSettings = SeasonSettings.DEFAULT;

    /** In a player's game: the rules the server sent, or null if it sent none. */
    @Nullable
    private static volatile SeasonSettings sentSettings;

    private LeafCalendar() {
    }

    /** The rules from this game's own settings file: on a server, the rules of the world. */
    public static SeasonSettings serverSettings() {
        return serverSettings;
    }

    /**
     * Takes the rules from this game's own settings file into use.
     *
     * @return true if they differ from the ones in use before
     */
    public static boolean setServerSettings(SeasonSettings settings) {
        boolean changed = !settings.equals(serverSettings);
        serverSettings = settings;
        return changed;
    }

    /**
     * The rules a player's game draws by: the ones the server sent, or
     * else the ones from the player's own settings file.
     */
    public static SeasonSettings clientSettings() {
        SeasonSettings sent = sentSettings;
        return sent != null ? sent : serverSettings;
    }

    /**
     * In a player's game: takes the rules a server sent into use, or
     * forgets them (null) when the player leaves that server.
     */
    public static void setSentSettings(@Nullable SeasonSettings settings) {
        sentSettings = settings;
    }

    /** Whether a server sent its rules to this player's game. */
    public static boolean hasSentSettings() {
        return sentSettings != null;
    }

    /**
     * The leaf calendar of a world at this moment, by the rules given.
     *
     * @param level    the world; its date comes from the Calendar mod
     * @param settings the rules
     */
    public static LeafClock clockOf(LevelReader level, SeasonSettings settings) {
        Calendar calendar = Calendar.of(level);
        return LeafClock.of(settings, calendar.rules(), LeafClock.stepOf(settings, calendar.date().dayOfYear(), calendar.tickOfDay()));
    }
}
