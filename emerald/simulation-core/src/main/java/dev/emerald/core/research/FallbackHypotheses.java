package dev.emerald.core.research;

import dev.emerald.core.knowledge.ConceptId;
import dev.emerald.core.observe.ObservationType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Hand-authored hypothesis table keyed by problem type. The village advances with no model at all;
 * when an AI provider exists, this is also its fallback.
 */
public final class FallbackHypotheses {
    public static final String HOPPER_UNDER_PEN = "A hopper beneath the pen floor may collect the eggs the chickens lay.";

    private FallbackHypotheses() {
    }

    public static Optional<Hypothesis> forProblem(ResearchProblem problem, UUID author, long now) {
        if (problem.type() == ProblemType.EGGS_WASTED) {
            return Optional.of(new Hypothesis(UUID.randomUUID(), author, HOPPER_UNDER_PEN,
                    List.of(ConceptId.CHICKEN_LAYING, ConceptId.HOPPER_PULLS_ITEM),
                    ConceptId.HOPPER_PULLS_ITEM, ObservationType.HOPPER_PULLED, problem.id(),
                    Hypothesis.Origin.FALLBACK, now));
        }
        return Optional.empty();
    }
}
