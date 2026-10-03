package dev.emerald.core.village;

import dev.emerald.core.SimulationConfig;
import dev.emerald.core.citizen.CitizenRecord;
import dev.emerald.core.director.VillageDirector;
import dev.emerald.core.item.ItemStore;
import dev.emerald.core.knowledge.ConceptCatalog;
import dev.emerald.core.observe.WorldObservation;
import dev.emerald.core.research.ExperimentEngine;
import dev.emerald.core.research.HypothesisSource;

import java.util.Optional;
import java.util.UUID;

/**
 * Village-level slow tick (requests, problems, experiments) and the observation fan-out.
 * Entity-level work is driven separately by {@link dev.emerald.core.job.CitizenScheduler}.
 */
public final class VillageSimulator {
    private VillageSimulator() {
    }

    /** Run once a second or so. */
    public static void tick(VillageState v, long now, ItemStore warehouse, SimulationConfig cfg, HypothesisSource hypotheses) {
        v.requests().resolve(warehouse, id -> v.citizens().get(id).map(CitizenRecord::carried).orElse(null), now);
        ExperimentEngine.tick(v, now);
        VillageDirector.evaluate(v, now, warehouse, cfg, hypotheses);
        v.setLastSimulationTime(now);
    }

    /**
     * Wires a village's bus so every observation reaches the experiment engine and the witness's
     * knowledge. Call once after loading or founding a village.
     */
    public static void wire(VillageState v) {
        v.observations().clearListeners();
        v.observations().subscribe(obs -> onObservation(v, obs));
    }

    static void onObservation(VillageState v, WorldObservation obs) {
        ExperimentEngine.onObservation(v, obs);
        UUID witness = obs.witness();
        if (witness != null && !ConceptCatalog.witnessedBy(obs).isEmpty()) {
            Optional<CitizenRecord> c = v.citizens().get(witness);
            c.filter(CitizenRecord::alive).ifPresent(r -> r.knowledge().witness(v.observations(), obs));
        }
    }
}
