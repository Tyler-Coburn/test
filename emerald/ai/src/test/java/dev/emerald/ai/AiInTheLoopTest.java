package dev.emerald.ai;

import dev.emerald.core.SimulationConfig;
import dev.emerald.core.citizen.Role;
import dev.emerald.core.item.ItemCounter;
import dev.emerald.core.knowledge.ConceptId;
import dev.emerald.core.research.Experiment;
import dev.emerald.core.research.Hypothesis;
import dev.emerald.core.sandbox.SandboxWorld;
import dev.emerald.core.village.VillageState;
import dev.emerald.core.village.VillageWorld;
import dev.emerald.core.world.Box;
import dev.emerald.core.world.ItemIds;
import dev.emerald.core.world.Pos;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** M8 inside the running village: the model proposes, the world decides, and the table covers failures. */
class AiInTheLoopTest {
    static final AiConfig ON = new AiConfig(true, "http://localhost:11434", "scripted", Duration.ofSeconds(5), 0);

    record Scripted(String reply, AtomicInteger calls) implements AiProvider {
        @Override
        public CompletableFuture<String> completeJson(String schema, String system, String user) {
            calls.incrementAndGet();
            assertTrue(user.contains("problem=EGGS_WASTED"), "context names the problem");
            return reply == null ? CompletableFuture.failedFuture(new java.net.ConnectException("ollama down"))
                    : CompletableFuture.completedFuture(reply);
        }

        @Override
        public String name() {
            return "scripted";
        }
    }

    static VillageState village(SandboxWorld w, AiProvider provider) {
        VillageWorld world = new VillageWorld(SimulationConfig.DEFAULT);
        VillageState v = world.found("Modelton", SandboxWorld.DIM, new Pos(0, 64, 0), w.time, 3L);
        w.bind(v);
        ItemCounter chest = w.chestAt(new Pos(3, 64, 0));
        chest.insert(ItemIds.HOPPER, 2);
        chest.insert(ItemIds.BREAD, 64);
        chest.insert(ItemIds.OAK_PLANKS, 128);
        v.setWarehouse(new Pos(3, 64, 0));
        v.setPen(Box.of(new Pos(10, 64, 10), new Pos(10, 64, 10)));   // a single cell: every egg lands on the apparatus
        w.addChicken(new Pos(10, 64, 10));
        w.addChicken(new Pos(10, 64, 10));
        w.hypotheses = new AiHypothesisSource(provider, ON, vs -> w.containers.get(vs.warehouse()).contents());
        w.summonMissingBodies(v.center());
        return v;
    }

    static Experiment runUntilConcluded(SandboxWorld w, VillageState v) {
        for (int i = 0; i < 2000 && v.experiments().all().stream().noneMatch(e -> !e.isActive()); i++) {
            w.runTicks(200);
        }
        return v.experiments().all().stream().filter(e -> !e.isActive()).findFirst().orElseThrow();
    }

    @Test
    void acceptedModelHypothesisIsTestedByTheWorld() {
        SandboxWorld w = new SandboxWorld(5, 1000, 64, SimulationConfig.DEFAULT);
        AtomicInteger calls = new AtomicInteger();
        VillageState v = village(w, new Scripted("""
                {"action":"PROPOSE_HYPOTHESIS","statement":"Eggs falling through the floor might be caught by a hopper.",
                 "conceptIds":["CHICKEN_LAYING"],"expectedObservation":"HOPPER_PULLED"}""", calls));
        Experiment e = runUntilConcluded(w, v);
        assertEquals(Hypothesis.Origin.AI, e.hypothesis().origin(), v.ledger().ofType("AI_PROPOSAL_REJECTED").toString());
        assertEquals(Experiment.Phase.PASSED, e.phase(), "the world, not the model, passed it");
        assertTrue(v.citizens().firstAlive(Role.RESEARCHER).orElseThrow().knowledge()
                .state(ConceptId.HOPPER_PULLS_ITEM).isConfirmedTrue());
        assertEquals(1, v.ledger().ofType("AI_PROPOSAL_ACCEPTED").size());
        assertTrue(calls.get() >= 1);
    }

    @Test
    void garbageOrOutageFallsBackAndTheLoopStillCompletes() {
        for (String reply : new String[]{"{\"action\":\"GIVE_ITEM\",\"item\":\"minecraft:diamond\"}", null}) {
            SandboxWorld w = new SandboxWorld(9, 1000, 64, SimulationConfig.DEFAULT);
            VillageState v = village(w, new Scripted(reply, new AtomicInteger()));
            Experiment e = runUntilConcluded(w, v);
            assertEquals(Hypothesis.Origin.FALLBACK, e.hypothesis().origin());
            assertEquals(Experiment.Phase.PASSED, e.phase());
            assertFalse(v.ledger().ofType("AI_PROPOSAL_REJECTED").isEmpty());
        }
    }
}
