package io.github.superveryfastcar.foliage.season;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.annotation.Nullable;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * The rules of the leaf calendar: which trees shed their leaves, and when.
 * They are rules of the world, set on the server (file
 * {@code config/foliage-common.toml}) and sent to every player, so that
 * everyone sees the same tree in the same state.
 *
 * <p>All times are in days of the Calendar mod and may have fractions.</p>
 *
 * @param enabled     whether trees shed their leaves at all
 * @param fallDays    over how many days, the last of autumn, the leaves
 *                    fall. Never more than autumn is long.
 * @param returnDays  over how many days, the first of spring, the leaves
 *                    come back. Never more than spring is long.
 * @param treeDays    how many days one single tree takes for it
 * @param budDays     for how many of a tree's days in spring it shows only
 *                    buds, before the first leaves
 * @param shiftDays   by how many days at most a tree runs behind others of
 *                    its kind, depending on where it stands
 * @param shiftPatch  over how many blocks that delay changes from one
 *                    value to the next: neighboring trees differ a little,
 *                    trees a patch apart differ freely
 * @param stepsPerDay how many times a day the leaves move on
 * @param spotShare   which share of a tree's time one single leaf spot
 *                    takes to go from full to bare (or back). Small: the
 *                    crown changes spot by spot. 1: all spots of a tree
 *                    change together.
 * @param depthWeight how strictly the spots of a tree go in order of their
 *                    place in the crown ({@link TreeOrder}): 1 strictly, 0
 *                    at random
 * @param deepest     the supply number Dynamic Trees gives a leaf block
 *                    right at its twig. Leaf blocks with this number or
 *                    more count as right at the twig, those with 1 as the
 *                    far edge.
 * @param kinds       the leaf blocks that shed, by block id, each with its
 *                    schedule. Every other leaf block stays green.
 */
public record SeasonSettings(boolean enabled, double fallDays, double returnDays, double treeDays, double budDays,
                             double shiftDays, int shiftPatch, int stepsPerDay, double spotShare, double depthWeight,
                             int deepest, Map<ResourceLocation, LeafSchedule> kinds) {

    /** The shortest and the longest a span of days may be set to. */
    public static final double MIN_DAYS = 0.25;
    public static final double MAX_DAYS = 120.0;

    /** The most days of buds and of delay. */
    public static final double MAX_EXTRA_DAYS = 30.0;

    /** The range of {@link #shiftPatch}. */
    public static final int MIN_PATCH = 1;
    public static final int MAX_PATCH = 512;

    /** The range of {@link #stepsPerDay}. */
    public static final int MIN_STEPS = 1;
    public static final int MAX_STEPS = 48;

    /** The smallest {@link #spotShare}. */
    public static final double MIN_SPOT_SHARE = 0.05;

    /** The range of {@link #deepest}. */
    public static final int MIN_DEEPEST = 2;
    public static final int MAX_DEEPEST = 7;

    /** The most kinds of leaves the settings may list. */
    public static final int MAX_KINDS = 256;

    /** The kinds that shed by default: oak a day after birch, dark oak a day after oak. */
    private static final Map<ResourceLocation, LeafSchedule> DEFAULT_KINDS = defaultKinds();

    /**
     * The defaults: leaves fall over the last 7 days of autumn and return
     * over the first 7 of spring; a tree takes 4 days, the first of them in
     * spring with buds only; trees run up to a day apart; the leaves move
     * on 4 times a day.
     */
    public static final SeasonSettings DEFAULT = new SeasonSettings(
            true, 7.0, 7.0, 4.0, 1.0, 1.0, 16, 4, 0.4, 0.6, 4, DEFAULT_KINDS);

    /** Forces every value into its allowed range. */
    public SeasonSettings {
        fallDays = clamp(fallDays, MIN_DAYS, MAX_DAYS, 7.0);
        returnDays = clamp(returnDays, MIN_DAYS, MAX_DAYS, 7.0);
        treeDays = clamp(treeDays, MIN_DAYS, MAX_DAYS, 4.0);
        budDays = clamp(budDays, 0.0, MAX_EXTRA_DAYS, 1.0);
        shiftDays = clamp(shiftDays, 0.0, MAX_EXTRA_DAYS, 1.0);
        shiftPatch = Math.max(MIN_PATCH, Math.min(MAX_PATCH, shiftPatch));
        stepsPerDay = Math.max(MIN_STEPS, Math.min(MAX_STEPS, stepsPerDay));
        spotShare = clamp(spotShare, MIN_SPOT_SHARE, 1.0, 0.4);
        depthWeight = clamp(depthWeight, 0.0, 1.0, 0.6);
        deepest = Math.max(MIN_DEEPEST, Math.min(MAX_DEEPEST, deepest));
        // A copy that cannot be changed, in the order of the block ids, so
        // that two settings with the same kinds are equal and are written
        // to the network the same way.
        kinds = Collections.unmodifiableMap(new TreeMap<>(kinds == null ? Map.of() : kinds));
    }

    /** The same settings with shedding switched on or off. */
    public SeasonSettings withEnabled(boolean value) {
        return new SeasonSettings(value, fallDays, returnDays, treeDays, budDays, shiftDays, shiftPatch, stepsPerDay,
                spotShare, depthWeight, deepest, kinds);
    }

    /** The same settings with other kinds of leaves that shed. */
    public SeasonSettings withKinds(Map<ResourceLocation, LeafSchedule> value) {
        return new SeasonSettings(enabled, fallDays, returnDays, treeDays, budDays, shiftDays, shiftPatch, stepsPerDay,
                spotShare, depthWeight, deepest, value);
    }

    /** The same settings with another number of steps a day. */
    public SeasonSettings withStepsPerDay(int value) {
        return new SeasonSettings(enabled, fallDays, returnDays, treeDays, budDays, shiftDays, shiftPatch, value,
                spotShare, depthWeight, deepest, kinds);
    }

    /** The same settings with other spans for the fall and the return of the leaves, and for one tree. */
    public SeasonSettings withDays(double fall, double back, double tree) {
        return new SeasonSettings(enabled, fall, back, tree, budDays, shiftDays, shiftPatch, stepsPerDay,
                spotShare, depthWeight, deepest, kinds);
    }

    // ------------------------------------------------------------------
    // The kinds as lines of text, for the settings file
    // ------------------------------------------------------------------

    /**
     * The default kinds as lines for the settings file, see
     * {@link #parseKind}.
     */
    public static List<String> defaultKindLines() {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<ResourceLocation, LeafSchedule> kind : DEFAULT_KINDS.entrySet()) {
            lines.add(formatKind(kind.getKey(), kind.getValue()));
        }
        return lines;
    }

    /** One kind as a line for the settings file: {@code dynamictrees:oak_leaves, 1, outside_in}. */
    public static String formatKind(ResourceLocation block, LeafSchedule schedule) {
        double start = schedule.startDay();
        String startText = start == Math.rint(start) ? Long.toString((long) start) : Double.toString(start);
        return block + ", " + startText + ", " + schedule.order().settingName();
    }

    /**
     * Reads one line of the settings file's list of kinds: the id of a
     * leaf block, the day it starts on (0 for the earliest kind) and the
     * order its spots go in ({@code outside_in} or {@code inside_out}),
     * separated by commas. The day and the order may be left out
     * ({@code 0} and {@code outside_in}).
     *
     * @return the block id and its schedule, or null if the line cannot be read
     */
    @Nullable
    public static Map.Entry<ResourceLocation, LeafSchedule> parseKind(String line) {
        String[] parts = line.split(",");
        if (parts.length < 1 || parts.length > 3) {
            return null;
        }
        ResourceLocation block = ResourceLocation.tryParse(parts[0].trim());
        if (block == null || parts[0].isBlank()) {
            return null;
        }
        double start = 0.0;
        if (parts.length >= 2) {
            try {
                start = Double.parseDouble(parts[1].trim());
            } catch (NumberFormatException e) {
                return null;
            }
            if (Double.isNaN(start) || start < 0.0 || start > LeafSchedule.MAX_START_DAY) {
                return null;
            }
        }
        TreeOrder order = TreeOrder.OUTSIDE_IN;
        if (parts.length == 3) {
            order = TreeOrder.bySettingName(parts[2]);
            if (order == null) {
                return null;
            }
        }
        return Map.entry(block, new LeafSchedule(start, order));
    }

    /** Whether a line of the list of kinds can be read. For checking the settings file. */
    public static boolean isKindLine(Object line) {
        return line instanceof String text && parseKind(text) != null;
    }

    /**
     * Reads the settings file's list of kinds. Lines that cannot be read
     * are left out (the settings file refuses them before, see
     * {@link #isKindLine}); a block named twice keeps its last line.
     */
    public static Map<ResourceLocation, LeafSchedule> parseKinds(List<? extends String> lines) {
        Map<ResourceLocation, LeafSchedule> kinds = new LinkedHashMap<>();
        for (String line : lines) {
            Map.Entry<ResourceLocation, LeafSchedule> kind = parseKind(line);
            if (kind != null && (kinds.size() < MAX_KINDS || kinds.containsKey(kind.getKey()))) {
                kinds.put(kind.getKey(), kind.getValue());
            }
        }
        return kinds;
    }

    // ------------------------------------------------------------------
    // The network
    // ------------------------------------------------------------------

    /**
     * How the settings are written to and read from the network: every
     * value in the order of the record, then the kinds as a count and, per
     * kind, the block id, the start day and the order as one byte.
     */
    public static final StreamCodec<ByteBuf, SeasonSettings> STREAM_CODEC = StreamCodec.of(
            (buffer, s) -> {
                buffer.writeBoolean(s.enabled);
                buffer.writeDouble(s.fallDays);
                buffer.writeDouble(s.returnDays);
                buffer.writeDouble(s.treeDays);
                buffer.writeDouble(s.budDays);
                buffer.writeDouble(s.shiftDays);
                ByteBufCodecs.VAR_INT.encode(buffer, s.shiftPatch);
                ByteBufCodecs.VAR_INT.encode(buffer, s.stepsPerDay);
                buffer.writeDouble(s.spotShare);
                buffer.writeDouble(s.depthWeight);
                ByteBufCodecs.VAR_INT.encode(buffer, s.deepest);
                ByteBufCodecs.VAR_INT.encode(buffer, s.kinds.size());
                for (Map.Entry<ResourceLocation, LeafSchedule> kind : s.kinds.entrySet()) {
                    ResourceLocation.STREAM_CODEC.encode(buffer, kind.getKey());
                    buffer.writeDouble(kind.getValue().startDay());
                    buffer.writeByte(kind.getValue().order().ordinal());
                }
            },
            buffer -> {
                boolean enabled = buffer.readBoolean();
                double fallDays = buffer.readDouble();
                double returnDays = buffer.readDouble();
                double treeDays = buffer.readDouble();
                double budDays = buffer.readDouble();
                double shiftDays = buffer.readDouble();
                int shiftPatch = ByteBufCodecs.VAR_INT.decode(buffer);
                int stepsPerDay = ByteBufCodecs.VAR_INT.decode(buffer);
                double spotShare = buffer.readDouble();
                double depthWeight = buffer.readDouble();
                int deepest = ByteBufCodecs.VAR_INT.decode(buffer);
                int count = ByteBufCodecs.VAR_INT.decode(buffer);
                if (count < 0 || count > MAX_KINDS) {
                    throw new io.netty.handler.codec.DecoderException("Foliage: " + count + " kinds of leaves in the settings");
                }
                TreeOrder[] orders = TreeOrder.values();
                Map<ResourceLocation, LeafSchedule> kinds = new LinkedHashMap<>();
                for (int i = 0; i < count; i++) {
                    ResourceLocation block = ResourceLocation.STREAM_CODEC.decode(buffer);
                    double start = buffer.readDouble();
                    int order = buffer.readUnsignedByte();
                    // An order this version does not know (from a newer server) is read as the usual one.
                    kinds.put(block, new LeafSchedule(start, order < orders.length ? orders[order] : TreeOrder.OUTSIDE_IN));
                }
                // The record forces every number into its allowed range.
                return new SeasonSettings(enabled, fallDays, returnDays, treeDays, budDays, shiftDays, shiftPatch,
                        stepsPerDay, spotShare, depthWeight, deepest, kinds);
            });

    private static Map<ResourceLocation, LeafSchedule> defaultKinds() {
        Map<ResourceLocation, LeafSchedule> kinds = new LinkedHashMap<>();
        kinds.put(ResourceLocation.fromNamespaceAndPath("dynamictrees", "birch_leaves"), new LeafSchedule(0.0, TreeOrder.INSIDE_OUT));
        kinds.put(ResourceLocation.fromNamespaceAndPath("dynamictrees", "oak_leaves"), new LeafSchedule(1.0, TreeOrder.OUTSIDE_IN));
        kinds.put(ResourceLocation.fromNamespaceAndPath("dynamictrees", "dark_oak_leaves"), new LeafSchedule(2.0, TreeOrder.OUTSIDE_IN));
        return kinds;
    }

    /** A number forced into a range; a value that is not a number becomes the fallback. */
    private static double clamp(double value, double low, double high, double fallback) {
        return Double.isNaN(value) ? fallback : Math.max(low, Math.min(high, value));
    }
}
