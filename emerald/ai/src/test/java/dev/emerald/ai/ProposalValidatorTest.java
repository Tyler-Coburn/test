package dev.emerald.ai;

import dev.emerald.core.SimulationConfig;
import dev.emerald.core.citizen.CitizenRecord;
import dev.emerald.core.citizen.Role;
import dev.emerald.core.knowledge.ConceptId;
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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProposalValidatorTest {
    VillageState v;
    CitizenRecord researcher;
    ResearchProblem problem;
    HypothesisContext ctx;

    @BeforeEach
    void setUp() {
        v = new VillageWorld(SimulationConfig.DEFAULT).found("T", "minecraft:overworld", new Pos(0, 64, 0), 0, 1L);
        researcher = v.citizens().firstAlive(Role.RESEARCHER).orElseThrow();
        var egg = v.observations().record(ObservationType.ITEM_SPAWNED, "minecraft:overworld", new Pos(1, 64, 1),
                ItemIds.EGG, researcher.id(), 5, null);
        researcher.knowledge().witness(v.observations(), egg);
        problem = v.problems().open(ProblemType.EGGS_WASTED, egg.id(), 10);
        ctx = HypothesisContext.of(v, problem, researcher, Map.of(ItemIds.HOPPER, 1));
    }

    Hypothesis validate(String json) {
        return ProposalValidator.validate(ProposalParser.parse(json), ctx, researcher.id(), 20);
    }

    static String proposal(String action, String statement, String concepts, String expected) {
        return "{\"action\":\"" + action + "\",\"statement\":\"" + statement + "\",\"conceptIds\":[" + concepts
                + "],\"expectedObservation\":\"" + expected + "\"}";
    }

    @Test
    void acceptsAGroundedFeasibleHypothesis() {
        Hypothesis h = validate(proposal("PROPOSE_HYPOTHESIS", "A hopper under the floor may catch eggs.",
                "\"CHICKEN_LAYING\"", "HOPPER_PULLED"));
        assertEquals(Hypothesis.Origin.AI, h.origin());
        assertEquals(ConceptId.HOPPER_PULLS_ITEM, h.target());
        assertEquals(ObservationType.HOPPER_PULLED, h.expected());
        assertTrue(h.concepts().contains(ConceptId.CHICKEN_LAYING));
        assertEquals(problem.id(), h.problemId());
    }

    /** llm_reject_unknown_concept */
    @Test
    void rejectsUnknownConcept() {
        ProposalRejected e = assertThrows(ProposalRejected.class, () -> validate(proposal("PROPOSE_HYPOTHESIS",
                "Magnets attract eggs.", "\"CHICKEN_LAYING\",\"EGG_MAGNETISM\"", "HOPPER_PULLED")));
        assertTrue(e.getMessage().contains("EGG_MAGNETISM"));
    }

    /** llm_reject_non_hypothesis_action */
    @Test
    void rejectsNonHypothesisAction() {
        for (String action : new String[]{"PLACE_BLOCK", "GIVE_ITEM", "RUN_COMMAND", "propose_hypothesis"}) {
            assertThrows(ProposalRejected.class, () -> validate(proposal(action, "Place a hopper.",
                    "\"CHICKEN_LAYING\"", "HOPPER_PULLED")), action);
        }
    }

    @Test
    void rejectsExtraFieldsSuchAsGrantsOrFactStates() {
        assertThrows(ProposalRejected.class, () -> validate("{\"action\":\"PROPOSE_HYPOTHESIS\",\"statement\":\"x\","
                + "\"conceptIds\":[\"CHICKEN_LAYING\"],\"expectedObservation\":\"HOPPER_PULLED\",\"grantItems\":[\"minecraft:hopper\"]}"));
        assertThrows(ProposalRejected.class, () -> validate("{\"action\":\"PROPOSE_HYPOTHESIS\",\"statement\":\"x\","
                + "\"conceptIds\":[\"CHICKEN_LAYING\"],\"expectedObservation\":\"HOPPER_PULLED\",\"state\":\"TESTED_TRUE\"}"));
    }

    @Test
    void rejectsFactClaimsCommandsAndGrantsInText() {
        assertThrows(ProposalRejected.class, () -> validate(proposal("PROPOSE_HYPOTHESIS",
                "It is proven that hoppers pull eggs.", "\"CHICKEN_LAYING\"", "HOPPER_PULLED")));
        assertThrows(ProposalRejected.class, () -> validate(proposal("PROPOSE_HYPOTHESIS",
                "Run /give @p hopper then test.", "\"CHICKEN_LAYING\"", "HOPPER_PULLED")));
        assertThrows(ProposalRejected.class, () -> validate(proposal("PROPOSE_HYPOTHESIS",
                "Grant the village ten hoppers.", "\"CHICKEN_LAYING\"", "HOPPER_PULLED")));
    }

    @Test
    void rejectsUnknownOrInfeasibleObservations() {
        assertThrows(ProposalRejected.class, () -> validate(proposal("PROPOSE_HYPOTHESIS", "Eggs teleport.",
                "\"CHICKEN_LAYING\"", "magic")));
        assertThrows(ProposalRejected.class, () -> validate(proposal("PROPOSE_HYPOTHESIS", "A piston will push eggs.",
                "\"CHICKEN_LAYING\"", "PISTON_MOVED")));
    }

    @Test
    void rejectsUngroundedOverlongAndMalformed() {
        assertThrows(ProposalRejected.class, () -> validate(proposal("PROPOSE_HYPOTHESIS", "Pistons help.",
                "\"PISTON_MOVES_BLOCK\"", "HOPPER_PULLED")), "researcher never observed pistons");
        assertThrows(ProposalRejected.class, () -> validate(proposal("PROPOSE_HYPOTHESIS", "x".repeat(241),
                "\"CHICKEN_LAYING\"", "HOPPER_PULLED")));
        assertThrows(ProposalRejected.class, () -> validate("not json"));
        assertThrows(ProposalRejected.class, () -> validate("[1,2]"));
        assertThrows(ProposalRejected.class, () -> validate("{\"action\":\"PROPOSE_HYPOTHESIS\"}"));
    }

    @Test
    void contextIsBounded() {
        for (int i = 0; i < 50; i++) {
            v.observations().record(ObservationType.CROP_GREW, "minecraft:overworld", new Pos(i, 64, 0), null, null, i, null);
        }
        HypothesisContext big = HypothesisContext.of(v, problem, researcher, Map.of());
        assertEquals(HypothesisContext.MAX_OBSERVATIONS, big.observations().size());
        assertTrue(big.toPrompt().contains("problem=EGGS_WASTED"));
    }

    @Test
    void schemaIsValidJsonListingTheClosedCatalogue() {
        var schema = com.google.gson.JsonParser.parseString(HypothesisSchema.json()).getAsJsonObject();
        assertFalse(schema.get("additionalProperties").getAsBoolean());
        assertTrue(HypothesisSchema.json().contains("HOPPER_PULLS_ITEM"));
        var body = com.google.gson.JsonParser.parseString(
                OllamaProvider.requestBody("qwen3", HypothesisSchema.json(), "sys", "user")).getAsJsonObject();
        assertFalse(body.get("stream").getAsBoolean());
        assertTrue(body.get("format").isJsonObject());
    }
}
