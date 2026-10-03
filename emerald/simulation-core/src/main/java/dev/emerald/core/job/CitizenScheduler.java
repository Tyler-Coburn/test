package dev.emerald.core.job;

import dev.emerald.core.citizen.CitizenRecord;
import dev.emerald.core.construction.ConstructionProject;
import dev.emerald.core.event.Provenance;
import dev.emerald.core.request.ResourceRequest;
import dev.emerald.core.research.Experiment;
import dev.emerald.core.utility.Need;
import dev.emerald.core.utility.NeedsModel;
import dev.emerald.core.utility.UtilityInputs;
import dev.emerald.core.utility.UtilityScorer;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Bridges utility and jobs for loaded citizens: every utility interval it drifts needs and picks a
 * need; every step it advances the citizen's current routine and writes the task onto the record
 * for the body layer to execute. Routines are transient and re-chosen after reloads.
 */
public final class CitizenScheduler {
    private final Map<UUID, Routine> routines = new HashMap<>();
    private final Map<UUID, Long> lastUtility = new HashMap<>();

    public void tick(RoutineContext ctx, boolean threatened, boolean night) {
        CitizenRecord c = ctx.citizen();
        if (!c.alive()) {
            routines.remove(c.id());
            return;
        }
        long now = ctx.now();
        Long last = lastUtility.get(c.id());
        if (last == null || now - last >= ctx.config().utilityIntervalTicks() || threatened) {
            if (last != null) {
                NeedsModel.advance(c, last, now, c.currentNeed() == Need.SLEEP);
            }
            lastUtility.put(c.id(), now);
            Need need = UtilityScorer.pick(new UtilityInputs(c.hunger(), c.energy(), threatened, night, c.role(), c.caution()));
            if (need != c.currentNeed()) {
                routines.remove(c.id());
                c.setNeed(need);
            }
        }
        if (c.currentNeed() == Need.FLEE) {
            routines.remove(c.id());
            c.setTask(TaskType.FLEE, "fleeing a threat");
            return;
        }
        Routine routine = routines.get(c.id());
        if (routine == null) {
            routine = choose(ctx, c.currentNeed());
            if (routine == null) {
                c.setTask(c.role() == dev.emerald.core.citizen.Role.GUARD ? TaskType.PATROL : TaskType.IDLE, "nothing to do");
                return;
            }
            routines.put(c.id(), routine);
        }
        RoutineStatus status = routine.step(ctx);
        c.setTask(routine.task(), routine.describe());
        if (status != RoutineStatus.RUNNING) {
            routines.remove(c.id());
            if (status == RoutineStatus.FAILED) {
                ctx.village().log("TASK_FAILED", now, ctx.body().position(), c.id(), null, null, null,
                        Provenance.JOB_SYSTEM, "task", routine.task().name(), "reason", routine.failureReason());
            }
        }
    }

    /** Drops transient state for a citizen (body unloaded or died). */
    public void forget(UUID citizenId) {
        routines.remove(citizenId);
        lastUtility.remove(citizenId);
    }

    static Routine choose(RoutineContext ctx, Need need) {
        return switch (need) {
            case EAT -> new EatRoutine();
            case SLEEP -> new SleepRoutine();
            case WORK -> work(ctx);
            case FLEE -> null;
        };
    }

    static Routine work(RoutineContext ctx) {
        var v = ctx.village();
        var c = ctx.citizen();
        switch (c.role()) {
            case FARMER:
                return new FarmerRoutine();
            case GENERAL: {
                for (ResourceRequest r : v.requests().active()) {
                    if (r.state() == ResourceRequest.State.CLAIMED && c.id().equals(r.carrier())) {
                        return new CourierRoutine(r.id());
                    }
                }
                Optional<ResourceRequest> next = v.requests().nextClaimable(ctx.world().warehouse());
                return next.<Routine>map(r -> new CourierRoutine(r.id())).orElse(null);
            }
            case BUILDER: {
                Optional<ConstructionProject> project = v.construction().activeFor(c.id());
                return project.<Routine>map(p -> new BuilderRoutine(p.id())).orElse(null);
            }
            case RESEARCHER: {
                for (Experiment e : v.experiments().active()) {
                    if (c.id().equals(e.researcher())) {
                        return e.phase() == Experiment.Phase.SETUP
                                ? new ExperimentSetupRoutine(e.id())
                                : new WatchExperimentRoutine(e.id());
                    }
                }
                return null;
            }
            default:
                return null;
        }
    }
}
