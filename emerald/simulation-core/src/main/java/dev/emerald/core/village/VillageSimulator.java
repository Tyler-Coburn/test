package dev.emerald.core.village;

import dev.emerald.core.SimulationConfig;
import dev.emerald.core.citizen.CitizenRecord;
import dev.emerald.core.construction.Blueprint;
import dev.emerald.core.construction.BlueprintBlock;
import dev.emerald.core.construction.BlueprintLibrary;
import dev.emerald.core.construction.ConstructionProject;
import dev.emerald.core.director.VillageDirector;
import dev.emerald.core.event.Provenance;
import dev.emerald.core.item.ItemStore;
import dev.emerald.core.job.WorldPort;
import dev.emerald.core.knowledge.ConceptCatalog;
import dev.emerald.core.observe.ObservationType;
import dev.emerald.core.observe.WorldObservation;
import dev.emerald.core.research.ExperimentEngine;
import dev.emerald.core.research.HypothesisSource;
import dev.emerald.core.simulation.Materializer;
import dev.emerald.core.simulation.OfflineSimulator;
import dev.emerald.core.world.ItemIds;
import dev.emerald.core.world.Pos;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Village-level ticks and the observation fan-out. Entity-level work is driven separately by
 * {@link dev.emerald.core.job.CitizenScheduler}.
 * <ul>
 *   <li>{@link #tick}: while the village is loaded (about once a second).</li>
 *   <li>{@link #tickUnloaded}: while it is not; delegates to {@link OfflineSimulator}.</li>
 * </ul>
 */
public final class VillageSimulator {
    /** Queued offline blocks placed per loaded tick. */
    public static final int MATERIALIZE_PER_TICK = 4;

    private VillageSimulator() {
    }

    public static void tick(VillageState v, WorldPort world, SimulationConfig cfg, HypothesisSource hypotheses,
                            BlueprintLibrary blueprints) {
        long now = world.gameTime();
        if (!v.loaded()) {
            v.setLoaded(true);
            v.economy().resetSampling();
            v.log("VILLAGE_LOADED", now, v.center(), null, v.id(), null, null, Provenance.SYSTEM_OBSERVED,
                    "pendingBlocks", String.valueOf(v.pending().blocks().size()));
        }
        ItemStore warehouse = world.warehouse();
        v.requests().resolve(warehouse, id -> v.citizens().get(id).map(CitizenRecord::carried).orElse(null), now);
        ExperimentEngine.tick(v, now);
        Materializer.step(v, world, MATERIALIZE_PER_TICK);
        if (v.lastDirectorRun() == Long.MIN_VALUE || now - v.lastDirectorRun() >= cfg.directorIntervalTicks()) {
            refreshSnapshot(v, world, cfg, blueprints);
            v.economy().sample(now);
            VillageDirector.evaluate(v, now, world, cfg, hypotheses);
            v.setLastDirectorRun(now);
        }
        v.setLastSimulationTime(now);
    }

    /** Statistical simulation while the village's chunks are not loaded. */
    public static OfflineSimulator.Summary tickUnloaded(VillageState v, long now, SimulationConfig cfg,
                                                       BlueprintLibrary blueprints) {
        if (v.loaded()) {
            v.setLoaded(false);
            v.log("VILLAGE_UNLOADED", now, v.center(), null, v.id(), null, null, Provenance.SYSTEM_OBSERVED,
                    "warehouse", v.snapshot().warehouse().toString(), "plots", String.valueOf(v.snapshot().cropPlots()));
        }
        return OfflineSimulator.advance(v, now, cfg, blueprints);
    }

    /** Records what the village can see right now, for use while it is unloaded. */
    static void refreshSnapshot(VillageState v, WorldPort world, SimulationConfig cfg, BlueprintLibrary blueprints) {
        ItemStore wh = world.warehouse();
        int chickens = v.pen() == null ? 0 : world.countAnimals(v.pen(), "minecraft:chicken");
        v.snapshot().refresh(wh == null ? v.snapshot().warehouse() : wh.contents(),
                world.countCropPlots(v.center(), cfg.farmRadius()), chickens, world.gameTime());
        for (ConstructionProject p : v.construction().active()) {
            Optional<Blueprint> bp = blueprints.get(p.blueprintId());
            if (bp.isEmpty()) continue;
            List<Pos> remaining = new ArrayList<>();
            boolean allLoaded = true;
            for (BlueprintBlock b : bp.get().blocks()) {
                Pos pos = p.origin().offset(b.rel());
                if (!world.isLoaded(pos)) {
                    allLoaded = false;
                    break;
                }
                if (!world.matches(pos, b)) remaining.add(pos);
            }
            if (allLoaded) v.snapshot().setRemaining(p.id(), remaining);
        }
    }

    /**
     * Wires a village's bus so every observation reaches the experiment engine, the witness's
     * knowledge and the economy. Call once after loading or founding a village.
     */
    public static void wire(VillageState v) {
        v.observations().clearListeners();
        v.observations().subscribe(obs -> onObservation(v, obs));
    }

    static void onObservation(VillageState v, WorldObservation obs) {
        ExperimentEngine.onObservation(v, obs);
        if (obs.type() == ObservationType.ITEM_STORED && obs.isItem(ItemIds.EGG)) {
            v.economy().produced(ItemIds.EGG, 1, obs.gameTime());
        }
        UUID witness = obs.witness();
        if (witness != null && !ConceptCatalog.witnessedBy(obs).isEmpty()) {
            Optional<CitizenRecord> c = v.citizens().get(witness);
            c.filter(CitizenRecord::alive).ifPresent(r -> r.knowledge().witness(v.observations(), obs));
        }
    }
}
