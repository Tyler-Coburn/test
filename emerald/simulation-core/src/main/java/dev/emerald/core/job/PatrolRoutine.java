package dev.emerald.core.job;

import dev.emerald.core.citizen.SkillType;
import dev.emerald.core.world.Pos;

/** Walk the four corners of the village perimeter. Runs until the scheduler picks something else. */
public final class PatrolRoutine extends AbstractRoutine {
    private int waypoint;

    @Override
    public TaskType task() {
        return TaskType.PATROL;
    }

    @Override
    public SkillType skill() {
        return SkillType.COMBAT;
    }

    @Override
    public RoutineStatus step(RoutineContext ctx) {
        int r = ctx.config().patrolRadius();
        Pos c = ctx.village().center();
        Pos[] corners = {c.offset(r, 0, r), c.offset(-r, 0, r), c.offset(-r, 0, -r), c.offset(r, 0, -r)};
        Pos target = corners[waypoint % corners.length];
        detail = "patrolling toward " + target;
        if (walk(ctx, target, 3.0) || walkTimedOut()) {
            waypoint++;
        }
        return RoutineStatus.RUNNING;
    }
}
