package dev.emerald.core.simulation;

import dev.emerald.core.SimulationConfig;
import dev.emerald.core.citizen.CitizenRecord;
import dev.emerald.core.construction.BlueprintLibrary;
import dev.emerald.core.construction.Blueprints;
import dev.emerald.core.construction.ConstructionProject;
import dev.emerald.core.item.ItemCounter;
import dev.emerald.core.testing.FakeWorld;
import dev.emerald.core.testing.Fixtures;
import dev.emerald.core.testing.TestData;
import dev.emerald.core.village.VillageSimulator;
import dev.emerald.core.village.VillageState;
import dev.emerald.core.world.ItemIds;
import dev.emerald.core.world.Pos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OfflineSimulationTest {
    final SimulationConfig cfg = SimulationConfig.DEFAULT;
    final BlueprintLibrary lib = BlueprintLibrary.withDefaults();

    VillageState unloadedVillage() {
        VillageState v = Fixtures.village();
        v.setWarehouse(Fixtures.WAREHOUSE);
        v.snapshot().refresh(Map.of(ItemIds.BREAD, 20, ItemIds.OAK_PLANKS, 60), 9, 0, v.lastSimulationTime());
        v.setLoaded(false);
        return v;
    }

    @Test
    void unloadedVillageEatsFarmsAndBuildsAbstractly() {
        VillageState v = unloadedVillage();
        CitizenRecord builder = Fixtures.role(v, dev.emerald.core.citizen.Role.BUILDER);
        ConstructionProject p = v.construction().start(Blueprints.HUT, new Pos(20, 64, 20), builder.id(), v.lastSimulationTime());
        v.snapshot().setRemaining(p.id(), lib.get(Blueprints.HUT).orElseThrow().blocks().stream()
                .map(b -> p.origin().offset(b.rel())).toList());

        OfflineSimulator.Summary s = OfflineSimulator.advance(v, v.lastSimulationTime() + 3L * 24000, cfg, lib);

        assertEquals(60, s.steps());
        assertTrue(s.meals() > 0, "citizens ate from the counted stock");
        assertTrue(v.pending().delta(Fixtures.WAREHOUSE, ItemIds.WHEAT) > 0, "farm produced from counted plots");
        assertTrue(v.pending().delta(Fixtures.WAREHOUSE, ItemIds.BREAD) < 0);
        assertEquals(55, v.pending().blocks().size(), "the hut was built abstractly from counted planks");
        assertEquals(-55, v.pending().delta(Fixtures.WAREHOUSE, ItemIds.OAK_PLANKS));
        assertEquals(ConstructionProject.State.ACTIVE, p.state(), "not complete until the blocks are real");
        assertTrue(v.citizens().alive().stream().allMatch(c -> c.hunger() < 100));
    }

    @Test
    void sameSeedSameHistory() {
        VillageState a = unloadedVillage();
        VillageState b = VillageState.fromMap(TestData.reserialize(a.toMap()), cfg);
        OfflineSimulator.advance(a, a.lastSimulationTime() + 2L * 24000, cfg, lib);
        OfflineSimulator.advance(b, b.lastSimulationTime() + 2L * 24000, cfg, lib);
        assertEquals(a.pending().deltas(), b.pending().deltas());
        assertEquals(a.citizens().all().stream().map(CitizenRecord::hunger).toList(),
                b.citizens().all().stream().map(CitizenRecord::hunger).toList());
    }

    @Test
    void veryLongAbsenceIsBoundedAndLogged() {
        VillageState v = unloadedVillage();
        OfflineSimulator.Summary s = OfflineSimulator.advance(v, v.lastSimulationTime() + 100L * 24000, cfg, lib);
        assertTrue(s.skippedTicks() > 0);
        assertEquals(cfg.offlineMaxTicks() / cfg.offlineStepTicks(), s.steps());
        assertFalse(v.ledger().ofType("OFFLINE_TIME_SKIPPED").isEmpty());
    }

    @Test
    void materializerReconcilesWithRealityAndRealityWins() {
        VillageState v = unloadedVillage();
        CitizenRecord builder = Fixtures.role(v, dev.emerald.core.citizen.Role.BUILDER);
        ConstructionProject p = v.construction().start(Blueprints.HUT, new Pos(20, 64, 20), builder.id(), v.lastSimulationTime());
        v.snapshot().setRemaining(p.id(), lib.get(Blueprints.HUT).orElseThrow().blocks().stream()
                .map(b -> p.origin().offset(b.rel())).toList());
        OfflineSimulator.advance(v, v.lastSimulationTime() + 3L * 24000, cfg, lib);

        // Meanwhile a player took most of the planks: only 30 are really there.
        FakeWorld w = new FakeWorld();
        w.time = v.lastSimulationTime();
        w.warehouse = new ItemCounter();
        w.warehouse.insert(ItemIds.OAK_PLANKS, 30);
        w.warehouse.insert(ItemIds.BREAD, 20);
        w.containers.put(Fixtures.WAREHOUSE, w.warehouse);
        v.setWarehouse(Fixtures.WAREHOUSE);

        for (int i = 0; i < 40; i++) {
            Materializer.step(v, w, 4);
        }

        assertEquals(0, w.warehouse.count(ItemIds.OAK_PLANKS));
        long placed = w.blocks.values().stream().filter(ItemIds.OAK_PLANKS::equals).count();
        assertEquals(30, placed, "only blocks backed by real planks were placed");
        assertTrue(w.warehouse.count(ItemIds.WHEAT) > 0, "offline harvest delivered");
        assertTrue(v.pending().deltas().isEmpty());
        assertTrue(v.pending().blocks().isEmpty());
        assertFalse(v.ledger().ofType("RECONCILED_SHORTFALL").isEmpty());
    }

    @Test
    void loadedTickResumesAfterUnload() {
        VillageState v = unloadedVillage();
        FakeWorld w = new FakeWorld();
        w.time = v.lastSimulationTime() + 5000;
        w.warehouse = new ItemCounter();
        VillageSimulator.tickUnloaded(v, w.time, cfg, lib);
        assertFalse(v.loaded());
        VillageSimulator.tick(v, w, cfg, dev.emerald.core.research.HypothesisSource.FALLBACK, lib);
        assertTrue(v.loaded());
        assertEquals(1, v.ledger().ofType("VILLAGE_LOADED").size());
        assertEquals(List.of(), v.pending().blocks());
    }
}
