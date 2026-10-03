package dev.emerald.minecraft.client;

import dev.emerald.minecraft.entity.CivVillager;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/** v1 bodies use the vanilla villager model and texture (referenced, not copied). */
public final class CivVillagerRenderer extends MobRenderer<CivVillager, VillagerModel<CivVillager>> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/villager/villager.png");

    public CivVillagerRenderer(EntityRendererProvider.Context context) {
        super(context, new VillagerModel<>(context.bakeLayer(ModelLayers.VILLAGER)), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(CivVillager entity) {
        return TEXTURE;
    }
}
