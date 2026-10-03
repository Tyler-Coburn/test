package dev.emerald.core.job;

import dev.emerald.core.world.Pos;

/** Walk home and rest. Energy is restored by NeedsModel while the chosen need is SLEEP. */
public final class SleepRoutine extends AbstractRoutine {
    @Override
    public TaskType task() {
        return TaskType.SLEEP;
    }

    @Override
    public RoutineStatus step(RoutineContext ctx) {
        Pos home = ctx.citizen().home() != null ? ctx.citizen().home() : ctx.village().center();
        if (!walk(ctx, home, 3.0)) {
            detail = "walking home";
            if (walkTimedOut()) {
                detail = "resting where it stands (home unreachable)";
            }
            return RoutineStatus.RUNNING;
        }
        detail = "resting at home";
        return RoutineStatus.RUNNING;
    }
}
