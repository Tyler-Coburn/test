package dev.emerald.ai;

import dev.emerald.core.SimulationConfig;
import dev.emerald.core.citizen.CitizenRecord;
import dev.emerald.core.citizen.Role;
import dev.emerald.core.observe.ObservationType;
import dev.emerald.core.research.Hypothesis;
import dev.emerald.core.research.ProblemType;
import dev.emerald.core.research.ResearchProblem;
import dev.emerald.core.village.VillageState;
import dev.emerald.core.village.VillageWorld;
import dev.emerald.core.world.ItemIds;
import dev.emerald.core.world.Pos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class AiHypothesisSourceTest {
    static final AiConfig ON = new AiConfig(true, "http://localhost:11434", "test", Duration.ofSeconds(5), 0);

    VillageState v;
    CitizenRecord researcher;
    ResearchProblem problem;

    /** Provider whose future the test completes by hand. */
    static final class ManualProvider implements AiProvider {
        CompletableFuture<String> future = new CompletableFuture<>();
        int calls;

        @Override
        public CompletableFuture<String> completeJson(String schema, String system, String user) {
            calls++;
            return future;
        }

        @Override
        public String name() {
            return "manual";
        }
    }

    @BeforeEach
    void setUp() {
        v = new VillageWorld(SimulationConfig.DEFAULT).found("T", "minecraft:overworld", new Pos(0, 64, 0), 0, 1L);
        researcher = v.citizens().firstAlive(Role.RESEARCHER).orElseThrow();
        var egg = v.observations().record(ObservationType.ITEM_SPAWNED, "minecraft:overworld", new Pos(1, 64, 1),
                ItemIds.EGG, researcher.id(), 5, null);
        researcher.knowledge().witness(v.observations(), egg);
        problem = v.problems().open(ProblemType.EGGS_WASTED, egg.id(), 10);
    }

    AiHypothesisSource source(AiProvider p, AiConfig c) {
        return new AiHypothesisSource(p, c, village -> Map.of(ItemIds.HOPPER, 1));
    }

    @Test
    void disabledProviderFallsBackImmediately() {
        Hypothesis h = source(null, AiConfig.DISABLED).next(v, problem, researcher, 20).orElseThrow();
        assertEquals(Hypothesis.Origin.FALLBACK, h.origin());
    }

    @Test
    void validModelProposalIsUsedAfterItCompletes() {
        ManualProvider p = new ManualProvider();
        AiHypothesisSource s = source(p, ON);
        assertTrue(s.next(v, problem, researcher, 20).isEmpty(), "first call only starts the request");
        assertTrue(s.next(v, problem, researcher, 21).isEmpty(), "still in flight");
        assertEquals(1, p.calls, "queue depth one: no duplicate calls");
        p.future.complete("{\"action\":\"PROPOSE_HYPOTHESIS\",\"statement\":\"A hopper below may catch eggs.\","
                + "\"conceptIds\":[\"CHICKEN_LAYING\"],\"expectedObservation\":\"HOPPER_PULLED\"}");
        Hypothesis h = s.next(v, problem, researcher, 22).orElseThrow();
        assertEquals(Hypothesis.Origin.AI, h.origin());
        assertEquals(1, v.ledger().ofType("AI_PROPOSAL_ACCEPTED").size());
    }

    @Test
    void rejectedProposalFallsBackAndLogsWhy() {
        ManualProvider p = new ManualProvider();
        AiHypothesisSource s = source(p, ON);
        s.next(v, problem, researcher, 20);
        p.future.complete("{\"action\":\"GIVE_ITEM\",\"statement\":\"x\",\"conceptIds\":[\"CHICKEN_LAYING\"],"
                + "\"expectedObservation\":\"HOPPER_PULLED\"}");
        Hypothesis h = s.next(v, problem, researcher, 21).orElseThrow();
        assertEquals(Hypothesis.Origin.FALLBACK, h.origin());
        String reason = v.ledger().ofType("AI_PROPOSAL_REJECTED").get(0).payload().get("reason");
        assertTrue(reason.contains("GIVE_ITEM"), reason);
    }

    @Test
    void transportFailureFallsBack() {
        ManualProvider p = new ManualProvider();
        AiHypothesisSource s = source(p, ON);
        s.next(v, problem, researcher, 20);
        p.future.completeExceptionally(new java.net.ConnectException("refused"));
        Optional<Hypothesis> h = s.next(v, problem, researcher, 21);
        assertEquals(Hypothesis.Origin.FALLBACK, h.orElseThrow().origin());
        assertTrue(v.ledger().ofType("AI_PROPOSAL_REJECTED").get(0).payload().get("reason").contains("ConnectException"));
    }
}
