package dev.emerald.core.research;

import dev.emerald.core.event.Provenance;
import dev.emerald.core.observe.ObservationType;
import dev.emerald.core.observe.WorldObservation;
import dev.emerald.core.village.VillageState;
import dev.emerald.core.world.ItemIds;

import java.util.Optional;

/**
 * EGGS_WASTED predicate: an egg spawned in the pen, the window has elapsed, and the bus holds no
 * HOPPER_PULLED or ITEM_STORED observation caused by that egg. Reads only the bus.
 */
public final class EggWasteDetector {
    private EggWasteDetector() {
    }

    public static Optional<ResearchProblem> evaluate(VillageState v, long now, int windowTicks) {
        if (v.pen() == null || v.problems().active(ProblemType.EGGS_WASTED).isPresent()) {
            return Optional.empty();
        }
        long since = v.problems().lastResolved(ProblemType.EGGS_WASTED);
        for (WorldObservation egg : v.observations().matching(o -> o.type() == ObservationType.ITEM_SPAWNED
                && o.isItem(ItemIds.EGG) && v.pen().containsWithin(o.pos(), 1)
                && o.gameTime() > since && o.gameTime() + windowTicks <= now)) {
            boolean collected = v.observations().findCausedBy(ObservationType.HOPPER_PULLED, egg.id()).isPresent()
                    || v.observations().findCausedBy(ObservationType.ITEM_STORED, egg.id()).isPresent();
            if (!collected) {
                ResearchProblem p = v.problems().open(ProblemType.EGGS_WASTED, egg.id(), now);
                v.log("PROBLEM_OPENED", now, egg.pos(), null, p.id(), egg.id(), p.id(),
                        Provenance.VILLAGE_DIRECTOR, "problem", ProblemType.EGGS_WASTED.name(),
                        "evidence", egg.id().toString());
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }
}
