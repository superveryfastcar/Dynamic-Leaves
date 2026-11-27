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
     * right at the twigs, as on an oak.
     */
    OUTSIDE_IN,

    /**
     * The leaves at the twigs go first and the last hang on at the far
     * edge of the crown, as on a birch.
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
