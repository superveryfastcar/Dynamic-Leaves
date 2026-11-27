package io.github.superveryfastcar.foliage.shape;

import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.TranslatableEnum;

/**
 * On which leaf spots of a bare tree a twig is drawn in winter (and with
 * buds on it in spring). The player picks one in the settings.
 */
public enum WinterTwigs implements TranslatableEnum {

    /** No twigs: a bare tree shows its branches only. */
    NONE,

    /** A twig on every leaf spot that touches a branch. */
    NEAR,

    /**
     * A twig on every leaf spot that touches a branch, and on those that
     * sit deep in the crown ({@link ShapeSettings#twigDepth}). A denser
     * winter crown.
     */
    MORE;

    /**
     * The name as shown in the settings screen, from the language file
     * ({@code foliage.configuration.winterTwigs.<name>}).
     */
    @Override
    public Component getTranslatedName() {
        return Component.translatable("foliage.configuration.winterTwigs." + name().toLowerCase(Locale.ROOT));
    }
}
