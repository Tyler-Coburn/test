package dev.emerald.core.job;

import dev.emerald.core.citizen.SkillType;
import dev.emerald.core.education.Teaching;
import dev.emerald.core.knowledge.ConceptId;
import dev.emerald.core.world.Pos;

import java.util.Optional;
import java.util.UUID;

/** Walk to a student and pass on one concept (the student reaches OBSERVED at most). */
public final class TeachRoutine extends AbstractRoutine {
    private final UUID studentId;
    private final ConceptId concept;

    public TeachRoutine(UUID studentId, ConceptId concept) {
        this.studentId = studentId;
        this.concept = concept;
    }

    @Override
    public TaskType task() {
        return TaskType.TEACH;
    }

    @Override
    public SkillType skill() {
        return SkillType.TEACHING;
    }

    @Override
    public RoutineStatus step(RoutineContext ctx) {
        var student = ctx.village().citizens().get(studentId);
        Optional<Pos> where = ctx.world().bodyPosition(studentId);
        if (student.isEmpty() || !student.get().alive() || where.isEmpty()) {
            return fail("student not available");
        }
        detail = "teaching " + concept + " to " + student.get().name();
        if (!walk(ctx, where.get(), 3.0)) {
            return walkTimedOut() ? fail("could not reach student") : RoutineStatus.RUNNING;
        }
        Teaching.Result r = Teaching.teach(ctx.village(), ctx.citizen(), student.get(), concept, ctx.now());
        detail = "taught " + concept + " to " + student.get().name() + ": " + r;
        return r == Teaching.Result.TAUGHT ? RoutineStatus.DONE : fail("teaching " + r);
    }
}
