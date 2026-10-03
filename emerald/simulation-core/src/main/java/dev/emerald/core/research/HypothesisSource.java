package dev.emerald.core.research;

import dev.emerald.core.citizen.CitizenRecord;
import dev.emerald.core.village.VillageState;

import java.util.Optional;

/**
 * Where a researcher's next hypothesis comes from. The default is the hand-authored table; the ai
 * module supplies an asynchronous, validated model-backed source that falls back to the table.
 * Returning empty means "nothing yet" (e.g. a model call is still in flight).
 */
public interface HypothesisSource {
    Optional<Hypothesis> next(VillageState village, ResearchProblem problem, CitizenRecord researcher, long now);

    HypothesisSource FALLBACK = (village, problem, researcher, now) ->
            FallbackHypotheses.forProblem(problem, researcher.id(), now);
}
