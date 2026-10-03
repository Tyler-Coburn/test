package dev.emerald.core.director;

import dev.emerald.core.SimulationConfig;
import dev.emerald.core.citizen.CitizenRecord;
import dev.emerald.core.citizen.Role;
import dev.emerald.core.event.Provenance;
import dev.emerald.core.item.ItemStore;
import dev.emerald.core.research.EggWasteDetector;
import dev.emerald.core.research.Experiment;
import dev.emerald.core.research.ExperimentEngine;
import dev.emerald.core.research.Hypothesis;
import dev.emerald.core.research.HypothesisSource;
import dev.emerald.core.research.ProblemType;
import dev.emerald.core.research.ResearchProblem;
import dev.emerald.core.utility.NeedsModel;
import dev.emerald.core.village.VillageState;
import dev.emerald.core.world.Box;
import dev.emerald.core.world.Pos;

import java.util.Optional;

/**
 * Slow strategic evaluator. Opens and resolves problems from state and evidence, and hands an
 * open researchable problem to an idle researcher. Never micromanages bodies.
 */
public final class VillageDirector {
    public static final int FOOD_RESERVE_PER_CITIZEN = 4;
    public static final int BEDS_PER_HUT = 2;

    private VillageDirector() {
    }

    public static void evaluate(VillageState v, long now, ItemStore warehouse, SimulationConfig cfg,
                                HypothesisSource hypotheses) {
        EggWasteDetector.evaluate(v, now, cfg.eggWasteWindowTicks());
        evaluateFood(v, now, warehouse);
        evaluateHousing(v, now);
        startResearch(v, now, hypotheses);
    }

    static void evaluateFood(VillageState v, long now, ItemStore warehouse) {
        if (warehouse == null) {
            return;
        }
        int food = 0;
        for (String item : NeedsModel.FOOD_VALUES.keySet()) {
            food += warehouse.count(item);
        }
        int target = v.citizens().alive().size() * FOOD_RESERVE_PER_CITIZEN;
        Optional<ResearchProblem> active = v.problems().active(ProblemType.FOOD_LOW);
        if (food < target && active.isEmpty()) {
            ResearchProblem p = v.problems().open(ProblemType.FOOD_LOW, null, now);
            v.log("PROBLEM_OPENED", now, v.warehouse(), null, p.id(), null, p.id(), Provenance.VILLAGE_DIRECTOR,
                    "problem", "FOOD_LOW", "food", String.valueOf(food), "target", String.valueOf(target));
        } else if (food >= target && active.isPresent()) {
            v.problems().setStatus(active.get().id(), ResearchProblem.Status.RESOLVED, now, "stock " + food);
        }
    }

    static void evaluateHousing(VillageState v, long now) {
        long adults = v.citizens().alive().stream().filter(c -> c.role().isAdult()).count();
        int beds = v.construction().totalBeds();
        Optional<ResearchProblem> active = v.problems().active(ProblemType.HOMELESS);
        if (adults > beds && active.isEmpty()) {
            ResearchProblem p = v.problems().open(ProblemType.HOMELESS, null, now);
            v.log("PROBLEM_OPENED", now, v.center(), null, p.id(), null, p.id(), Provenance.VILLAGE_DIRECTOR,
                    "problem", "HOMELESS", "adults", String.valueOf(adults), "beds", String.valueOf(beds));
        } else if (adults <= beds && active.isPresent()) {
            v.problems().setStatus(active.get().id(), ResearchProblem.Status.RESOLVED, now, "beds " + beds);
        }
    }

    static void startResearch(VillageState v, long now, HypothesisSource hypotheses) {
        if (v.pen() == null || !v.experiments().active().isEmpty()) {
            return;
        }
        Optional<ResearchProblem> problem = v.problems().active(ProblemType.EGGS_WASTED)
                .filter(p -> p.status() == ResearchProblem.Status.OPEN);
        Optional<CitizenRecord> researcher = v.citizens().firstAlive(Role.RESEARCHER);
        if (problem.isEmpty() || researcher.isEmpty()) {
            return;
        }
        Optional<Hypothesis> h = hypotheses.next(v, problem.get(), researcher.get(), now);
        if (h.isPresent()) {
            ExperimentEngine.propose(v, h.get(), researcher.get(), apparatusFor(v.pen()), now);
        }
    }

    /** The hopper goes in the pen floor: centre column, one below the pen's lowest air layer. */
    public static Pos apparatusFor(Box pen) {
        return new Pos((pen.min().x() + pen.max().x()) / 2, pen.min().y() - 1, (pen.min().z() + pen.max().z()) / 2);
    }

    /** True if any experiment is waiting for its apparatus. */
    public static boolean awaitingSetup(VillageState v) {
        return v.experiments().active().stream().anyMatch(e -> e.phase() == Experiment.Phase.SETUP);
    }
}
