package dev.emerald.core.job;

import dev.emerald.core.world.Pos;

/** Shared walking/timeout plumbing for routines. */
public abstract class AbstractRoutine implements Routine {
    /** Steps allowed for one walk before the target is declared unreachable. */
    public static final int MAX_WALK_STEPS = 120;

    protected String detail = "";
    protected String failure = "";
    private int walkSteps;
    private Pos walkTarget;

    /** Returns true when arrived; counts steps toward the unreachable timeout. */
    protected boolean walk(RoutineContext ctx, Pos target, double reach) {
        if (!target.equals(walkTarget)) {
            walkTarget = target;
            walkSteps = 0;
        }
        walkSteps++;
        return ctx.body().moveTo(target, reach);
    }

    protected boolean walkTimedOut() {
        return walkSteps > MAX_WALK_STEPS;
    }

    protected RoutineStatus fail(String why) {
        failure = why;
        detail = "failed: " + why;
        return RoutineStatus.FAILED;
    }

    @Override
    public String describe() {
        return detail;
    }

    @Override
    public String failureReason() {
        return failure;
    }
}
