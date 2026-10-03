package dev.emerald.core.utility;

import dev.emerald.core.citizen.Role;

import java.util.EnumMap;
import java.util.Map;

/**
 * Answers "what matters most right now?" for one citizen. It never executes anything; the job
 * layer decides how to satisfy the chosen need and the body layer moves.
 * Scores are in 0..1. Ticked every {@code utilityIntervalTicks}, not every server tick.
 */
public final class UtilityScorer {
    private UtilityScorer() {
    }

    public static Map<Need, Double> score(UtilityInputs in) {
        Map<Need, Double> scores = new EnumMap<>(Need.class);
        double hunger = in.hunger() / 100.0;
        double tiredness = (100 - in.energy()) / 100.0;

        double flee = in.threatened() ? 0.6 + in.caution() / 250.0 : 0.0;
        double eat = hunger < 0.35 ? hunger * 0.5 : hunger;
        double sleep = tiredness * (in.night() ? 1.0 : 0.5);
        if (in.night() && in.role() != Role.GUARD) {
            sleep = Math.max(sleep, 0.75);
        }
        double work = in.role() == Role.CHILD ? 0.0 : 0.7 * (1.0 - hunger * 0.5) * (in.night() ? 0.3 : 1.0);
        if (in.role() == Role.GUARD && in.night()) {
            work = 0.7;
        }

        scores.put(Need.FLEE, round(flee));
        scores.put(Need.EAT, round(eat));
        scores.put(Need.SLEEP, round(sleep));
        scores.put(Need.WORK, round(work));
        return scores;
    }

    /** Highest score wins; ties go to the earlier need (FLEE, EAT, SLEEP, WORK). */
    public static Need pick(UtilityInputs in) {
        Map<Need, Double> s = score(in);
        Need best = Need.WORK;
        double bestScore = -1;
        for (Need n : Need.values()) {
            if (s.get(n) > bestScore) {
                best = n;
                bestScore = s.get(n);
            }
        }
        return best;
    }

    private static double round(double v) {
        return Math.round(Math.max(0, Math.min(1, v)) * 100) / 100.0;
    }
}
