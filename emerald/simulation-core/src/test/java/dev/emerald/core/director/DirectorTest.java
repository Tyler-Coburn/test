package dev.emerald.core.director;

import dev.emerald.core.SimulationConfig;
import dev.emerald.core.citizen.CitizenRecord;
import dev.emerald.core.citizen.Role;
import dev.emerald.core.construction.Blueprints;
import dev.emerald.core.construction.Building;
import dev.emerald.core.construction.ConstructionProject;
import dev.emerald.core.item.ItemCounter;
import dev.emerald.core.knowledge.ConceptId;
import dev.emerald.core.observe.ObservationType;
import dev.emerald.core.observe.WorldObservation;
import dev.emerald.core.research.HypothesisSource;
import dev.emerald.core.research.ProblemType;
import dev.emerald.core.research.ResearchProblem;
import dev.emerald.core.technology.DesignRegistry;
import dev.emerald.core.technology.DesignRevision;
import dev.emerald.core.testing.FakeWorld;
import dev.emerald.core.testing.Fixtures;
import dev.emerald.core.village.VillageState;
import dev.emerald.core.world.Box;
import dev.emerald.core.world.ItemIds;
import dev.emerald.core.world.Pos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DirectorTest {
    final SimulationConfig cfg = SimulationConfig.DEFAULT;

    @Test
    void homelessnessStartsAHutOnAFoundSiteOnlyWhenFoodAllows() {
        VillageState v = Fixtures.village();
        FakeWorld w = new FakeWorld();
        w.warehouse = new ItemCounter();
        v.setWarehouse(Fixtures.WAREHOUSE);
        w.sites.add(new Pos(20, 64, 0));
        VillageDirector.evaluate(v, w.time, w, cfg, HypothesisSource.FALLBACK);
        assertTrue(v.problems().active(ProblemType.FOOD_LOW).isPresent());
        assertTrue(v.construction().active().isEmpty(), "no housing while hungry");

        w.warehouse.insert(ItemIds.BREAD, 64);
        VillageDirector.evaluate(v, w.time + 100, w, cfg, HypothesisSource.FALLBACK);
        ConstructionProject p = v.construction().active().get(0);
        assertEquals(Blueprints.HUT, p.blueprintId());
        assertEquals(new Pos(20, 64, 0), p.origin());
        assertEquals(ConstructionProject.Purpose.HOUSING, p.purpose());
    }

    @Test
    void foodShortageMovesAGeneralToTheFieldOnlyIfThereIsAField() {
        VillageState v = Fixtures.village();
        FakeWorld w = new FakeWorld();
        w.warehouse = new ItemCounter();
        v.setWarehouse(Fixtures.WAREHOUSE);
        VillageDirector.evaluate(v, w.time, w, cfg, HypothesisSource.FALLBACK);
        CitizenRecord general = Fixtures.role(v, Role.GENERAL);
        assertNull(general.jobOverride());

        v.snapshot().refresh(Map.of(), 12, 0, w.time);
        VillageDirector.evaluate(v, w.time + 100, w, cfg, HypothesisSource.FALLBACK);
        assertEquals(Role.FARMER, general.effectiveRole());

        w.warehouse.insert(ItemIds.BREAD, 64);
        VillageDirector.evaluate(v, w.time + 200, w, cfg, HypothesisSource.FALLBACK);
        assertEquals(Role.GENERAL, general.effectiveRole(), "returns to logistics once food recovers");
    }

    @Test
    void newcomersArriveOnlyWithSpareBedsAndFood() {
        VillageState v = Fixtures.village();
        ItemCounter wh = new ItemCounter();
        wh.insert(ItemIds.BREAD, 200);
        assertTrue(VillageDirector.considerImmigration(v, 1000, wh, cfg).isEmpty(), "no spare beds");
        for (int i = 0; i < 3; i++) {
            v.construction().register(new Building(UUID.randomUUID(), Blueprints.HUT, new Pos(i * 7, 64, 20), 0, null, 2));
        }
        CitizenRecord c = VillageDirector.considerImmigration(v, 1000, wh, cfg).orElseThrow();
        assertTrue(v.citizens().get(c.id()).isPresent());
        assertTrue(VillageDirector.considerImmigration(v, 1100, wh, cfg).isEmpty(), "cooldown");
        assertEquals(1, v.ledger().ofType("CITIZEN_ARRIVED").size());
    }

    @Test
    void prototypeIsAdoptedOnlyOnTrialEvidence() {
        VillageState v = Fixtures.village();
        v.setPen(Box.of(new Pos(10, 64, 10), new Pos(12, 64, 10)));
        FakeWorld w = new FakeWorld();
        w.warehouse = new ItemCounter();
        w.warehouse.insert(ItemIds.BREAD, 64);
        v.setWarehouse(Fixtures.WAREHOUSE);
        CitizenRecord researcher = Fixtures.role(v, Role.RESEARCHER);
        DesignRevision mk1 = v.designs().record(DesignRegistry.CHICKEN_COLLECTOR, Map.of(), 1, 0, true, researcher.id(),
                null, List.of(ConceptId.HOPPER_PULLS_ITEM), Blueprints.COLLECTOR, 0);
        v.construction().register(new Building(UUID.randomUUID(), Blueprints.COLLECTOR,
                VillageDirector.apparatusFor(v.pen()), 0, null, 0));
        WorldObservation wasted = v.observations().record(ObservationType.ITEM_SPAWNED, "minecraft:overworld",
                new Pos(10, 64, 10), ItemIds.EGG, null, 10, null);
        v.problems().open(ProblemType.EGGS_WASTED, wasted.id(), 3000);

        VillageDirector.evaluate(v, 3000, w, cfg, HypothesisSource.FALLBACK);
        DesignRevision proto = v.designs().inTrial(DesignRegistry.CHICKEN_COLLECTOR).orElseThrow();
        assertEquals(Blueprints.fullCollectorId(3, 1), proto.blueprintId());
        assertEquals(mk1.revision() + 1, proto.revision());
        assertTrue(v.experiments().all().isEmpty(), "no new science: the principle is known");

        // Built at t=4000; then eggs fall anywhere and every one is pulled.
        v.construction().register(new Building(UUID.randomUUID(), proto.blueprintId(), new Pos(10, 63, 10), 4000, null, 0));
        for (int i = 0; i < 5; i++) {
            WorldObservation egg = v.observations().record(ObservationType.ITEM_SPAWNED, "minecraft:overworld",
                    new Pos(10 + i % 3, 64, 10), ItemIds.EGG, null, 4100 + i, null);
            v.observations().record(ObservationType.HOPPER_PULLED, "minecraft:overworld", new Pos(10 + i % 3, 63, 10),
                    ItemIds.EGG, null, 4100 + i, egg.id());
        }
        VillageDirector.evaluate(v, 5000, w, cfg, HypothesisSource.FALLBACK);
        DesignRevision adopted = v.designs().latestAdopted(DesignRegistry.CHICKEN_COLLECTOR).orElseThrow();
        assertEquals(proto.revision(), adopted.revision());
        assertEquals(5, adopted.measuredOutput());
        assertEquals(ResearchProblem.Status.RESOLVED,
                v.problems().all().stream().filter(p -> p.type() == ProblemType.EGGS_WASTED).findFirst().orElseThrow().status());
    }

    @Test
    void fullCollectorChainsEveryCellIntoOneChest() {
        var bp = Blueprints.fullCollector(3, 3);
        assertEquals(10, bp.blocks().size());
        assertEquals(ItemIds.CHEST, bp.blocks().get(0).blockId());
        assertEquals("down", bp.blocks().stream().filter(b -> b.rel().equals(new Pos(1, 0, 1))).findFirst()
                .orElseThrow().properties().get("facing"));
        assertEquals("east", bp.blocks().stream().filter(b -> b.rel().equals(new Pos(0, 0, 2))).findFirst()
                .orElseThrow().properties().get("facing"));
        assertEquals(bp.blocks(), Blueprints.fromId(Blueprints.fullCollectorId(3, 3)).blocks());
    }
}
