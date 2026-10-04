package dev.emerald.core.job;

import dev.emerald.core.citizen.SkillType;
import dev.emerald.core.event.Provenance;
import dev.emerald.core.education.Writing;
import dev.emerald.core.knowledge.ConceptId;
import dev.emerald.core.world.Pos;

import java.util.Optional;

/**
 * Write a tested result into the village library. Costs one real book (requested through the
 * RequestBoard); the writing cites the observation that proved the result.
 */
public final class WriteBookRoutine extends AbstractRoutine {
    public static final String BOOK = "minecraft:book";

    private final ConceptId concept;

    public WriteBookRoutine(ConceptId concept) {
        this.concept = concept;
    }

    @Override
    public TaskType task() {
        return TaskType.WRITE_BOOK;
    }

    @Override
    public SkillType skill() {
        return SkillType.RESEARCH;
    }

    @Override
    public RoutineStatus step(RoutineContext ctx) {
        var v = ctx.village();
        var me = ctx.citizen();
        if (me.carried().count(BOOK) == 0) {
            if (v.requests().activeFor(me.id(), BOOK).isEmpty()) {
                var r = v.requests().open(me.id(), BOOK, 1, v.libraryPos(), null, ctx.now());
                v.log("REQUEST_OPENED", ctx.now(), v.libraryPos(), me.id(), null, null, r.id(), Provenance.JOB_SYSTEM,
                        "item", BOOK, "count", "1", "purpose", "write " + concept);
            }
            detail = "waiting for a blank book to record " + concept;
            return RoutineStatus.RUNNING;
        }
        Pos library = v.libraryPos();
        detail = "writing up " + concept;
        if (!walk(ctx, library, 3.0)) {
            return walkTimedOut() ? fail("library unreachable") : RoutineStatus.RUNNING;
        }
        Optional<Writing> w = v.library().write(me, concept, ctx.now());
        if (w.isEmpty()) {
            detail = "nothing new to write about " + concept;
            return RoutineStatus.NOTHING;
        }
        me.carried().extract(BOOK, 1);
        v.economy().consumed(BOOK, 1, ctx.now());
        v.log("BOOK_WRITTEN", ctx.now(), library, me.id(), w.get().id(), w.get().sourceObservation(), null,
                Provenance.JOB_SYSTEM, "concept", concept.name(), "state", w.get().state().name());
        detail = "wrote " + concept + " (" + w.get().state() + ")";
        return RoutineStatus.DONE;
    }
}
