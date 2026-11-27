package io.github.superveryfastcar.foliage.client;

import io.github.superveryfastcar.foliage.Foliage;
import io.github.superveryfastcar.foliage.block.BareLeavesBlock;
import io.github.superveryfastcar.foliage.client.fall.FallingLeaves;
import io.github.superveryfastcar.foliage.client.model.LeafModels;
import io.github.superveryfastcar.foliage.client.sprite.LeafPartsSource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * The part of Foliage that only exists in a game with a screen: the
 * player's settings, the leaf pictures, the leaf models, the player's
 * leaf clock and the leaves that drift down in autumn. A server without a screen never loads this class.
 */
@Mod(value = Foliage.MOD_ID, dist = Dist.CLIENT)
public final class FoliageClient {

    /**
     * Called by NeoForge once while the game starts.
     *
     * @param modBus    where a mod signs up for the game's start-up events
     * @param container this mod as NeoForge sees it
     */
    public FoliageClient(IEventBus modBus, ModContainer container) {
        // The player's own settings: config/foliage-client.toml.
        container.registerConfig(ModConfig.Type.CLIENT, FoliageClientConfig.SPEC);
        modBus.addListener((ModConfigEvent.Loading event) -> onConfigChanged(event.getConfig()));
        modBus.addListener((ModConfigEvent.Reloading event) -> onConfigChanged(event.getConfig()));

        // The leaf pictures, made from the leaf textures, and the leaf models that use them.
        modBus.addListener(LeafPartsSource::register);
        modBus.addListener(LeafModels::wrap);

        // The bare blocks: their block state files, and the color of the leaves that are drawn on them.
        modBus.addListener(BareBlockStates::addPack);
        modBus.addListener(FoliageClient::registerColors);

        // The leaf calendar of the world the player is in.
        NeoForge.EVENT_BUS.addListener(ClientLeafClock::onTick);
        NeoForge.EVENT_BUS.addListener(ClientLeafClock::onLoggingOut);

        // The leaves that drift down from the crowns while the leaves fall.
        NeoForge.EVENT_BUS.addListener(FallingLeaves::onTick);
        NeoForge.EVENT_BUS.addListener(FallingLeaves::onLoggingOut);

        // Adds a "Config" button for Foliage in the game's Mods screen,
        // using NeoForge's built-in settings screen. The labels come from
        // the language file (foliage.configuration.*).
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    /**
     * Gives every bare block the color of its leaf. Leaves are drawn in
     * gray and tinted by the game; a bare block still shows leaves on some
     * days of the year, and those must have the tint of the tree.
     */
    private static void registerColors(RegisterColorHandlersEvent.Block event) {
        for (Block block : BuiltInRegistries.BLOCK) {
            if (block instanceof BareLeavesBlock bare) {
                event.register((state, level, pos, tintIndex) -> level != null && pos != null
                        ? bare.kind().foliageColorMultiplier(state, level, pos)
                        : -1, bare);
            }
        }
    }

    /** Takes the settings file's values into use, when it was read or saved. */
    private static void onConfigChanged(ModConfig config) {
        if (config.getSpec() != FoliageClientConfig.SPEC) {
            return;
        }
        LeafModels.setSettings(FoliageClientConfig.shapeSettings());
        FallingLeaves.setSettings(FoliageClientConfig.fallSettings());
    }
}
