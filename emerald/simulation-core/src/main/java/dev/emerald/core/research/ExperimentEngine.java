package dev.emerald.core.research;

import dev.emerald.core.SimulationConfig;
import dev.emerald.core.citizen.CitizenRecord;
import dev.emerald.core.event.Provenance;
import dev.emerald.core.knowledge.ConceptId;
import dev.emerald.core.knowledge.ExperimentOutcome;
import dev.emerald.core.knowledge.KnowState;
import dev.emerald.core.observe.ObservationType;
import dev.emerald.core.observe.WorldObservation;
import dev.emerald.core.technology.DesignRegistry;
import dev.emerald.core.technology.DesignRevision;
import dev.emerald.core.village.VillageState;
import dev.emerald.core.world.ItemIds;
import dev.emerald.core.world.Pos;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Runs physical experiments. Pass/fail is decided only from ObservationBus evidence:
 * <ul>
 *   <li>Opportunity: an egg ITEM_SPAWNED inside the pen after the test started.</li>
 *   <li>Pass: the expected observation (HOPPER_PULLED) whose causedBy is one of those eggs.</li>
 *   <li>Fail: the window closes after at least one opportunity with no expected evidence.</li>
 *   <li>Abort: the window closes with no opportunity at all (inconclusive; no knowledge change).</li>
 * </ul>
 */
public final class ExperimentEngine {
    /** Structure a builder places for an adopted chicken collector. */
    public static final String COLLECTOR_BLUEPRINT = dev.emerald.core.construction.Blueprints.COLLECTOR;
    public static final List<ConceptId> COLLECTOR_REQUIRES =
            List.of(ConceptId.CHICKEN_LAYING, ConceptId.HOPPER_PULLS_ITEM, ConceptId.CHEST_STORES_ITEM);

    private ExperimentEngine() {
    }

    /** Creates an experiment in SETUP. The researcher must still place the apparatus. */
    public static Experiment propose(VillageState v, Hypothesis h, CitizenRecord researcher, Pos apparatus, long now) {
        if (v.pen() == null) {
            throw new IllegalStateException("Experiment needs a registered pen");
        }
        Experiment e = new Experiment(UUID.randomUUID(), h, researcher.id(), apparatus, ItemIds.HOPPER, v.pen(), now);
        v.experiments().add(e);
        researcher.knowledge().hypothesize(h.target(), now);
        if (h.problemId() != null) {
            v.problems().setStatus(h.problemId(), ResearchProblem.Status.RESEARCHING, now, "experiment " + shortId(e.id()));
        }
        v.log("HYPOTHESIS_PROPOSED", now, apparatus, researcher.id(), h.problemId(), null, e.id(),
                h.origin() == Hypothesis.Origin.AI ? Provenance.AI_PROPOSAL : Provenance.JOB_SYSTEM,
                "statement", h.statement(), "target", h.target().name(), "expected", h.expected().name());
        return e;
    }

    /** Apparatus confirmed in the world: start the observation window. */
    public static void apparatusReady(VillageState v, Experiment e, long now, SimulationConfig config) {
        if (e.phase() != Experiment.Phase.SETUP) {
            return;
        }
        e.begin(now, config.experimentTimeoutTicks());
        v.log("EXPERIMENT_STARTED", now, e.apparatus(), e.researcher(), null, null, e.id(),
                Provenance.EXPERIMENT_ENGINE, "timeoutAt", String.valueOf(e.timeoutAt()));
    }

    /** Feed every new bus observation here. */
    public static void onObservation(VillageState v, WorldObservation obs) {
        for (Experiment e : v.experiments().active()) {
            if (e.phase() != Experiment.Phase.RUNNING || obs.gameTime() < e.startedAt()) {
                continue;
            }
            if (obs.type() == ObservationType.ITEM_SPAWNED && obs.isItem(ItemIds.EGG)
                    && e.region().containsWithin(obs.pos(), 1)) {
                e.addOpportunity(obs.id());
            } else if (obs.type() == e.hypothesis().expected() && obs.causedBy() != null
                    && e.opportunities().contains(obs.causedBy())) {
                e.noteCollected();
                pass(v, e, obs);
            }
        }
    }

    /** Closes experiments whose window has elapsed. */
    public static void tick(VillageState v, long now) {
        for (Experiment e : v.experiments().active()) {
            if (e.phase() == Experiment.Phase.RUNNING && now >= e.timeoutAt()) {
                if (e.opportunities().isEmpty()) {
                    abort(v, e, now, "no eggs were laid in the pen during the window; inconclusive");
                } else {
                    fail(v, e, now);
                }
            }
        }
    }

    public static void abort(VillageState v, Experiment e, long now, String why) {
        e.end(Experiment.Phase.ABORTED, now, null, why);
        reopenProblem(v, e, now, "experiment aborted");
        v.log("EXPERIMENT_ABORTED", now, e.apparatus(), e.researcher(), null, null, e.id(),
                Provenance.EXPERIMENT_ENGINE, "reason", why);
    }

    private static void pass(VillageState v, Experiment e, WorldObservation evidence) {
        long now = evidence.gameTime();
        ConceptId target = e.hypothesis().target();
        ExperimentOutcome outcome = ExperimentOutcome.passed(v.observations(), e.id(), target, evidence);
        KnowState state = applyToResearcher(v, e, outcome);
        e.end(Experiment.Phase.PASSED, now, evidence.id(), "evidence " + shortId(evidence.id()));
        if (e.hypothesis().problemId() != null) {
            v.problems().setStatus(e.hypothesis().problemId(), ResearchProblem.Status.RESOLVED, now,
                    "solved by " + DesignRegistry.CHICKEN_COLLECTOR);
        }
        v.log("EXPERIMENT_PASSED", now, e.apparatus(), e.researcher(), null, evidence.id(), e.id(),
                Provenance.EXPERIMENT_ENGINE, "evidence", evidence.id().toString(), "cause", String.valueOf(evidence.causedBy()));
        v.log("CONCEPT_TESTED", now, null, e.researcher(), null, evidence.id(), e.id(),
                Provenance.EXPERIMENT_ENGINE, "concept", target.name(), "state", state.name());
        DesignRevision rev = v.designs().record(DesignRegistry.CHICKEN_COLLECTOR,
                Map.of(ItemIds.HOPPER, 1, ItemIds.CHEST, 1), e.collected(), 0, true, e.researcher(), e.id(),
                COLLECTOR_REQUIRES, COLLECTOR_BLUEPRINT, now);
        v.log("DESIGN_ADOPTED", now, e.apparatus(), e.researcher(), null, evidence.id(), e.id(),
                Provenance.EXPERIMENT_ENGINE, "design", rev.designId(), "revision", String.valueOf(rev.revision()));
    }

    private static void fail(VillageState v, Experiment e, long now) {
        Optional<WorldObservation> opportunity = v.observations().get(e.opportunities().get(0));
        if (opportunity.isEmpty()) {
            abort(v, e, now, "opportunity evidence aged out of the observation tail");
            return;
        }
        ConceptId target = e.hypothesis().target();
        ExperimentOutcome outcome = ExperimentOutcome.failed(v.observations(), e.id(), target, opportunity.get(), now);
        KnowState state = applyToResearcher(v, e, outcome);
        e.end(Experiment.Phase.FAILED, now, opportunity.get().id(),
                e.opportunities().size() + " egg(s) laid, none produced " + e.hypothesis().expected());
        reopenProblem(v, e, now, "experiment failed");
        v.designs().record(DesignRegistry.CHICKEN_COLLECTOR, Map.of(ItemIds.HOPPER, 1), 0, 1, false,
                e.researcher(), e.id(), COLLECTOR_REQUIRES, null, now);
        v.log("EXPERIMENT_FAILED", now, e.apparatus(), e.researcher(), null, opportunity.get().id(), e.id(),
                Provenance.EXPERIMENT_ENGINE, "opportunities", String.valueOf(e.opportunities().size()));
        v.log("CONCEPT_TESTED", now, null, e.researcher(), null, opportunity.get().id(), e.id(),
                Provenance.EXPERIMENT_ENGINE, "concept", target.name(), "state", state.name());
    }

    private static KnowState applyToResearcher(VillageState v, Experiment e, ExperimentOutcome outcome) {
        return v.citizens().get(e.researcher())
                .map(r -> r.knowledge().applyOutcome(outcome))
                .orElse(KnowState.UNKNOWN);
    }

    private static void reopenProblem(VillageState v, Experiment e, long now, String note) {
        if (e.hypothesis().problemId() != null) {
            v.problems().setStatus(e.hypothesis().problemId(), ResearchProblem.Status.OPEN, now, note);
        }
    }

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }
}
