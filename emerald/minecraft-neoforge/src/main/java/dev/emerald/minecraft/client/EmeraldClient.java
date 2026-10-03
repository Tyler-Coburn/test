package dev.emerald.minecraft.client;

import dev.emerald.core.EmeraldConstants;
import dev.emerald.minecraft.registry.EmeraldEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = EmeraldConstants.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class EmeraldClient {
    private EmeraldClient() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(EmeraldEntities.CIV_VILLAGER.get(), CivVillagerRenderer::new);
    }
}
