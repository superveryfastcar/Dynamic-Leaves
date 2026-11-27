package io.github.superveryfastcar.dynamicleaves.shape;

import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.TranslatableEnum;

/**
 * The shapes a crown can be drawn in. The player picks one in the settings.
 *
 * <p>All of them draw nothing for a leaf block that is buried in the crown,
 * and no outer face toward another leaf block, which is why they need fewer
 * faces than the plain cubes of the unmodded game.</p>
 */
public enum LeafShape implements TranslatableEnum {

    /** The unmodded look: one plain cube per leaf block, all six faces drawn. Dynamic Leaves draws nothing itself. */
    PLAIN,

    /** Outer faces where a side is open, and flat leaf planes through the middle behind them. */
    SHELL,

    /**
     * Outer faces, one leaf plane inside, and round leaf cards that reach
     * out past the block at a slant. Softer, rounder crowns.
     */
    BUSHY,

    /**
     * Like the shell, but leaf blocks at the edge of the crown shrink to a
     * small clump of leaves that sits on its branch. Airy crowns with the
     * limbs showing through.
     */
    TUFTS,

    /**
     * Bushy in the body of the crown, small clumps of leaves at the very
     * tips and on thin side branches.
     */
    MIXED;

    /**
     * The shape's name as shown in the settings screen, from the language
     * file ({@code dynamicleaves.configuration.shape.<name>}).
     */
    @Override
    public Component getTranslatedName() {
        return Component.translatable("dynamicleaves.configuration.shape." + name().toLowerCase(Locale.ROOT));
    }
}
