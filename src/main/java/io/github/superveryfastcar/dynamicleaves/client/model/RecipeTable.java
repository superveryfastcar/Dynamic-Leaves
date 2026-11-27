package io.github.superveryfastcar.dynamicleaves.client.model;

import javax.annotation.Nullable;

/**
 * A table from recipe numbers to whatever was built for them, made for
 * being read by many chunk-building threads at once.
 *
 * <p>Reading takes no lock and makes no garbage (a general-purpose map
 * would wrap every recipe number into an object first). Writing happens
 * only the first time a recipe is seen, a few thousand times in all, and
 * is the caller's job to keep to one thread at a time.</p>
 *
 * <h2>Why reading without a lock is safe here</h2>
 * Entries are only ever added, never changed or removed. A reader that
 * runs while an entry is being added either sees it or misses it. A miss
 * sends the reader to the slow path, which takes the lock, looks again and
 * then finds it. The values must be objects whose fields never change
 * after they are made.
 *
 * @param <V> what is kept per recipe
 */
final class RecipeTable<V> {

    /** The keys and values of one size of the table. A slot is empty while its key is 0. */
    private static final class Slots {
        /** Recipe number plus one, so that 0 can mean "empty". */
        final int[] keys;
        final Object[] values;

        Slots(int capacity) {
            keys = new int[capacity];
            values = new Object[capacity];
        }
    }

    /** Replaced as a whole when the table grows. */
    private volatile Slots slots = new Slots(256);

    /** How many entries the table holds. Only touched while writing. */
    private int size;

    /**
     * What is kept for a recipe, or null if nothing is, or if it is being
     * added this very moment. May be called from any thread.
     */
    @Nullable
    @SuppressWarnings("unchecked")
    V get(int recipe) {
        Slots current = slots;
        int[] keys = current.keys;
        int mask = keys.length - 1;
        int wanted = recipe + 1;
        for (int slot = spread(recipe) & mask; ; slot = slot + 1 & mask) {
            int key = keys[slot];
            if (key == wanted) {
                return (V) current.values[slot];
            }
            if (key == 0) {
                return null;
            }
        }
    }

    /**
     * Adds an entry. The caller makes sure that only one thread writes at
     * a time and that the recipe is not in the table yet.
     */
    void put(int recipe, V value) {
        Slots current = slots;
        if ((size + 1) * 2 > current.keys.length) {
            Slots larger = new Slots(current.keys.length * 2);
            for (int slot = 0; slot < current.keys.length; slot++) {
                if (current.keys[slot] != 0) {
                    insert(larger, current.keys[slot] - 1, current.values[slot]);
                }
            }
            insert(larger, recipe, value);
            slots = larger;
        } else {
            insert(current, recipe, value);
        }
        size++;
    }

    /** How many entries the table holds. For the writing thread and for reports. */
    int size() {
        return size;
    }

    private static void insert(Slots into, int recipe, Object value) {
        int mask = into.keys.length - 1;
        int slot = spread(recipe) & mask;
        while (into.keys[slot] != 0) {
            slot = slot + 1 & mask;
        }
        // The value first: a reader that already sees the key must find it.
        into.values[slot] = value;
        into.keys[slot] = recipe + 1;
    }

    /** Spreads the bits of a recipe number over the whole table. */
    private static int spread(int recipe) {
        int mixed = recipe * 0x9E3779B1;
        return mixed ^ mixed >>> 15;
    }
}
