package dev.emerald.ai;

import dev.emerald.core.citizen.CitizenRecord;
import dev.emerald.core.knowledge.ConceptId;
import dev.emerald.core.observe.WorldObservation;
import dev.emerald.core.research.ResearchProblem;
import dev.emerald.core.village.VillageState;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Bounded model context: the problem, the last few relevant observations, the researcher's known
 * concepts (OBSERVED or better) and the village's stock. No entity lists, no chunk dumps, no player data.
 */
public record HypothesisContext(
        ResearchProblem problem,
        List<String> observations,
        Set<ConceptId> knownConcepts,
        Map<String, Integer> resources
) {
    public static final int MAX_OBSERVATIONS = 8;
    public static final int MAX_RESOURCES = 16;

    public static HypothesisContext of(VillageState v, ResearchProblem problem, CitizenRecord researcher,
                                       Map<String, Integer> warehouse) {
        List<WorldObservation> recent = v.observations().recent(MAX_OBSERVATIONS);
        Map<String, Integer> res = new TreeMap<>();
        warehouse.entrySet().stream().limit(MAX_RESOURCES).forEach(e -> res.put(e.getKey(), e.getValue()));
        return new HypothesisContext(problem, recent.stream().map(WorldObservation::summary).toList(),
                researcher.knowledge().known(), res);
    }

    public String toPrompt() {
        return "problem=" + problem.type()
                + "\nknown=" + knownConcepts.stream().map(Enum::name).sorted().collect(Collectors.joining(",", "[", "]"))
                + "\nresources=" + resources
                + "\nrecent_observations:\n- " + String.join("\n- ", observations);
    }

    public static final String SYSTEM_PROMPT = """
            You are the researcher of a small village. Propose exactly one testable hypothesis that could \
            solve the problem. You only propose; the world decides. Use only concept ids from the schema. \
            Name the observation type that would prove it. Do not claim anything is already true, do not \
            grant items, do not issue commands.""";
}
