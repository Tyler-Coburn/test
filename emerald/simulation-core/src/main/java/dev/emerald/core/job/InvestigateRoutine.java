package dev.emerald.core.job;

import dev.emerald.core.citizen.SkillType;
import dev.emerald.core.knowledge.ConceptId;
import dev.emerald.core.knowledge.KnowState;
import dev.emerald.core.world.Box;
import dev.emerald.core.world.Pos;

/**
 * Go and see the problem first-hand: stand by the pen until the researcher has witnessed the
 * grounding observation (an egg being laid). Hypotheses come after observation, not before.
 */
public final class InvestigateRoutine extends AbstractRoutine {
    private final ConceptId mustWitness;

    public InvestigateRoutine(ConceptId mustWitness) {
        this.mustWitness = mustWitness;
    }

    @Override
    public TaskType task() {
        return TaskType.INVESTIGATE;
    }

    @Override
    public SkillType skill() {
        return SkillType.RESEARCH;
    }

    @Override
    public RoutineStatus step(RoutineContext ctx) {
        if (ctx.citizen().knowledge().state(mustWitness) != KnowState.UNKNOWN) {
            detail = "has seen " + mustWitness + " first-hand";
            return RoutineStatus.DONE;
        }
        Box pen = ctx.village().pen();
        if (pen == null) {
            return RoutineStatus.NOTHING;
        }
        Pos spot = new Pos((pen.min().x() + pen.max().x()) / 2, pen.min().y(), pen.max().z() + 2);
        detail = walk(ctx, spot, 2.5) ? "watching the pen for " + mustWitness : "going to observe the pen";
        return RoutineStatus.RUNNING;
    }
}
