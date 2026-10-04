package dev.emerald.minecraft;

import com.mojang.logging.LogUtils;
import dev.emerald.core.EmeraldConstants;
import dev.emerald.minecraft.command.EmeraldCommands;
import dev.emerald.minecraft.config.EmeraldConfig;
import dev.emerald.minecraft.observe.ObservationAdapter;
import dev.emerald.minecraft.registry.EmeraldEntities;
import dev.emerald.minecraft.server.EmeraldServer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * Emerald bootstrap. Registers the citizen body entity, the server config, commands and the
 * server-side simulation hooks. All civilisation rules live in simulation-core.
 */
@Mod(EmeraldConstants.MOD_ID)
public final class EmeraldMod {
    public static final Logger LOGGER = LogUtils.getLogger();

    public EmeraldMod(IEventBus modBus, ModContainer container) {
        EmeraldEntities.ENTITY_TYPES.register(modBus);
        modBus.addListener(EmeraldEntities::registerAttributes);
        container.registerConfig(ModConfig.Type.SERVER, EmeraldConfig.SPEC);

        NeoForge.EVENT_BUS.addListener(EmeraldCommands::register);
        NeoForge.EVENT_BUS.addListener(EmeraldServer::onServerStarted);
        NeoForge.EVENT_BUS.addListener(EmeraldServer::onServerStopping);
        NeoForge.EVENT_BUS.addListener(EmeraldServer::onServerTick);
        NeoForge.EVENT_BUS.addListener(EmeraldServer::onEntityJoin);
        NeoForge.EVENT_BUS.addListener(EmeraldServer::onEntityLeave);
        NeoForge.EVENT_BUS.addListener(EmeraldServer::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(ObservationAdapter::onEntityJoin);
        NeoForge.EVENT_BUS.addListener(ObservationAdapter::onEntityLeave);
        NeoForge.EVENT_BUS.addListener(ObservationAdapter::onCropGrow);
        NeoForge.EVENT_BUS.addListener(ObservationAdapter::onPistonMoved);
        NeoForge.EVENT_BUS.addListener(ObservationAdapter::onNeighborNotify);

        LOGGER.info("Emerald loaded (schema v{})", EmeraldConstants.SCHEMA_VERSION);
    }
}
