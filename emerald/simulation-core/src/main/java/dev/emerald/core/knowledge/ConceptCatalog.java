package dev.emerald.core.knowledge;

import dev.emerald.core.observe.ObservationType;
import dev.emerald.core.observe.WorldObservation;
import dev.emerald.core.world.ItemIds;

import java.util.EnumSet;
import java.util.Set;

/**
 * Maps observations to the concepts a witness learns (at OBSERVED) from them.
 *
 * <p>WATER_PUSHES_ITEM has no observation type in the first-slice catalogue, so it cannot be
 * witnessed yet; it becomes observable when an ITEM_MOVED observation is added.
 */
public final class ConceptCatalog {
    private ConceptCatalog() {
    }

    public static Set<ConceptId> witnessedBy(WorldObservation obs) {
        Set<ConceptId> out = EnumSet.noneOf(ConceptId.class);
        switch (obs.type()) {
            case ITEM_SPAWNED -> {
                out.add(ConceptId.ITEM_ENTITY_SPAWN);
                if (obs.isItem(ItemIds.EGG)) {
                    out.add(ConceptId.CHICKEN_LAYING);
                }
            }
            case HOPPER_PULLED -> out.add(ConceptId.HOPPER_PULLS_ITEM);
            case ITEM_STORED -> out.add(ConceptId.CHEST_STORES_ITEM);
            case CROP_GREW -> out.add(ConceptId.CROP_GROWTH);
            case CROP_HARVESTED -> out.add(ConceptId.CROP_HARVEST);
            case PISTON_MOVED -> out.add(ConceptId.PISTON_MOVES_BLOCK);
            case REDSTONE_SIGNAL -> out.add(ConceptId.REDSTONE_SIGNAL);
            case BLOCK_PLACED -> {
                // Construction evidence; teaches no first-slice concept.
            }
        }
        return out;
    }

    /** The observation type that is direct evidence for {@code concept}, or null if none exists yet. */
    public static ObservationType evidenceFor(ConceptId concept) {
        return switch (concept) {
            case CHICKEN_LAYING, ITEM_ENTITY_SPAWN -> ObservationType.ITEM_SPAWNED;
            case HOPPER_PULLS_ITEM -> ObservationType.HOPPER_PULLED;
            case CHEST_STORES_ITEM -> ObservationType.ITEM_STORED;
            case CROP_GROWTH -> ObservationType.CROP_GREW;
            case CROP_HARVEST -> ObservationType.CROP_HARVESTED;
            case PISTON_MOVES_BLOCK -> ObservationType.PISTON_MOVED;
            case REDSTONE_SIGNAL -> ObservationType.REDSTONE_SIGNAL;
            case WATER_PUSHES_ITEM -> null;
        };
    }
}
