package dev.emerald.core.technology;

import dev.emerald.core.citizen.CitizenRecord;
import dev.emerald.core.education.Writing;
import dev.emerald.core.knowledge.ConceptId;
import dev.emerald.core.knowledge.KnowState;
import dev.emerald.core.village.VillageState;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Derives a village's capabilities from what it actually holds: principles confirmed by a living
 * citizen or recorded in the library, plus adopted designs. Lose the people and the books and the
 * capability goes; an adopted design keeps what it embodies.
 */
public final class Capabilities {
    static final Map<ConceptId, Capability> FROM_CONCEPT = Map.of(
            ConceptId.CHEST_STORES_ITEM, Capability.STORE_ITEM,
            ConceptId.HOPPER_PULLS_ITEM, Capability.MOVE_ITEM,
            ConceptId.WATER_PUSHES_ITEM, Capability.MOVE_ITEM,
            ConceptId.PISTON_MOVES_BLOCK, Capability.PUSH_BLOCK,
            ConceptId.REDSTONE_SIGNAL, Capability.DETECT_CHANGE,
            ConceptId.CROP_HARVEST, Capability.HARVEST_CROP);

    private Capabilities() {
    }

    public static Set<Capability> of(VillageState v) {
        Set<Capability> out = EnumSet.noneOf(Capability.class);
        for (CitizenRecord c : v.citizens().alive()) {
            c.knowledge().entries().forEach(e -> {
                if (e.state().isConfirmedTrue() && FROM_CONCEPT.containsKey(e.concept())) {
                    out.add(FROM_CONCEPT.get(e.concept()));
                }
            });
        }
        for (Writing w : v.library().all()) {
            if (w.state() == KnowState.TESTED_TRUE && FROM_CONCEPT.containsKey(w.concept())) {
                out.add(FROM_CONCEPT.get(w.concept()));
            }
        }
        if (v.designs().latestAdopted(DesignRegistry.CHICKEN_COLLECTOR).isPresent()) {
            out.add(Capability.COLLECT_DROPS);
            out.add(Capability.MOVE_ITEM);
        }
        return out;
    }
}
