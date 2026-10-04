package dev.emerald.core.job;

import dev.emerald.core.research.Experiment;
import dev.emerald.core.world.Box;
import dev.emerald.core.world.Pos;

import java.util.UUID;

/** Stand by the pen while the experiment runs, so the researcher is the nearest witness. */
public final class WatchExperimentRoutine extends AbstractRoutine {
    private final UUID experimentId;

    public WatchExperimentRoutine(UUID experimentId) {
        this.experimentId = experimentId;
    }

    @Override
    public TaskType task() {
        return TaskType.WATCH_EXPERIMENT;
    }

    @Override
    public RoutineStatus step(RoutineContext ctx) {
        var e = ctx.village().experiments().get(experimentId);
        if (e.isEmpty() || e.get().phase() != Experiment.Phase.RUNNING) {
            detail = "experiment over";
            return RoutineStatus.NOTHING;
        }
        Box pen = e.get().region();
        Pos spot = new Pos(pen.max().x() + 2, pen.min().y(), (pen.min().z() + pen.max().z()) / 2);
        detail = walk(ctx, spot, 3.0) ? "watching the pen" : "walking to the pen";
        return RoutineStatus.RUNNING;
    }
}
