package dev.emerald.minecraft.registry;

import dev.emerald.core.EmeraldConstants;
import dev.emerald.minecraft.entity.CivVillager;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class EmeraldEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, EmeraldConstants.MOD_ID);

    /** The physical body of a citizen. Villager-sized, CREATURE category, never despawns. */
    public static final DeferredHolder<EntityType<?>, EntityType<CivVillager>> CIV_VILLAGER =
            ENTITY_TYPES.register("civ_villager", () -> EntityType.Builder.of(CivVillager::new, MobCategory.CREATURE)
                    .sized(0.6f, 1.95f)
                    .clientTrackingRange(10)
                    .build("civ_villager"));

    private EmeraldEntities() {
    }

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(CIV_VILLAGER.get(), CivVillager.createAttributes().build());
    }
}
