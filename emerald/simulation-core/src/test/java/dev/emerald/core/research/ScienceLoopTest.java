package dev.emerald.core.research;

import dev.emerald.core.citizen.CitizenRecord;
import dev.emerald.core.citizen.Role;
import dev.emerald.core.director.VillageDirector;
import dev.emerald.core.event.LedgerEvent;
import dev.emerald.core.item.ItemCounter;
import dev.emerald.core.job.ExperimentSetupRoutine;
import dev.emerald.core.knowledge.ConceptId;
import dev.emerald.core.knowledge.ExperimentOutcome;
import dev.emerald.core.knowledge.KnowState;
import dev.emerald.core.observe.ObservationType;
import dev.emerald.core.observe.WorldObservation;
import dev.emerald.core.technology.DesignRegistry;
import dev.emerald.core.technology.DesignRevision;
import dev.emerald.core.testing.FakeWorld;
import dev.emerald.core.testing.Fixtures;
import dev.emerald.core.testing.TestData;
import dev.emerald.core.village.VillageState;
import dev.emerald.core.village.VillageWorld;
import dev.emerald.core.world.ItemIds;
import dev.emerald.core.world.Pos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** M6/M7: problem from the bus, hard-coded hypothesis, physical test, evidence-only knowledge, design. */
class ScienceLoopTest {
    VillageState v;
    FakeWorld w;
    CitizenRecord researcher;
    CitizenRecord general;
    final Pos inPen = new Pos(11, 64, 11);

    @BeforeEach
    void setUp() {
        v = Fixtures.village();
        v.setWarehouse(Fixtures.WAREHOUSE);
        v.setPen(Fixtures.PEN);
        w = new FakeWorld();
        w.warehouse = new ItemCounter();
        w.warehouse.insert(ItemIds.HOPPER, 1);
        w.warehouse.insert(ItemIds.BREAD, 64);
        researcher = Fixtures.role(v, Role.RESEARCHER);
        general = Fixtures.role(v, Role.GENERAL);
        w.spawnBody(researcher, Fixtures.CENTER);
        w.spawnBody(general, Fixtures.CENTER);
    }

    ResearchProblem eggProblem() {
        return v.problems().all().stream().filter(p -> p.type() == ProblemType.EGGS_WASTED).findFirst().orElseThrow();
    }

    WorldObservation egg() {
        return v.observations().record(ObservationType.ITEM_SPAWNED, FakeWorld.DIM, inPen, ItemIds.EGG,
                researcher.id(), w.time, null);
    }

    WorldObservation pull(WorldObservation egg, Pos at) {
        return v.observations().record(ObservationType.HOPPER_PULLED, FakeWorld.DIM, at, ItemIds.EGG,
                researcher.id(), w.time, egg.id());
    }

    void stepUntil(java.util.function.BooleanSupplier done, int max) {
        for (int i = 0; i < max && !done.getAsBoolean(); i++) {
            w.step(v, HypothesisSource.FALLBACK);
        }
        assertTrue(done.getAsBoolean(), "condition not reached in " + max + " steps");
    }

    /** Drives: wasted egg -> EGGS_WASTED -> fallback hypothesis -> hopper delivered and placed -> RUNNING. */
    Experiment runningExperiment() {
        egg();
        stepUntil(() -> v.problems().active(ProblemType.EGGS_WASTED).isPresent(), 400);
        stepUntil(() -> !v.experiments().all().isEmpty(), 10);
        Experiment e = v.experiments().all().get(0);
        assertEquals(Hypothesis.Origin.FALLBACK, e.hypothesis().origin());
        assertEquals(KnowState.HYPOTHESIS, researcher.knowledge().state(ConceptId.HOPPER_PULLS_ITEM));
        stepUntil(() -> e.phase() == Experiment.Phase.RUNNING, 300);
        assertEquals(ItemIds.HOPPER, w.blocks.get(e.apparatus()), "a real hopper was placed");
        assertEquals(VillageDirector.apparatusFor(Fixtures.PEN), e.apparatus());
        assertEquals(0, w.warehouse.count(ItemIds.HOPPER), "and it came out of the warehouse");
        return e;
    }

    /** wasted_egg_opens_problem */
    @Test
    void wastedEggOpensProblemOnlyAfterTheWindow() {
        WorldObservation egg = egg();
        int window = w.config.eggWasteWindowTicks();
        assertTrue(EggWasteDetector.evaluate(v, egg.gameTime() + window - 1, window).isEmpty());
        ResearchProblem p = EggWasteDetector.evaluate(v, egg.gameTime() + window, window).orElseThrow();
        assertEquals(ProblemType.EGGS_WASTED, p.type());
        assertEquals(egg.id(), p.evidence(), "problem cites its evidence");
    }

    @Test
    void collectedEggDoesNotOpenProblem() {
        WorldObservation egg = egg();
        pull(egg, new Pos(11, 63, 11));
        assertTrue(EggWasteDetector.evaluate(v, egg.gameTime() + 100_000, 2400).isEmpty());
    }

    @Test
    void eggOutsideThePenIsNotEvidence() {
        v.observations().record(ObservationType.ITEM_SPAWNED, FakeWorld.DIM, new Pos(40, 64, 40), ItemIds.EGG,
                null, w.time, null);
        assertTrue(EggWasteDetector.evaluate(v, w.time + 100_000, 2400).isEmpty());
    }

    /** hopper_experiment_passes */
    @Test
    void hopperExperimentPassesOnlyOnBusEvidence() {
        Experiment e = runningExperiment();
        WorldObservation egg = egg();
        WorldObservation pulled = pull(egg, e.apparatus());

        assertEquals(Experiment.Phase.PASSED, e.phase());
        assertEquals(pulled.id(), e.evidence());
        assertEquals(KnowState.TESTED_TRUE, researcher.knowledge().state(ConceptId.HOPPER_PULLS_ITEM));
        assertEquals(pulled.id(), researcher.knowledge().entry(ConceptId.HOPPER_PULLS_ITEM).orElseThrow().sourceObservation());
        assertEquals(ResearchProblem.Status.RESOLVED, eggProblem().status());

        DesignRevision mk1 = v.designs().latestAdopted(DesignRegistry.CHICKEN_COLLECTOR).orElseThrow();
        assertEquals(1, mk1.revision());
        assertEquals(e.id(), mk1.experimentId());
        assertTrue(mk1.requires().contains(ConceptId.HOPPER_PULLS_ITEM), "design cites the concept, separately");

        List<LedgerEvent> chain = v.ledger().byCorrelation(e.id());
        assertTrue(chain.stream().anyMatch(ev -> ev.type().equals("EXPERIMENT_PASSED") && pulled.id().equals(ev.causationId())));
        assertTrue(chain.stream().anyMatch(ev -> ev.type().equals("DESIGN_ADOPTED")));
    }

    /** hopper_experiment_fails_without_hopper: eggs are laid, nothing is ever pulled. */
    @Test
    void hopperExperimentFailsWhenNoPullEverArrives() {
        Experiment e = runningExperiment();
        egg();
        egg();
        w.time = e.timeoutAt();
        ExperimentEngine.tick(v, w.time);

        assertEquals(Experiment.Phase.FAILED, e.phase());
        assertEquals(KnowState.TESTED_FALSE, researcher.knowledge().state(ConceptId.HOPPER_PULLS_ITEM));
        assertTrue(v.designs().latestAdopted(DesignRegistry.CHICKEN_COLLECTOR).isEmpty());
        DesignRevision failed = v.designs().of(DesignRegistry.CHICKEN_COLLECTOR).get(0);
        assertFalse(failed.adopted());
        assertEquals(1, failed.failures());
        assertFalse(v.designs().adopt(DesignRegistry.CHICKEN_COLLECTOR, failed.revision()), "a failed revision cannot be adopted");
        assertEquals(ResearchProblem.Status.OPEN, eggProblem().status(), "problem reopens for another try");
    }

    @Test
    void windowWithNoEggsIsInconclusiveNotFalse() {
        Experiment e = runningExperiment();
        w.time = e.timeoutAt();
        ExperimentEngine.tick(v, w.time);
        assertEquals(Experiment.Phase.ABORTED, e.phase());
        assertEquals(KnowState.HYPOTHESIS, researcher.knowledge().state(ConceptId.HOPPER_PULLS_ITEM));
    }

    @Test
    void pullCausedByAnEggFromBeforeTheTestDoesNotCount() {
        WorldObservation oldEgg = egg();
        Experiment e = runningExperiment();
        pull(oldEgg, e.apparatus());
        assertEquals(Experiment.Phase.RUNNING, e.phase());
    }

    @Test
    void forgedEvidenceCannotSetTestedTrue() {
        WorldObservation forged = new WorldObservation(UUID.randomUUID(), ObservationType.HOPPER_PULLED,
                FakeWorld.DIM, inPen, ItemIds.EGG, null, w.time, null);
        assertThrows(IllegalArgumentException.class,
                () -> ExperimentOutcome.passed(v.observations(), UUID.randomUUID(), ConceptId.HOPPER_PULLS_ITEM, forged));
        assertEquals(KnowState.UNKNOWN, researcher.knowledge().state(ConceptId.HOPPER_PULLS_ITEM));
    }

    @Test
    void setupRoutineUsesTheHopperBlockFacingDown() {
        assertEquals("down", ExperimentSetupRoutine.HOPPER.properties().get("facing"));
    }

    /** design_survives_researcher_death */
    @Test
    void designSurvivesResearcherDeathAndReload() {
        Experiment e = runningExperiment();
        pull(egg(), e.apparatus());
        v.recordDeath(researcher, "test", w.time);

        assertFalse(researcher.alive());
        assertTrue(v.citizens().get(researcher.id()).isPresent(), "the record outlives the body");
        assertTrue(v.designs().latestAdopted(DesignRegistry.CHICKEN_COLLECTOR).isPresent());

        VillageWorld world = new VillageWorld(w.config);
        VillageState back = VillageState.fromMap(TestData.reserialize(v.toMap()), w.config);
        assertNotNull(world);
        DesignRevision mk1 = back.designs().latestAdopted(DesignRegistry.CHICKEN_COLLECTOR).orElseThrow();
        assertEquals(researcher.id(), mk1.inventor());
        assertFalse(back.citizens().get(researcher.id()).orElseThrow().alive());
        assertEquals(Experiment.Phase.PASSED, back.experiments().get(e.id()).orElseThrow().phase());
        assertEquals(1, back.ledger().ofType("CITIZEN_DIED").size());
    }
}
