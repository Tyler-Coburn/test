package dev.emerald.core.job;

import dev.emerald.core.citizen.SkillType;
import dev.emerald.core.event.Provenance;
import dev.emerald.core.knowledge.ConceptId;

/** Go to the library and read the writing on one concept. Documents carry their tested state. */
public final class StudyRoutine extends AbstractRoutine {
    private final ConceptId concept;

    public StudyRoutine(ConceptId concept) {
        this.concept = concept;
    }

    @Override
    public TaskType task() {
        return TaskType.STUDY;
    }

    @Override
    public SkillType skill() {
        return SkillType.RESEARCH;
    }

    @Override
    public RoutineStatus step(RoutineContext ctx) {
        var v = ctx.village();
        var w = v.library().find(concept);
        if (w.isEmpty()) {
            return RoutineStatus.NOTHING;
        }
        detail = "walking to the library to read about " + concept;
        if (!walk(ctx, v.libraryPos(), 3.0)) {
            return walkTimedOut() ? fail("library unreachable") : RoutineStatus.RUNNING;
        }
        if (ctx.citizen().knowledge().readWriting(w.get(), ctx.now())) {
            v.log("BOOK_READ", ctx.now(), v.libraryPos(), ctx.citizen().id(), w.get().id(), w.get().sourceObservation(),
                    null, Provenance.TEACHING, "concept", concept.name(),
                    "state", ctx.citizen().knowledge().state(concept).name());
        }
        detail = "read about " + concept;
        return RoutineStatus.DONE;
    }
}
