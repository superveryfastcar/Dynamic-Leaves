package io.github.superveryfastcar.foliage.season;

import java.util.Locale;
import javax.annotation.Nullable;

/**
 * In which order the leaf spots of one tree lose their leaves in autumn.
 *
 * <p>Dynamic Trees keeps for every leaf block how well it is supplied from
 * its twig: high right at the twig, low at the far edge of the crown. The
 * order goes by that number, mixed with a little scatter per spot so that
 * no ring of leaf blocks goes all at once.</p>
 */
public enum TreeOrder {

    /**
     * The far edge of the crown goes first and the last leaves hang on
     * right at the twigs. The order of every kind the mod ships with: the
     * leaves draw back toward the branches, and the last of them sit on
     * the small branches of the bare tree.
     */
    OUTSIDE_IN,

    /**
     * The leaves at the twigs go first and the last hang on at the far
     * edge of the crown. A crown then ends as a ring of leaves around a
     * bare middle, a block or two away from any wood, so no kind uses it
     * by default; it is kept for those who want it.
     */
    INSIDE_OUT;

    /** The name as written in the settings: lower case. */
    public String settingName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The order with a name from the settings, in any case; null for a name that is none. */
    @Nullable
    public static TreeOrder bySettingName(String name) {
        for (TreeOrder order : values()) {
            if (order.name().equalsIgnoreCase(name.trim())) {
                return order;
            }
        }
        return null;
    }
}
