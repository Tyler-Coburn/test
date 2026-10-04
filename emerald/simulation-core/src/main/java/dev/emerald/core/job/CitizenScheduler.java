package dev.emerald.core.job;

import dev.emerald.core.citizen.CitizenRecord;
import dev.emerald.core.citizen.Role;
import dev.emerald.core.construction.ConstructionProject;
import dev.emerald.core.education.Library;
import dev.emerald.core.education.Writing;
import dev.emerald.core.event.Provenance;
import dev.emerald.core.knowledge.KnowState;
import dev.emerald.core.knowledge.KnowledgeEntry;
import dev.emerald.core.request.ResourceRequest;
import dev.emerald.core.research.Experiment;
import dev.emerald.core.utility.Need;
import dev.emerald.core.utility.NeedsModel;
import dev.emerald.core.utility.UtilityInputs;
import dev.emerald.core.utility.UtilityScorer;
import dev.emerald.core.village.VillageBias;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Bridges utility and jobs for loaded citizens: every utility interval it drifts needs and picks a
 * need; every step it advances the citizen's routine and writes the task onto the record for the
 * body layer. Finished routines feed skill experience and the village procedure metrics.
 * Routines are transient and re-chosen after reloads.
 */
public final class CitizenScheduler {
    /** Completion experience for a finished routine of a given skill. */
    public static final int ROUTINE_XP = 5;

    private final Map<UUID, Routine> routines = new HashMap<>();
    private final Map<UUID, Long> routineStart = new HashMap<>();
    private final Map<UUID, Long> lastUtility = new HashMap<>();
    /** After "nothing to do", a citizen does not look for work again until this tick (saves world scans). */
    private final Map<UUID, Long> idleUntil = new HashMap<>();
    public static final int IDLE_BACKOFF_TICKS = 100;

    public void tick(RoutineContext ctx, boolean threatened, boolean night) {
        CitizenRecord c = ctx.citizen();
        if (!c.alive()) {
            forget(c.id());
            return;
        }
        long now = ctx.now();
        Long last = lastUtility.get(c.id());
        if (last == null || now - last >= ctx.config().utilityIntervalTicks() || threatened) {
            if (last != null) {
                NeedsModel.advance(c, last, now, c.currentNeed() == Need.SLEEP);
            }
            lastUtility.put(c.id(), now);
            Need need = UtilityScorer.pick(new UtilityInputs(c.hunger(), c.energy(), threatened, night,
                    c.effectiveRole(), c.caution()));
            if (need != c.currentNeed()) {
                routines.remove(c.id());
                idleUntil.remove(c.id());
                c.setNeed(need);
            }
        }
        if (c.currentNeed() == Need.FLEE) {
            routines.remove(c.id());
            c.setTask(TaskType.FLEE, "fleeing a threat");
            return;
        }
        Routine routine = routines.get(c.id());
        if (routine != null && c.effectiveRole() == Role.GUARD && c.currentNeed() == Need.WORK
                && (routine instanceof DefendRoutine) != threatened) {
            routine = null;   // threat appeared or ended: switch between patrol and defence
        }
        if (routine == null && now < idleUntil.getOrDefault(c.id(), Long.MIN_VALUE)) {
            c.setTask(TaskType.IDLE, "nothing to do");
            return;
        }
        if (routine == null) {
            routine = choose(ctx, c.currentNeed(), threatened);
            if (routine == null) {
                routines.remove(c.id());
                idleUntil.put(c.id(), now + IDLE_BACKOFF_TICKS);
                c.setTask(TaskType.IDLE, "nothing to do");
                return;
            }
            routines.put(c.id(), routine);
            routineStart.put(c.id(), now);
        }
        RoutineStatus status = routine.step(ctx);
        c.setTask(routine.task(), routine.describe());
        if (status == RoutineStatus.NOTHING) {
            routines.remove(c.id());
            idleUntil.put(c.id(), now + IDLE_BACKOFF_TICKS);
            c.setTask(TaskType.IDLE, routine.describe());
        } else if (status != RoutineStatus.RUNNING) {
            routines.remove(c.id());
            long started = routineStart.getOrDefault(c.id(), now);
            ctx.village().skills().record(routine.procedureId(), status == RoutineStatus.DONE, now - started);
            if (status == RoutineStatus.DONE && routine.skill() != null) {
                c.addXp(routine.skill(), ROUTINE_XP);
            }
            if (status == RoutineStatus.FAILED) {
                ctx.village().log("TASK_FAILED", now, ctx.body().position(), c.id(), null, null, null,
                        Provenance.JOB_SYSTEM, "task", routine.task().name(), "reason", routine.failureReason());
            }
        }
    }

    /** The routine currently held for a citizen, if any (for debug views). */
    public Optional<Routine> current(UUID citizenId) {
        return Optional.ofNullable(routines.get(citizenId));
    }

    /** Drops transient state for a citizen (body unloaded or died). */
    public void forget(UUID citizenId) {
        routines.remove(citizenId);
        routineStart.remove(citizenId);
        idleUntil.remove(citizenId);
        lastUtility.remove(citizenId);
    }

    static Routine choose(RoutineContext ctx, Need need, boolean threatened) {
        return switch (need) {
            case EAT -> new EatRoutine();
            case SLEEP -> new SleepRoutine();
            case WORK -> work(ctx, threatened);
            case FLEE -> null;
        };
    }

    static Routine work(RoutineContext ctx, boolean threatened) {
        var v = ctx.village();
        var c = ctx.citizen();
        switch (c.effectiveRole()) {
            case FARMER:
                return new FarmerRoutine();
            case GENERAL: {
                for (ResourceRequest r : v.requests().active()) {
                    if (r.state() == ResourceRequest.State.CLAIMED && c.id().equals(r.carrier())) {
                        return new CourierRoutine(r.id());
                    }
                }
                return v.requests().nextClaimable(ctx.world().warehouse())
                        .<Routine>map(r -> new CourierRoutine(r.id())).orElse(null);
            }
            case BUILDER: {
                Optional<ConstructionProject> project = v.construction().activeFor(c.id());
                return project.<Routine>map(p -> new BuilderRoutine(p.id())).orElse(null);
            }
            case RESEARCHER:
                return researcherWork(ctx);
            case GUARD:
                return threatened ? new DefendRoutine() : new PatrolRoutine();
            case CHILD:
                return studyWork(ctx);
            default:
                return null;
        }
    }

    /** Experiments first, then writing results down, then teaching what is known. */
    static Routine researcherWork(RoutineContext ctx) {
        var v = ctx.village();
        var me = ctx.citizen();
        for (Experiment e : v.experiments().active()) {
            if (me.id().equals(e.researcher())) {
                return e.phase() == Experiment.Phase.SETUP
                        ? new ExperimentSetupRoutine(e.id())
                        : new WatchExperimentRoutine(e.id());
            }
        }
        for (KnowledgeEntry e : me.knowledge().entries()) {
            if (!Library.isWritable(e)) continue;
            KnowState writable = e.state() == KnowState.ADOPTED ? KnowState.TESTED_TRUE : e.state();
            boolean written = v.library().find(e.concept()).map(Writing::state).filter(s -> s == writable).isPresent();
            boolean bookBlocked = v.requests().activeFor(me.id(), WriteBookRoutine.BOOK).stream()
                    .anyMatch(r -> r.state() == ResourceRequest.State.BLOCKED);
            if (!written && (!bookBlocked || me.carried().count(WriteBookRoutine.BOOK) > 0)) {
                return new WriteBookRoutine(e.concept());
            }
        }
        return teachWork(ctx);
    }

    /** Teach the first loaded citizen (children first; academic villages teach everyone) who lacks a concept. */
    static Routine teachWork(RoutineContext ctx) {
        var v = ctx.village();
        var me = ctx.citizen();
        List<CitizenRecord> students = v.citizens().alive().stream()
                .filter(s -> !s.id().equals(me.id()))
                .filter(s -> ctx.world().bodyPosition(s.id()).isPresent())
                .filter(s -> s.role() == Role.CHILD || v.bias() == VillageBias.ACADEMIC || s.role() == Role.BUILDER)
                .sorted(Comparator.comparing(s -> s.role() == Role.CHILD ? 0 : 1))
                .toList();
        for (KnowledgeEntry e : me.knowledge().entries()) {
            if (e.sourceObservation() == null || e.state() == KnowState.UNKNOWN) continue;
            for (CitizenRecord s : students) {
                if (s.knowledge().state(e.concept()) == KnowState.UNKNOWN) {
                    return new TeachRoutine(s.id(), e.concept());
                }
            }
        }
        return null;
    }

    /** Children read library books on concepts they know less well than the book does. */
    static Routine studyWork(RoutineContext ctx) {
        var me = ctx.citizen();
        for (Writing w : ctx.village().library().all()) {
            KnowState mine = me.knowledge().state(w.concept());
            if (!mine.isConfirmedTrue() && mine != w.state()) {
                return new StudyRoutine(w.concept());
            }
        }
        return null;
    }
}
