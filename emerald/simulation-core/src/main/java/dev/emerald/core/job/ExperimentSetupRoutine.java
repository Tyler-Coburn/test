package dev.emerald.core.job;

import dev.emerald.core.construction.BlueprintBlock;
import dev.emerald.core.event.Provenance;
import dev.emerald.core.observe.ObservationType;
import dev.emerald.core.research.Experiment;
import dev.emerald.core.research.ExperimentEngine;
import dev.emerald.core.world.ItemIds;
import dev.emerald.core.world.Pos;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The researcher builds the apparatus: obtain a real hopper through the RequestBoard, place it at
 * the apparatus position (facing down), then start the observation window.
 */
public final class ExperimentSetupRoutine extends AbstractRoutine {
    public static final BlueprintBlock HOPPER = new BlueprintBlock(new Pos(0, 0, 0), ItemIds.HOPPER,
            Map.of("facing", "down"), ItemIds.HOPPER);

    private final UUID experimentId;

    public ExperimentSetupRoutine(UUID experimentId) {
        this.experimentId = experimentId;
    }

    @Override
    public dev.emerald.core.citizen.SkillType skill() {
        return dev.emerald.core.citizen.SkillType.RESEARCH;
    }

    @Override
    public TaskType task() {
        return TaskType.SETUP_EXPERIMENT;
    }

    @Override
    public RoutineStatus step(RoutineContext ctx) {
        var v = ctx.village();
        var me = ctx.citizen();
        Optional<Experiment> found = v.experiments().get(experimentId);
        if (found.isEmpty() || found.get().phase() != Experiment.Phase.SETUP) {
            return RoutineStatus.NOTHING;
        }
        Experiment e = found.get();
        Pos at = e.apparatus();
        if (!ctx.world().isLoaded(at)) {
            detail = "apparatus site not loaded";
            return RoutineStatus.RUNNING;
        }
        if (ctx.world().matches(at, HOPPER)) {
            ExperimentEngine.apparatusReady(v, e, ctx.now(), ctx.config());
            detail = "apparatus ready; watching";
            return RoutineStatus.DONE;
        }
        if (me.carried().count(ItemIds.HOPPER) == 0) {
            if (v.requests().activeFor(me.id(), ItemIds.HOPPER).isEmpty()) {
                var r = v.requests().open(me.id(), ItemIds.HOPPER, 1, at, null, ctx.now());
                v.log("REQUEST_OPENED", ctx.now(), at, me.id(), null, null, r.id(), Provenance.JOB_SYSTEM,
                        "item", ItemIds.HOPPER, "count", "1", "experiment", e.id().toString());
            }
            detail = "waiting for a hopper";
            return RoutineStatus.RUNNING;
        }
        detail = "placing hopper under the pen at " + at;
        if (!walk(ctx, at, 3.5)) {
            return walkTimedOut() ? fail("cannot reach apparatus site " + at) : RoutineStatus.RUNNING;
        }
        if (!ctx.world().place(at, HOPPER, me.carried())) {
            return fail("hopper placement refused at " + at);
        }
        v.observations().record(ObservationType.BLOCK_PLACED, ctx.world().dimension(), at, ItemIds.HOPPER,
                me.id(), ctx.now(), null);
        ExperimentEngine.apparatusReady(v, e, ctx.now(), ctx.config());
        detail = "hopper placed; experiment running";
        return RoutineStatus.DONE;
    }
}
