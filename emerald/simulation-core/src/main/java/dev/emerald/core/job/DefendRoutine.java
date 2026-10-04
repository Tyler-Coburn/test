package dev.emerald.core.job;

import dev.emerald.core.citizen.SkillType;

/**
 * Marks the guard as defending. Target selection and melee are body behaviour (server-side, on the
 * entity); this routine only holds the task while the threat lasts.
 */
public final class DefendRoutine extends AbstractRoutine {
    @Override
    public TaskType task() {
        return TaskType.DEFEND;
    }

    @Override
    public SkillType skill() {
        return SkillType.COMBAT;
    }

    @Override
    public RoutineStatus step(RoutineContext ctx) {
        detail = "defending the village";
        return RoutineStatus.RUNNING;
    }
}
